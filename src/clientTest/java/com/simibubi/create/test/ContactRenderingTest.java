package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.redstone.contact.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

/** Native placement/wrench, physical wire coupling, a real tracked owner and two-chunk save/return. */
final class ContactRenderingTest {
    private static final BlockPos P = new BlockPos(4751, 4, 5), C = P.above(5), WORK = C.east(3).north(), WIRE = WORK.east();
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer(); server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 4751.5 4 8.5");
        context.waitFor(client -> Math.abs(client.player.getX() - 4751.5) < .1 && client.level.isLoaded(C.east(4)), 400);
        server.runCommand("fill 4746 3 -2 4761 3 16 minecraft:smooth_quartz"); server.runCommand("gamemode survival @p"); server.runCommand("item replace entity @p weapon.mainhand with create:redstone_contact 2");
        context.waitFor(client -> !client.player.isSpectator() && client.player.onGround() && client.player.getMainHandItem().is(AllBlocks.REDSTONE_CONTACT.asItem()), 200);
        look(context, Vec3.atCenterOf(P.below()).add(0, .5, 0)); use(context);
        waitServer(context, world, level -> level.getBlockState(P).is(AllBlocks.REDSTONE_CONTACT) && level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().getCount() == 1, 200, "Actual contact placement failed");
        look(context, Vec3.atCenterOf(P)); use(context);
        waitServer(context, world, level -> level.getBlockState(P.south()).is(AllBlocks.REDSTONE_CONTACT) && powered(level, P) && powered(level, P.south()), 200, "Clicked opposing pair did not power");
        context.waitFor(client -> powered(client.level, P) && powered(client.level, P.south()), 200);
        context.runOnClient(client -> {
            if (client.level.getBlockState(P.south()).getValue(RedstoneContactBlock.FACING) != Direction.NORTH) throw new AssertionError("Placement ignored source matching-face preference");
            for (var state : AllBlocks.REDSTONE_CONTACT.getStateDefinition().getPossibleStates()) {
                var models = client.getModelManager().getBlockStateModelSet(); var model = models.get(state); if (model == models.missingModel()) throw new AssertionError("Missing contact orientation/model " + state);
                var mesh = net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh(); model.emitQuads(mesh.emitter(), client.level, P, state, net.minecraft.util.RandomSource.create(42), side -> false);
                var sprite = Create.asResource(state.getValue(RedstoneContactBlock.POWERED) ? "block/contact_side_powered" : "block/contact_side");
                if (mesh.immutableCopy().size() == 0 || !model.particleMaterial().sprite().contents().name().equals(sprite)) throw new AssertionError("Contact geometry or source sprite missing");
            }
        }); screenshot(context,world, "create-vulkan-contact-native-placement-powered-pair");
        block(world, P.south(3).above(2), "minecraft:smooth_quartz"); server.runCommand("tp @p 4751.5 7 8.5"); server.runCommand("item replace entity @p weapon.mainhand with create:wrench");
        context.waitFor(client -> client.player.onGround() && Math.abs(client.player.getY() - 7) < .1 && client.player.getMainHandItem().is(AllItems.WRENCH), 200);
        look(context, Vec3.atCenterOf(P.south()).add(0, .5, 0)); use(context);
        waitServer(context, world, level -> level.getBlockState(P.south()).getValue(RedstoneContactBlock.FACING) == Direction.EAST && !powered(level, P), 200, "Real wrench did not rotate/break pair");
        context.getInput().holdKey(options -> options.keyShift); try { context.waitTicks(3); use(context); waitServer(context, world, level -> level.getBlockState(P.south()).isAir(), 200, "Contact Shift-wrench failed"); } finally { context.getInput().releaseKey(options -> options.keyShift); }
        context.waitFor(client -> { int n = 0; for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) if (client.player.getInventory().getItem(i).is(AllBlocks.REDSTONE_CONTACT.asItem())) n += client.player.getInventory().getItem(i).getCount(); return n == 1; }, 200);
        screenshot(context,world, "create-vulkan-contact-native-wrench-and-refund"); server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 4754.5 12 12.5");
        block(world, C, "minecraft:stone"); block(world, C.east(), "minecraft:barrel"); block(world, C.east(2), "create:redstone_contact[facing=north]"); block(world, WORK, "create:redstone_contact[facing=south]"); block(world, WIRE.below(), "minecraft:stone"); block(world, WIRE, "minecraft:redstone_wire");
        var id = new AtomicReference<UUID>();
        NativeContraptionFixtures.awaitCaptureReady(context,world,200,C,C.east(2));
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); ((BarrelBlockEntity) level.getBlockEntity(C.east())).setItem(0, new ItemStack(Items.DIAMOND, 7)); level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(C, C.east(2))));
            var structure = new BearingContraption(false, Direction.UP); try { if (!structure.assemble(level, C.below()) || !structure.tryRemoveBlocksFromWorld(level, BlockPos.ZERO)) throw new AssertionError("Contact capture failed"); } catch (AssemblyException failure) { throw new AssertionError(failure); }
            var owner = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, level); owner.setContraption(structure); owner.setPos(Vec3.atLowerCornerOf(C)); level.addFreshEntity(owner); owner.tickActors(); owner.setPos(owner.position().add(.1, 0, 0)); owner.tickActors();
            if (powered(level, WORK) || structure.getActorAt(new BlockPos(2, 0, 0)).right.firstMovement) throw new AssertionError("First source moving visit was not skipped");
            owner.setPos(Vec3.atLowerCornerOf(C.east())); owner.tickActors(); id.set(owner.getUUID());
            if (!powered(level, WORK)) throw new AssertionError("Actual transformed contact did not activate world target");
        });
        context.waitFor(client -> find(client.level, id.get()) instanceof ControlledContraptionEntity owner && owner.getContraption() != null && powered(client.level, WORK) && client.level.getBlockState(WIRE).getValue(RedstoneWireBlock.POWER) == 15, 300);
        look(context, Vec3.atCenterOf(WORK)); context.runOnClient(client -> { var owner = (ControlledContraptionEntity) find(client.level, id.get()); var state = ((ContraptionEntityRenderer) client.getEntityRenderDispatcher().getRenderer(owner)).createRenderState(owner, .5f); if (state.blocks.stream().noneMatch(block -> block.pos().equals(new BlockPos(2, 0, 0)) && block.model() instanceof com.simibubi.create.foundation.model.PartialModelRenderState model && model.mesh != null && model.mesh.size() > 0)) throw new AssertionError("Moving contact native mesh missing"); });
        screenshot(context,world, "create-vulkan-moving-contact-powers-real-wire");
        disable(context, world, id.get(), true);
        waitServer(context, world, level -> !powered(level, WORK) && level.getBlockState(WIRE).getValue(RedstoneWireBlock.POWER) == 0, 200, "Controls did not release contact/wire");
        server.runOnServer(ignored -> { var owner = (ControlledContraptionEntity) world.getConnection().getServerLevel().getEntity(id.get()); owner.getContraption().setActorsActive(new ItemStack(AllBlocks.REDSTONE_CONTACT), true); owner.setPos(Vec3.atLowerCornerOf(C.east(2))); owner.tickActors(); owner.setPos(Vec3.atLowerCornerOf(C.east())); owner.tickActors(); });
        waitServer(context, world, level -> powered(level, WORK), 200, "Returning contact did not reactivate");
        server.runCommand("tp @p 5400 12 5400");
        waitServer(context, world, level -> level.getEntity(id.get()) == null && level.getChunkSource().getChunkNow(C.getX() >> 4, 0) == null && level.getChunkSource().getChunkNow(WORK.getX() >> 4, 0) == null, 800, "Contact kept owner or source/target chunks loaded");
        server.runCommand("tp @p 4754.5 12 12.5");
        waitServer(context, world, level -> level.getEntity(id.get()) instanceof ControlledContraptionEntity && level.getChunkSource().getChunkNow(C.getX() >> 4, 0) != null && powered(level, WORK), 500, "Contact owner/state did not recover from real unload");
        context.waitFor(client -> find(client.level, id.get()) instanceof ControlledContraptionEntity owner && owner.getContraption() != null && powered(client.level, WORK), 300);
        disable(context, world, id.get(), true); waitServer(context, world, level -> !powered(level, WORK) && level.getBlockState(WIRE).getValue(RedstoneWireBlock.POWER) == 0, 200, "Restored contact stranded signal");
        var old = context.computeOnClient(client -> client.getModelManager().getBlockStateModelSet().get(AllBlocks.REDSTONE_CONTACT.defaultBlockState())); var reload = context.computeOnClient(client -> client.reloadResourcePacks()); context.waitFor(client -> reload.isDone(), 500); reload.join();
        context.runOnClient(client -> { if (client.getModelManager().getBlockStateModelSet().get(AllBlocks.REDSTONE_CONTACT.defaultBlockState()) == old) throw new AssertionError("Contact retained old model after resource reload"); }); screenshot(context,world, "create-vulkan-contact-real-unload-return-disable-reload");
        server.runOnServer(ignored -> { var level = world.getConnection().getServerLevel(); var owner = (ControlledContraptionEntity) level.getEntity(id.get()); if (!owner.disassemble()) throw new AssertionError("Contact disassembly failed"); var barrel = (BarrelBlockEntity) level.getBlockEntity(C.east(2)); if (barrel == null || barrel.getItem(0).getCount() != 7 || !level.getBlockState(WORK.south()).is(AllBlocks.REDSTONE_CONTACT)) throw new AssertionError("Disassembly lost contact/cargo"); });
        waitServer(context, world, level -> powered(level, WORK) && powered(level, WORK.south()) && level.getBlockState(WIRE).getValue(RedstoneWireBlock.POWER) == 15, 200, "Restored genuine static pair did not power");
        block(world, WORK.south(), "minecraft:air"); waitServer(context, world, level -> !powered(level, WORK) && level.getBlockState(WIRE).getValue(RedstoneWireBlock.POWER) == 0, 200, "Removing restored mate stranded signal"); screenshot(context,world, "create-vulkan-contact-world-restoration-and-final-release");
    }
    private static void disable(ClientGameTestContext context, TestSingleplayerContext world, UUID id, boolean disable) { world.getServer().runOnServer(ignored -> ((ControlledContraptionEntity) world.getConnection().getServerLevel().getEntity(id)).getContraption().setActorsActive(new ItemStack(AllBlocks.REDSTONE_CONTACT), !disable)); }
    private static boolean powered(net.minecraft.world.level.Level level, BlockPos pos) { var state = level.getBlockState(pos); return state.is(AllBlocks.REDSTONE_CONTACT) && state.getValue(RedstoneContactBlock.POWERED); }
    private static net.minecraft.world.entity.Entity find(net.minecraft.client.multiplayer.ClientLevel level, UUID id) { for (var entity : level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity; return null; }
    private static void waitServer(ClientGameTestContext context, TestSingleplayerContext world, Predicate<ServerLevel> predicate, int ticks, String message) { var ready = new AtomicBoolean(); for (int i = 0; i < ticks && !ready.get(); i++) { world.getServer().runOnServer(ignored -> ready.set(predicate.test(world.getConnection().getServerLevel()))); if (!ready.get()) context.waitTicks(1); } if (!ready.get()) throw new AssertionError(message); }
    private static void block(TestSingleplayerContext world, BlockPos pos, String state) { world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state); }
    private static void use(ClientGameTestContext context) { context.waitTicks(6); context.getInput().pressKey(options -> options.keyUse); }
    private static void screenshot(ClientGameTestContext context,TestSingleplayerContext world,String name){
        NativeScreenshotReadiness.await(context,world);
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); context.takeScreenshot(name);
    }
    private static void look(ClientGameTestContext context, Vec3 target) { var angle = context.computeOnClient(client -> { var d = target.subtract(client.player.getEyePosition()); return new Vec2((float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)))); }); context.getInput().lookAt(angle.x, angle.y); }
}
