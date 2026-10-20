package com.simibubi.create.content.redstone.diodes;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class BrassDiodeBlock extends AbstractDiodeBlock implements EntityBlock {
    public static final BooleanProperty POWERING=BooleanProperty.create("powering"),INVERTED=BooleanProperty.create("inverted");
    public BrassDiodeBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(POWERED,false).setValue(POWERING,false).setValue(INVERTED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED,POWERING,INVERTED);}
    @Override protected int getDelay(BlockState state){return 2;}
    @Override protected int getOutputSignal(BlockGetter level,BlockPos pos,BlockState state){return state.getValue(POWERING)^state.getValue(INVERTED)?15:0;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.is(AllBlocks.PULSE_TIMER)?new PulseTimerBlockEntity(pos,state):state.is(AllBlocks.PULSE_EXTENDER)?new PulseExtenderBlockEntity(pos,state):new PulseRepeaterBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        var expected=state.is(AllBlocks.PULSE_TIMER)?AllBlockEntityTypes.PULSE_TIMER:state.is(AllBlocks.PULSE_EXTENDER)?AllBlockEntityTypes.PULSE_EXTENDER:AllBlockEntityTypes.PULSE_REPEATER;
        return type==expected?(world,pos,block,entity)->BrassDiodeBlockEntity.tick(world,pos,block,(BrassDiodeBlockEntity)entity):null;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return stack.is(AllItems.WRENCH)?InteractionResult.PASS:toggle(level,pos,state,player);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return toggle(level,pos,state,player);}
    public InteractionResult toggle(Level level,BlockPos pos,BlockState state,Player player){
        if(player.isSpectator()||!player.mayBuild()||player.isShiftKeyDown()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(!level.isClientSide()){writeState(level,pos,state.cycle(INVERTED));level.playSound(null,pos,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,.3f,state.getValue(INVERTED)?.5f:.6f);}return InteractionResult.SUCCESS;
    }
    public void setPowering(Level level,BlockPos pos,boolean powering){var state=LoadedLevelAccess.blockState(level,pos);if(state.is(this)&&state.getValue(POWERING)!=powering)writeState(level,pos,state.setValue(POWERING,powering));}
    public void refreshInput(Level level,BlockPos pos,BlockState state){checkTickOnNeighbor(level,pos,state);}
}
