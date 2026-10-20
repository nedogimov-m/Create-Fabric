package com.simibubi.create.content.contraptions.actors.roller;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.AttachedActorBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public class RollerBlock extends AttachedActorBlock implements EntityBlock {
    public static volatile int placementAssistRange = 12;
    public RollerBlock(Properties properties) { super(properties.strength(1.5f, 6).requiresCorrectToolForDrops().sound(SoundType.STONE).mapColor(MapColor.COLOR_GRAY)); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(WATERLOGGED, LoadedLevelAccess.blockState(context.getLevel(), context.getClickedPos()).getFluidState().getType() == Fluids.WATER);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return true; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RollerBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == AllBlockEntityTypes.MECHANICAL_ROLLER ? (world, pos, current, entity) -> ((RollerBlockEntity) entity).filtering.tick() : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (LoadedLevelAccess.blockEntity(level, pos) instanceof RollerBlockEntity roller) roller.searchForSharedValues();
    }
    public static float yaw(Direction facing) { return switch (facing) { case EAST -> 270; case SOUTH -> 180; case WEST -> 90; default -> 0; }; }
    public static Vec3 panelOffset(BlockState state, boolean filter) {
        return StructureTransform.rotate(new Vec3((filter ? 11 : 5) / 16d - .5, 15.5 / 16d - .5, 11 / 16d - .5), yaw(state.getValue(FACING)), Direction.Axis.Y).add(.5, .5, .5);
    }
    public static boolean testPanelHit(BlockState state, BlockPos pos, BlockHitResult hit, boolean filter) {
        return hit.getDirection() == Direction.UP && panelOffset(state, filter).distanceTo(hit.getLocation().subtract(Vec3.atLowerCornerOf(pos))) < .5 / 3;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() || !player.mayBuild() || !stack.is(asItem())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        // Original pole helper: extend the row along the roller's lateral axis, retaining its facing.
        var axis = state.getValue(FACING).getClockWise();
        int first = hit.getLocation().subtract(Vec3.atCenterOf(pos)).dot(axis.getUnitVec3()) < 0 ? -1 : 1;
        var reach = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
        int range = Math.max(3, placementAssistRange) + (reach != null && reach.hasModifier(com.simibubi.create.Create.asResource("single_range_attribute_modifier")) ? 4 : 0);
        for (int sign : new int[]{first, -first}) for (int i = 1; i <= range; i++) {
            var target = pos.relative(axis, sign * i);
            if (!LoadedLevelAccess.isLoaded(level, target)) break;
            var next = LoadedLevelAccess.blockState(level, target);
            if (next.is(this) && next.getValue(FACING).getAxis() == state.getValue(FACING).getAxis()) continue;
            if (!next.canBeReplaced()) break;
            var placement = BlockPlaceContext.at(new BlockPlaceContext(new UseOnContext(player, hand, hit)), target, hit.getDirection());
            return ((RollerBlockItem) stack.getItem()).placeInRow(placement, state.getValue(FACING));
        }
        return InteractionResult.SUCCESS;
    }
}
