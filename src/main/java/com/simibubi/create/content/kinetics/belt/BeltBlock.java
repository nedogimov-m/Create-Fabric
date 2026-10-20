package com.simibubi.create.content.kinetics.belt;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.KineticNetworks;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

public final class BeltBlock extends KineticBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BeltSlope> SLOPE = EnumProperty.create("slope", BeltSlope.class);
    public static final EnumProperty<BeltPart> PART = EnumProperty.create("part", BeltPart.class);
    public static final BooleanProperty CASING = BooleanProperty.create("casing");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Set<Level> DISMANTLING = Collections.newSetFromMap(new IdentityHashMap<>());
    public static void editChain(Level level, Runnable edit) {
        if (!DISMANTLING.add(level)) throw new IllegalStateException("Nested belt chain edit");
        try { edit.run(); } finally { DISMANTLING.remove(level); }
    }

    public BeltBlock(Properties properties) {
        super(properties.pushReaction(PushReaction.IMMOVEABLE));
        registerDefaultState(stateDefinition.any().setValue(HORIZONTAL_FACING, Direction.SOUTH).setValue(SLOPE, BeltSlope.HORIZONTAL)
                .setValue(PART, BeltPart.START).setValue(CASING, false).setValue(WATERLOGGED, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(HORIZONTAL_FACING, SLOPE, PART, CASING, WATERLOGGED); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(SLOPE) == BeltSlope.SIDEWAYS ? Direction.Axis.Y : state.getValue(HORIZONTAL_FACING).getClockWise().getAxis(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return state.getValue(PART) != BeltPart.MIDDLE && face.getAxis() == getRotationAxis(state); }
    public static boolean canTransportObjects(BlockState state) { return state.getBlock() instanceof BeltBlock && state.getValue(SLOPE) != BeltSlope.VERTICAL && state.getValue(SLOPE) != BeltSlope.SIDEWAYS; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return BeltShapes.getShape(state); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return BeltShapes.getCollisionShape(state); }
    @Override protected FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BeltBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, AllBlockEntityTypes.BELT, BeltBlockEntity::tick); }
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) { return new ItemStack(AllItems.BELT_CONNECTOR); }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder context) {
        var drops = new ArrayList<>(super.getDrops(state, context));
        if (state.getValue(PART) != BeltPart.MIDDLE) drops.add(new ItemStack(AllBlocks.SHAFT));
        return drops;
    }
    public static BlockPos nextSegmentPosition(BlockState state, BlockPos pos, boolean forward) {
        var part = state.getValue(PART); var slope = state.getValue(SLOPE); var facing = state.getValue(HORIZONTAL_FACING);
        if (forward && part == BeltPart.END || !forward && part == BeltPart.START) return null;
        int step = forward ? 1 : -1;
        if (slope == BeltSlope.VERTICAL) return pos.above(step * facing.getAxisDirection().getStep());
        return pos.relative(facing, step).above(slope == BeltSlope.UPWARD ? step : slope == BeltSlope.DOWNWARD ? -step : 0);
    }
    public static boolean areConnected(BlockPos fromPos, BlockState from, BlockPos toPos, BlockState to) {
        if (!(from.getBlock() instanceof BeltBlock) || !(to.getBlock() instanceof BeltBlock)
                || from.getValue(SLOPE) != to.getValue(SLOPE) || from.getValue(HORIZONTAL_FACING) != to.getValue(HORIZONTAL_FACING)) return false;
        return toPos.equals(nextSegmentPosition(from, fromPos, true)) && fromPos.equals(nextSegmentPosition(to, toPos, false))
                || toPos.equals(nextSegmentPosition(from, fromPos, false)) && fromPos.equals(nextSegmentPosition(to, toPos, true));
    }
    public static List<BlockPos> getBeltChain(Level level, BlockPos first) {
        var result = new ArrayList<BlockPos>();
        BlockPos cursor = first;
        var seen = new HashSet<BlockPos>();
        BlockPos previous = null; BlockState previousState = null;
        while (cursor != null && seen.add(cursor)) {
            if (!level.isInWorldBounds(cursor) || !level.isLoaded(cursor)) break;
            var current = level.getBlockState(cursor);
            if (!(current.getBlock() instanceof BeltBlock) || previous != null && !areConnected(previous, previousState, cursor, current)) break;
            result.add(cursor.immutable()); previous = cursor; previousState = current;
            cursor = nextSegmentPosition(current, cursor, true);
        }
        return result;
    }
    public static void initBelt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel) || DISMANTLING.contains(level) || !level.isLoaded(pos)) return;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BeltBlock)) return;
        BlockPos first = pos;
        var seen = new HashSet<BlockPos>();
        while (true) {
            if (!level.isInWorldBounds(first) || !seen.add(first)) { dismantle(level, pos, state); return; }
            if (!level.isLoaded(first)) return;
            var current = level.getBlockState(first);
            if (!(current.getBlock() instanceof BeltBlock)) { dismantle(level, pos, state); return; }
            if (current.getValue(PART) == BeltPart.START) break;
            var previous = nextSegmentPosition(current, first, false);
            if (previous == null || !level.isInWorldBounds(previous)) { dismantle(level, pos, state); return; }
            if (!level.isLoaded(previous)) return;
            var before = level.getBlockState(previous);
            if (!areConnected(first, current, previous, before)) { dismantle(level, pos, state); return; }
            first = previous;
        }
        var chain = getBeltChain(level, first);
        if (chain.isEmpty()) return;
        var last = chain.getLast();
        var lastState = level.getBlockState(last);
        if (lastState.getValue(PART) != BeltPart.END) {
            var next = nextSegmentPosition(lastState, last, true);
            if (next != null && !level.isLoaded(next)) return;
            dismantle(level, pos, state); return;
        }
        if (chain.size() < 2) { dismantle(level, pos, state); return; }
        for (int i = 0; i < chain.size(); i++) {
            var cell = chain.get(i);
            if (i > 0 && !areConnected(chain.get(i - 1), level.getBlockState(chain.get(i - 1)), cell, level.getBlockState(cell))) { dismantle(level, pos, state); return; }
            if (!(level.getBlockEntity(cell) instanceof BeltBlockEntity)) return;
        }
        for (int i = 0; i < chain.size(); i++) ((BeltBlockEntity) level.getBlockEntity(chain.get(i))).configure(first, i, chain.size());
        KineticNetworks.markDirty((ServerLevel) level);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved); if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        initBelt(level,pos);
        var above=pos.above();var tunnel=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,above);
        if(tunnel.getBlock() instanceof com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock)level.scheduleTick(above,tunnel.getBlock(),1);
    }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction side, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        ticks.scheduleTick(pos, this, 1); return state;
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        if (!moved && !level.getBlockState(pos).is(this)) dismantle(level, pos, state);
    }
    public static void dismantle(Level level, BlockPos brokenPos, BlockState state) {
        if (level.isClientSide() || !level.isLoaded(brokenPos) || !DISMANTLING.add(level)) return;
        try {
            var cells = new LinkedHashMap<BlockPos, BlockState>();
            if (level.getBlockState(brokenPos).getBlock() instanceof BeltBlock) cells.put(brokenPos, state);
            for (boolean forward : new boolean[]{true, false}) {
                BlockPos previous = brokenPos; BlockState previousState = state;
                var seen = new HashSet<BlockPos>(); seen.add(brokenPos);
                while (true) {
                    var next = nextSegmentPosition(previousState, previous, forward);
                    if (next == null || !level.isInWorldBounds(next) || !seen.add(next) || !level.isLoaded(next)) break;
                    var nextState = level.getBlockState(next);
                    if (!areConnected(previous, previousState, next, nextState)) break;
                    cells.put(next, nextState); previous = next; previousState = nextState;
                }
            }
            for (var cell : cells.keySet()) {
                var belt = BeltHelper.getControllerBE(level, cell);
                if (belt != null) belt.getInventory().ejectAll();
            }
            cells.forEach((cell, old) -> {
                var replacement = old.getValue(PART) == BeltPart.MIDDLE ? (old.getValue(WATERLOGGED) ? Blocks.WATER : Blocks.AIR).defaultBlockState()
                        : AllBlocks.SHAFT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS, ((BeltBlock) old.getBlock()).getRotationAxis(old))
                        .setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.WATERLOGGED, old.getValue(WATERLOGGED));
                level.setBlock(cell, replacement, Block.UPDATE_ALL);
            });
        } finally { DISMANTLING.remove(level); }
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.mayBuild() || player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof BeltBlockEntity belt)) return InteractionResult.PASS;
        var dye = stack.get(net.minecraft.core.component.DataComponents.DYE);
        if (dye != null || stack.is(Items.WATER_BUCKET)) return belt.applyColor(dye) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (stack.is(AllItems.BELT_CONNECTOR)) return BeltSlicer.useConnector(level, pos, player, hit);
        if (stack.is(AllBlocks.SHAFT.asItem()) && state.getValue(PART) == BeltPart.MIDDLE) {
            if (!level.isClientSide()) { level.setBlockAndUpdate(pos, state.setValue(PART, BeltPart.PULLEY)); stack.consume(1, player); }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(AllBlocks.ANDESITE_CASING.asItem()) || stack.is(AllBlocks.BRASS_CASING.asItem())) {
            if (!level.isClientSide()) belt.setCasingType(stack.is(AllBlocks.BRASS_CASING.asItem()) ? BeltBlockEntity.CasingType.BRASS : BeltBlockEntity.CasingType.ANDESITE);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild() || !canTransportObjects(state)) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BeltBlockEntity belt) belt.collectItems(player);
        return InteractionResult.SUCCESS;
    }
    public InteractionResult onWrenched(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.getValue(CASING)) { if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BeltBlockEntity belt) belt.setCasingType(BeltBlockEntity.CasingType.NONE); return InteractionResult.SUCCESS; }
        if (state.getValue(PART) == BeltPart.PULLEY) {
            if (!level.isClientSide()) {
                level.setBlockAndUpdate(pos, state.setValue(PART, BeltPart.MIDDLE));
                if (!player.isCreative()) player.getInventory().placeItemBackInInventory(new ItemStack(AllBlocks.SHAFT), Prediction.SERVER_ONLY);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
