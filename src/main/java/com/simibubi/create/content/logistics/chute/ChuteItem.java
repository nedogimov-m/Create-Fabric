package com.simibubi.create.content.logistics.chute;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.material.Fluids;

public final class ChuteItem extends BlockItem {
    public ChuteItem(ChuteBlock block,Properties properties){super(block,properties);}
    @Override public InteractionResult place(BlockPlaceContext context){var face=context.getClickedFace();var placedOn=context.getClickedPos().relative(face.getOpposite());var level=context.getLevel();if(!AbstractChuteBlock.isChute(LoadedLevelAccess.blockState(level,placedOn))||context.isSecondaryUseActive()||face.getAxis().isVertical())return super.place(context);var correct=context.getClickedPos().above();var state=LoadedLevelAccess.blockState(level,correct);if(state.canBeReplaced())return super.place(BlockPlaceContext.at(context,correct,face));if(!(state.getBlock() instanceof ChuteBlock chute)||level.isClientSide())return InteractionResult.FAIL;if(chute.getFacing(state)!=Direction.DOWN)return InteractionResult.FAIL;level.setBlockAndUpdate(correct,chute.updateChuteState(state.setValue(ChuteBlock.FACING,face),LoadedLevelAccess.blockState(level,correct.above()),level,correct).setValue(ChuteBlock.WATERLOGGED,level.getFluidState(correct).getType()==Fluids.WATER));return InteractionResult.SUCCESS;}
}
