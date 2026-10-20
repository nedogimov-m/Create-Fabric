package com.simibubi.create.content.kinetics.press;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.mixin.accessor.SavedDataStorageAccessor;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Paid real world-processing outputs survive native refusal and removal/reload of their source press. */
public final class PressingWorldOutputRecovery extends SavedData {
    private record Encoded(Vec3 position,ItemStack loose,Optional<CompoundTag> entity,Optional<ResourceKey<Level>> dimension){
        static final Codec<Encoded> CODEC=RecordCodecBuilder.create(i->i.group(
            Vec3.CODEC.fieldOf("Position").forGetter(Encoded::position),
            ItemStack.OPTIONAL_CODEC.fieldOf("Loose").forGetter(Encoded::loose),
            CompoundTag.CODEC.optionalFieldOf("Entity").forGetter(Encoded::entity),
            Level.RESOURCE_KEY_CODEC.optionalFieldOf("Dimension").forGetter(Encoded::dimension)).apply(i,Encoded::new));
    }
    public static final Codec<PressingWorldOutputRecovery> CODEC=Encoded.CODEC.listOf().fieldOf("Pending").codec()
        .xmap(PressingWorldOutputRecovery::new,data->data.entries.stream().map(Receipt::encode).toList());
    public static final SavedDataType<PressingWorldOutputRecovery> TYPE=new SavedDataType<>(Create.asResource("pressing_world_output_recovery"),PressingWorldOutputRecovery::new,CODEC,null);
    private List<Receipt> entries=List.of();
    private boolean delivering;
    public PressingWorldOutputRecovery(){}
    private PressingWorldOutputRecovery(List<Encoded> saved){
        var loaded=new ArrayList<Receipt>();
        for(var value:saved){var receipt=new Receipt(value.position,value.loose,value.entity.orElse(null));receipt.dimension=value.dimension.orElse(null);loaded.add(receipt);}
        entries=List.copyOf(loaded);
    }
    private static PressingWorldOutputRecovery get(ServerLevel level){return level.getDataStorage().computeIfAbsent(TYPE);}
    private boolean canonical(ServerLevel level){
        var current=((SavedDataStorageAccessor)level.getDataStorage()).create$getCache().get(TYPE);
        return current!=null&&current.orElse(null)==this;
    }

