package com.simibubi.create.content.logistics.chute;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class SmartChuteBlockEntity extends ChuteBlockEntity implements BlockEntityBehaviour.Provider {
    public final FilteringBehaviour filtering=new FilteringBehaviour(this).showCountWhen(this::isExtracting).withCallback(stack->inventoryTracker.reset());
    public SmartChuteBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.SMART_CHUTE,pos,state);}
    @Override public boolean canAcceptItem(ItemStack stack){return super.canAcceptItem(stack)&&canActivate()&&filtering.test(stack);}
    @Override protected boolean canActivate(){return !getBlockState().getValue(SmartChuteBlock.POWERED);}
    @Override protected int getExtractionAmount(){return filtering.isCountVisible()&&!filtering.anyAmount()?filtering.getAmount():64;}
    @Override protected boolean isExact(){return filtering.isCountVisible()&&!filtering.anyAmount()&&!filtering.upTo;}
    public boolean isExtracting(){if(level==null)return false;var source=worldPosition.relative(getItemMotion()<0?Direction.UP:Direction.DOWN);var state=LoadedLevelAccess.blockState(level,source);return !AbstractChuteBlock.isChute(state)&&!state.canBeReplaced();}
    @Override public void clearContent(){super.clearContent();filtering.setFilter(ItemStack.EMPTY);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (filtering != null) behaviours.add(filtering);
    }
}
