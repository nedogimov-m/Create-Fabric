package com.simibubi.create.content.contraptions.actors.psi;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Optional;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Source alignment, working position and keep-alive/stall protocol for controlled constructs. */
public class PortableStorageInterfaceMovement implements MovementBehaviour {
    public static final String WORKING_POS = "WorkingPos", CLIENT_PREV_POS = "ClientPrevPos";
    public static final class ConnectionAnimation {
        private float previous, value, target;
        public void chase(float target) { this.target = target; }
        void tick() { previous = value; value += Math.clamp(target - value, -.25f, .25f); }
        public float getValue(float partial) { return previous + (value - previous) * partial; }
        public boolean settled() { return Math.abs(target - value) < 1e-5; }
    }
    public static ConnectionAnimation getAnimation(MovementContext context) {
        if (!(context.temporaryData instanceof ConnectionAnimation)) context.temporaryData = new ConnectionAnimation();
        return (ConnectionAnimation) context.temporaryData;
    }
    @Override public Vec3 getActiveAreaOffset(MovementContext context) { return context.state.getValue(PortableStorageInterfaceBlock.FACING).getUnitVec3().scale(1.85f); }
    @Override public boolean disableBlockEntityRendering() { return true; }
    @Override public void visitNewPosition(MovementContext context, BlockPos pos) { if (!findInterface(context, pos)) context.data.remove(WORKING_POS); }
    @Override public void tick(MovementContext context) {
        if (context.position == null) return;
        if (context.world.isClientSide()) {
            getAnimation(context).tick();
            if (!findInterface(context, BlockPos.containing(context.position))) reset(context);
            return;
        }
        var working = context.data.read(WORKING_POS, BlockPos.CODEC).orElse(null);
        if (working == null) { if (context.stall) cancelStall(context); return; }
        var target = Vec3.atCenterOf(working);
        if (!context.stall && context.position.distanceToSqr(target) < context.position.add(context.motion).distanceToSqr(target)) context.stall = true;
        var facing = getCurrentFacingIfValid(context);
        if (facing.isEmpty()) { reset(context); return; }
        var endpoint = getStationaryInterfaceAt(context.world, working, context.state, facing.get());
        if (endpoint == null || endpoint.connectedToAnother(context.contraption)) { reset(context); return; }
        if (endpoint.connectedEntity == null) endpoint.startTransferringTo(context.contraption, endpoint.distance);
        boolean ending = endpoint.transferTimer <= PortableStorageInterfaceBlockEntity.ANIMATION;
        endpoint.keepAlive = 2;
        if (context.stall && ending) context.stall = false;
    }
    protected boolean findInterface(MovementContext context, BlockPos pos) {
        if (context.position == null) return false;
        var facing = getCurrentFacingIfValid(context);
        if (facing.isEmpty()) return false;
        PortableStorageInterfaceBlockEntity endpoint = null;
        for (int i = 0; i < 2; i++) {
            endpoint = getStationaryInterfaceAt(context.world, pos.relative(facing.get(), i), context.state, facing.get());
            if (endpoint != null) break;
        }
        if (endpoint == null || endpoint.connectedToAnother(context.contraption)) return false;
        context.data.store(WORKING_POS, BlockPos.CODEC, endpoint.getBlockPos());
        if (!context.world.isClientSide()) {
            double projected = Vec3.atCenterOf(endpoint.getBlockPos()).subtract(context.position).dot(facing.get().getUnitVec3());
            endpoint.startTransferringTo(context.contraption, (float) (Math.abs(projected) + 1.85f - 1));
        } else {
            context.data.store(CLIENT_PREV_POS, BlockPos.CODEC, pos);
            if (context.contraption.entity.isStalled() || context.motion.lengthSqr() == 0) getAnimation(context).chase(endpoint.getConnectionDistance() / 2);
        }
        return true;
    }
    public static PortableStorageInterfaceBlockEntity getStationaryInterfaceAt(Level level, BlockPos pos, BlockState state, Direction facing) {
        if (!(LoadedLevelAccess.blockEntity(level, pos) instanceof PortableStorageInterfaceBlockEntity endpoint)) return null;
        var target = LoadedLevelAccess.blockState(level, pos);
        return target.getBlock() == state.getBlock() && target.getValue(PortableStorageInterfaceBlock.FACING) == facing.getOpposite() && !endpoint.isPowered() ? endpoint : null;
    }
    public Optional<Direction> getCurrentFacingIfValid(MovementContext context) {
        var vector = context.rotation.apply(context.state.getValue(PortableStorageInterfaceBlock.FACING).getUnitVec3());
        var nearest = Direction.getApproximateNearest(vector);
        return vector.distanceTo(nearest.getUnitVec3()) > .5 ? Optional.empty() : Optional.of(nearest);
    }
    @Override public void cancelStall(MovementContext context) { reset(context); }
    public void reset(MovementContext context) {
        context.data.remove(WORKING_POS); context.data.remove(CLIENT_PREV_POS); context.stall = false; getAnimation(context).chase(0);
    }
}