    /** The existing unpaid recipe rolls are passed unchanged; no RNG or recipe is re-evaluated here. */
    static Source prepareSource(MechanicalPressBlockEntity press,ItemEntity input,int consumed){return new Source(press,input,consumed);}
    static boolean process(Source source,List<ItemStack> outputs){
        var level=source.level;var input=source.input;
        if(!level.getServer().isSameThread())return false;
        if(!source.valid()||!source.press.ownsWorldProcessingRolls(outputs,source.actual,source.consumed))return false;
        var recovery=get(level);var previous=recovery.entries;
        var endpoints=new ArrayList<ItemEntity>();
        for(var stack:outputs)if(!stack.isEmpty())endpoints.add(new ItemEntity(level,input.getX(),input.getY(),input.getZ(),stack.copy(),0,0,0));
        if(endpoints.isEmpty()){
            if(!recovery.canonical(level)||recovery.entries!=previous||!source.valid()
                ||!source.press.ownsWorldProcessingRolls(outputs,source.actual,source.consumed))return false;
            source.debitPure();return true;
        }
        var first=endpoints.getFirst();var expected=snapshot(level,first.getItem());
        var next=new ArrayList<>(previous);
        for(int i=1;i<endpoints.size();i++){var receipt=new Receipt(source.position,ItemStack.EMPTY,null);receipt.pending=endpoints.get(i);next.add(receipt);}
        var prepared=List.copyOf(next);boolean paid;
        if(!source.valid()||!source.press.ownsWorldProcessingRolls(outputs,source.actual,source.consumed)
            ||!recovery.canonical(level)||recovery.entries!=previous||first.isRemoved()||first.level()!=level
            ||level.getEntity(first.getUUID())!=null||!LoadedEntityAccess.isReady(level,first.blockPosition()))return false;
        try(var acceptance=NativeEntityAcceptance.prepare(first,
            ()->recovery.canonical(level)&&recovery.entries==previous&&source.valid()
                &&source.press.ownsWorldProcessingRolls(outputs,source.actual,source.consumed)
                &&!first.isRemoved()&&ItemStack.matches(first.getItem(),expected),
            insertion->{
                insertion.requireCommitting(first);
                // Complete ownership lists and snapshots already exist. These operations are callback-free.
                recovery.entries=prepared;source.debitPure();recovery.setDirty();
            })){
            acceptance.add();paid=acceptance.insertion()!=null;
        }
        if(paid)recovery.deliver(level);
        return paid;
    }
    private static ItemStack snapshot(ServerLevel level,ItemStack stack){
        var ops=level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return ItemStack.OPTIONAL_CODEC.parse(ops,ItemStack.OPTIONAL_CODEC.encodeStart(ops,stack).getOrThrow().copy()).getOrThrow();
    }
    /** A read witness, not an inventory or a refundable copy of the physical source. */
    static final class Source {
        final MechanicalPressBlockEntity press;final PressingBehaviour cycle;final ItemEntity input;
        final ServerLevel level;final UUID uuid;final int id,consumed;final Vec3 position;final ItemStack actual,observed;
        final Collection<BlockEntityBehaviour> installedRegistry;
        final PressingBehaviour.Mode mode;final boolean running,impact;final int ticks;
        final net.minecraft.world.level.block.state.BlockState blockState;final float speed;
        Source(MechanicalPressBlockEntity press,ItemEntity input,int consumed){
            this.press=press;cycle=press.getPressingBehaviour();installedRegistry=press.getAllBehaviours();this.input=input;level=(ServerLevel)press.getLevel();
            mode=cycle.mode;running=cycle.running;impact=cycle.hasAppliedImpact();ticks=cycle.runningTicks;
            blockState=press.getBlockState();speed=press.getSpeed();
            uuid=input.getUUID();id=input.getId();position=input.position();actual=input.getItem();observed=snapshot(level,actual);this.consumed=consumed;
        }
        boolean valid(){
            var pos=press.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            return consumed>0&&consumed<=observed.getCount()&&!press.isRemoved()&&!press.isVirtual()&&press.getLevel()==level
                &&chunk!=null&&chunk.getBlockEntities().get(pos)==press&&chunk.getBlockState(pos)==blockState
                &&press.getBlockState()==blockState&&press.getSpeed()==speed&&press.getPressingBehaviour()==cycle
                &&cycle.blockEntity==press&&installedRegistry.contains(cycle)&&cycle.mode==mode&&cycle.running==running
                &&cycle.hasAppliedImpact()==impact&&cycle.runningTicks==ticks&&!input.isRemoved()&&input.level()==level
                &&input.getId()==id&&input.getUUID().equals(uuid)&&level.getEntity(uuid)==input&&input.onGround()
                &&input.position().equals(position)&&LoadedEntityAccess.isReady(level,input.blockPosition())
                &&LoadedEntityAccess.isReady(level,press.getBlockPos())&&input.getItem()==actual
                &&actual.getCount()==observed.getCount()&&ItemStack.matches(actual,observed);
        }
        void debitPure(){actual.shrink(consumed);press.commitWorldProcessingPure(cycle);}
    }

