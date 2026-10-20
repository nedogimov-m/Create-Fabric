package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.content.contraptions.ContraptionAssemblyGraph;
import com.simibubi.create.content.contraptions.ContraptionSettings;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Deployer output transfer debits the real actor slot in the destination's transaction. */
public final class DeployerOutputTransfer {
    private interface Slot {
        ItemStack actual();
        void replacePure(ItemStack stack);
        boolean present();
    }
    public static void inventory(MovementContext context,DeployerFakePlayer actor,int index){
        var inventory=actor.getInventory();
        // Equipment slots use native Inventory's direct underlying container; this output
        // path must retain those source slots too, rather than silently clearing them.
        transfer(context,actor,new Slot(){
            public ItemStack actual(){return inventory.getItem(index);}
            public void replacePure(ItemStack stack){inventory.setItem(index,stack);}
            public boolean present(){return actor.getInventory()==inventory&&index>=0&&index<inventory.getContainerSize();}
        });
    }
    public static void overflow(MovementContext context,DeployerFakePlayer actor,List<ItemStack> actualQueue,int index){
        transfer(context,actor,new Slot(){
            public ItemStack actual(){return actualQueue.get(index);}
            public void replacePure(ItemStack stack){actualQueue.set(index,stack);}
            public boolean present(){return actor.movingOverflow==actualQueue&&index>=0&&index<actualQueue.size();}
        });
    }
    private static boolean live(MovementContext context,DeployerFakePlayer actor){
        if(!(context.world instanceof ServerLevel level)||!level.getServer().isSameThread()||actor.level()!=level
            ||actor.isRemoved()||context.temporaryData!=actor||context.contraption==null)return false;
        var structure=context.contraption;var owner=structure.entity;
        return owner!=null&&owner.level()==level&&owner.getContraption()==structure&&owner.hasOwnedStructure()
            &&level.getEntity(owner.getUUID())==owner&&LoadedEntityAccess.isReady(level,owner.blockPosition())
            &&!ContraptionAssemblyGraph.isRecovering(owner)&&ContraptionAssemblyGraph.isTransferPermitted(structure)
            &&structure.getActors().stream().anyMatch(entry->entry.right==context&&entry.left.pos().equals(context.localPos));
    }
    private static void transfer(MovementContext context,DeployerFakePlayer actor,Slot slot){
        if(context.position==null||!live(context,actor)||!slot.present()||slot.actual().isEmpty())return;
        var source=new ActualOutput(context,actor,slot);
        if(ContraptionSettings.moveItemsToStorage){
            try(var transaction=Transaction.openOuter()){
                StorageUtil.move(source,context.contraption.getStorage().getAllItems(),variant->true,source.getAmount(),transaction);
                transaction.commit();
            }
        }
        if(!live(context,actor)||!slot.present()||slot.actual().isEmpty())return;
        var original=slot.actual();int count=original.getCount();int emitted=PackageItem.isPackage(original)?1:Math.min(count,original.getMaxStackSize());
        var p=context.position;var level=(ServerLevel)context.world;
        if(!LoadedEntityAccess.isReady(level,net.minecraft.core.BlockPos.containing(p)))return;
        var ops=level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var observed=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,original).getOrThrow().copy()).getOrThrow();
        var sample=emitted==count?original:original.copyWithCount(emitted);
        var expected=observed.copyWithCount(emitted);
        var item=new ItemEntity(level,p.x,p.y,p.z,sample);
        item.setDeltaMovement(context.motion.add(0,.5,0).scale(level.getRandom().nextFloat()*.3f));
        Entity endpoint=PackageItem.isPackage(sample)?PackageEntity.fromDroppedItem(level,item,sample):item;
        try(var acceptance=NativeEntityAcceptance.prepare(endpoint,
            ()->live(context,actor)&&slot.present()&&slot.actual()==original&&original.getCount()==count
                &&ItemStack.matches(original,observed)&&ItemStack.matches(payload(endpoint),expected)&&!endpoint.isRemoved(),
            insertion->{insertion.requireCommitting(endpoint);if(emitted==count)slot.replacePure(ItemStack.EMPTY);else original.shrink(emitted);})){
            acceptance.add();
        }
        // Rejected admission leaves the actual source intact; no copied stack is restored.
    }
    private static ItemStack payload(Entity endpoint){return endpoint instanceof ItemEntity item?item.getItem():((PackageEntity)endpoint).getBox();}
    private record State(ItemStack actual,int count) {}
    private static final class ActualOutput extends SnapshotParticipant<State> implements SingleSlotStorage<ItemVariant> {
        private final MovementContext context;private final DeployerFakePlayer actor;private final Slot slot;
        ActualOutput(MovementContext context,DeployerFakePlayer actor,Slot slot){this.context=context;this.actor=actor;this.slot=slot;}
        private ItemStack current(){return slot.present()?slot.actual():ItemStack.EMPTY;}
        @Override protected State createSnapshot(){var stack=current();return new State(stack,stack.getCount());}
        @Override protected void readSnapshot(State state){state.actual.setCount(state.count);slot.replacePure(state.actual);}
        @Override protected void onFinalCommit(){}
        @Override public boolean isResourceBlank(){return current().isEmpty();}
        @Override public ItemVariant getResource(){return ItemVariant.of(current());}
        @Override public long getAmount(){return current().getCount();}
        @Override public long getCapacity(){return current().getMaxStackSize();}
        @Override public boolean supportsInsertion(){return false;}
        @Override public long insert(ItemVariant variant,long maximum,TransactionContext transaction){return 0;}
        @Override public long extract(ItemVariant variant,long maximum,TransactionContext transaction){
            net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions.notBlankNotNegative(variant,maximum);
            if(!live(context,actor)||!slot.present()||!variant.matches(current()))return 0;
            long moved=Math.min(maximum,getAmount());if(moved<=0)return 0;updateSnapshots(transaction);
            var actual=current();actual.shrink((int)moved);if(actual.isEmpty())slot.replacePure(ItemStack.EMPTY);return moved;
        }
    }
    private DeployerOutputTransfer(){}
}
