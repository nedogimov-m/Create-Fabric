package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/** Isolated pipes must have complete end attachments before and after a real resource reload. */
public final class PipeGeometryRenderingTest implements FabricClientGameTest {
    private static final List<BlockPos> PIPES = List.of(new BlockPos(0, 5, 0), new BlockPos(3, 5, 0), new BlockPos(6, 5, 0));

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
            context.runOnClient(client -> {
                client.options.framerateLimit().set(60);
                client.options.renderDistance().set(5);
                client.options.simulationDistance().set(5);
                if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
                PipeRenderRefreshAudit.clear();
                System.out.println("PIPE_GEOMETRY_BACKEND " + RenderSystem.getDevice().getDeviceInfo().backendName());
            });
            var server = world.getServer();
            server.runCommand("gamemode spectator @p");
            server.runCommand("tp @p 3.5 8 6.5 180 25");
            server.runCommand("time set day");
            server.runCommand("weather clear");
            context.waitFor(client -> client.level != null && client.level.isLoaded(PIPES.getLast()), 400);
            server.runCommand("fill -3 3 -3 10 3 5 minecraft:smooth_quartz");
            server.runCommand("setblock 3 4 0 minecraft:grass_block");
            for (int index = 0; index < PIPES.size(); index++) {
                var pos = PIPES.get(index);
                var axis = Direction.Axis.values()[index];
                server.runOnServer(ignored -> {
                    var level = world.getConnection().getServerLevel();
                    if (axis == Direction.Axis.Y) {
                        var player = level.getServer().getPlayerList().getPlayers().getFirst();
                        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        player.setXRot(60);
                        var stack = new net.minecraft.world.item.ItemStack(AllBlocks.FLUID_PIPE);
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                        var support = pos.below();
                        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false);
                        var placementContext = new net.minecraft.world.item.context.BlockPlaceContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit);
                        if (!((net.minecraft.world.item.BlockItem)stack.getItem()).place(placementContext).consumesAction()) throw new AssertionError("Native pipe item must place on the grass block");
                        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                    } else level.setBlockAndUpdate(pos, AllBlocks.FLUID_PIPE.getAxisState(axis));
                });
            }
            context.waitFor(client -> PIPES.stream().allMatch(pos -> client.level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe
                && pipe.getRenderData() instanceof FluidTransportBehaviour.PipeRenderData data
                && data.attachments().stream().filter(attachment -> attachment == FluidTransportBehaviour.AttachmentTypes.RIM).count() == 2), 200);
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            context.runOnClient(client -> {
                for (var pos : PIPES) if (!PipeRenderRefreshAudit.refreshed(pos))
                    throw new AssertionError("Pipe attachment packet did not request a native chunk rebuild after publishing its geometry: " + pos);
            });
            inspect(context);
            context.takeScreenshot("create-pipes-isolated-all-axes");
            for (int index = 0; index < PIPES.size(); index++) {
                var pos = PIPES.get(index);
                server.runCommand("tp @p " + (pos.getX() + 2.0) + " 6.5 2.5 140 15");
                context.waitTicks(8);
                context.getInput().lookAt(pos);
                context.waitTicks(5);
                context.takeScreenshot("create-pipe-isolated-axis-" + Direction.Axis.values()[index].getName());
            }
            var reload = context.computeOnClient(client -> client.reloadResourcePacks());
            context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 600);
            reload.join();
            world.getConnection().waitForChunksRender();
            inspect(context);
            context.takeScreenshot("create-pipe-isolated-after-reload");
        }
    }

    private static void inspect(ClientGameTestContext context) {
        context.runOnClient(client -> {
            for (var pos : PIPES) {
                var state = client.level.getBlockState(pos);
                var model = client.getModelManager().getBlockStateModelSet().get(state);
                var mesh = Renderer.get().mutableMesh();
                model.emitQuads(mesh.emitter(), (BlockAndTintGetter) client.level, pos, state, RandomSource.create(42), face -> false);
                var geometry = mesh.immutableCopy();
                if (geometry.size() < 20) throw new AssertionError("Isolated pipe is missing its connectors or rims: " + pos + " quads=" + geometry.size());
                System.out.println("PIPE_GEOMETRY " + pos + " " + state + " quads=" + geometry.size());
            }
        });
    }
}
