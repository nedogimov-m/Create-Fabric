package com.simibubi.create.content.redstone.diodes;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Toggle only the rising back edge; held input and falling edges preserve the output latch. */
public class ToggleLatchBlock extends AbstractDiodeBlock {
    public static final BooleanProperty POWERING=BooleanProperty.create("powering");
    public ToggleLatchBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(POWERED,false).setValue(POWERING,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED,POWERING);}
    @Override protected int getDelay(BlockState state){return 1;}
    @Override protected int getOutputSignal(BlockGetter level,BlockPos pos,BlockState state){return state.getValue(POWERING)?15:0;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return stack.is(AllItems.WRENCH)?InteractionResult.PASS:useWithoutItem(state,level,pos,player,hit);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        return player.isSpectator()||!player.mayBuild()||player.isShiftKeyDown()||!level.mayInteract(player,pos)?InteractionResult.PASS:activated(level,pos,state);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        boolean previous=state.getValue(POWERED);super.tick(state,level,pos,random);var current=LoadedLevelAccess.blockState(level,pos);
        if(current.is(this)&&current.getValue(POWERED)&&!previous)writeState(level,pos,current.cycle(POWERING));
    }
    protected InteractionResult activated(Level level,BlockPos pos,BlockState state){
        if(!level.isClientSide()){level.playSound(null,pos,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,.3f,state.getValue(POWERING)?.5f:.6f);writeState(level,pos,state.cycle(POWERING));}return InteractionResult.SUCCESS;
    }
}
