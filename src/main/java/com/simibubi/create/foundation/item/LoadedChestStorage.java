package com.simibubi.create.foundation.item;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Fabric's default chest fallback calls ChestBlock.getContainer, whose ordinary world reads renew chunk tickets. */
public final class LoadedChestStorage implements Storage<ItemVariant> {
    private static final Map<ChestBlockEntity,Map<Direction,LoadedChestStorage>> CACHE=new WeakHashMap<>();
    private final WeakReference<ChestBlockEntity> owner;
    private final Direction side;
    private LoadedChestStorage(ChestBlockEntity chest,Direction side){owner=new WeakReference<>(chest);this.side=side;}
    public static synchronized Storage<ItemVariant> of(ChestBlockEntity chest,Direction side){return CACHE.computeIfAbsent(chest,ignored->new HashMap<>()).computeIfAbsent(side,ignored->new LoadedChestStorage(chest,side));}
    private static boolean live(ChestBlockEntity chest){return chest!=null&&chest.getLevel()!=null&&!chest.isRemoved()&&LoadedLevelAccess.blockEntity(chest.getLevel(),chest.getBlockPos())==chest;}
    private List<ChestBlockEntity> parts(){
        var chest=owner.get();if(!live(chest))return List.of();var level=chest.getLevel();var pos=chest.getBlockPos();var state=LoadedLevelAccess.blockState(level,pos);
        if(!(state.getBlock() instanceof ChestBlock))return List.of();
        if(state.getValue(ChestBlock.TYPE)==ChestType.SINGLE)return List.of(chest);
        var otherPos=ChestBlock.getConnectedBlockPos(pos,state);if(!level.isLoaded(otherPos))return List.of();var otherState=LoadedLevelAccess.blockState(level,otherPos);
        if(otherState.getBlock()!=state.getBlock()||otherState.getValue(ChestBlock.TYPE)==ChestType.SINGLE||otherState.getValue(ChestBlock.TYPE)==state.getValue(ChestBlock.TYPE)||otherState.getValue(ChestBlock.FACING)!=state.getValue(ChestBlock.FACING)||ChestBlock.getConnectedDirection(otherState)!=ChestBlock.getConnectedDirection(state).getOpposite())return List.of(chest);
        if(!(LoadedLevelAccess.blockEntity(level,otherPos) instanceof ChestBlockEntity other)||!live(other))return List.of();
        return ChestBlock.getBlockType(state)==DoubleBlockCombiner.BlockType.FIRST?List.of(chest,other):List.of(other,chest);
    }
    @Override public long insert(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);long moved=0;for(var part:parts()){moved+=ContainerStorage.of(part,side).insert(resource,amount-moved,tx);if(moved==amount)break;}return moved;}
    @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);long moved=0;for(var part:parts()){moved+=ContainerStorage.of(part,side).extract(resource,amount-moved,tx);if(moved==amount)break;}return moved;}
    @Override public Iterator<StorageView<ItemVariant>> iterator(){
        var captured=parts();var result=new ArrayList<StorageView<ItemVariant>>();for(var part:captured)for(var view:ContainerStorage.of(part,side))result.add(new StorageView<>(){
            private boolean valid(){return parts().equals(captured);}
            @Override public boolean isResourceBlank(){return !valid()||view.isResourceBlank();}
            @Override public ItemVariant getResource(){return valid()?view.getResource():ItemVariant.blank();}
            @Override public long getAmount(){return valid()?view.getAmount():0;}
            @Override public long getCapacity(){return valid()?view.getCapacity():0;}
            @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return valid()?view.extract(resource,amount,tx):0;}
            @Override public StorageView<ItemVariant> getUnderlyingView(){return view.getUnderlyingView();}
        });return result.iterator();
    }
}
