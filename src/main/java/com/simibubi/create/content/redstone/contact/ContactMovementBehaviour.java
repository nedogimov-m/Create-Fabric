package com.simibubi.create.content.redstone.contact;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public class ContactMovementBehaviour implements MovementBehaviour {
    public static final String LAST_CONTACT = "lastContact";
    @Override public Vec3 getActiveAreaOffset(MovementContext context) { return context.state.getValue(RedstoneContactBlock.FACING).getUnitVec3().scale(.65); }
    @Override public void visitNewPosition(MovementContext context, BlockPos pos) {
        if (!(context.world instanceof ServerLevel) || context.firstMovement) return;
        deactivateLastVisitedContact(context);
        if (!matches(context, pos, LoadedLevelAccess.blockState(context.world, pos))) return;
        context.data.store(LAST_CONTACT, BlockPos.CODEC, pos);
        tick(context);
    }
    private static boolean matches(MovementContext context, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof RedstoneContactBlock)) return false;
        var normal = context.rotation.apply(context.state.getValue(RedstoneContactBlock.FACING).getUnitVec3());
        return state.getValue(RedstoneContactBlock.FACING) == Direction.getApproximateNearest(normal).getOpposite();
    }
    @Override public void tick(MovementContext context) {
        if (!(context.world instanceof ServerLevel server) || context.disabled || context.position == null) return;
        var last = context.data.read(LAST_CONTACT, BlockPos.CODEC).orElse(null);
        if (last == null || !last.equals(BlockPos.containing(context.position))) return;
        if (!LoadedLevelAccess.isLoaded(server, last)) return;
        var state = LoadedLevelAccess.blockState(server, last);
        if (!matches(context, last, state)) { deactivateLastVisitedContact(context); return; }
        if(state.getBlock() instanceof com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock){if(context.contraption instanceof com.simibubi.create.content.contraptions.elevator.ElevatorContraption cabin)cabin.broadcastFloorData(server,last);return;}
        RedstoneContactBlock.setPowered(server, last, state, true);
        server.scheduleTick(last, state.getBlock(), 1);
    }
    @Override public void stopMoving(MovementContext context) { deactivateLastVisitedContact(context); }
    @Override public void cancelStall(MovementContext context) { MovementBehaviour.super.cancelStall(context); deactivateLastVisitedContact(context); }
    public void deactivateLastVisitedContact(MovementContext context) {
        var last = context.data.read(LAST_CONTACT, BlockPos.CODEC).orElse(null);
        context.data.remove(LAST_CONTACT);
        if (!(context.world instanceof ServerLevel server) || last == null || !LoadedLevelAccess.isLoaded(server, last)) return;
        var state = LoadedLevelAccess.blockState(server, last);
        if (state.getBlock() instanceof RedstoneContactBlock&&!(state.getBlock() instanceof com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock)) server.scheduleTick(last, state.getBlock(), 1);
    }
    public static boolean isPowering(ServerLevel level, BlockPos pos, BlockState state) {
        for (var owner : level.getEntitiesOfClass(AbstractContraptionEntity.class, new AABB(pos).inflate(2))) {
            var structure = owner.getContraption(); if (structure == null || !owner.isAlive()) continue;
            for (var actor : structure.getActors()) {
                var ctx = actor.right;
                if (ctx == null || ctx.disabled || !(ctx.state.getBlock() instanceof RedstoneContactBlock) || ctx.position == null
                        || !pos.equals(ctx.data.read(LAST_CONTACT, BlockPos.CODEC).orElse(null)) || !pos.equals(BlockPos.containing(ctx.position))) continue;
                if (matches(ctx, pos, state)) return true;
            }
        }
        return false;
    }
}
