package net.createmod.ponder.test;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.render.PonderBlockModels;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.test.mixin.PonderSceneScheduleAuditAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Audits native block model emission throughout every registered Create teaching program. */
public final class PonderBlockGeometryTest implements FabricClientGameTest {
    private record Geometry(BlockState state, Object key) {}
    private record EmptyModel(String scene, int tick, String position, String state, String renderer) {}
    private static final class Report {
        int registeredBindings, programs, ticks, checkedModels, enclosedVaultMembers;
        final Set<String> rendererOnlyStates = new java.util.TreeSet<>();
        final List<EmptyModel> missingGeometry = new ArrayList<>();
    }

    @Override public void runTest(ClientGameTestContext context) {
        var report = new Report();
        try (var host = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.framerateLimit().set(60);
                client.options.renderDistance().set(5);
                client.options.simulationDistance().set(5);
            });
            context.runOnClient(client -> verifyVirtualPipeAttachments(client));
            var entries = context.computeOnClient(client -> PonderIndex.getSceneAccess().getRegisteredEntries().stream()
                .map(Map.Entry::getValue).filter(entry -> entry.getNamespace().equals("create")).toList());
            report.registeredBindings = entries.size();
            if (entries.isEmpty()) throw new AssertionError("Create must register its actual teaching programs");
            var programs = new HashSet<String>();
            for (var entry : entries) {
                var scene = context.computeOnClient(client -> PonderIndex.getSceneAccess().compile(List.of(entry)).getFirst());
                if (!programs.add(scene.getId() + " " + entry.getSchematicLocation())) continue;
                report.programs++;
                var checked = new HashMap<BlockPos, Geometry>();
                var reported = new HashSet<BlockState>();
                context.runOnClient(client -> inspect(scene, checked, reported, report));
                boolean finished;
                do {
                    finished = context.computeOnClient(client -> {
                        for (int batch = 0; batch < 100 && hasSchedule(scene); batch++) {
                            if (scene.getWorld().simulationTicks() > 20_000)
                                throw new AssertionError("Ponder program did not finish: " + scene.getId());
                            scene.tick();
                            report.ticks++;
                            if (scene.getWorld().simulationTicks() % 5 == 0 || !hasSchedule(scene))
                                inspect(scene, checked, reported, report);
                        }
                        return !hasSchedule(scene);
                    });
                    context.waitTicks(1);
                } while (!finished);
                System.out.println("PONDER_BLOCK_GEOMETRY " + scene.getId() + " checked=" + checked.size());
            }
            context.runOnClient(client -> {
                var ui = PonderUI.of(new ItemStack(AllBlocks.FLUID_PIPE));
                client.gui.setScreen(ui);
                ui.seekToTime(60);
            });
            context.waitTicks(3);
            context.takeScreenshot("ponder-copper-pipe-initial-segments");
            context.runOnClient(client -> {
                var ui = (PonderUI) client.gui.screen();
                ui.seekToTime(170);
            });
            context.waitTicks(3);
            context.takeScreenshot("ponder-copper-pipe-windowed-segments");
            context.runOnClient(client -> client.gui.setScreen(null));
        } finally { write(report); }
        if (!report.missingGeometry.isEmpty())
            throw new AssertionError("Ponder blocks have no visible model: " + report.missingGeometry);
        System.out.println("PONDER_BLOCK_GEOMETRY_PASS programs=" + report.programs + " models=" + report.checkedModels);
    }

    private static void verifyVirtualPipeAttachments(Minecraft client) {
        var world = new PonderLevel(BlockPos.ZERO, client.level);
        var models = new PonderBlockModels();
        for (var axis : Direction.Axis.values()) {
            var pos = new BlockPos(axis.ordinal() * 3, 5, 0);
            var state = AllBlocks.FLUID_PIPE.getAxisState(axis);
            world.setBlock(pos, state, 0);
            var data = (FluidTransportBehaviour.PipeRenderData) world.getBlockEntityRenderData(pos);
            if (data.attachments().stream().filter(attachment -> attachment == FluidTransportBehaviour.AttachmentTypes.RIM).count() != 2
                    || models.extract(world, pos, state).mesh.size() < 20)
                throw new AssertionError("A newly added virtual pipe needs both visible end rims: " + axis);
        }
        var pos = new BlockPos(0, 5, 0);
        var state = world.getBlockState(pos);
        var isolated = models.extract(world, pos, state).mesh;
        world.setBlock(pos.east(), state, 0);
        var connected = (FluidTransportBehaviour.PipeRenderData) world.getBlockEntityRenderData(pos);
        if (connected.attachments().get(Direction.EAST.ordinal()) != FluidTransportBehaviour.AttachmentTypes.CONNECTION
                || models.extract(world, pos, state).mesh == isolated)
            throw new AssertionError("Virtual neighbor changes must replace an open rim with a connected mesh");
        world.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 0);
        var reopened = (FluidTransportBehaviour.PipeRenderData) world.getBlockEntityRenderData(pos);
        if (reopened.attachments().get(Direction.EAST.ordinal()) != FluidTransportBehaviour.AttachmentTypes.RIM)
            throw new AssertionError("Removing a virtual neighbor must restore the open rim");
        var junction = new BlockPos(0, 8, 0);
        var junctionState = AllBlocks.FLUID_PIPE.defaultBlockState();
        for (var property : FluidPipeBlock.PROPERTY_BY_DIRECTION.values()) junctionState = junctionState.setValue(property, true);
        world.setBlock(junction, junctionState, 0);
        var data = (FluidTransportBehaviour.PipeRenderData) world.getBlockEntityRenderData(junction);
        if (!data.casing() || models.extract(world, junction, junctionState).isEmpty())
            throw new AssertionError("A virtual pipe junction needs its visible central casing");
    }

    private static void inspect(PonderScene scene, Map<BlockPos, Geometry> checked,
            Set<BlockState> reported, Report report) {
        var client = Minecraft.getInstance();
        var world = scene.getWorld();
        for (var entry : world.getBlockMap().entrySet()) {
            var pos = entry.getKey();
            var state = entry.getValue();
            if (state.getRenderShape() != RenderShape.MODEL) continue;
            var model = client.getModelManager().getBlockStateModelSet().get(state);
            var key = model.createGeometryKey(world, pos, state, RandomSource.create(state.getSeed(pos)));
            var geometry = new Geometry(state, key);
            var previous = checked.put(pos.immutable(), geometry);
            if (key != null && geometry.equals(previous)) continue;
            var mesh = Renderer.get().mutableMesh();
            // Disable face culling: a fully surrounded solid block is not a missing model.
            model.emitQuads(mesh.emitter(), world, pos, state, RandomSource.create(state.getSeed(pos)), face -> false);
            report.checkedModels++;
            if (mesh.size() != 0) continue;
            // Connected vaults deliberately omit all six faces of a fully enclosed member.
            if (state.is(AllBlocks.ITEM_VAULT)
                    && world.getBlockEntityRenderData(pos) instanceof ItemVaultBlockEntity.VaultRenderData vault
                    && java.util.Arrays.stream(Direction.values()).allMatch(side ->
                        world.getBlockState(pos.relative(side)) == state
                        && world.getBlockEntityRenderData(pos.relative(side)) instanceof ItemVaultBlockEntity.VaultRenderData adjacent
                        && vault.controller().equals(adjacent.controller()))) {
                report.enclosedVaultMembers++;
                continue;
            }
            var owner = world.getBlockEntity(pos);
            var renderer = owner == null ? null : client.getBlockEntityRenderDispatcher().getRenderer(owner);
            if (renderer != null && !(state.getBlock() instanceof FluidPipeBlock)) {
                report.rendererOnlyStates.add(state + " " + renderer.getClass().getName());
                continue;
            }
            if (reported.add(state)) report.missingGeometry.add(new EmptyModel(scene.getId().toString(),
                scene.getCurrentTime(), pos.toShortString(), state.toString(), renderer == null ? null : renderer.getClass().getName()));
        }
    }

    private static boolean hasSchedule(PonderScene scene) {
        return !((PonderSceneScheduleAuditAccessor) scene).create$actualActiveSchedule().isEmpty();
    }

    private static void write(Report report) {
        var path = Path.of(System.getProperty("create.test.ponderGeometryReport", "build/reports/create-ponder/block-geometry.json"));
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (IOException failure) { throw new IllegalStateException("Cannot write Ponder geometry report", failure); }
    }
}
