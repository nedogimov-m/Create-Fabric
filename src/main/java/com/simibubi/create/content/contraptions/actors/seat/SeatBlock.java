package com.simibubi.create.content.contraptions.actors.seat;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** World seat interaction, with passenger handoff provided by moving contraptions. */
public final class SeatBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final TagKey<EntityType<?>> IGNORE_SEAT = TagKey.create(Registries.ENTITY_TYPE, Create.asResource("ignore_seat"));
    private static final VoxelShape OUTLINE = box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape COLLISION = box(0, 0, 0, 16, 6, 16);
    private static final VoxelShape PLAYER_COLLISION = box(0, 0, 0, 16, 3, 16);
    private final DyeColor color;

    public SeatBlock(Properties properties, DyeColor color) {
        super(properties.mapColor(color.getMapColor()).sound(SoundType.WOOD).strength(2).ignitedByLava().bounceRestitution(.66f).fallDistanceReduction(.5f));
        this.color = color;
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    public DyeColor getColor() { return color; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(WATERLOGGED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(WATERLOGGED, LoadedLevelAccess.blockState(context.getLevel(), context.getClickedPos()).getFluidState().getType() == Fluids.WATER);
    }
    @Override protected FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return OUTLINE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context instanceof EntityCollisionContext entity && entity.getEntity() instanceof Player ? PLAYER_COLLISION : COLLISION;
    }
    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }
    @Override public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double distance) { super.fallOn(level, state, pos, entity, distance); capture(level, pos, entity); }
    @Override public void bounceOn(Level level, BlockState state, BlockPos pos, Entity entity, double distance) { capture(level, pos, entity); }
    @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) { super.stepOn(level, pos, state, entity); capture(level, pos, entity); }
    private static void capture(Level level, BlockPos pos, Entity entity) { if (!entity.isPassenger() && canBePickedUp(entity)) sitDown(level, pos, entity); }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(ItemStack.EMPTY, state, level, pos, player);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(stack, state, level, pos, player);
    }
    private InteractionResult interact(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
        if (player.isShiftKeyDown() || player instanceof FakePlayer || player.isSpectator() || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        var dye = stack.get(net.minecraft.core.component.DataComponents.DYE);
        if (dye != null && dye != color) {
            if (!level.isClientSide()) level.setBlockAndUpdate(pos, AllBlocks.SEATS.get(dye).defaultBlockState().setValue(WATERLOGGED, state.getValue(WATERLOGGED)));
            return InteractionResult.SUCCESS;
        }
        var seats = level.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
        if (!seats.isEmpty()) {
            var seat = seats.getFirst();
            if (seat.getFirstPassenger() instanceof Player) return InteractionResult.PASS;
            if (!level.isClientSide()) { seat.ejectPassengers(); player.startRiding(seat); }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) sitDown(level, pos, getLeashed(level, player).orElse(player));
        return InteractionResult.SUCCESS;
    }

    public static boolean isSeatOccupied(Level level, BlockPos pos) { return !level.getEntitiesOfClass(SeatEntity.class, new AABB(pos)).isEmpty(); }
    public static Optional<Entity> getLeashed(Level level, Player player) {
        return level.getEntities((Entity) null, player.getBoundingBox().inflate(10), entity -> entity instanceof Mob mob && mob.getLeashHolder() == player && canBePickedUp(entity)).stream().findFirst();
    }
    public static boolean canBePickedUp(Entity entity) {
        if(!com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.seatHostileMobs.get()&&!entity.getType().getCategory().isFriendly())return false;
        return entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof Shulker) && !entity.getType().builtInRegistryHolder().is(IGNORE_SEAT);
    }
    public static void sitDown(Level level, BlockPos pos, Entity passenger) {
        trySitDown(level, pos, passenger);
    }
    /** No IMMEDIATE entity query or source detachment precedes native target readiness/acceptance. */
    public static boolean trySitDown(Level level, BlockPos pos, Entity passenger) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || passenger instanceof FakePlayer
            || !passenger.isAlive() || passenger.level() != level
            || !com.simibubi.create.foundation.utility.LoadedEntityAccess.isReady(level,pos)
            || !(LoadedLevelAccess.blockState(level, pos).getBlock() instanceof SeatBlock)) return false;
        var existing = level.getEntitiesOfClass(SeatEntity.class,new AABB(pos));
        for (var vehicle : existing) {
            if (vehicle.hasPassenger(passenger)) return true;
            if (vehicle.isVehicle()) return false;
        }
        if (!existing.isEmpty()) return passenger.startRiding(existing.getFirst(),true,true);
        var seat = new SeatEntity(level);
        seat.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        if (!level.addFreshEntity(seat) || seat.isRemoved() || server.getEntity(seat.getUUID()) != seat) return false;
        if (!passenger.startRiding(seat, true, true)) { seat.discard(); return false; }
        if (passenger instanceof TamableAnimal animal) animal.setInSittingPose(true);
        return true;
    }
}
