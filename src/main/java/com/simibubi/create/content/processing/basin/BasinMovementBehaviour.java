package com.simibubi.create.content.processing.basin;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionNetworking;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.item.ProcessingInventory;
import java.util.ArrayList;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.storage.TagValueInput;

/** The captured basin payload owns its inventory; it is not also mounted storage. */
public final class BasinMovementBehaviour implements MovementBehaviour {
    public static final String DUMPED = "BasinDumped";

    @Override public void tick(MovementContext context) {
        if (!(context.world instanceof ServerLevel) || context.position == null
                || context.contraption.phase() != Contraption.Phase.REMOVED || context.data.getBooleanOr(DUMPED, false)) return;
        var facing = context.rotation.apply(Direction.UP.getUnitVec3());
        if (Direction.getApproximateNearest(facing) != Direction.DOWN) return;
        var inventory = new ProcessingInventory(9, 9, variant -> true, () -> {});
        inventory.load(TagValueInput.create(ProblemReporter.DISCARDING, context.world.registryAccess(), context.blockEntityData).childOrEmpty("Inventory"));
        var spawned = new ArrayList<ItemEntity>();
        var slots = new ArrayList<>(inventory.inputs);
        slots.addAll(inventory.outputs);
        for (var slot : slots) {
            if (slot.isResourceBlank()) continue;
            var point = context.position;
            var entity = new ItemEntity(context.world, point.x, point.y, point.z, slot.stack());
            entity.setDeltaMovement(facing.scale(.05));
            if (!context.world.addFreshEntity(entity)) {
                // A refused spawn leaves the saved inventory intact and removes this attempt's drops.
                spawned.forEach(ItemEntity::discard);
                return;
            }
            spawned.add(entity);
        }
        context.data.putBoolean(DUMPED, true);
        clearCapturedItems(context);
        context.temporaryData = false;
        ContraptionNetworking.syncActorData(context);
    }

    private static void clearCapturedItems(MovementContext context) {
        context.blockEntityData.put("Inventory", new CompoundTag());
        context.contraption.updateBlockEntityPreview(context.localPos, tag -> tag.put("Inventory", new CompoundTag()));
    }

    @Override public void onDataUpdated(MovementContext context) {
        if (!context.world.isClientSide() || !context.data.getBooleanOr(DUMPED, false)) return;
        clearCapturedItems(context);
        if (context.contraption.getBlockEntityClientSide(context.localPos) instanceof BasinBlockEntity basin)
            basin.inventory.load(TagValueInput.create(ProblemReporter.DISCARDING, context.world.registryAccess(), new CompoundTag()));
    }
}
