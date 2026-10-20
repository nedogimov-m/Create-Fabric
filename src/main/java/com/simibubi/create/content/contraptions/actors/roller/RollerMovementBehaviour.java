package com.simibubi.create.content.contraptions.actors.roller;

import com.simibubi.create.Create;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.contraptions.ContraptionSettings;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity.RollingMode;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/** Source normal/profile paving algorithms, with Fabric transactional material extraction. */
public class RollerMovementBehaviour extends BlockBreakingMovementBehaviour {
    public static final ResourceKey<DamageType> ROLLER_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Create.asResource("mechanical_roller"));
    private static final TagKey<Block> TRACKS = TagKey.create(Registries.BLOCK, Create.asResource("tracks"));
    @Override public boolean isActive(MovementContext context) {
        var type = CreateBuiltInRegistries.CONTRAPTION_TYPE.getKey(context.contraption.getType());
        return super.isActive(context) && !Create.asResource("pulley").equals(type)
            && context.relativeMotion.normalize().dot(context.state.getValue(RollerBlock.FACING).getUnitVec3()) > .125;
    }
    @Override public boolean disableBlockEntityRendering() { return true; }
    @Override public net.minecraft.world.phys.Vec3 getActiveAreaOffset(MovementContext context) { return context.state.getValue(RollerBlock.FACING).getUnitVec3().scale(.45).subtract(0, 2, 0); }
    @Override protected float getBlockBreakingSpeed(MovementContext context) { return Mth.clamp(super.getBlockBreakingSpeed(context) * 1.5f, 1 / 128f, 16); }
    @Override public boolean canBreak(Level level, BlockPos pos, BlockState state) {
        if (!LoadedLevelAccess.isLoaded(level, pos)) return false;
        for (var side : Direction.values()) if (!LoadedLevelAccess.isLoaded(level, pos.relative(side)) || LoadedLevelAccess.blockState(level, pos.relative(side)).is(BlockTags.PORTALS)) return false;
        return super.canBreak(level, pos, state) && !state.getCollisionShape(level, pos).isEmpty() && !state.is(TRACKS);
    }
    @Override protected DamageSource getDamageSource(Level level) { return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ROLLER_DAMAGE)); }
    @Override public void visitNewPosition(MovementContext context, BlockPos pos) {
        var level = context.world; if (!LoadedLevelAccess.isLoaded(level, pos)) return;
        if (!LoadedLevelAccess.blockState(level, pos).isRedstoneConductor(level, pos)) damageEntities(context, pos, level);
        if (level.isClientSide()) return;
        var targets = getPositionsToBreak(context, pos); BlockPos hardest = null; float hardness = -1;
        for (var target : targets) { float value = LoadedLevelAccess.blockState(level, target).getDestroySpeed(level, target); if (value >= hardness) { hardest = target; hardness = value; } }
        if (hardest == null) { triggerPaver(context, pos); return; }
        context.data.store("ReferencePos", BlockPos.CODEC, pos); context.data.store("BreakingPos", BlockPos.CODEC, hardest); context.stall = true;
    }
    @Override protected void onBlockBroken(MovementContext context, BlockPos pos, BlockState state) {
        super.onBlockBroken(context, pos, state);
        var reference = context.data.read("ReferencePos", BlockPos.CODEC).orElse(null); if (reference == null) return;
        for (var target : getPositionsToBreak(context, reference)) if (!target.equals(pos)) destroyBlock(context, target);
        triggerPaver(context, reference); context.data.remove("ReferencePos");
    }
    @Override public void cancelStall(MovementContext context) { super.cancelStall(context); if (!context.world.isClientSide()) context.data.remove("ReferencePos"); }
    @Override protected void destroyBlock(MovementContext context, BlockPos pos) {
        if (!(context.world instanceof ServerLevel level) || !LoadedLevelAccess.isLoaded(level, pos)) return;
        var state = LoadedLevelAccess.blockState(level, pos);
        boolean noHarvest = state.is(BlockTags.NEEDS_IRON_TOOL) || state.is(BlockTags.NEEDS_STONE_TOOL) || state.is(BlockTags.NEEDS_DIAMOND_TOOL);
        BlockHelper.destroyBlock(level, pos, 1, stack -> { if (!noHarvest && !level.getRandom().nextBoolean()) collectOrDropItem(context, stack); });
        super.destroyBlock(context, pos);
    }
    protected List<BlockPos> getPositionsToBreak(MovementContext context, BlockPos visited) {
        var result = new ArrayList<BlockPos>(); if (getMode(context) != RollingMode.TUNNEL_PAVE) return result;
        int start = hasMaterial(context) && !getStateToPaveWith(context).isAir() ? 0 : 1;
        var profile = createHeightProfileForTracks(context);
        if (profile == null) { for (int i = start; i <= 2; i++) if (testBreakerTarget(context, visited.above(i), i)) result.add(visited.above(i)); return result; }
        for (var coords : profile.keys()) {
            float height = profile.get(coords); var target = BlockPos.containing(coords.getFirst(), height, coords.getSecond()); boolean slab = height > Math.floor(height) + .45;
            int first = start;
            if (first == 1 && slab && LoadedLevelAccess.blockState(context.world, target.above()).getOptionalValue(SlabBlock.TYPE).orElse(SlabType.DOUBLE) == SlabType.BOTTOM) first = 2;
            for (int i = first; i <= (slab ? 3 : 2); i++) if (testBreakerTarget(context, target.above(i), i)) result.add(target.above(i));
        }
        return result;
    }
    protected boolean testBreakerTarget(MovementContext context, BlockPos target, int columnY) {
        if (!LoadedLevelAccess.isLoaded(context.world, target)) return false;
        var existing = LoadedLevelAccess.blockState(context.world, target); var slab = getStateToPaveWithAsSlab(context);
        if (columnY == 0 && existing.is(getStateToPaveWith(context).getBlock()) || slab != null && columnY == 1 && existing.is(slab.getBlock())) return false;
        return canBreak(context.world, target, existing);
    }
    protected PaveTask createHeightProfileForTracks(MovementContext context) {
        if (context.contraption == null) return null;
        if (!(context.contraption.entity instanceof CarriageContraptionEntity owner))
            return context.contraption instanceof CarriageContraption ? new PaveTask(0, 0) : null;
        // A real carriage which is not currently owned must never turn into the ordinary flat paver.
        // Empty train profiles perform no terrain/material operation and retain no retired owner.
        var empty = new PaveTask(0, 0);
        var carriage = owner.getCarriage();
        if (owner.isRemoved() || owner.level() != context.world || owner.getContraption() != context.contraption
            || !owner.hasOwnedStructure() || carriage == null || carriage.train == null)
            return empty;
        var train = carriage.train;
        if (train.graph == null || train.invalid || train.derailed || train.isRestoring()
            || !owner.isGloballyManaged() || !(context.world instanceof ServerLevel server)
            || server.getEntity(owner.getUUID()) != owner)
            return empty;
        var point = carriage.bogeys.getFirst().trailing();
        var scout = new RollerTravellingPoint();
        scout.node1 = point.node1;
        scout.node2 = point.node2;
        scout.edge = point.edge;
        scout.position = point.position;
        if (!scout.resolve(train.graph)) return empty;

        Direction.Axis axis = Direction.Axis.X;
        var info = context.contraption.getBlocks().get(BlockPos.ZERO);
        if (info != null && info.state().hasProperty(AbstractBogeyBlock.AXIS))
            axis = info.state().getValue(AbstractBogeyBlock.AXIS);
        Direction orientation = owner.getInitialOrientation();
        Direction rollerFacing = context.state.getValue(RollerBlock.FACING);
        int step = orientation.getAxisDirection().getStep();
        double widthWiseOffset = axis.choose(-context.localPos.getZ(), 0, -context.localPos.getX()) * step;
        double lengthWiseOffset = axis.choose(-context.localPos.getX(), 0, context.localPos.getZ()) * step - 1;
        if (rollerFacing == orientation.getClockWise()) lengthWiseOffset += 1;

        var profile = new PaveTask(widthWiseOffset, widthWiseOffset);
        var steering = scout.steer(TravellingPoint.SteerDirection.NONE, new Vec3(0, 1, 0));
        scout.traversalCallback = (edge, coordinates) -> {};
        scout.travel(train.graph, lengthWiseOffset + 1, steering);
        scout.traversalCallback = (edge, coordinates) -> {
            if (edge == null || edge.isInterDimensional()
                || !edge.node1.getLocation().dimension.equals(context.world.dimension())) return;
            TrackPaverV2.pave(profile, train.graph, edge, coordinates.getFirst(), coordinates.getSecond());
        };
        scout.travel(train.graph, 2, steering);
        for (var coordinates : profile.keys())
            profile.put(coordinates.getFirst(), coordinates.getSecond(), context.localPos.getY() + profile.get(coordinates));
        return profile;
    }

    private static final class RollerTravellingPoint extends TravellingPoint {
        java.util.function.BiConsumer<TrackEdge, Couple<Double>> traversalCallback;
        @Override protected Double edgeTraversedFrom(TrackGraph graph, boolean forward,
            IEdgePointListener edgePointListener, ITurnListener turnListener, double previousPosition, double totalDistance) {
            double from = forward ? previousPosition : position;
            double to = forward ? position : previousPosition;
            traversalCallback.accept(edge, Couple.create(from, to));
            return super.edgeTraversedFrom(graph, forward, edgePointListener, turnListener, previousPosition, totalDistance);
        }
    }
    protected void triggerPaver(MovementContext context, BlockPos pos) {
        var toPlace = getStateToPaveWith(context); var slab = getStateToPaveWithAsSlab(context); var mode = getMode(context);
        if (mode != RollingMode.TUNNEL_PAVE && toPlace.isAir()) return;
        var anchors = new ArrayList<Pair<BlockPos, Boolean>>(); var profile = createHeightProfileForTracks(context);
        if (profile == null) anchors.add(Pair.of(pos, false));
        else for (var coords : profile.keys()) { float height = profile.get(coords); anchors.add(Pair.of(BlockPos.containing(coords.getFirst(), height, coords.getSecond()), height > Math.floor(height) + .45)); }
        if (anchors.isEmpty()) return;
        PaveResult result = PaveResult.PASS;
        for (int depth = 0; result == PaveResult.PASS && depth <= Math.max(1, ContraptionSettings.rollerFillDepth); depth++) {
            var layer = new HashSet<Pair<BlockPos, Boolean>>();
            for (var anchor : anchors) {
                int radius = mode == RollingMode.WIDE_FILL ? (depth + 1) / 2 : 0;
                for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
                    if (Math.abs(x) + Math.abs(z) <= radius) layer.add(Pair.of(anchor.getFirst().offset(x, -depth, z), anchor.getSecond()));
            }
            boolean blocked = true, placed = false;
            for (var target : layer) {
                if (slab != null && depth == 0 && target.getSecond()) tryFill(context, target.getFirst().above(), slab);
                result = tryFill(context, target.getFirst(), toPlace); if (result != PaveResult.FAIL) blocked = false; if (result == PaveResult.SUCCESS) placed = true;
            }
            if (placed) result = PaveResult.SUCCESS; else if (!blocked || depth == 0) result = PaveResult.PASS;
            if (result == PaveResult.SUCCESS && toPlace.getBlock() instanceof FallingBlock) result = PaveResult.PASS;
            if (result != PaveResult.PASS || mode == RollingMode.TUNNEL_PAVE) break;
        }
        if (result == PaveResult.SUCCESS) { context.data.putInt("WaitingTicks", 2); context.data.store("LastPos", BlockPos.CODEC, pos); context.stall = true; }
    }
    public static BlockState getStateToPaveWith(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return Blocks.AIR.defaultBlockState();
        var state = item.getBlock().defaultBlockState(); return state.hasProperty(SlabBlock.TYPE) ? state.setValue(SlabBlock.TYPE, SlabType.DOUBLE) : state;
    }
    protected BlockState getStateToPaveWith(MovementContext context) {
        var input = TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, context.world.registryAccess(), context.blockEntityData);
        return getStateToPaveWith(input.read("Filter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }
    protected BlockState getStateToPaveWithAsSlab(MovementContext context) {
        var state = getStateToPaveWith(context); if (state.hasProperty(SlabBlock.TYPE)) return state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock()); String name = id.getPath(); var names = new ArrayList<String>(); names.add(name + "_slab");
        if (name.endsWith("s") && name.length() > 1) names.add(name.substring(0, name.length() - 1) + "_slab");
        if (name.endsWith("planks") && name.length() > 7) names.add(name.substring(0, name.length() - 7) + "_slab");
        for (var candidate : names) { var block = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), candidate)); if (block.isPresent()) return block.get().defaultBlockState(); }
        return null;
    }
    protected RollingMode getMode(MovementContext context) { return RollingMode.values()[Math.clamp(context.blockEntityData.getIntOr("ScrollValue", 0), 0, 2)]; }
    private boolean hasMaterial(MovementContext context) { try (var tx = Transaction.openOuter()) { return extractMaterial(context, tx); } }
    private boolean extractMaterial(MovementContext context, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext tx) {
        var filter = context.getFilterFromBE();
        for (var view : context.contraption.getStorage().getAllItems()) if (!view.isResourceBlank() && view.getAmount() > 0 && filter.test(context.world, view.getResource().toStack()) && view.extract(view.getResource(), 1, tx) == 1) return true;
        return false;
    }
    protected enum PaveResult { FAIL, PASS, SUCCESS }
    protected PaveResult tryFill(MovementContext context, BlockPos target, BlockState toPlace) {
        var level = context.world;
        if (level.isOutsideBuildHeight(target) || !LoadedLevelAccess.isLoaded(level, target)) return PaveResult.FAIL;
        var existing = LoadedLevelAccess.blockState(level, target); if (existing.is(toPlace.getBlock())) return PaveResult.PASS;
        if (!existing.is(BlockTags.LEAVES) && !existing.canBeReplaced() && (!existing.getCollisionShape(level, target).isEmpty() || existing.is(BlockTags.PORTALS))) return PaveResult.FAIL;
        try (var tx = Transaction.openOuter()) {
            if (!extractMaterial(context, tx) || !level.setBlockAndUpdate(target, toPlace)) return PaveResult.FAIL;
            tx.commit(); return PaveResult.SUCCESS;
        }
    }
}
