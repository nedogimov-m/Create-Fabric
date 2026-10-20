package com.simibubi.create.content.contraptions.actors.contraptionControls;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Actor controls use their original horizontal, waterlogged state and source collision boxes. */
public class ContraptionControlsBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN, VIRTUAL = BooleanProperty.create("virtual"), WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final TagKey<Item> CONTROLLED_ITEMS = TagKey.create(Registries.ITEM, Create.asResource("contraption_controlled"));
    public ContraptionControlsBlock(Properties properties) {
        super(properties); registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(VIRTUAL, false).setValue(WATERLOGGED, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, OPEN, VIRTUAL, WATERLOGGED); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var facing = context.getHorizontalDirection();
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) facing = facing.getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ContraptionControlsBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, AllBlockEntityTypes.CONTRAPTION_CONTROLS, ContraptionControlsBlockEntity::tick); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side, BlockPos next, BlockState neighbor, RandomSource random) {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state.setValue(OPEN, false);
    }
    @Override protected FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState(); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return ContraptionControlsShapes.outline(state.getValue(FACING)); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return ContraptionControlsShapes.collision(state.getValue(FACING)); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) { if (LoadedLevelAccess.blockEntity(level, pos) instanceof ContraptionControlsBlockEntity controls) controls.updatePoweredState(); }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        if (level instanceof ServerLevel server && !previous.is(this)) server.scheduleTick(pos, this, 1);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { if (LoadedLevelAccess.blockEntity(level, pos) instanceof ContraptionControlsBlockEntity controls) controls.updatePoweredState(); }
    public static int yaw(Direction facing) { return switch (facing) { case NORTH -> 180; case EAST -> 90; case WEST -> 270; default -> 0; }; }
    public static Vec3 filterPosition(BlockState state) {
        double radians = Math.toRadians(yaw(state.getValue(FACING))), x = 0, z = -2.5 / 16;
        return new Vec3(.5 + x * Math.cos(radians) + z * Math.sin(radians), 14 / 16d, .5 + z * Math.cos(radians) - x * Math.sin(radians));
    }
    public static boolean onFilter(BlockState state, BlockPos pos, Vec3 hit) { return hit.subtract(Vec3.atLowerCornerOf(pos)).distanceToSqr(filterPosition(state)) < .254 * .254; }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(AllItems.WRENCH) || !player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        if (!player.isShiftKeyDown() && onFilter(state, pos, hit.getLocation())) {
            if (!level.isClientSide() && LoadedLevelAccess.blockEntity(level, pos) instanceof ContraptionControlsBlockEntity controls && !controls.filtering.applyPlayerSample(player, hand)) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("create.logistics.filter.invalid_item"));
                level.playSound(null, pos, AllSoundEvents.DENY, SoundSource.BLOCKS, 1, 1);
            }
            return InteractionResult.SUCCESS;
        }
        return toggle(state, level, pos, player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        if (!player.isShiftKeyDown() && onFilter(state, pos, hit.getLocation())) {
            if (!level.isClientSide() && LoadedLevelAccess.blockEntity(level, pos) instanceof ContraptionControlsBlockEntity controls) controls.filtering.setFilter(ItemStack.EMPTY);
            return InteractionResult.SUCCESS;
        }
        return toggle(state, level, pos, player);
    }
    private InteractionResult toggle(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(LoadedLevelAccess.blockEntity(level, pos) instanceof ContraptionControlsBlockEntity controls)) return InteractionResult.PASS;
        controls.pressButton();
        if (!level.isClientSide()) {
            controls.disabled = !controls.disabled; controls.pressButtonAndSync();
            ContraptionControlsBlockEntity.sendStatus(player, controls.filtering.getFilter(), !controls.disabled);
            level.playSound(null, pos, AllSoundEvents.CONTROLLER_CLICK, SoundSource.BLOCKS, 1, controls.disabled ? .8f : 1.5f);
        }
        return InteractionResult.SUCCESS;
    }
}
