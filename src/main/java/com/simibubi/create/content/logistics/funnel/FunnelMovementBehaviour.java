package com.simibubi.create.content.logistics.funnel;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Moving funnels transfer directly between world entities and the exposed mounted inventory. */
public final class FunnelMovementBehaviour implements MovementBehaviour {
    private final boolean hasFilter;
    private FunnelMovementBehaviour(boolean hasFilter) { this.hasFilter = hasFilter; }
    public static FunnelMovementBehaviour andesite() { return new FunnelMovementBehaviour(false); }
    public static FunnelMovementBehaviour brass() { return new FunnelMovementBehaviour(true); }

    @Override public Vec3 getActiveAreaOffset(MovementContext context) {
        var facing = context.state.getValue(FunnelBlock.FACING);
        return facing.getUnitVec3().scale(facing != Direction.UP && context.state.getValue(FunnelBlock.EXTRACTING) ? .15 : .65);
    }

    @Override public void visitNewPosition(MovementContext context, BlockPos pos) {
        if (!(context.world instanceof ServerLevel) || !LoadedLevelAccess.isLoaded(context.world, pos)) return;
        if (context.state.getValue(FunnelBlock.EXTRACTING)) extract(context, pos);
        else collect(context, pos);
    }

    private void extract(MovementContext context, BlockPos pos) {
        if (context.position == null || !LoadedLevelAccess.blockState(context.world, pos).getCollisionShape(context.world, pos).isEmpty()) return;
        var point = context.state.getValue(FunnelBlock.FACING) == Direction.DOWN ? context.position : context.position.add(0, -.5, 0);
        var dropCell = BlockPos.containing(point);
        if (!LoadedLevelAccess.isLoaded(context.world, dropCell)
                || !context.world.getEntitiesOfClass(ItemEntity.class, new AABB(dropCell), Entity::isAlive).isEmpty()) return;
        int amount = hasFilter ? context.blockEntityData.getIntOr("FilterAmount", 64) : 1;
        boolean upTo = context.blockEntityData.getBooleanOr("UpTo", true);
        // Legacy filtering count zero means any amount (one stack).
        if (amount == 0) { amount = 64; upTo = true; }
        try (var transaction = Transaction.openOuter()) {
            var stack = FunnelTransfer.extract(context.contraption.getStorage().getAllItems(),
                    item -> context.getFilterFromBE().test(context.world, item), amount, !upTo, transaction);
            if (stack.isEmpty()) return;
            var entity = new ItemEntity(context.world, point.x, point.y, point.z, stack);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPickUpDelay(5);
            if (!context.world.addFreshEntity(entity)) return;
            transaction.commit();
        }
        context.world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1 / 16f, .1f);
    }

    private void collect(MovementContext context, BlockPos pos) {
        for (var entity : context.world.getEntities((Entity) null, new AABB(pos), e -> e instanceof ItemEntity || e instanceof PackageEntity)) {
            if (!entity.isAlive()) continue;
            var stack = entity instanceof ItemEntity item ? item.getItem().copy() : ((PackageEntity) entity).getBox().copy();
            if (stack.isEmpty() || !context.getFilterFromBE().test(context.world, stack)) continue;
            long inserted;
            try (var transaction = Transaction.openOuter()) {
                inserted = context.contraption.getStorage().getAllItems().insert(ItemVariant.of(stack), stack.getCount(), transaction);
                if (inserted == 0) continue;
                transaction.commit();
            }
            var remainder = stack.copyWithCount(stack.getCount() - (int) inserted);
            if (remainder.isEmpty()) entity.discard();
            else if (entity instanceof ItemEntity item) item.setItem(remainder);
        }
    }
}
