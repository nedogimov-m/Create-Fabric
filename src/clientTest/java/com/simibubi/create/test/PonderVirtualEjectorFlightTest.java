package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.depot.EjectorBlock;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import net.createmod.ponder.api.level.PonderLevel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Actual native charge, simulated input rollback, incoming merge, flight delivery and replay. UNRUN. */
public final class PonderVirtualEjectorFlightTest implements FabricClientGameTest {
    private static final BlockPos SOURCE = new BlockPos(6, 3, 3);
    private static final BlockPos TARGET = SOURCE.west(4);

    @Override public void runTest(ClientGameTestContext context) {
        try (var joined = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var host = client.level;
                require(host != null, "Actual native client world required");
                var hostSource = host.getBlockState(SOURCE);
                var hostTarget = host.getBlockState(TARGET);
                var hostSourceOwner = host.getBlockEntity(SOURCE);
                var hostTargetOwner = host.getBlockEntity(TARGET);
                var scene = new PonderLevel(BlockPos.ZERO, host);
                scene.setBlock(SOURCE, AllBlocks.WEIGHTED_EJECTOR.defaultBlockState()
                    .setValue(EjectorBlock.HORIZONTAL_FACING, Direction.WEST), 0);
                scene.setBlock(TARGET, AllBlocks.DEPOT.defaultBlockState(), 0);
                var ejector = (EjectorBlockEntity) scene.getBlockEntity(SOURCE);
                require(ejector != null && ejector.isVirtual() && ejector.getLevel() == scene,
                    "Registered native factory creates the actual local virtual owner");
                ejector.setTarget(4, 0);
                ejector.setSpeed(256);
                ejector.setMinimumStackSize(10);
                for (int tick = 0; tick < 12; tick++)
                    EjectorBlockEntity.tick(scene, SOURCE, ejector.getBlockState(), ejector);
                require(ejector.getState() == EjectorBlockEntity.State.CHARGED,
                    "Actual virtual source clock completes kinetic charging");
                var input = ejector.getBehaviour(DirectBeltInputBehaviour.TYPE);
                require(input != null && input.handleInsertion(new ItemStack(Items.IRON_INGOT, 3), Direction.UP, false).isEmpty(),
                    "Actual source entrypoint commits three units into the canonical depot slot");
                var remaining = new ItemStack(Items.IRON_INGOT, 7);
                require(input.handleInsertion(remaining, Direction.UP, true).isEmpty()
                    && ejector.depotBehaviour.getPresentStackSize() == 3
                    && ejector.depotBehaviour.getIncomingItems().isEmpty(),
                    "Simulation rolls back the real incoming collection without duplicating stock");
                require(input.handleInsertion(remaining, Direction.UP, false).isEmpty()
                    && ejector.depotBehaviour.getPresentStackSize() == 3
                    && ejector.depotBehaviour.getIncomingItems().size() == 1,
                    "Committed source insertion owns seven additional units in its actual incoming collection");
                scene.createBackup();
                scene.restore();
                var restored = (EjectorBlockEntity) scene.getBlockEntity(SOURCE);
                require(restored != ejector && ejector.isRemoved() && restored.isVirtual()
                    && restored.getMinimumStackSize() == 10,
                    "Replay creates a fresh virtual owner with the canonical stack limit");
                ejector.activateDeferred();
                require(ejector.getState() == EjectorBlockEntity.State.CHARGED
                    && ejector.depotBehaviour.getPresentStackSize() == 3
                    && ejector.getLaunchedItems().isEmpty(),
                    "A stale public source callback cannot consume detached old stock or launch entities");
                for (int tick = 0; tick < 40 && restored.getLaunchedItems().isEmpty(); tick++)
                    EjectorBlockEntity.tick(scene, SOURCE, restored.getBlockState(), restored);
                require(restored.getLaunchedItems().stream().mapToInt(flight -> flight.getValue().getCount()).sum() == 10
                    && restored.depotBehaviour.getPresentStackSize() == 0
                    && restored.depotBehaviour.getIncomingItems().isEmpty(),
                    "The actual virtual merge and launch transfer exactly ten units to the sole flight owner");
                var target = (DepotBlockEntity) scene.getBlockEntity(TARGET);
                for (int tick = 0; tick < 128 && !restored.getLaunchedItems().isEmpty(); tick++)
                    EjectorBlockEntity.tick(scene, SOURCE, restored.getBlockState(), restored);
                require(target != null && target.depotBehaviour.getHeldItemStack().is(Items.IRON_INGOT)
                    && target.depotBehaviour.getPresentStackSize() == 10
                    && restored.getLaunchedItems().isEmpty(),
                    "The original cargo reaches the actual registered local target without a second flight copy");
                require(host.getBlockState(SOURCE) == hostSource && host.getBlockState(TARGET) == hostTarget
                    && host.getBlockEntity(SOURCE) == hostSourceOwner && host.getBlockEntity(TARGET) == hostTargetOwner,
                    "Charging, transactions, replay and flight preserve actual host blocks and owners");
            });
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
