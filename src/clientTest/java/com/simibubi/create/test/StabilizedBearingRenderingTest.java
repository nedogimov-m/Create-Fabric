package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

/** ISOLATED STAGING: actual solver/riding/counterrotation/native save, never compiled or run here. */
final class StabilizedBearingRenderingTest {
    private static final BlockPos B = new BlockPos(7455, 5, 5), P = B.above(), R = P.east(2), Q = R.above();
    private static final BlockPos LOCAL_BEARING = new BlockPos(2, 0, 0), LOCAL_CHILD_ANCHOR = LOCAL_BEARING.above();
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer(); server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 7457.5 10 12.5");
        context.waitFor(client -> Math.abs(client.player.getX() - 7457.5) < .1 && client.level.isLoaded(Q), 400);
        server.runCommand("fill 7448 3 -2 7465 3 16 minecraft:smooth_quartz");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); level.setBlockAndUpdate(B, AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP));
            ((MechanicalBearingBlockEntity) level.getBlockEntity(B)).setMovementMode(IControlContraption.RotationMode.ROTATE_NEVER_PLACE);
            level.setBlockAndUpdate(P, Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(P.east(), Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(R, AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP));
            level.setBlockAndUpdate(Q, Blocks.BARREL.defaultBlockState()); ((BarrelBlockEntity) level.getBlockEntity(Q)).setItem(0, cargo()); level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(P, R)));
        });
        NativeContraptionFixtures.awaitCaptureReady(context, world, 300, P, P.east(), R, Q);
        server.runCommand("setblock 7455 4 5 create:creative_motor[facing=up]");
        var parentId = new AtomicReference<UUID>(); var childId = new AtomicReference<UUID>();
        waitServer(context, world, level -> {
            var bearing = (MechanicalBearingBlockEntity) level.getBlockEntity(B); var parent = bearing.getMovedContraption();
            if (parent == null || !bearing.isRunning() || parent.getContraption().getStabilizedSubContraptions().children().size() != 1) return false;
            var id = parent.getContraption().getStabilizedSubContraptions().children().keySet().iterator().next();
            if (!(level.getEntity(id) instanceof OrientedContraptionEntity child) || child.getContraption() == null || child.getVehicle() != parent) return false;
            if (parent.getContraption().getBlocks().size() != 3 || child.getContraption().getBlocks().size() != 1 || !exact(child.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0))) throw new AssertionError("Actual native graph duplicates parent/child capture or loses exact cargo");
            parentId.set(parent.getUUID()); childId.set(child.getUUID()); return Math.abs(parent.getAngle(1)) > 20;
        }, 300, "Real kinetic input did not assemble a distinct native oriented passenger");
        waitClient(context, parentId.get(), childId.get()); verify(context, parentId.get(), childId.get()); look(context, Vec3.atCenterOf(P)); world.getConnection().waitForChunksRender(); screenshot(context, "create-vulkan-stabilized-real-oriented-child-world-frame-and-counterrotating-bearing-top");
        var before = new AtomicReference<Float>(); server.runOnServer(ignored -> before.set(((ControlledContraptionEntity) world.getConnection().getServerLevel().getEntity(parentId.get())).getAngle(1))); context.waitTicks(12);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var parent = (ControlledContraptionEntity) level.getEntity(parentId.get()); var child = (OrientedContraptionEntity) level.getEntity(childId.get());
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(parent.getAngle(1) - before.get())) < 10 || child.getVehicle() != parent) throw new AssertionError("Real solver or native riding stopped");
            var expected = parent.toGlobalVector(Vec3.atCenterOf(LOCAL_CHILD_ANCHOR), 1).add(0, -.5, 0);double anchorError=expected.distanceToSqr(child.position());if(anchorError>1e-8)throw new AssertionError("Native child anchor lags the actual controller pose: angle="+parent.getAngle(1)+", actual="+child.position()+", expected="+expected+", squaredError="+anchorError);
            var worldFrame=child.applyRotation(new Vec3(1,0,0),1);if(worldFrame.distanceToSqr(new Vec3(1,0,0))>1e-10)throw new AssertionError("Native stabilized child changed its independent source world frame: vector="+worldFrame+", orientation="+child.getOrientationQuaternion(1));
        });
        var oldModel = context.computeOnClient(client -> client.getModelManager().getModel(AllPartialModels.BEARING_TOP)); var reload = context.computeOnClient(Minecraft::reloadResourcePacks); context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 600); reload.join();
        context.runOnClient(client -> { if (client.getModelManager().getModel(AllPartialModels.BEARING_TOP) == oldModel) throw new AssertionError("Stabilized actor kept a stale top model after native resource reload"); }); verify(context, parentId.get(), childId.get()); screenshot(context, "create-vulkan-stabilized-native-actor-and-oriented-mesh-after-resource-reload");
        server.runCommand("tp @p 9000 10 9000");
        waitServer(context, world, level -> level.getEntity(parentId.get()) == null && level.getEntity(childId.get()) == null && level.getChunkSource().getChunkNow(P.getX() >> 4, 0) == null && level.getChunkSource().getChunkNow(Q.getX() >> 4, 0) == null, 800, "Actual parent/passenger graph retained source chunks");
        server.runCommand("tp @p 7457.5 10 12.5");
        waitServer(context, world, level -> {
            if (!(level.getEntity(parentId.get()) instanceof ControlledContraptionEntity parent) || !(level.getEntity(childId.get()) instanceof OrientedContraptionEntity child) || child.getVehicle() != parent) return false;
            if (!(level.getBlockEntity(B) instanceof MechanicalBearingBlockEntity bearing) || bearing.getMovedContraption() != parent) return false;
            if (!parent.getContraption().getStabilizedSubContraptions().contains(child.getUUID()) || !exact(child.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0))) throw new AssertionError("Native recursive passenger save/load lost exact UUID topology/cargo"); return true;
        }, 600, "The two original native UUIDs did not return with real riding topology");
        waitClient(context, parentId.get(), childId.get()); verify(context, parentId.get(), childId.get()); look(context, Vec3.atCenterOf(P)); screenshot(context, "create-vulkan-stabilized-original-oriented-passenger-after-genuine-native-unload-return");
        server.runOnServer(ignored -> ((KineticBlockEntity) world.getConnection().getServerLevel().getBlockEntity(B.below())).setGeneratedSpeed(0));
        waitServer(context, world, level -> ((MechanicalBearingBlockEntity) level.getBlockEntity(B)).getSpeed() == 0, 200, "Real motor source failed to pause the graph");
        var restoredBarrel = new AtomicReference<BlockPos>(); server.runOnServer(ignored -> restoredBarrel.set(((ControlledContraptionEntity) world.getConnection().getServerLevel().getEntity(parentId.get())).getRestorationTransform().apply(LOCAL_CHILD_ANCHOR)));
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air"); server.runCommand("tp @p 7455.5 4 9.0"); server.runCommand("gamemode survival @p");
        context.waitFor(client -> !client.player.isSpectator() && client.player.onGround() && client.player.getMainHandItem().isEmpty(), 200); look(context, Vec3.atCenterOf(B)); context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(B), 200); context.waitTicks(6); context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(client -> find(client, parentId.get()) == null && find(client, childId.get()) == null && client.level.getBlockEntity(restoredBarrel.get()) instanceof BarrelBlockEntity, 300);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); if (!exact(((BarrelBlockEntity) level.getBlockEntity(restoredBarrel.get())).getItem(0))) throw new AssertionError("Real player graph restoration changed exact named cargo");
            int diamonds = 0; for (var pos : BlockPos.betweenClosed(P.offset(-4, -1, -4), P.offset(4, 3, 4))) if (level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) for (int slot = 0; slot < barrel.getContainerSize(); slot++) if (barrel.getItem(slot).is(Items.DIAMOND)) diamonds += barrel.getItem(slot).getCount();
            if (diamonds != 7 || ((MechanicalBearingBlockEntity) level.getBlockEntity(B)).isRunning()) throw new AssertionError("Graph restoration duplicates cargo or leaves its actual parent controller running");
        }); look(context, Vec3.atCenterOf(restoredBarrel.get())); screenshot(context, "create-vulkan-stabilized-player-restored-parent-bearing-and-exact-child-cargo"); server.runCommand("gamemode spectator @p");
    }
    private static void verify(ClientGameTestContext context, UUID parentId, UUID childId) {
        context.runOnClient(client -> {
            var parent = (ControlledContraptionEntity) find(client, parentId); var child = (OrientedContraptionEntity) find(client, childId);
            var rendered = (ContraptionEntityRenderer.State) ((ContraptionEntityRenderer) client.getEntityRenderDispatcher().getRenderer(parent)).createRenderState(parent, .5f);
            var top = rendered.actors.stream().filter(actor -> actor.pos().equals(LOCAL_BEARING)).findFirst().orElseThrow(() -> new AssertionError("Actual moving bearing has no native actor top")); var state = (StabilizedBearingActorRenderer.State) top.state();
            if (state.top().mesh == null || state.top().mesh.size() == 0 || rendered.blockEntities.stream().anyMatch(be -> be.pos().equals(LOCAL_BEARING))) throw new AssertionError("Native top is missing or ordinary BE duplicates its actor geometry");
            var combined = parent.getOrientationQuaternion(.5f).mul(state.orientation()); if (ContraptionOrientation.apply(combined, new Vec3(1, 0, 0)).distanceToSqr(new Vec3(1, 0, 0)) > 1e-8) throw new AssertionError("Source moving top does not cancel the actual parent orientation");
            var childRendered = (ContraptionEntityRenderer.State) ((ContraptionEntityRenderer) client.getEntityRenderDispatcher().getRenderer(child)).createRenderState(child, .5f); if (childRendered.blocks.size() != 1 || child.getVehicle() != parent) throw new AssertionError("Real native oriented child geometry/riding snapshot missing");
        });
    }
    private static void waitClient(ClientGameTestContext context, UUID parent, UUID child) { context.waitFor(client -> find(client, parent) instanceof ControlledContraptionEntity p && p.getContraption() != null && find(client, child) instanceof OrientedContraptionEntity c && c.getContraption() != null && c.getVehicle() == p, 500); }
    private static AbstractContraptionEntity find(Minecraft client, UUID id) { if (client.level != null) for (var entity : client.level.entitiesForRendering()) if (entity instanceof AbstractContraptionEntity owner && owner.getUUID().equals(id)) return owner; return null; }
    private static ItemStack cargo() { var stack = new ItemStack(Items.DIAMOND, 7); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Native stabilized passenger cargo")); return stack; }
    private static boolean exact(ItemStack stack) { return stack.getCount() == 7 && ItemStack.isSameItemSameComponents(stack, cargo()); }
    private static void waitServer(ClientGameTestContext context, TestSingleplayerContext world, Predicate<ServerLevel> predicate, int ticks, String message) { var done = new AtomicBoolean(); for (int i = 0; i < ticks && !done.get(); i++) { world.getServer().runOnServer(ignored -> done.set(predicate.test(world.getConnection().getServerLevel()))); if (!done.get()) context.waitTicks(1); } if (!done.get()) throw new AssertionError(message); }
    private static void look(ClientGameTestContext context, Vec3 point) { var angles = context.computeOnClient(client -> { var delta = point.subtract(client.player.getEyePosition()); return new Vec2((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)), (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)))); }); context.getInput().lookAt(angles.x, angles.y); }
    private static void screenshot(ClientGameTestContext context, String name) { context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); context.takeScreenshot(name); }
}
