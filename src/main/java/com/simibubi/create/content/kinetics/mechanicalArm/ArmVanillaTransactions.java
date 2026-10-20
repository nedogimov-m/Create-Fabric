package com.simibubi.create.content.kinetics.mechanicalArm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;

/** Deferred vanilla actions follow Fabric's composter transaction pattern. Shared per actual target. */
final class ArmVanillaTransactions {
    private static final Map<CampfireBlockEntity,java.lang.ref.WeakReference<CampfireAction>> CAMPFIRES=new WeakHashMap<>();
    private static final Map<Level,Map<BlockPos,java.lang.ref.WeakReference<AnchorAction>>> ANCHORS=new WeakHashMap<>();
    static long campfire(CampfireBlockEntity entity,ItemVariant item,long amount,TransactionContext tx) {
        var reference=CAMPFIRES.get(entity);var action=reference==null?null:reference.get();
        if(action==null){action=new CampfireAction(entity);CAMPFIRES.put(entity,new java.lang.ref.WeakReference<>(action));}
        return action.insert(item,amount,tx);
    }
    static long anchor(Level level,BlockPos pos,ItemVariant item,long amount,TransactionContext tx) {
        var locations=ANCHORS.computeIfAbsent(level,ignored->new HashMap<>());
        locations.entrySet().removeIf(entry->entry.getValue().get()==null);
        var reference=locations.get(pos);var action=reference==null?null:reference.get();
        if(action==null){action=new AnchorAction(level,pos);locations.put(pos.immutable(),new java.lang.ref.WeakReference<>(action));}
        return action.insert(item,amount,tx);
    }
    private static final class CampfireAction extends SnapshotParticipant<List<ItemVariant>> {
        private final CampfireBlockEntity entity;
        private final List<ItemVariant> pending=new ArrayList<>();
        CampfireAction(CampfireBlockEntity entity){this.entity=entity;}
        long insert(ItemVariant item,long amount,TransactionContext tx) {
            if(amount<1||entity.isRemoved()||!(entity.getLevel() instanceof ServerLevel server)
                ||LoadedLevelAccess.blockEntity(server,entity.getBlockPos())!=entity)return 0;
            long spaces=entity.getItems().stream().filter(stack->stack.isEmpty()).count();
            if(pending.size()>=spaces||server.recipeAccess().getRecipeFor(RecipeType.CAMPFIRE_COOKING,new SingleRecipeInput(item.toStack()),server).isEmpty())return 0;
            updateSnapshots(tx);pending.add(item);return 1;
        }
        @Override protected List<ItemVariant> createSnapshot(){return List.copyOf(pending);}
        @Override protected void readSnapshot(List<ItemVariant> snapshot){pending.clear();pending.addAll(snapshot);}
        @Override protected void onFinalCommit(){
            var insertions=List.copyOf(pending);pending.clear();
            for(var item:insertions)if(!entity.placeFood((ServerLevel)entity.getLevel(),null,item.toStack()))throw new IllegalStateException("Reserved campfire cooking slot was changed during transaction commit");
        }
    }
    private static final class AnchorAction extends SnapshotParticipant<Integer> {
        private final Level level;private final BlockPos pos;private int pending;
        AnchorAction(Level level,BlockPos pos){this.level=level;this.pos=pos.immutable();}
        long insert(ItemVariant item,long amount,TransactionContext tx){
            var state=LoadedLevelAccess.blockState(level,pos);
            if(amount<1||!item.isOf(Items.GLOWSTONE)||!(state.getBlock() instanceof RespawnAnchorBlock)||state.getValue(RespawnAnchorBlock.CHARGE)+pending>=4)return 0;
            updateSnapshots(tx);pending++;return 1;
        }
        @Override protected Integer createSnapshot(){return pending;}
        @Override protected void readSnapshot(Integer snapshot){pending=snapshot;}
        @Override protected void onFinalCommit(){int count=pending;pending=0;for(int i=0;i<count;i++)RespawnAnchorBlock.charge(null,level,pos,LoadedLevelAccess.blockState(level,pos));}
    }
    private ArmVanillaTransactions(){}
}
