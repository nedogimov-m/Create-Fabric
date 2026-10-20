package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.drill.DrillBlock;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;

/** Real player windmill input, native meshes, independent working clock hands and saved owners. */
final class WindmillClockworkRenderingTest {
    private static final BlockPos W = new BlockPos(7045, 5, 5), C = W.east(8);
    private static final BlockPos TARGET = C.above().north();

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 7045.5 4 8.5");
        context.waitFor(client -> Math.abs(client.player.getX() - 7045.5) < .1 && client.level.isLoaded(C.above(2)), 400);
        NativeScreenshotReadiness.beforeFloor(context, world, new BlockPos(7039, 3, -2), new BlockPos(7059, 3, 16), W.below(), C.below());
        server.runCommand("fill 7039 3 -2 7059 3 16 minecraft:smooth_quartz");
        NativeScreenshotReadiness.afterFloor(context, world, new BlockPos(7039, 3, -2), new BlockPos(7059, 3, 16));
        var sails = new ArrayList<BlockPos>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 1 || z != 1) sails.add(W.above().offset(x, 0, z));
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            level.setBlockAndUpdate(W, AllBlocks.WINDMILL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP));
            level.setBlockAndUpdate(W.below(), AllBlocks.SHAFT.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
            for (var pos : sails) level.setBlockAndUpdate(pos, AllBlocks.SAIL.defaultBlockState().setValue(SailBlock.FACING, Direction.UP));
            level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(W.above().offset(-1, 0, -1), W.above().offset(1, 0, 1))));
            var player = level.getServer().getPlayerList().getPlayers().getFirst(); player.getInventory().clearContent(); player.inventoryMenu.broadcastFullState();
        });
        NativeContraptionFixtures.awaitCaptureReady(context, world, 300, sails.toArray(BlockPos[]::new));
        context.waitTicks(8);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var bearing = (WindmillBearingBlockEntity) level.getBlockEntity(W);
            if (bearing.isRunning() || bearing.getSpeed() != 0 || ((KineticBlockEntity) level.getBlockEntity(W.below())).getSpeed() != 0) throw new AssertionError("Windmill must await real player input at zero network power");
        });
        server.runCommand("gamemode survival @p");
        context.waitFor(client -> !client.player.isSpectator() && client.player.getMainHandItem().isEmpty() && client.player.onGround(), 200);
        var observingInput = new AtomicBoolean(true); var serverInputs = new AtomicInteger(); var lastInput = new AtomicReference<String>("none");
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (observingInput.get() && !level.isClientSide() && hit.getBlockPos().equals(W)) { serverInputs.incrementAndGet(); lastInput.set("hand=" + hand + ", item=" + player.getItemInHand(hand) + ", face=" + hit.getDirection() + ", build=" + player.mayBuild() + ", shift=" + player.isShiftKeyDown()); }
            return InteractionResult.PASS;
        });
        click(context, W);
        var windmill = new AtomicReference<UUID>();
        try {
        waitServer(context, world, level -> {
            var bearing = (WindmillBearingBlockEntity) level.getBlockEntity(W); var owner = bearing.getMovedContraption();
            if (!bearing.isRunning() || owner == null || bearing.getSpeed() != 1 || ((KineticBlockEntity) level.getBlockEntity(W.below())).getSpeed() != 1) return false;
            if (((BearingContraption) owner.getContraption()).getSailBlocks() != 8 || bearing.getStressCapacity() != 512 || owner.getContraption().getBlocks().size() != 8) throw new AssertionError("Actual eight-sail source/capacity differs");
            windmill.set(owner.getUUID()); return true;
        }, 300, "Zero-input player click did not start the real windmill/shaft solver");
        } catch (AssertionError failed) { throw new AssertionError(failed.getMessage() + "; native input callbacks=" + serverInputs.get() + ", last=" + lastInput.get(), failed); }
        finally { observingInput.set(false); }
        waitOwner(context, windmill.get());
        context.waitFor(client -> Math.abs(find(client, windmill.get()).getAngle(1)) > 3, 200);
        verifyBearing(context, W, true); verifyMoving(context, windmill.get(), 8);
        look(context, Vec3.atCenterOf(W.above())); screenshot(context, "create-vulkan-windmill-real-player-eight-sails-zero-input-and-wooden-top");

        server.runCommand("item replace entity @p weapon.mainhand with create:wrench");
        context.waitFor(client -> client.player.getMainHandItem().is(AllItems.WRENCH), 200); click(context, W);
        waitServer(context, world, level -> {
            var bearing = (WindmillBearingBlockEntity) level.getBlockEntity(W);
            return bearing.getMovementDirection() == WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE && bearing.getGeneratedSpeed() == -1 && ((KineticBlockEntity) level.getBlockEntity(W.below())).getSpeed() == -1;
        }, 200, "Real side-wrench input did not reverse the native windmill source and shaft");
        context.waitFor(client -> client.level.getBlockEntity(W) instanceof WindmillBearingBlockEntity bearing && bearing.getMovementDirection() == WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE && bearing.getSpeed() == -1, 200);
        verifyBearing(context, W, true); screenshot(context, "create-vulkan-windmill-native-side-wrench-reversed-source");

        server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 7053.5 8 10.5"); server.runCommand("time set 500");
        context.waitFor(client -> Math.abs(client.player.getX() - 7053.5) < .1 && client.level.isLoaded(TARGET), 300);
        NativeContraptionFixtures.awaitCaptureReady(context, world, 300, C.above(), C.above(2));
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            level.setBlockAndUpdate(C, AllBlocks.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP));
            level.setBlockAndUpdate(C.above(), AllBlocks.MECHANICAL_DRILL.defaultBlockState().setValue(DrillBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(C.above(2), Blocks.BARREL.defaultBlockState()); ((BarrelBlockEntity) level.getBlockEntity(C.above(2))).setItem(0, cargo());
            level.setBlockAndUpdate(TARGET, Blocks.OBSIDIAN.defaultBlockState());
        });
        server.runCommand("setblock 7053 4 5 create:creative_motor[facing=up]");
        var hour = new AtomicReference<UUID>(); var minute = new AtomicReference<UUID>();
        waitServer(context, world, level -> {
            var bearing = (ClockworkBearingBlockEntity) level.getBlockEntity(C); var a = bearing.getHourHand(); var b = bearing.getMinuteHand();
            if (!bearing.isRunning() || a == null || b == null || !a.isStalled()) return false;
            var actor = a.getContraption().getActors().getFirst().right;
            if (!actor.stall || actor.data.getIntOr("Progress", 0) < 1 || !level.getBlockState(TARGET).is(Blocks.OBSIDIAN) || b.isStalled()) return false;
            hour.set(a.getUUID()); minute.set(b.getUUID()); return true;
        }, 300, "Native hour drill never stalled on the real obsidian target");
        waitOwner(context, hour.get()); waitOwner(context, minute.get());
        context.waitFor(client -> find(client, hour.get()).isStalled() && !find(client, minute.get()).isStalled(), 200);
        var hourAngle = new AtomicReference<Float>(); var minuteAngle = new AtomicReference<Float>();
        server.runOnServer(ignored -> { var level = world.getConnection().getServerLevel(); hourAngle.set(((ControlledContraptionEntity) level.getEntity(hour.get())).getAngle(1)); minuteAngle.set(((ControlledContraptionEntity) level.getEntity(minute.get())).getAngle(1)); });
        context.waitTicks(12);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var a = (ControlledContraptionEntity) level.getEntity(hour.get()); var b = (ControlledContraptionEntity) level.getEntity(minute.get());
            if (!a.isStalled() || Math.abs(Mth.wrapDegrees(a.getAngle(1) - hourAngle.get())) > .001 || b.isStalled() || Math.abs(Mth.wrapDegrees(b.getAngle(1) - minuteAngle.get())) < 1) throw new AssertionError("Real hour work must stop only its own hand while the minute continues rotating");
            if (!sameCargo(b.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0))) throw new AssertionError("Minute hand lost component-bearing mounted cargo");
        });
        verifyBearing(context, C, false); verifyMoving(context, minute.get(), 1); look(context, Vec3.atCenterOf(C.above()));
        screenshot(context, "create-vulkan-clockwork-two-real-hands-independent-native-drill-stall");

        var oldTop = context.computeOnClient(client -> client.getModelManager().getModel(AllPartialModels.BEARING_TOP_WOODEN));
        var reload = context.computeOnClient(Minecraft::reloadResourcePacks); context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 600); reload.join();
        context.runOnClient(client -> { if (client.getModelManager().getModel(AllPartialModels.BEARING_TOP_WOODEN) == oldTop) throw new AssertionError("Windmill wooden top retained stale native model after reload"); });
        verifyBearing(context, W, true); verifyBearing(context, C, false); verifyMoving(context, windmill.get(), 8); verifyMoving(context, minute.get(), 1);
        screenshot(context, "create-vulkan-windmill-clockwork-native-models-after-resource-reload");

        server.runCommand("tp @p 8100 10 8100");
        waitServer(context, world, level -> level.getEntity(windmill.get()) == null && level.getEntity(hour.get()) == null && level.getEntity(minute.get()) == null && level.getChunkSource().getChunkNow(W.getX() >> 4, W.getZ() >> 4) == null, 800, "Windmill/clockwork native owners or controller chunk failed to unload");
        server.runCommand("tp @p 7049.5 8 10.5");
        waitServer(context, world, level -> {
            if (!(level.getEntity(windmill.get()) instanceof ControlledContraptionEntity w) || !(level.getEntity(hour.get()) instanceof ControlledContraptionEntity h) || !(level.getEntity(minute.get()) instanceof ControlledContraptionEntity m)) return false;
            if (!(level.getBlockEntity(W) instanceof WindmillBearingBlockEntity wind) || !(level.getBlockEntity(C) instanceof ClockworkBearingBlockEntity clock)) return false;
            if (wind.getMovedContraption() != w || clock.getHourHand() != h || clock.getMinuteHand() != m || !wind.isRunning() || !clock.isRunning()) return false;
            if (wind.getMovementDirection() != WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE || wind.getGeneratedSpeed() != -1 || !sameCargo(m.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0))) throw new AssertionError("Actual saved owner return lost direction or exact mounted cargo");
            return true;
        }, 600, "The three original UUIDs did not reattach after genuine native unload");
        waitOwner(context, windmill.get()); waitOwner(context, hour.get()); waitOwner(context, minute.get());
        verifyBearing(context, W, true); verifyBearing(context, C, false); look(context, Vec3.atCenterOf(C.above()));
        screenshot(context, "create-vulkan-windmill-clockwork-original-three-owners-after-native-unload-return");

        // Removing the actual blocker releases the actor through its ordinary native work lifecycle.
        server.runCommand("setblock 7053 6 4 minecraft:air");
        waitServer(context, world, level -> {
            var h = (ControlledContraptionEntity) level.getEntity(hour.get());
            return !h.isStalled() && Math.abs(Mth.wrapDegrees(h.getAngle(1) - hourAngle.get())) > 5;
        }, 400, "Hour hand did not resume naturally after the actual drill blocker disappeared");
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air"); server.runCommand("tp @p 7053.5 4 8.5"); server.runCommand("gamemode survival @p");
        context.waitFor(client -> !client.player.isSpectator() && client.player.getMainHandItem().isEmpty() && client.player.onGround() && Math.abs(client.player.getX() - 7053.5) < .1, 200); click(context, C);
        context.waitFor(client -> find(client, hour.get()) == null && find(client, minute.get()) == null && client.level.getBlockState(C.above(2)).is(Blocks.BARREL), 300);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var bearing = (ClockworkBearingBlockEntity) level.getBlockEntity(C);
            if (bearing.isRunning() || bearing.getHourHand() != null || bearing.getMinuteHand() != null || !sameCargo(((BarrelBlockEntity) level.getBlockEntity(C.above(2))).getItem(0))) throw new AssertionError("Real player clockwork restoration changed saved minute cargo");
        });
        server.runCommand("tp @p 7045.5 4 8.5"); context.waitFor(client -> Math.abs(client.player.getX() - 7045.5) < .1 && client.player.onGround(), 200); click(context, W);
        context.waitFor(client -> find(client, windmill.get()) == null && client.level.getBlockState(W.above()).is(AllBlocks.SAIL), 300);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var bearing = (WindmillBearingBlockEntity) level.getBlockEntity(W);
            if (bearing.isRunning() || bearing.getGeneratedSpeed() != 0) throw new AssertionError("Native player disassembly did not stop windmill generation");
            for (var pos : sails) if (!level.getBlockState(pos).is(AllBlocks.SAIL)) throw new AssertionError("Saved rotor failed to restore original sail at " + pos);
            int diamonds = 0;
            for (var pos : BlockPos.betweenClosed(C.offset(-2, 0, -2), C.offset(2, 3, 2))) if (level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) for (int i = 0; i < barrel.getContainerSize(); i++) if (barrel.getItem(i).is(Items.DIAMOND)) diamonds += barrel.getItem(i).getCount();
            if (diamonds != 7) throw new AssertionError("Clockwork restoration duplicated or lost cargo: " + diamonds);
        });
        look(context, Vec3.atCenterOf(W.above())); screenshot(context, "create-vulkan-windmill-clockwork-player-restored-sails-and-exact-cargo"); server.runCommand("gamemode spectator @p");
    }

    private static void verifyBearing(ClientGameTestContext context, BlockPos pos, boolean wooden) {
        context.runOnClient(client -> {
            var state = (BearingRenderer.State) client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos), .5f, null, false);
            if (state == null || state.wooden != wooden || state.facing != Direction.UP || state.top.mesh == null || state.top.mesh.size() == 0 || state.shaft.mesh == null || state.shaft.mesh.size() == 0 || !Float.isFinite(state.angle)) throw new AssertionError("Native bearing top/shaft extraction is incomplete at " + pos + ": " + (state == null ? "null snapshot" : "wooden=" + state.wooden + ", facing=" + state.facing + ", top=" + state.top.mesh + ", shaft=" + state.shaft.mesh + ", angle=" + state.angle));
        });
    }
    private static void verifyMoving(ClientGameTestContext context, UUID id, int blocks) {
        context.runOnClient(client -> { var owner = find(client, id); var state = ((ContraptionEntityRenderer) client.getEntityRenderDispatcher().getRenderer(owner)).createRenderState(owner, .5f); if (state.blocks.size() != blocks) throw new AssertionError("Native moving geometry lost captured sail/hand blocks"); });
    }
    private static void waitOwner(ClientGameTestContext context, UUID id) { context.waitFor(client -> { var owner = find(client, id); return owner != null && owner.getContraption() != null; }, 400); }
    private static ControlledContraptionEntity find(Minecraft client, UUID id) { if (client.level != null) for (var entity : client.level.entitiesForRendering()) if (entity instanceof ControlledContraptionEntity owner && owner.getUUID().equals(id)) return owner; return null; }
    private static ItemStack cargo() { var stack = new ItemStack(Items.DIAMOND, 7); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Clockwork saved minute cargo")); return stack; }
    private static boolean sameCargo(ItemStack stack) { return stack.getCount() == 7 && ItemStack.isSameItemSameComponents(stack, cargo()); }
    private static void waitServer(ClientGameTestContext context, TestSingleplayerContext world, Predicate<ServerLevel> predicate, int ticks, String message) {
        var done = new AtomicBoolean();
        for (int i = 0; i < ticks && !done.get(); i++) { world.getServer().runOnServer(ignored -> done.set(predicate.test(world.getConnection().getServerLevel()))); if (!done.get()) context.waitTicks(1); }
        if (!done.get()) {
            var diagnostic = new AtomicReference<String>();
            world.getServer().runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel(); var text = new StringBuilder();
                if (level.getBlockEntity(W) instanceof WindmillBearingBlockEntity bearing) {
                    var owner = bearing.getMovedContraption(); int sourceSails = 0;
                    for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (level.getBlockState(W.above().offset(x, 0, z)).is(BearingContraption.WINDMILL_SAILS)) sourceSails++;
                    text.append("windmill running=").append(bearing.isRunning()).append(", speed=").append(bearing.getSpeed()).append(", generated=").append(bearing.getGeneratedSpeed()).append(", capacity=").append(bearing.getStressCapacity()).append(", sourceSails=").append(sourceSails).append(", ownedBlocks=").append(owner == null ? 0 : owner.getContraption().getBlocks().size()).append(", error=").append(bearing.getLastAssemblyException() == null ? "none" : bearing.getLastAssemblyException().component.getString());
                }
                if (level.getBlockEntity(W.below()) instanceof KineticBlockEntity shaft) text.append(", shaft=").append(shaft.getSpeed());
                if (level.getBlockEntity(C) instanceof ClockworkBearingBlockEntity clock) text.append("; clock running=").append(clock.isRunning()).append(", speed=").append(clock.getSpeed()).append(", error=").append(clock.getLastAssemblyException() == null ? "none" : clock.getLastAssemblyException().component.getString());
                var player = level.getServer().getPlayerList().getPlayers().getFirst(); text.append("; serverPlayer=").append(player.position()).append(", hand=").append(player.getMainHandItem()).append(", hit=").append(player.pick(player.blockInteractionRange(), 1, false)); diagnostic.set(text.toString());
            });
            throw new AssertionError(message + "; " + diagnostic.get() + "; clientHit=" + context.computeOnClient(client -> String.valueOf(client.hitResult)));
        }
    }
    private static void click(ClientGameTestContext context, BlockPos pos) { look(context, Vec3.atCenterOf(pos)); context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos), 200); context.waitTicks(6); context.getInput().pressKey(options -> options.keyUse); }
    private static void look(ClientGameTestContext context, Vec3 target) { var angle = context.computeOnClient(client -> { var delta = target.subtract(client.player.getEyePosition()); return new Vec2((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)), (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)))); }); context.getInput().lookAt(angle.x, angle.y); }
    private static void screenshot(ClientGameTestContext context, String name) { context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); context.takeScreenshot(name); }
}
