package com.simibubi.create.content.redstone.contact;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;

/** Six-direction source contact. Non-conduction replaces NeoForge shouldCheckWeakPower=false. */
public class RedstoneContactBlock extends DirectionalBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public RedstoneContactBlock(Properties properties) {
        super(properties.isRedstoneConductor((state, level, pos) -> false));
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(POWERED, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, POWERED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var facing = context.getNearestLookingDirection().getOpposite();
        var placed = context.getClickedFace().getOpposite();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown() || hasValidContact(context.getLevel(), context.getClickedPos(), placed)) facing = placed;
        return defaultBlockState().setValue(FACING, facing).setValue(POWERED, hasValidContact(context.getLevel(), context.getClickedPos(), facing));
    }
    public static boolean hasValidContact(BlockGetter level, BlockPos pos, Direction facing) {
        var other = LoadedLevelAccess.blockState(level, pos.relative(facing));
        return other.getBlock() instanceof RedstoneContactBlock && other.getValue(FACING) == facing.getOpposite();
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected boolean isSignalSource(BlockState state) { return state.getValue(POWERED); }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return state.getValue(POWERED) && side != state.getValue(FACING).getOpposite() ? 15 : 0; }
    @Override protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return side != null && side != state.getValue(FACING).getOpposite(); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side, BlockPos otherPos, BlockState other, RandomSource random) {
        if (side != state.getValue(FACING) || level instanceof Level world && !LoadedLevelAccess.isLoaded(world, otherPos)) return state;
        return state.setValue(POWERED, hasValidContact(level, pos, side));
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!(level instanceof ServerLevel server)) return;
        if (!previous.is(this)) server.scheduleTick(pos, this, 1);
        else if (previous.getValue(POWERED) != state.getValue(POWERED) || previous.getValue(FACING) != state.getValue(FACING)) notifyLoadedNeighbors(server, pos);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) {
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) { if (state.getValue(POWERED)) notifyLoadedNeighbors(level, pos); }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean paired = hasValidContact(level, pos, state.getValue(FACING));
        boolean mounted = ContactMovementBehaviour.isPowering(level, pos, state);
        setPowered(level, pos, state, paired || mounted);
        // A live moving contact owns a native saved tick; departure/unload cannot strand POWERED.
        if (mounted) level.scheduleTick(pos, this, 1);
    }
    public static void setPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {
        if (state.getValue(POWERED) == powered || !LoadedLevelAccess.isLoaded(level, pos)) return;
        LoadedLevelAccess.setBlockQuiet(level, pos, state.setValue(POWERED, powered));
    }
    private void notifyLoadedNeighbors(ServerLevel level, BlockPos pos) {
        refreshWireConnections(level, pos);
        for (var side : Direction.values()) {
            var next = pos.relative(side);
            if (LoadedLevelAccess.isLoaded(level, next)) level.neighborChanged(LoadedLevelAccess.blockState(level, next), next, this, null, false);
        }
    }
    public static void refreshWireConnections(ServerLevel level, BlockPos pos) {
        var reader = new com.simibubi.create.foundation.utility.LoadedLevelReader(level);
        var source = LoadedLevelAccess.blockState(level, pos);
        for (var side : Direction.Plane.HORIZONTAL) {
            var next = pos.relative(side); var state = reader.getBlockState(next);
            if (!state.is(Blocks.REDSTONE_WIRE)) continue;
            var updated = state.updateShape(reader, level, next, side.getOpposite(), pos, source, level.getRandom());
            if (updated != state) LoadedLevelAccess.setBlockQuiet(level, next, updated);
        }
    }
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        var facing = state.getValue(FACING); var axis = context.getClickedFace().getAxis();
        if (facing.getAxis() == axis) return InteractionResult.PASS;
        if (context.getLevel() instanceof ServerLevel server) {
            var next = state.setValue(FACING, facing.getClockWise(axis));
            next = next.setValue(POWERED, hasValidContact(server, context.getClickedPos(), next.getValue(FACING)));
            if (LoadedLevelAccess.setBlockQuiet(server, context.getClickedPos(), next)) {
                server.playSound(null, context.getClickedPos(), com.simibubi.create.AllSoundEvents.WRENCH_ROTATE, net.minecraft.sounds.SoundSource.BLOCKS, .25f, 1.25f);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
