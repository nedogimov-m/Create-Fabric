package com.simibubi.create.content.kinetics.simpleRelays.encased;

import java.util.function.Supplier;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.simpleRelays.*;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class EncasedCogwheelBlock extends RotatedPillarKineticBlock
        implements ICogWheel, EncasedBlock, SpecialBlockItemRequirement, TransformableBlock {
    public static final BooleanProperty TOP_SHAFT = BooleanProperty.create("top_shaft");
    public static final BooleanProperty BOTTOM_SHAFT = BooleanProperty.create("bottom_shaft");
    private final boolean large;
    private final Supplier<Block> casing;
    public EncasedCogwheelBlock(Properties properties, boolean large, Supplier<Block> casing) {
        super(properties); this.large = large; this.casing = casing;
        registerDefaultState(defaultBlockState().setValue(TOP_SHAFT, false).setValue(BOTTOM_SHAFT, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(TOP_SHAFT, BOTTOM_SHAFT));
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public Block getCasing() { return casing.get(); }
    @Override public boolean isLargeCog() { return large; }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS)
                && state.getValue(face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? TOP_SHAFT : BOTTOM_SHAFT);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var placedOn = LoadedLevelAccess.blockState(context.getLevel(), context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        var placed = super.getStateForPlacement(context);
        return ICogWheel.isSmallCog(placedOn) ? placed.setValue(AXIS, ((IRotate) placedOn.getBlock()).getRotationAxis(placedOn)) : placed;
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return CogWheelBlock.isValidCogwheelPosition(large, level, pos, state.getValue(AXIS));
    }
    @Override protected boolean skipRendering(BlockState state, BlockState adjacent, Direction face) {
        return state.getBlock() == adjacent.getBlock() && state.getValue(AXIS) == adjacent.getValue(AXIS);
    }
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        var face = context.getClickedFace();
        if (!context.getLevel().isClientSide()) {
            var next = face.getAxis() == state.getValue(AXIS)
                    ? state.cycle(face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? TOP_SHAFT : BOTTOM_SHAFT)
                    : getRotatedBlockState(state, face);
            if (!next.canSurvive(context.getLevel(), context.getClickedPos())) return InteractionResult.PASS;
            KineticBlockEntity.switchToBlockState(context.getLevel(), context.getClickedPos(), next);
            context.getLevel().playSound(null, context.getClickedPos(), AllSoundEvents.WRENCH_ROTATE, SoundSource.BLOCKS, 1, 1);
        }
        return InteractionResult.SUCCESS;
    }
    public BlockState getRotatedBlockState(BlockState state, Direction face) {
        return swapShaftsForRotation(state, Rotation.CLOCKWISE_90, face.getAxis()).setValue(AXIS,
                Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS)).getClockWise(face.getAxis()).getAxis());
    }
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide()) {
            context.getLevel().levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, context.getClickedPos(), Block.getId(state));
            KineticBlockEntity.switchToBlockState(context.getLevel(), context.getClickedPos(),
                    (large ? AllBlocks.LARGE_COGWHEEL : AllBlocks.COGWHEEL).defaultBlockState().setValue(AXIS, state.getValue(AXIS)));
        }
        return InteractionResult.SUCCESS;
    }
    public BlockState swapShafts(BlockState state) {
        boolean top = state.getValue(TOP_SHAFT);
        return state.setValue(TOP_SHAFT, state.getValue(BOTTOM_SHAFT)).setValue(BOTTOM_SHAFT, top);
    }
    public BlockState swapShaftsForRotation(BlockState state, Rotation rotation, Direction.Axis rotationAxis) {
        var axis = state.getValue(AXIS);
        if (rotation == Rotation.NONE || axis == rotationAxis) return state;
        if (rotation == Rotation.CLOCKWISE_180) return swapShafts(state);
        boolean clockwise = rotation == Rotation.CLOCKWISE_90;
        boolean swap = switch (rotationAxis) {
            case X -> axis == Direction.Axis.Z && !clockwise || axis == Direction.Axis.Y && clockwise;
            case Y -> axis == Direction.Axis.X && !clockwise || axis == Direction.Axis.Z && clockwise;
            case Z -> axis == Direction.Axis.Y && !clockwise || axis == Direction.Axis.X && clockwise;
        };
        return swap ? swapShafts(state) : state;
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        var axis = state.getValue(AXIS);
        return axis == Direction.Axis.X && mirror == Mirror.FRONT_BACK || axis == Direction.Axis.Z && mirror == Mirror.LEFT_RIGHT
                ? swapShafts(state) : state;
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return super.rotate(swapShaftsForRotation(state, rotation, Direction.Axis.Y), rotation);
    }
    @Override public BlockState transform(BlockState state, StructureTransform transform) {
        if (transform.mirror != null) state = mirror(state, transform.mirror);
        if (transform.rotationAxis == Direction.Axis.Y) return rotate(state, transform.rotation);
        return swapShaftsForRotation(state, transform.rotation, transform.rotationAxis).setValue(AXIS, transform.rotateAxis(state.getValue(AXIS)));
    }
    public ItemStack getCloneItemStack(BlockState state, Direction face) {
        return new ItemStack(face.getAxis() != getRotationAxis(state) ? large ? AllBlocks.LARGE_COGWHEEL : AllBlocks.COGWHEEL : getCasing());
    }
    @Override public ItemRequirement getRequiredItems(BlockState state, BlockEntity entity) {
        return ItemRequirement.of((large ? AllBlocks.LARGE_COGWHEEL : AllBlocks.COGWHEEL).defaultBlockState(), null);
    }
    @Override public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack heldItem,
            Player player, InteractionHand hand, BlockHitResult ray) {
        var encased = defaultBlockState().setValue(AXIS, state.getValue(AXIS));
        for (var side : Direction.values()) {
            if (side.getAxis() != state.getValue(AXIS)) continue;
            var adjacent = pos.relative(side);
            var adjacentState = LoadedLevelAccess.blockState(level, adjacent);
            if (adjacentState.getBlock() instanceof IRotate rotate && rotate.hasShaftTowards(level, adjacent, adjacentState, side.getOpposite()))
                encased = encased.setValue(side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? TOP_SHAFT : BOTTOM_SHAFT, true);
        }
        KineticBlockEntity.switchToBlockState(level, pos, encased);
    }
}
