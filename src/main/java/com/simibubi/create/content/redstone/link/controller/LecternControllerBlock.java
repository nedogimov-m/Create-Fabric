package com.simibubi.create.content.redstone.link.controller;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class LecternControllerBlock extends LecternBlock{
    public LecternControllerBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(HAS_BOOK,true));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new LecternControllerBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.LECTERN_CONTROLLER,LecternControllerBlockEntity::tick);}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side){return 15;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return interact(state,level,pos,player);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return interact(state,level,pos,player);}
    private InteractionResult interact(BlockState state,Level level,BlockPos pos,Player player){
        if(player.isShiftKeyDown()){
            if(level instanceof ServerLevel){if(LoadedLevelAccess.blockEntity(level,pos)instanceof LecternControllerBlockEntity entity)entity.dropController();
                level.playSound(null,pos,AllSoundEvents.CONTROLLER_TAKE,SoundSource.BLOCKS,1,1);
                level.setBlockAndUpdate(pos,Blocks.LECTERN.defaultBlockState().setValue(FACING,state.getValue(FACING)).setValue(POWERED,state.getValue(POWERED)));}
            return InteractionResult.SUCCESS;
        }
        if(!LecternControllerBlockEntity.playerInRange(player,level,pos))return InteractionResult.PASS;
        if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos)instanceof LecternControllerBlockEntity entity)entity.tryStartUsing(player);
        return InteractionResult.SUCCESS;
    }
    public static boolean install(Level level,BlockPos pos,BlockState state,ItemStack held,boolean creative){
        if(level.isClientSide()||!LoadedLevelAccess.isLoaded(level,pos)||LoadedLevelAccess.blockState(level,pos)!=state||!state.is(Blocks.LECTERN)||state.getValue(HAS_BOOK)||held.isEmpty())return false;
        if(!level.setBlockAndUpdate(pos,AllBlocks.LECTERN_CONTROLLER.defaultBlockState().setValue(FACING,state.getValue(FACING)).setValue(POWERED,state.getValue(POWERED))))return false;
        if(!(LoadedLevelAccess.blockEntity(level,pos)instanceof LecternControllerBlockEntity entity))throw new IllegalStateException("Controller lectern registration did not create its block entity");
        entity.setController(held.copyWithCount(1));if(!creative)held.shrink(1);return true;
    }
}
