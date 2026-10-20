package com.simibubi.create.content.kinetics.belt.transport;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A transported stack retains its components, motion and processing state across saves. */
public final class TransportedItemStack {
    public final UUID id;
    public ItemStack stack;
    public float beltPosition;
    public float prevBeltPosition;
    public float sideOffset;
    public float prevSideOffset;
    public boolean locked;
    public int angle;
    public int insertedAt;
    public Direction insertedFrom = Direction.UP;
    public BlockPos heldBy;
    /** Arm acquisition holds cargo for a tick and renews the hold while its claw approaches. */
    public boolean lockedExternally;
    public com.simibubi.create.content.kinetics.fan.processing.FanProcessingType processedBy;
    /** Missing addon IDs remain opaque saved metadata, together with all original pending stock. */
    public String unresolvedFanProcessingType;
    public net.fabricmc.fabric.api.transfer.v1.item.ItemVariant pendingFanInput=net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.blank();
    public int pendingFanCount;
    public int processingTime;
    public long lastProcessingTick = Long.MIN_VALUE;
    public List<ItemStack> pendingFanResults;

    public TransportedItemStack(ItemStack stack, float position) {
        this(UUID.randomUUID(), stack, position, (int) (Math.random() * 360));
        if (com.simibubi.create.content.kinetics.belt.BeltHelper.isItemUpright(stack)) angle = 180;
        if (com.simibubi.create.content.logistics.box.PackageItem.isPackage(stack))
            angle = java.util.concurrent.ThreadLocalRandom.current().nextInt(4) * 90 + java.util.concurrent.ThreadLocalRandom.current().nextInt(20) - 10;
        sideOffset = prevSideOffset = (angle - 180) / (360 * 3f);
    }
    private TransportedItemStack(UUID id, ItemStack stack, float position, int angle) { this.id = id; this.stack = stack; beltPosition = prevBeltPosition = position; this.angle = angle; }
    public void clearFanProcessingData() {
        processedBy = null; unresolvedFanProcessingType = null; processingTime = 0; lastProcessingTick = Long.MIN_VALUE; pendingFanResults = null; pendingFanInput=net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.blank(); pendingFanCount=0;
    }
    public TransportedItemStack copy() {
        var result = new TransportedItemStack(id, stack.copy(), beltPosition, angle);
        result.sideOffset = sideOffset; result.prevSideOffset = prevSideOffset;
        result.locked = locked; result.lockedExternally = lockedExternally;
        result.prevBeltPosition = prevBeltPosition; result.insertedAt = insertedAt; result.insertedFrom = insertedFrom;
        result.heldBy = heldBy; result.lockedExternally=lockedExternally; result.processedBy = processedBy; result.processingTime = processingTime; result.lastProcessingTick = lastProcessingTick;
        result.pendingFanResults = pendingFanResults == null ? null : pendingFanResults.stream().map(ItemStack::copy).toList();
        result.unresolvedFanProcessingType=unresolvedFanProcessingType;result.pendingFanInput=pendingFanInput;result.pendingFanCount=pendingFanCount;
        return result;
    }
    private record PendingBinding(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant input,int count){
        static final Codec<PendingBinding> CODEC=RecordCodecBuilder.create(i->i.group(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.CODEC.fieldOf("Input").forGetter(PendingBinding::input),Codec.intRange(1,99).fieldOf("Count").forGetter(PendingBinding::count)).apply(i,PendingBinding::new));
    }
    public static final com.mojang.serialization.MapCodec<TransportedItemStack> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("Id").forGetter(s -> s.id), ItemStack.CODEC.fieldOf("Item").forGetter(s -> s.stack),
            Codec.FLOAT.fieldOf("Pos").forGetter(s -> s.beltPosition), Codec.INT.optionalFieldOf("Angle", 0).forGetter(s -> s.angle),
            Codec.INT.optionalFieldOf("InSegment", 0).forGetter(s -> s.insertedAt), Direction.CODEC.optionalFieldOf("InDirection", Direction.UP).forGetter(s -> s.insertedFrom),
            BlockPos.CODEC.optionalFieldOf("HeldBy").forGetter(s -> Optional.ofNullable(s.heldBy)),
            Codec.STRING.optionalFieldOf("FanProcessingType").forGetter(s -> Optional.ofNullable(s.processedBy==null?s.unresolvedFanProcessingType:FanProcessing.id(s.processedBy))),
            Codec.INT.optionalFieldOf("FanProcessingTime", 0).forGetter(s -> s.processingTime),
            ItemStack.CODEC.listOf().optionalFieldOf("PendingFanResults").forGetter(s -> Optional.ofNullable(s.pendingFanResults)),
            Codec.FLOAT.optionalFieldOf("Offset", 0f).forGetter(s -> s.sideOffset),
            Codec.FLOAT.optionalFieldOf("PrevOffset", 0f).forGetter(s -> s.prevSideOffset),
            Codec.BOOL.optionalFieldOf("Locked", false).forGetter(s -> s.locked),
            Codec.BOOL.optionalFieldOf("LockedExternally", false).forGetter(s -> s.lockedExternally),
            Codec.FLOAT.optionalFieldOf("PrevPos").forGetter(s -> Optional.of(s.prevBeltPosition)),
            PendingBinding.CODEC.optionalFieldOf("PendingFanBinding").forGetter(s -> s.pendingFanResults==null?Optional.empty():Optional.of(new PendingBinding(s.pendingFanInput.isBlank()?net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(s.stack):s.pendingFanInput,s.pendingFanCount==0?s.stack.getCount():s.pendingFanCount)))
    ).apply(i, (id, stack, position, angle, segment, from, held, type, time, pending, offset, prevOffset, locked, lockedExternally, previous, binding) -> {
        var result = new TransportedItemStack(id, stack, Float.isFinite(position) ? position : 0.5f, angle);
        result.insertedAt = segment; result.insertedFrom = from; result.heldBy = held.orElse(null);
        result.processedBy = type.map(FanProcessing::resolve).orElse(null); result.unresolvedFanProcessingType=result.processedBy==null?type.orElse(null):null;
        result.processingTime = time; result.pendingFanResults = pending.orElse(null);
        if(result.pendingFanResults!=null){result.pendingFanInput=binding.map(PendingBinding::input).orElseGet(()->net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(stack));result.pendingFanCount=binding.map(PendingBinding::count).orElse(stack.getCount());}
        result.sideOffset = offset; result.prevSideOffset = prevOffset; result.locked = locked; result.lockedExternally = lockedExternally;
        result.prevBeltPosition = previous.filter(Float::isFinite).orElse(result.beltPosition); return result;
    }));
    public static final Codec<TransportedItemStack> CODEC = MAP_CODEC.codec();
}
