package com.simibubi.create.content.kinetics.simpleRelays;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ShaftBlock extends KineticBlock implements SimpleWaterloggedBlock, com.simibubi.create.content.decoration.encasing.EncasableBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape X_SHAPE = box(0, 5, 5, 16, 11, 11);
    private static final VoxelShape Y_SHAPE = box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape Z_SHAPE = box(5, 5, 0, 11, 11, 16);

    public ShaftBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y).setValue(WATERLOGGED, false));
    }

    @Override protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if(player.isShiftKeyDown()||!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos))return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
        var encased=tryEncase(state,level,pos,stack,player,hand,hit);
        if(encased.consumesAction())return encased;
        if(state.is(com.simibubi.create.AllBlocks.SHAFT)&&stack.is(com.simibubi.create.AllBlocks.METAL_GIRDER.asItem())&&state.getValue(AXIS)!=Direction.Axis.Y){
            if(!level.isClientSide()){
                var next=com.simibubi.create.AllBlocks.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState()
                        .setValue(WATERLOGGED,state.getValue(WATERLOGGED))
                        .setValue(com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock.HORIZONTAL_AXIS,state.getValue(AXIS));
                if(!level.setBlock(pos,next,Block.UPDATE_ALL))return net.minecraft.world.InteractionResult.FAIL;
                if(!player.isCreative()){stack.shrink(1);if(stack.isEmpty())player.setItemInHand(hand,net.minecraft.world.item.ItemStack.EMPTY);}
            }return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if(state.is(com.simibubi.create.AllBlocks.SHAFT)||state.is(com.simibubi.create.AllBlocks.POWERED_SHAFT))
            return CogwheelBlockItem.extendShaft(stack,state,level,pos,player,hand,hit);
        return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override protected void tick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,RandomSource random) {
        if(com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlock.stillValid(state,level,pos))level.setBlockAndUpdate(pos,com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlock.getEquivalent(state));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var preferred=com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.getPreferredAxis(context);
        boolean shift=context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown();
        var assisted=CogwheelBlockItem.assistedAxis(context);
        var axis=assisted!=null?assisted:preferred!=null
                ?shift?context.getClickedFace().getAxis():preferred:context.getNearestLookingDirection().getAxis();
        var placed=defaultBlockState().setValue(AXIS,axis)
                .setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER);
        return com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlock.stillValid(placed,context.getLevel(),context.getClickedPos())
                ?com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlock.getEquivalent(placed):placed;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return switch (state.getValue(AXIS)) {
                case X -> state.setValue(AXIS, Direction.Axis.Z);
                case Z -> state.setValue(AXIS, Direction.Axis.X);
                case Y -> state;
            };
        }
        return state;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (getRotationAxis(state)) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }
}
