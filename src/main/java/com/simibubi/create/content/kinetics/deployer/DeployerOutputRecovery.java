package com.simibubi.create.content.kinetics.deployer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import com.simibubi.create.foundation.utility.NativeSavedDataIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** A removed Deployer's actual outputs remain saved until their exact native drops are admitted. */
public final class DeployerOutputRecovery extends SavedData {
    private record Encoded(Vec3 position,ItemStack loose,Optional<CompoundTag> entity,Optional<ResourceKey<Level>> dimension){
        static final Codec<Encoded> CODEC=RecordCodecBuilder.create(i->i.group(
            Vec3.CODEC.fieldOf("Position").forGetter(Encoded::position),
            ItemStack.OPTIONAL_CODEC.fieldOf("Loose").forGetter(Encoded::loose),
            CompoundTag.CODEC.optionalFieldOf("Entity").forGetter(Encoded::entity),
            Level.RESOURCE_KEY_CODEC.optionalFieldOf("Dimension").forGetter(Encoded::dimension)).apply(i,Encoded::new));
    }
    public static final Codec<DeployerOutputRecovery> CODEC=Encoded.CODEC.listOf().fieldOf("Pending").codec()
        .xmap(DeployerOutputRecovery::new,data->data.entries.stream().map(Receipt::encode).toList());
    public static final SavedDataType<DeployerOutputRecovery> TYPE=new SavedDataType<>(Create.asResource("deployer_output_recovery"),DeployerOutputRecovery::new,CODEC,null);
    private List<Receipt> entries=new ArrayList<>();
    private boolean delivering;
    public DeployerOutputRecovery(){}
    private DeployerOutputRecovery(List<Encoded> saved){for(var value:saved){var receipt=new Receipt(value.position,value.loose,value.entity.orElse(null));receipt.dimension=value.dimension.orElse(null);entries.add(receipt);}}
    private static DeployerOutputRecovery get(ServerLevel level){return level.getDataStorage().computeIfAbsent(TYPE);}
    private boolean current(ServerLevel level){return NativeSavedDataIdentity.isCurrent(level,TYPE,this);}

    /** The world data owner exists before all source pointers are revoked without a callback. */
    public static void captureRemoved(DeployerBlockEntity owner){
        if(!(owner.getLevel() instanceof ServerLevel level))return;
        if(!level.getServer().isSameThread())throw new IllegalStateException("Deployer removal belongs to its actual server thread");
        var recovery=get(level);var next=new ArrayList<>(recovery.entries);var position=Vec3.atCenterOf(owner.getBlockPos());
        for(var output:owner.inventoryForRemoval())next.add(new Receipt(position,output,null));
        var cleared=new ArrayList<ItemStack>();
        // All allocations and saved-data decoding precede the actual material handoff.
        if(!recovery.current(level))throw new IllegalStateException("Retired Deployer journal cannot take actual removed outputs");
        owner.releaseRemovedInventory(cleared);recovery.entries=next;recovery.setDirty();
        recovery.deliver(level);
    }
    public static void tick(ServerLevel level){if(level.getGameTime()%10==0)get(level).deliver(level);}
    private void deliver(ServerLevel level){
        if(delivering||!current(level))return;delivering=true;
        try{
            for(var receipt:List.copyOf(entries)){
                if(!current(level))return;
                if(!entries.contains(receipt))continue;
                receipt.retainRejectedCurrent();
                if(receipt.serialized!=null){
                    var id=receipt.serialized.read("UUID",net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
                    var target=receipt.dimension==null?level:level.getServer().getLevel(receipt.dimension);if(target==null||target.getEntity(id)!=null)continue;
                    var problems=new ProblemReporter.Collector();
                    var decoded=EntityType.loadEntityRecursive(TagValueInput.create(problems,target.registryAccess(),receipt.serialized),target,EntitySpawnReason.LOAD,entity->entity);
                    if(!current(level))return;
                    if(!problems.isEmpty()||!(decoded instanceof ItemEntity||decoded instanceof PackageEntity)||!decoded.getUUID().equals(id))
                        throw new IllegalStateException("Invalid saved actual Deployer output entity: "+problems.getReport());
                    receipt.pending=decoded;receipt.serialized=null;setDirty();
                }
                if(receipt.pending==null){
                    if(receipt.loose.isEmpty()){entries.remove(receipt);setDirty();continue;}
                    if(!LoadedEntityAccess.isReady(level,net.minecraft.core.BlockPos.containing(receipt.position)))continue;
                    var original=receipt.loose;int emitted=PackageItem.isPackage(original)?1:Math.min(original.getCount(),original.getMaxStackSize());
                    var sample=emitted==original.getCount()?original:original.copyWithCount(emitted);
                    var p=receipt.position;var item=new ItemEntity(level,p.x,p.y,p.z,sample);item.setDefaultPickUpDelay();
                    Entity endpoint=PackageItem.isPackage(sample)?PackageEntity.fromDroppedItem(level,item,sample):item;
                    if(!current(level))return;
                    if(emitted==original.getCount()){receipt.pending=endpoint;receipt.loose=ItemStack.EMPTY;}
                    else{
                        // These are two finite disjoint portions in the same saved owner;
                        // no native callback occurs between splitting and publishing the child.
                        var child=new Receipt(receipt.position,ItemStack.EMPTY,null);child.pending=endpoint;
                        var next=new ArrayList<>(entries);next.add(child);original.shrink(emitted);entries=next;setDirty();continue;
                    }
                    setDirty();
                }
                var endpoint=receipt.pending;
                if(!(endpoint.level() instanceof ServerLevel actual)||actual.getServer()!=level.getServer()||endpoint.isRemoved()||actual.getEntity(endpoint.getUUID())!=null||!LoadedEntityAccess.isReady(actual,endpoint.blockPosition()))continue;
                try(var acceptance=NativeEntityAcceptance.prepare(endpoint,
                    ()->current(level)&&entries.contains(receipt)&&receipt.pending==endpoint&&!endpoint.isRemoved()&&!payload(endpoint).isEmpty(),
                    insertion->{insertion.requireCommitting(endpoint);entries.remove(receipt);setDirty();})){
                    acceptance.add();
                }finally{if(current(level)&&entries.contains(receipt)){receipt.retainRejectedCurrent();setDirty();}}
            }
        }finally{delivering=false;setDirty();}
    }
    private static ItemStack payload(Entity endpoint){return endpoint instanceof ItemEntity item?item.getItem():((PackageEntity)endpoint).getBox();}
    private static final class Receipt {
        final Vec3 position;ItemStack loose;CompoundTag serialized;Entity pending;ResourceKey<Level> dimension;
        Receipt(Vec3 position,ItemStack loose,CompoundTag serialized){this.position=position;this.loose=loose;this.serialized=serialized;if(serialized!=null&&!loose.isEmpty())throw new IllegalArgumentException("Two owners in saved Deployer output");}
        void retainRejectedCurrent(){if(pending!=null&&(pending.isRemoved()||payload(pending).isEmpty())){loose=payload(pending);pending=null;serialized=null;dimension=null;}}
        Encoded encode(){
            retainRejectedCurrent();if(pending==null)return new Encoded(position,loose,Optional.ofNullable(serialized),Optional.ofNullable(dimension));
            var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,pending.registryAccess());
            if(!pending.save(output))throw new IllegalStateException("Actual rejected Deployer endpoint refused serialization");
            return new Encoded(position,ItemStack.EMPTY,Optional.of(output.buildResult()),Optional.of(pending.level().dimension()));
        }
    }
}