    public static void tick(ServerLevel level){if(level.getGameTime()%10==0)get(level).deliver(level);}
    private void deliver(ServerLevel level){
        if(delivering||!canonical(level))return;delivering=true;
        try{
            for(var receipt:entries){
                if(!canonical(level))return;
                if(!entries.contains(receipt))continue;
                receipt.retainRejectedCurrent();
                if(receipt.serialized!=null){
                    var id=receipt.serialized.read("UUID",net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
                    var target=receipt.dimension==null?level:level.getServer().getLevel(receipt.dimension);
                    if(target==null||target.getEntity(id)!=null)continue;
                    var previous=entries;var problems=new ProblemReporter.Collector();
                    var decoded=EntityType.loadEntityRecursive(TagValueInput.create(problems,target.registryAccess(),receipt.serialized),target,EntitySpawnReason.LOAD,entity->entity);
                    if(!canonical(level)||entries!=previous)return;
                    if(!problems.isEmpty()||!(decoded instanceof ItemEntity item)||!item.getUUID().equals(id))
                        throw new IllegalStateException("Invalid saved actual pressing output endpoint: "+problems.getReport());
                    receipt.pending=item;receipt.serialized=null;setDirty();
                }
                if(receipt.pending==null){
                    if(receipt.loose.isEmpty()){removePure(receipt);setDirty();continue;}
                    var target=receipt.dimension==null?level:level.getServer().getLevel(receipt.dimension);
                    if(target==null||!LoadedEntityAccess.isReady(target,net.minecraft.core.BlockPos.containing(receipt.position)))continue;
                    var original=receipt.loose;int emitted=Math.min(original.getCount(),original.getMaxStackSize());
                    var sample=emitted==original.getCount()?original:original.copyWithCount(emitted);var p=receipt.position;
                    var endpoint=new ItemEntity(target,p.x,p.y,p.z,sample,0,0,0);
                    if(emitted==original.getCount()){receipt.pending=endpoint;receipt.loose=ItemStack.EMPTY;}
                    else{
                        var child=new Receipt(receipt.position,ItemStack.EMPTY,null);child.pending=endpoint;
                        var next=new ArrayList<>(entries);next.add(child);var prepared=List.copyOf(next);
                        original.shrink(emitted);entries=prepared;setDirty();continue;
                    }
                    setDirty();
                }
                var endpoint=receipt.pending;
                if(!(endpoint.level() instanceof ServerLevel target)||target.getServer()!=level.getServer()||endpoint.isRemoved()
                    ||target.getEntity(endpoint.getUUID())!=null||!LoadedEntityAccess.isReady(target,endpoint.blockPosition()))continue;
                var previous=entries;var next=new ArrayList<>(previous);next.remove(receipt);var prepared=List.copyOf(next);
                try(var acceptance=NativeEntityAcceptance.prepare(endpoint,
                    ()->canonical(level)&&entries==previous&&receipt.pending==endpoint&&!endpoint.isRemoved()&&!endpoint.getItem().isEmpty(),
                    insertion->{insertion.requireCommitting(endpoint);entries=prepared;setDirty();})){
                    acceptance.add();
                }finally{if(entries.contains(receipt)){receipt.retainRejectedCurrent();setDirty();}}
            }
        }finally{delivering=false;setDirty();}
    }
    private void removePure(Receipt receipt){var next=new ArrayList<>(entries);next.remove(receipt);entries=List.copyOf(next);}
    private static final class Receipt {
        Vec3 position;ItemStack loose;CompoundTag serialized;ItemEntity pending;ResourceKey<Level> dimension;
        Receipt(Vec3 position,ItemStack loose,CompoundTag serialized){
            this.position=position;this.loose=loose;this.serialized=serialized;
            if(serialized!=null&&!loose.isEmpty())throw new IllegalArgumentException("Two owners for an actual paid pressing output");
        }
        void retainRejectedCurrent(){
            if(pending!=null&&(pending.isRemoved()||pending.getItem().isEmpty())){
                position=pending.position();dimension=pending.level().dimension();loose=pending.getItem();pending=null;serialized=null;
            }
        }
        Encoded encode(){
            retainRejectedCurrent();if(pending==null)return new Encoded(position,loose,Optional.ofNullable(serialized),Optional.ofNullable(dimension));
            var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,pending.registryAccess());
            if(!pending.save(output))throw new IllegalStateException("Actual rejected pressing endpoint refused serialization");
            return new Encoded(pending.position(),ItemStack.EMPTY,Optional.of(output.buildResult()),Optional.of(pending.level().dimension()));
        }
    }
}
