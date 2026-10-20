package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class ElevatorPulleyBlock extends HorizontalKineticBlock {
    private static final VoxelShape EAST=Shapes.or(Block.box(0,0,0,16,16,2),Block.box(0,0,14,16,16,16),Block.box(2,0,2,14,14,14));
    private static final VoxelShape NORTH=Shapes.or(Block.box(0,0,0,2,16,16),Block.box(14,0,0,16,16,16),Block.box(2,0,2,14,14,14));
    public ElevatorPulleyBlock(Properties properties){super(properties.noOcclusion());}
    @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(HORIZONTAL_FACING).getClockWise().getAxis();}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction side){return side.getAxis()==getRotationAxis(state);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(HORIZONTAL_FACING).getAxis()==Direction.Axis.X?EAST:NORTH;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ElevatorPulleyBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.ELEVATOR_PULLEY?(world,pos,block,be)->ElevatorPulleyBlockEntity.tick(world,pos,block,(ElevatorPulleyBlockEntity)be):null;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(LoadedLevelAccess.blockEntity(level,pos) instanceof ElevatorPulleyBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return InteractionResult.TRY_WITH_EMPTY_HAND;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!player.mayBuild()||player.isSpectator()||player.isShiftKeyDown()||!player.getMainHandItem().isEmpty()||!level.mayInteract(player,pos))return InteractionResult.PASS;if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos) instanceof ElevatorPulleyBlockEntity elevator)elevator.clicked();return InteractionResult.SUCCESS;}
}
