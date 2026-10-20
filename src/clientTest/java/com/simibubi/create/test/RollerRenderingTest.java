package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.roller.*;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity.RollingMode;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/** Native configuration, real terrain/material work and actual unfinished-column save/unload. */
final class RollerRenderingTest {
    private static final BlockPos P = new BlockPos(4431, 7, 5), TOOL = P.east(2), LOCAL_TOOL = new BlockPos(2, 0, 0), LOCAL_BARREL = new BlockPos(1, 0, 0);
    private static final Vec3 ACTIVE = new Vec3(2.5, -1.5, .05);
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        verifyWheelTransforms(context);
        var server = world.getServer(); server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 4433.5 7 3.5");
        context.waitFor(client -> Math.abs(client.player.getX() - 4433.5) < .1 && client.level.isLoaded(TOOL), 400);
        server.runCommand("fill 4424 3 -2 4440 3 13 minecraft:smooth_quartz"); server.runCommand("fill 4424 4 -2 4440 5 13 minecraft:stone");
        block(world, TOOL.north(2).below(), "minecraft:stone"); block(world, P, "minecraft:stone"); block(world, P.east(), "minecraft:barrel[facing=north]"); block(world, P.below(), "create:mechanical_bearing[facing=up]");
        server.runCommand("gamemode survival @p"); server.runCommand("item replace entity @p weapon.mainhand with create:mechanical_roller");
        context.waitFor(client -> !client.player.isSpectator() && client.player.getMainHandItem().is(AllBlocks.MECHANICAL_ROLLER.asItem()), 200);
        look(context, Vec3.atCenterOf(TOOL.below(2)).add(0, .5, 0));
        context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(TOOL.below(2)) && hit.getDirection() == Direction.UP, 150); use(context);
        waitServer(context, world, level -> level.getBlockState(TOOL).is(AllBlocks.MECHANICAL_ROLLER) && level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().isEmpty(), 200, "Native roller placement failed");
        context.waitFor(client -> client.level.getBlockEntity(TOOL) instanceof RollerBlockEntity, 200);
        server.runOnServer(ignored -> { var level = world.getConnection().getServerLevel(); if (!level.getBlockState(TOOL.below()).isAir() || level.getBlockState(TOOL).getValue(RollerBlock.FACING) != Direction.NORTH) throw new AssertionError("Roller item lost source clearance/facing"); });
        look(context, panel(context, false)); openMode(context); chooseMode(context, 2);
        waitServer(context, world, level -> ((RollerBlockEntity) level.getBlockEntity(TOOL)).getMode() == RollingMode.WIDE_FILL, 200, "Actual mode board failed to set wide fill");
        look(context, panel(context, false)); openMode(context); var point = modeCoordinate(context, 1); context.getInput().setCursorPos(point.x, point.y); context.getInput().pressKey(InputConstants.KEY_ESCAPE); context.getInput().releaseKey(options -> options.keyUse);
        context.waitFor(client -> client.gui.screen() == null, 100); server.runOnServer(ignored -> { if (((RollerBlockEntity) world.getConnection().getServerLevel().getBlockEntity(TOOL)).getMode() != RollingMode.WIDE_FILL) throw new AssertionError("Escape changed roller mode"); });
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:stone 5"); context.waitFor(client -> client.player.getMainHandItem().is(Items.STONE) && client.player.getMainHandItem().getCount() == 5, 200);
        look(context, panel(context, true)); use(context);
        waitServer(context, world, level -> ((RollerBlockEntity) level.getBlockEntity(TOOL)).filtering.getFilter().is(Items.STONE) && level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().getCount() == 5, 200, "Actual sample input consumed stock or failed");
        look(context, panel(context, false)); openMode(context); chooseMode(context, 0);
        waitServer(context, world, level -> ((RollerBlockEntity) level.getBlockEntity(TOOL)).getMode() == RollingMode.TUNNEL_PAVE, 200, "Actual mode board failed to restore tunnel mode");
        context.waitFor(client -> client.level.getBlockEntity(TOOL) instanceof RollerBlockEntity roller && roller.filtering.getFilter().is(Items.STONE) && roller.getMode() == RollingMode.TUNNEL_PAVE, 200);
        world.getConnection().waitForChunksRender();
        context.runOnClient(client -> {
            var rendered = (RollerRenderer.State) client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(TOOL), .5f, null, true);
            if (rendered == null || rendered.wheel.mesh.size() != 40 || rendered.frame.mesh.size() == 0 || rendered.modeIcon.mesh.size() == 0 || rendered.material.isEmpty()) throw new AssertionError("Native roller wheel/frame/value-box geometry incomplete");
            for (var key : AllPartialModels.ROLLER_MODE_ICONS) if (!client.getModelManager().getModel(key).particleMaterial().sprite().contents().name().equals(Create.asResource("block/roller_mode_icons"))) throw new AssertionError("World mode icon is using a missing texture");
        }); screenshot(context, "create-vulkan-roller-native-material-and-mode-panels");
        server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 4434.5 11 11.5"); block(world, TOOL.north(2).below(), "minecraft:air");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var barrel = (BarrelBlockEntity) level.getBlockEntity(P.east()); barrel.setItem(0, new ItemStack(Items.STONE, 12)); barrel.setItem(4, new ItemStack(Items.DIAMOND, 7));
            ((MechanicalBearingBlockEntity) level.getBlockEntity(P.below())).setMovementMode(IControlContraption.RotationMode.ROTATE_NEVER_PLACE);
            level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(P, TOOL)));
        }); block(world, P.below(2), "create:creative_motor[facing=up]");
        var id = new AtomicReference<UUID>();
        waitServer(context, world, level -> { var owner = ((MechanicalBearingBlockEntity) level.getBlockEntity(P.below())).getMovedContraption(); if (owner == null) return false; id.set(owner.getUUID()); return !owner.isStalled() && owner.getContraption().getActors().getFirst().right.motion.length() > 0; }, 300, "Actual roller did not rotate");
        context.waitFor(client -> find(client.level, id.get()) instanceof ControlledContraptionEntity owner && owner.getContraption() != null, 300);
        look(context, Vec3.atCenterOf(TOOL.below())); var firstAngle = context.computeOnClient(client -> rendered(client, id.get()).angle); context.waitTicks(2);
        context.runOnClient(client -> { var state = rendered(client, id.get()); if (state.panels || state.wheel.mesh.size() != 40 || Math.abs(net.minecraft.util.Mth.wrapDegrees(state.angle - firstAngle)) < 1) throw new AssertionError("Moving roller wheel did not animate using native geometry"); });
        screenshot(context, "create-vulkan-roller-native-moving-wheel");
        var target = new AtomicReference<BlockPos>();
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var owner = (ControlledContraptionEntity) level.getEntity(id.get());
            var previous = BlockPos.containing(owner.toGlobalVector(ACTIVE, 1));
            for (int tick = 1; tick < 40; tick++) {
                var next = BlockPos.containing(StructureTransform.rotate(ACTIVE.subtract(.5, .5, .5), owner.getAngle(1) + tick * 4.8f, Direction.Axis.Y).add(.5, .5, .5).add(owner.position()));
                if (!next.equals(previous)) { target.set(next); break; }
            }
            if (target.get() == null) throw new AssertionError("Fixture has no next roller work column");
            level.setBlockAndUpdate(target.get(), Blocks.DIRT.defaultBlockState()); level.setBlockAndUpdate(target.get().above(), Blocks.IRON_BLOCK.defaultBlockState());
        });
        waitServer(context, world, level -> { var owner = (ControlledContraptionEntity) level.getEntity(id.get()); var ctx = owner.getContraption().getActors().getFirst().right; return owner.isStalled() && ctx.data.getIntOr("Progress", 0) > 0; }, 300, "Actual roller did not begin the hard column");
        context.waitFor(client -> ((ControlledContraptionEntity) find(client.level, id.get())).isStalled(), 200); look(context, Vec3.atCenterOf(target.get().above()));
        context.runOnClient(client -> { if (rendered(client, id.get()).angle != 0) throw new AssertionError("Stalled roller wheel should stop"); }); screenshot(context, "create-vulkan-roller-unfinished-column");
        var progress = new AtomicInteger(); var breaker = new AtomicInteger(); var angle = new AtomicReference<Float>();
        server.runOnServer(ignored -> { var owner = (ControlledContraptionEntity) world.getConnection().getServerLevel().getEntity(id.get()); var ctx = owner.getContraption().getActors().getFirst().right; progress.set(ctx.data.getIntOr("Progress", 0)); breaker.set(ctx.data.getIntOr("BreakerId", 0)); angle.set(owner.getAngle(1)); if (count(owner, Items.STONE) != 12) throw new AssertionError("Material charged before column completion"); });
        server.runCommand("tp @p 5000 10 5000");
        waitServer(context, world, level -> level.getEntity(id.get()) == null && level.getChunkSource().getChunkNow(P.getX() >> 4, 0) == null && level.getChunkSource().getChunkNow(P.east().getX() >> 4, 0) == null, 800, "Roller retained its owner or source chunks");
        server.runCommand("tp @p 4434.5 11 11.5"); waitServer(context, world, level -> level.getEntity(id.get()) instanceof ControlledContraptionEntity, 500, "Roller did not return with original UUID");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var owner = (ControlledContraptionEntity) level.getEntity(id.get()); var ctx = owner.getContraption().getActors().getFirst().right;
            if (!owner.isStalled() || Math.abs(owner.getAngle(1) - angle.get()) > .001 || ctx.data.getIntOr("Progress", 0) < progress.get() || ctx.data.getIntOr("BreakerId", 0) == breaker.get() || !ctx.data.read("ReferencePos", BlockPos.CODEC).orElseThrow().equals(target.get()) || !level.getBlockState(target.get().above()).is(Blocks.IRON_BLOCK) || count(owner, Items.STONE) != 12 || count(owner, Items.DIAMOND) != 7)
                throw new AssertionError("Actual unload lost column/pose/progress/identity/material: " + ctx.data);
        });
        context.waitFor(client -> find(client.level, id.get()) instanceof ControlledContraptionEntity owner && owner.getContraption() != null && owner.isStalled(), 300); screenshot(context, "create-vulkan-roller-unfinished-column-unload-return");
        waitServer(context, world, level -> { var owner = (ControlledContraptionEntity) level.getEntity(id.get()); return level.getBlockState(target.get()).is(Blocks.STONE) && level.getBlockState(target.get().above()).isAir() && count(owner, Items.STONE) == 11 && !owner.isStalled(); }, 500, "Restored roller failed actual excavation/paving/consumption");
        server.runOnServer(ignored -> ((KineticBlockEntity) world.getConnection().getServerLevel().getBlockEntity(P.below(2))).setGeneratedSpeed(0)); context.waitTicks(8);
        look(context, Vec3.atCenterOf(target.get())); screenshot(context, "create-vulkan-roller-column-paved-exact-stock");
        openBarrel(context, world, id.get()); context.waitFor(client -> client.player.containerMenu instanceof ChestMenu menu && menu.getSlot(0).getItem().is(Items.STONE) && menu.getSlot(0).getItem().getCount() == 11 && menu.getSlot(4).getItem().is(Items.DIAMOND) && menu.getSlot(4).getItem().getCount() == 7, 200);
        screenshot(context, "create-vulkan-roller-unspent-material-native-menu"); context.getInput().pressKey(InputConstants.KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null, 100);
        var mesh = context.computeOnClient(client -> rendered(client, id.get()).wheel.mesh); var reload = context.computeOnClient(client -> client.reloadResourcePacks()); context.waitFor(client -> reload.isDone(), 500); reload.join(); context.waitTicks(3);
        context.runOnClient(client -> { if (rendered(client, id.get()).wheel.mesh == mesh) throw new AssertionError("Roller retained old mesh after resource reload"); });
        var toolPos = new AtomicReference<BlockPos>(); var barrelPos = new AtomicReference<BlockPos>();
        server.runOnServer(ignored -> { var owner = (ControlledContraptionEntity) world.getConnection().getServerLevel().getEntity(id.get()); var transform = new StructureTransform(P, 0, owner.getAngle(1), 0); toolPos.set(transform.apply(LOCAL_TOOL)); barrelPos.set(transform.apply(LOCAL_BARREL)); });
        server.runCommand("tp @p 4431.5 6 9.5"); context.waitFor(client -> Math.abs(client.player.getZ() - 9.5) < .1, 200); look(context, Vec3.atCenterOf(P.below())); context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(P.below()), 150); use(context);
        context.waitFor(client -> find(client.level, id.get()) == null, 200);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var roller = (RollerBlockEntity) level.getBlockEntity(toolPos.get()); var barrel = (BarrelBlockEntity) level.getBlockEntity(barrelPos.get()); int stones = 0, diamonds = 0;
            for (int i = 0; i < barrel.getContainerSize(); i++) { if (barrel.getItem(i).is(Items.STONE)) stones += barrel.getItem(i).getCount(); if (barrel.getItem(i).is(Items.DIAMOND)) diamonds += barrel.getItem(i).getCount(); }
            if (stones != 11 || diamonds != 7 || roller.getMode() != RollingMode.TUNNEL_PAVE || !roller.filtering.getFilter().is(Items.STONE)) throw new AssertionError("World restoration changed roller configuration/material");
        }); look(context, Vec3.atCenterOf(toolPos.get())); screenshot(context, "create-vulkan-roller-world-stock-and-settings-restored");
        var approach = Vec3.atCenterOf(toolPos.get()).add(0, 0, 3); server.runCommand("tp @p " + approach.x + " 6 " + approach.z); server.runCommand("item replace entity @p weapon.mainhand with create:wrench"); context.waitFor(client -> Math.abs(client.player.getZ() - approach.z) < .1 && client.player.getMainHandItem().is(AllItems.WRENCH), 200);
        look(context, Vec3.atCenterOf(toolPos.get())); context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(toolPos.get()), 150); context.getInput().holdKey(options -> options.keyShift);
        try { context.waitTicks(3); use(context); waitServer(context, world, level -> level.getBlockState(toolPos.get()).isAir(), 200, "Native Shift-wrench removal failed"); } finally { context.getInput().releaseKey(options -> options.keyShift); }
        context.waitFor(client -> { int n = 0; for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) if (client.player.getInventory().getItem(i).is(AllBlocks.MECHANICAL_ROLLER.asItem())) n += client.player.getInventory().getItem(i).getCount(); return n == 1; }, 200);
        screenshot(context, "create-vulkan-roller-native-shift-wrench-refund"); server.runCommand("gamemode spectator @p");
    }
    private static Vec3 panel(ClientGameTestContext context, boolean filter) { return context.computeOnClient(client -> Vec3.atLowerCornerOf(TOOL).add(RollerBlock.panelOffset(client.level.getBlockState(TOOL), filter)).add(0, 1 / 32d + .001, 0)); }
    private static void openMode(ClientGameTestContext context) { context.waitTicks(6); context.getInput().holdKey(options -> options.keyUse); context.waitFor(client -> client.gui.screen() instanceof RollerSettingsClient.ModeScreen screen && screen.position().equals(TOOL), 200); }
    private static Vec2 modeCoordinate(ClientGameTestContext context, int mode) { return context.computeOnClient(client -> { var p = ((RollerSettingsClient.ModeScreen) client.gui.screen()).coordinate(mode); float scaleX=(float)client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth();float scaleY=(float)client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight(); return new Vec2(p.x * scaleX, p.y * scaleY); }); }
    private static void chooseMode(ClientGameTestContext context, int mode) { var p = modeCoordinate(context, mode); context.getInput().setCursorPos(p.x, p.y); context.waitTicks(3); screenshot(context, "create-vulkan-roller-mode-board-" + mode); context.getInput().releaseKey(options -> options.keyUse); context.waitFor(client -> client.gui.screen() == null, 100); }
    private static void verifyWheelTransforms(ClientGameTestContext context) {
        context.runOnClient(client -> { for (var facing : Direction.Plane.HORIZONTAL) for (int angle : new int[]{0, 90, 210}) {
            var pose = new PoseStack(); RollerRenderer.transformWheel(pose, facing, angle, new Vec3(0, -.25, 0)); var center = pose.last().pose().transformPosition(new Vector3f(.5f, .5f, .5f)); var expected = new Vec3(.5, -.25, .5).add(facing.getUnitVec3().scale(9 / 16d));
            if (new Vec3(center.x, center.y, center.z).distanceTo(expected) > 1e-5) throw new AssertionError("Wheel pivot moved while rotating: " + facing + "/" + angle);
        } });
    }
    private static RollerRenderer.State rendered(net.minecraft.client.Minecraft client, UUID id) {
        var owner = (ControlledContraptionEntity) find(client.level, id); var renderer = (ContraptionEntityRenderer) client.getEntityRenderDispatcher().getRenderer(owner); var state = renderer.createRenderState(owner, .5f);
        if (state.actors.size() != 1 || state.blockEntities.stream().anyMatch(be -> be.pos().equals(LOCAL_TOOL))) throw new AssertionError("Native roller actor missing or rendered twice"); return ((RollerActorRenderer.State) state.actors.getFirst().state()).state();
    }
    private static void openBarrel(ClientGameTestContext context, TestSingleplayerContext world, UUID id) {
        var pos = context.computeOnClient(client -> ((ControlledContraptionEntity) find(client.level, id)).toGlobalVector(new Vec3(1.5, .5, 3.5), 1)); world.getServer().runCommand("tp @p " + pos.x + " 6 " + pos.z); world.getServer().runCommand("gamemode survival @p");
        context.waitFor(client -> !client.player.isSpectator() && Math.abs(client.player.getX() - pos.x) < .1, 200); look(context, context.computeOnClient(client -> ((ControlledContraptionEntity) find(client.level, id)).toGlobalVector(Vec3.atCenterOf(LOCAL_BARREL), 1))); use(context); context.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen, 200);
    }
    private static long count(ControlledContraptionEntity owner, Item item) { long n = 0; for (var view : owner.getContraption().getStorage().getAllItems()) if (view.getResource().isOf(item)) n += view.getAmount(); return n; }
    private static net.minecraft.world.entity.Entity find(net.minecraft.client.multiplayer.ClientLevel level, UUID id) { if (level != null) for (var entity : level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity; return null; }
    private static void waitServer(ClientGameTestContext context, TestSingleplayerContext world, Predicate<ServerLevel> predicate, int ticks, String message) { var ready = new AtomicBoolean(); for (int i = 0; i < ticks && !ready.get(); i++) { world.getServer().runOnServer(ignored -> ready.set(predicate.test(world.getConnection().getServerLevel()))); if (!ready.get()) context.waitTicks(1); } if (!ready.get()) throw new AssertionError(message); }
    private static void block(TestSingleplayerContext world, BlockPos pos, String state) { world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state); }
    private static void use(ClientGameTestContext context) { context.waitTicks(6); context.getInput().pressKey(options -> options.keyUse); }
    private static void screenshot(ClientGameTestContext context, String name) { context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); context.takeScreenshot(name); }
    private static void look(ClientGameTestContext context, Vec3 target) { var angle = context.computeOnClient(client -> { var d = target.subtract(client.player.getEyePosition()); return new Vec2((float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)))); }); context.getInput().lookAt(angle.x, angle.y); }
}
