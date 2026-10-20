package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import org.jspecify.annotations.Nullable;

/** Thin source sail/frame, including bounded plane dyeing and native restitution physics. */
public class SailBlock extends DirectionalBlock {
    private final boolean frame;
    private final @Nullable DyeColor color;
    private static final EnumMap<Direction, VoxelShape> CANVAS = new EnumMap<>(Direction.class), FRAME = new EnumMap<>(Direction.class), RING = new EnumMap<>(Direction.class);
    static {
        var ring = Shapes.join(Shapes.box(0, 5/16d, 0, 1, 9/16d, 1), Shapes.box(2/16d, 0, 2/16d, 14/16d, 1, 14/16d), BooleanOp.ONLY_FIRST);
        for (var facing : Direction.values()) {
            CANVAS.put(facing, orient(Shapes.box(0, 5/16d, 0, 1, 10/16d, 1), facing));
            FRAME.put(facing, orient(Shapes.box(0, 5/16d, 0, 1, 9/16d, 1), facing)); RING.put(facing, orient(ring, facing));
        }
    }
    public static SailBlock frame(Properties properties) { return new SailBlock(properties, true, null); }
    public static SailBlock withCanvas(Properties properties, DyeColor color) { return new SailBlock(properties, false, Objects.requireNonNull(color)); }
    protected SailBlock(Properties properties, boolean frame, @Nullable DyeColor color) {
        super(frame ? properties : properties.bounceRestitution(.26f).fallDistanceReduction(1)); this.frame = frame; this.color = color;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    public boolean isFrame() { return frame; }
    public @Nullable DyeColor getColor() { return color; }
    private static Vec3 orient(Vec3 v, Direction facing) {
        return switch (facing) {
            case UP -> v; case DOWN -> new Vec3(v.x, 1-v.y, 1-v.z);
            case NORTH -> new Vec3(v.x, v.z, 1-v.y); case SOUTH -> new Vec3(v.x, 1-v.z, v.y);
            case EAST -> new Vec3(v.y, 1-v.x, v.z); case WEST -> new Vec3(1-v.y, v.x, v.z);
        };
    }
    private static VoxelShape orient(VoxelShape source, Direction facing) {
        var result = new VoxelShape[]{Shapes.empty()}; source.forAllBoxes((x1,y1,z1,x2,y2,z2) -> {
            var a = orient(new Vec3(x1,y1,z1), facing); var b = orient(new Vec3(x2,y2,z2), facing);
            result[0] = Shapes.or(result[0], Shapes.box(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z)));
        }); return result[0];
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return context instanceof PlanePlacement plane ? defaultBlockState().setValue(FACING, plane.facing) : defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(FACING, mirror.mirror(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return (frame ? FRAME : CANVAS).get(state.getValue(FACING)); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return (frame ? RING : CANVAS).get(state.getValue(FACING)); }
    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) { return new ItemStack(frame ? AllBlocks.SAIL_FRAME : AllBlocks.SAIL); }
    @Override public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double distance) { super.fallOn(level, state, pos, entity, frame ? distance : 0); }
    private static List<Direction> planeDirections(BlockPos pos, Vec3 hit, Direction.Axis excluded) {
        var directions = new ArrayList<Direction>(); for (var side : Direction.values()) if (side.getAxis() != excluded) directions.add(side);
        directions.sort(Comparator.comparingDouble(side -> Vec3.atCenterOf(pos.relative(side)).distanceToSqr(hit))); return directions;
    }
    private static final class PlanePlacement extends BlockPlaceContext {
        private final BlockPos target; private final Direction facing;
        PlanePlacement(Player player, InteractionHand hand, BlockHitResult hit, BlockPos target, Direction facing) { super(new UseOnContext(player, hand, hit)); this.target = target; this.facing = facing; }
        @Override public BlockPos getClickedPos() { return target; }
        @Override public boolean replacingClickedOnBlock() { return true; }
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        if (!player.isShiftKeyDown() && stack.getItem() instanceof BlockItem item && (stack.is(AllBlocks.SAIL.asItem()) || stack.is(AllBlocks.SAIL_FRAME.asItem()))) {
            for (var side : planeDirections(pos, hit.getLocation(), state.getValue(FACING).getAxis())) {
                var target = pos.relative(side);
                if (!LoadedLevelAccess.isLoaded(level, target) || !level.mayInteract(player, target) || !LoadedLevelAccess.blockState(level, target).canBeReplaced()) continue;
                return item.place(new PlanePlacement(player, hand, hit, target, state.getValue(FACING)));
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof ShearsItem) {
            if (!level.isClientSide()) { level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1, 1); applyDye(state, level, pos, hit.getLocation(), null); }
            return InteractionResult.SUCCESS;
        }
        if (frame) return InteractionResult.PASS;
        var dye = stack.get(DataComponents.DYE);
        if (dye == null) return InteractionResult.PASS;
        if (!level.isClientSide()) { level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1, 1.1f-level.getRandom().nextFloat()*.2f); applyDye(state, level, pos, hit.getLocation(), dye); }
        return InteractionResult.SUCCESS;
    }
    private static void change(Level level, BlockPos pos, BlockState state) {
        if (!LoadedLevelAccess.isLoaded(level, pos)) return;
        LoadedLevelAccess.setBlockQuiet(level, pos, state);
        for (var side : Direction.values()) { var neighbor = pos.relative(side); if (LoadedLevelAccess.isLoaded(level, neighbor)) level.neighborChanged(LoadedLevelAccess.blockState(level, neighbor), neighbor, state.getBlock(), null, false); }
    }
    public void applyDye(BlockState state, Level level, BlockPos pos, Vec3 hit, @Nullable DyeColor color) {
        if (level.isClientSide() || !LoadedLevelAccess.isLoaded(level, pos)) return;
        var replacement = (color == null ? AllBlocks.SAIL_FRAME : AllBlocks.DYED_SAILS.get(color)).defaultBlockState().setValue(FACING, state.getValue(FACING));
        if (state != replacement) { change(level, pos, replacement); return; }
        for (var side : planeDirections(pos, hit, state.getValue(FACING).getAxis())) {
            var at = pos.relative(side); var next = LoadedLevelAccess.blockState(level, at);
            if (!(next.getBlock() instanceof SailBlock sail) || sail.frame || next.getValue(FACING) != state.getValue(FACING) || next == state) continue;
            change(level, at, replacement); return;
        }
        var frontier = new ArrayDeque<BlockPos>(); var visited = new HashSet<BlockPos>(); frontier.add(pos); int budget = 100;
        while (!frontier.isEmpty() && budget-- >= 0) {
            var current = frontier.removeFirst(); visited.add(current);
            for (var side : Direction.values()) {
                if (side.getAxis() == state.getValue(FACING).getAxis()) continue;
                var at = current.relative(side); if (visited.contains(at) || !LoadedLevelAccess.isLoaded(level, at)) continue;
                var next = LoadedLevelAccess.blockState(level, at);
                if (!(next.getBlock() instanceof SailBlock sail) || sail.frame && color != null || next.getValue(FACING) != state.getValue(FACING)) continue;
                if (next != state) change(level, at, replacement); frontier.addLast(at); visited.add(at);
            }
        }
    }
}
