package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import net.createmod.ponder.api.level.PonderLevel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Native scene factory, real registered input/transaction, real chute handoff and replay. UNRUN. */
public final class PonderVirtualChuteInputTest implements FabricClientGameTest {
    private static final BlockPos TOP = new BlockPos(2, 4, 2);

    @Override public void runTest(ClientGameTestContext context) {
        try (var joined = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var host = client.level;
                require(host != null, "Actual native client world required");
                var hostTop = host.getBlockState(TOP);
                var hostBelow = host.getBlockState(TOP.below());
                var hostOwner = host.getBlockEntity(TOP);
                var scene = new PonderLevel(BlockPos.ZERO, host);
                scene.setBlock(TOP, AllBlocks.CHUTE.defaultBlockState(), 0);
                scene.setBlock(TOP.below(), AllBlocks.CHUTE.defaultBlockState(), 0);
                var top = (ChuteBlockEntity) scene.getBlockEntity(TOP);
                var bottom = (ChuteBlockEntity) scene.getBlockEntity(TOP.below());
                require(top != null && bottom != null && top.isVirtual() && top.getLevel() == scene,
                    "Native factory creates actual local virtual chute owners");
                var input = top.getBehaviour(DirectBeltInputBehaviour.TYPE);
                require(input != null && !input.canInsertFromSide(Direction.UP),
                    "Ordinary native insertion remains restricted by the actual cached source predicate");
                var iron = new ItemStack(Items.IRON_INGOT, 7);
                require(input.handleInsertion(iron, Direction.UP, true).isEmpty() && top.getItem().isEmpty(),
                    "Forced source callback simulates actual slot insertion and rolls it back");
                require(input.handleInsertion(iron, Direction.UP, false).isEmpty() && top.getItem().getCount() == 7,
                    "Source entrypoint commits into the one actual local chute item owner");
                for (int tick = 0; tick < 7; tick++)
                    ChuteBlockEntity.tick(scene, TOP, top.getBlockState(), top);
                require(top.getItem().isEmpty() && bottom.getItem().getCount() == 7,
                    "Actual virtual ticker transfers the original cargo to the registered local destination");
                scene.createBackup();
                scene.restore();
                var restored = (ChuteBlockEntity) scene.getBlockEntity(TOP);
                require(restored != top && top.isRemoved() && restored.isVirtual(),
                    "Native replay replaces source owners instead of adopting or retaining stale instances");
                require(input.handleInsertion(iron, Direction.UP, false).getCount() == 7 && restored.getItem().isEmpty(),
                    "Stale source callback cannot insert into the restored owner or detached old storage");
                require(host.getBlockState(TOP) == hostTop && host.getBlockState(TOP.below()) == hostBelow
                    && host.getBlockEntity(TOP) == hostOwner,
                    "Simulation, handoff and replay preserve actual host blocks and inventory owner");
            });
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
