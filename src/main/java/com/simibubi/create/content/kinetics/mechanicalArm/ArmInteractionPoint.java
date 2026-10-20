package com.simibubi.create.content.kinetics.mechanicalArm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.item.LoadedItemStorage;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A real loaded block endpoint. Every preview is a Fabric transaction which is aborted. */
public class ArmInteractionPoint {
    private static final Codec<BlockPos> SOURCE_POSITION = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("X").forGetter(BlockPos::getX), Codec.INT.fieldOf("Y").forGetter(BlockPos::getY),
        Codec.INT.fieldOf("Z").forGetter(BlockPos::getZ)).apply(i, BlockPos::new));
    public record PointData(Identifier type, BlockPos pos, Mode mode, CompoundTag extra) {
        private static final Codec<PointData> CORE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("Type").forGetter(PointData::type),
            Codec.either(SOURCE_POSITION, BlockPos.CODEC).xmap(e -> e.map(p -> p,p -> p), com.mojang.datafixers.util.Either::left).fieldOf("Pos").forGetter(PointData::pos),
            Codec.STRING.comapFlatMap(name->{try{return com.mojang.serialization.DataResult.success(Mode.valueOf(name));}catch(IllegalArgumentException invalid){return com.mojang.serialization.DataResult.error(()->"Unknown arm target mode: "+name);}},Mode::name).optionalFieldOf("Mode").xmap(value->value.orElse(Mode.DEPOSIT),java.util.Optional::of).forGetter(PointData::mode)
        ).apply(i, PointData::new));
        /** Retain original flat addon fields beside Type/Pos/Mode, without exposing mutable aliases. */
        public static final Codec<PointData> CODEC=CompoundTag.CODEC.comapFlatMap(tag->{
            var extra=tag.copy();extra.remove("Type");extra.remove("Pos");extra.remove("Mode");
            if(extra.sizeInBytes()>8192)return com.mojang.serialization.DataResult.error(()->"Arm target addon data exceeds8KiB");
            return CORE_CODEC.parse(NbtOps.INSTANCE,tag).map(core->new PointData(core.type,core.pos,core.mode,extra));
        },data->{var tag=data.extra.copy();tag.merge((CompoundTag)CORE_CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow());return tag;});
        public PointData(Identifier type,BlockPos pos,Mode mode){this(type,pos,mode,new CompoundTag());}
        public PointData {
            java.util.Objects.requireNonNull(type);java.util.Objects.requireNonNull(mode);pos=pos.immutable();extra=extra.copy();
            extra.remove("Type");extra.remove("Pos");extra.remove("Mode");
            if(extra.sizeInBytes()>8192)throw new IllegalArgumentException("Arm target addon data exceeds8KiB");
        }
        @Override public CompoundTag extra(){return extra.copy();}
        public PointData transform(StructureTransform transform) { return new PointData(type,transform.applyWithoutOffset(pos),mode,extra); }
    }
    protected final ArmInteractionPointType type;
    protected Level level;
    protected final BlockPos pos;
    protected Mode mode = Mode.DEPOSIT;
    protected BlockState cachedState;
    protected ArmAngleTarget cachedAngles;
    public ArmInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
        this.type=type; this.level=level; this.pos=pos.immutable(); cachedState=state;
    }
    public ArmInteractionPointType getType() { return type; }
    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level=level; cachedAngles=null; }
    public BlockPos getPos() { return pos; }
    public Mode getMode() { return mode; }
    public boolean acceptsMode(Mode mode) { return true; }
    public void cycleMode() { mode = mode==Mode.DEPOSIT ? Mode.TAKE : Mode.DEPOSIT; }
    public Vec3 interactionPosition() { return getInteractionPositionVector(); }
    protected Vec3 getInteractionPositionVector() { return Vec3.atCenterOf(pos); }
    protected Direction getInteractionDirection() { return Direction.DOWN; }
    public ArmAngleTarget getTargetAngles(BlockPos armPos, boolean ceiling) {
        if(cachedAngles==null) cachedAngles=new ArmAngleTarget(armPos,getInteractionPositionVector(),getInteractionDirection(),ceiling);
        return cachedAngles;
    }
    public void updateCachedState() {
        var state=LoadedLevelAccess.blockState(level,pos);
        if(state!=cachedState) cachedAngles=null;
        cachedState=state;
    }
    public boolean isValid() {
        if(!LoadedLevelAccess.isLoaded(level,pos)) return false;
        updateCachedState(); return type.canCreatePoint(level,pos,cachedState);
    }
    public void keepAlive() {}
    protected boolean canInteract(ArmBlockEntity arm){return arm!=null&&arm.isActiveWorldOwner()&&arm.getLevel()==level&&pos.closerThan(arm.getBlockPos(),ArmConfig.get(level))&&isValid();}
    @Nullable protected Storage<ItemVariant> getHandler(ArmBlockEntity arm) { return LoadedItemStorage.find(level,pos,Direction.UP); }
    @Nullable protected Storage<ItemVariant> getExtractionHandler(ArmBlockEntity arm) { return getHandler(arm); }
    public long insert(ArmBlockEntity arm, ItemVariant resource, long amount, TransactionContext tx) {
        if(!canInteract(arm) || amount==0) return 0;
        var handler=getHandler(arm); return handler==null ? 0 : handler.insert(resource,amount,tx);
    }
    public ItemStack insertStack(ArmBlockEntity arm,ItemStack stack,TransactionContext tx) {
        if(stack.isEmpty())return ItemStack.EMPTY;
        return stack.copyWithCount(stack.getCount()-(int)insert(arm,ItemVariant.of(stack),stack.getCount(),tx));
    }
    public ItemStack extract(ArmBlockEntity arm,int slot,int amount,TransactionContext tx) {
        if(!canInteract(arm) || slot<0 || amount<=0) return ItemStack.EMPTY;
        var handler=getExtractionHandler(arm); if(handler==null) return ItemStack.EMPTY;
        int index=0;
        for(var view:handler) {
            if(index++!=slot) continue;
            if(view.isResourceBlank()) return ItemStack.EMPTY;
            var resource=view.getResource();
            return resource.toStack((int)view.extract(resource,Math.min(amount,resource.toStack().getMaxStackSize()),tx));
        }
        return ItemStack.EMPTY;
    }
    public ItemStack insert(ArmBlockEntity arm,ItemStack stack,boolean simulate) {
        if(stack.isEmpty()) return ItemStack.EMPTY;
        try(var tx=Transaction.openOuter()) {
            var result=insertStack(arm,stack,tx);
            if(!simulate) tx.commit(); return result;
        }
    }
    public ItemStack extract(ArmBlockEntity arm,int slot,int amount,boolean simulate) {
        try(var tx=Transaction.openOuter()) { var result=extract(arm,slot,amount,tx); if(!simulate) tx.commit(); return result; }
    }
    public ItemStack extract(ArmBlockEntity arm,int slot,boolean simulate) { return extract(arm,slot,64,simulate); }
    public int getSlotCount(ArmBlockEntity arm) {
        if(!canInteract(arm)) return 0;
        var handler=getExtractionHandler(arm); if(handler==null) return 0;
        int count=0; for(var ignored:handler) count++; return count;
    }
    public PointData data(BlockPos anchor) {
        var key=CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.getKey(type);
        if(key==null) throw new IllegalStateException("Unregistered mechanical arm interaction point");
        var extra=new CompoundTag();serialize(extra,anchor);
        return new PointData(key,pos.subtract(anchor),mode,extra);
    }
    /** Original addon extension points; overrides use native NBT and may call super. */
    protected void serialize(CompoundTag tag,BlockPos anchor){tag.putString("Mode",mode.name());}
    protected void deserialize(CompoundTag tag,BlockPos anchor){mode=Mode.valueOf(tag.getStringOr("Mode","DEPOSIT"));}
    public final CompoundTag serialize(BlockPos anchor) { return (CompoundTag)PointData.CODEC.encodeStart(NbtOps.INSTANCE,data(anchor)).getOrThrow(); }
    @Nullable public static ArmInteractionPoint deserialize(CompoundTag tag,Level level,BlockPos anchor) {
        return PointData.CODEC.parse(NbtOps.INSTANCE,tag).result().map(data -> fromData(data,level,anchor)).orElse(null);
    }
    @Nullable public static ArmInteractionPoint fromData(PointData data,Level level,BlockPos anchor) {
        var type=CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.getValue(data.type());
        var pos=anchor.offset(data.pos());
        if(type==null || !LoadedLevelAccess.isLoaded(level,pos)) return null;
        var state=LoadedLevelAccess.blockState(level,pos);
        if(!type.canCreatePoint(level,pos,state)) return null;
        var point=type.createPoint(level,pos,state);
        if(point==null || !point.acceptsMode(data.mode())) return null;
        point.deserialize((CompoundTag)PointData.CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow(),anchor);
        return point.acceptsMode(point.mode)?point:null;
    }
    public static void transformPos(CompoundTag tag,StructureTransform transform) {
        var data=PointData.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow().transform(transform);
        tag.merge((CompoundTag)PointData.CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow());
    }
    public static boolean isInteractable(Level level,BlockPos pos,BlockState state) { return ArmInteractionPointType.getPrimaryType(level,pos,state)!=null; }
    @Nullable public static ArmInteractionPoint create(Level level,BlockPos pos,BlockState state) {
        var type=ArmInteractionPointType.getPrimaryType(level,pos,state);return type==null?null:type.createPoint(level,pos,state);
    }
    public enum Mode {
        DEPOSIT("mechanical_arm.deposit_to",0xDDC166), TAKE("mechanical_arm.extract_from",0x7FCDE0);
        private final String translationKey; private final int color;
        Mode(String key,int color) { translationKey=key;this.color=color; }
        public String getTranslationKey() { return translationKey; }
        public int getColor() { return color; }
    }
}
