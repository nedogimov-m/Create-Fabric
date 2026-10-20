package com.simibubi.create.content.processing.burner;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Actual fueled burners must use their original animated fire palette and cutout geometry. */
public final class BurnerFlameRenderingTest implements FabricClientGameTest {
    private static final BlockPos NORMAL = new BlockPos(2, 4, 2), SUPERHEATED = new BlockPos(4, 4, 2);

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
            context.runOnClient(client -> {
                client.options.framerateLimit().set(60);
                client.options.renderDistance().set(5);
                client.options.simulationDistance().set(5);
                if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
            });
            var server = world.getServer();
            server.runCommand("gamemode spectator @p");
            server.runCommand("tp @p 3.5 4.8 7.5 180 0");
            server.runCommand("time set day");
            context.waitFor(client -> client.level != null && client.level.isLoaded(SUPERHEATED), 400);
            server.runCommand("fill -1 3 -1 7 3 8 minecraft:smooth_quartz");
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                for (var pos : List.of(NORMAL, SUPERHEATED)) {
                    level.setBlockAndUpdate(pos, AllBlocks.BLAZE_BURNER.defaultBlockState()
                        .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
                    level.setBlockAndUpdate(pos.above(), AllBlocks.FLUID_TANK.defaultBlockState());
                    var burner = (BlazeBurnerBlockEntity) level.getBlockEntity(pos);
                    if (!burner.tryFuel(new ItemStack(pos.equals(SUPERHEATED) ? AllItems.BLAZE_CAKE : Items.COAL), false, false))
                        throw new AssertionError("Real burner must accept its original fuel");
                }
            });
            context.waitFor(client -> client.level.getBlockState(NORMAL).is(AllBlocks.BLAZE_BURNER)
                && client.level.getBlockState(SUPERHEATED).is(AllBlocks.BLAZE_BURNER)
                && client.level.getBlockState(NORMAL).getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.KINDLED
                && client.level.getBlockState(SUPERHEATED).getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.SEETHING, 200);
            world.getConnection().waitForChunksRender();
            verify(context);
            context.getInput().lookAt(SUPERHEATED);
            context.waitTicks(5);
            context.takeScreenshot("create-burner-normal-and-blaze-cake-flames");
            var reload = context.computeOnClient(client -> client.reloadResourcePacks());
            context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 600);
            reload.join();
            verify(context);
            context.takeScreenshot("create-burner-blaze-cake-flame-after-reload");
        }
    }

    private static void verify(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var atlas = client.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
            for (var pos : List.of(NORMAL, SUPERHEATED)) {
                var burner = (BlazeBurnerBlockEntity) client.level.getBlockEntity(pos);
                var target = atlas.getSprite(Create.asResource(pos.equals(SUPERHEATED)
                    ? "block/blaze_burner_flame_superheated_scroll" : "block/blaze_burner_flame_scroll"));
                var first = (BlazeBurnerRenderer.State) client.getBlockEntityRenderDispatcher().tryExtractRenderState(burner, .1f, null, false);
                if (!first.hasFlame() || first.flame.mesh == null) throw new AssertionError("Fueled burner below a tank needs a flame mesh");
                var firstUv = new float[2];
                first.flame.mesh.forEach(quad -> {
                    if (quad.chunkLayer() != ChunkSectionLayer.CUTOUT) throw new AssertionError("Flame must preserve transparent pixels");
                    for (int vertex = 0; vertex < 4; vertex++) {
                        if (quad.u(vertex) < target.getU0() - .00001f || quad.u(vertex) > target.getU1() + .00001f
                            || quad.v(vertex) < target.getV0() - .00001f || quad.v(vertex) > target.getV1() + .00001f)
                            throw new AssertionError("Burner flame uses the wrong heat palette: " + pos);
                    }
                    firstUv[0] = quad.u(0); firstUv[1] = quad.v(0);
                });
                var next = (BlazeBurnerRenderer.State) client.getBlockEntityRenderDispatcher().tryExtractRenderState(burner, .9f, null, false);
                var nextUv = new float[2];
                next.flame.mesh.forEach(quad -> { nextUv[0] = quad.u(0); nextUv[1] = quad.v(0); });
                if (nextUv[0] == firstUv[0] && nextUv[1] == firstUv[1])
                    throw new AssertionError("Original burner flame must scroll between animation frames");
                System.out.println("BURNER_FLAME_OK " + pos + " heat=" + burner.getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL));
            }
        });
    }
}
