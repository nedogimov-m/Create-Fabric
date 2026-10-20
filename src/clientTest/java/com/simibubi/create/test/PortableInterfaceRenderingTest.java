package com.simibubi.create.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.psi.*;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/** Real hopper/pump automation, input redstone reconnect, native telescoping and world restoration. */
final class PortableInterfaceRenderingTest {
    private static final BlockPos P = new BlockPos(4271, 6, 5), Q = P.south(10), T = P.north(2), F = Q.north(2), L = T.west();
    private static final long WATER = FluidConstants.BUCKET * 5 + 1;
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        verifyTransforms(context);
        var server = world.getServer(); server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 4271.5 10 11.5");
        context.waitFor(client -> Math.abs(client.player.getX() - 4271.5) < .1 && client.level.isLoaded(Q.east()), 400);
        server.runCommand("fill 4264 3 -1 4280 3 21 minecraft:smooth_quartz");
        block(world, T, "create:portable_storage_interface[facing=south]"); block(world, T.below(), "minecraft:hopper");
        block(world, T.below(2), "minecraft:barrel"); block(world, L.below(), "minecraft:stone"); block(world, L, "minecraft:lever[face=floor,facing=north]");
        setup(world, P, false);
        var itemId = new AtomicReference<UUID>();
        waitServer(context, world, level -> { var owner = owner(level, P); if (owner == null) return false; itemId.set(owner.getUUID()); return owner.isStalled() && itemOutput(level) > 0; }, 400, "Real hopper did not receive mounted items through a stalled PSI");
        pause(world, P); context.waitTicks(3);
        context.waitFor(client -> find(client.level, itemId.get()) instanceof ControlledContraptionEntity owner && owner.getContraption() != null, 300);
        context.waitFor(client -> client.level.getBlockEntity(T) instanceof PortableStorageInterfaceBlockEntity endpoint && endpoint.isConnected()
            && client.getBlockEntityRenderDispatcher().tryExtractRenderState(endpoint, .5f, null, true) != null, 300);
        context.runOnClient(client -> {
            var moving = rendered(client, itemId.get());
            var stationary = (PortableStorageInterfaceRenderer.State) client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(T), .5f, null, true);
            if (!stationary.lit || stationary.extension <= 0 || stationary.middle.mesh.size() == 0 || moving.top.mesh.size() == 0)
                throw new AssertionError("Native connected item PSI geometry/extension missing");
        });
        look(context, Vec3.atCenterOf(T)); screenshot(context, "create-vulkan-portable-item-interface-native-transfer");
        server.runCommand("tp @p 4268.5 4 6.5"); server.runCommand("gamemode survival @p");
        context.waitFor(client -> !client.player.isSpectator() && Math.abs(client.player.getX() - 4268.5) < .1, 200);
        look(context, Vec3.atCenterOf(L).add(0, -.35, 0)); context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(L), 150); use(context);
        waitServer(context, world, level -> ((PortableStorageInterfaceBlockEntity) level.getBlockEntity(T)).isPowered() && !((PortableStorageInterfaceBlockEntity) level.getBlockEntity(T)).canTransfer(), 150, "Actual lever input failed to disconnect PSI");
        context.waitTicks(3); screenshot(context, "create-vulkan-portable-interface-redstone-disconnected"); use(context);
        waitServer(context, world, level -> ((PortableStorageInterfaceBlockEntity) level.getBlockEntity(T)).getConnectedEntity() == level.getEntity(itemId.get()) && ((PortableStorageInterfaceBlockEntity) level.getBlockEntity(T)).canTransfer(), 200, "Unpower did not reconnect the stationary construct");
        // Lock the real hopper independently of the endpoint before inspecting/reloading inventories.
        block(world, T.below().east(), "minecraft:redstone_block");
        var itemRemaining = new AtomicLong(); var oldItems = new AtomicReference<Storage<ItemVariant>>();
        server.runOnServer(ignored -> { var level = world.getConnection().getServerLevel(); itemRemaining.set(items((ControlledContraptionEntity) level.getEntity(itemId.get()))); oldItems.set(((PortableItemInterfaceBlockEntity) level.getBlockEntity(T)).storage()); assertItems(level, itemId.get(), itemRemaining.get()); });
        openBarrel(context, world, itemId.get());
        context.waitFor(client -> client.player.containerMenu instanceof ChestMenu menu && menu.getSlot(0).getItem().is(Items.DIAMOND) && menu.getSlot(0).getItem().getCount() == itemRemaining.get(), 200);
        screenshot(context, "create-vulkan-portable-interface-mounted-native-menu"); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null, 100);
        var oldMesh = context.computeOnClient(client -> rendered(client, itemId.get()).middle.mesh);
        var reload = context.computeOnClient(client -> client.reloadResourcePacks()); context.waitFor(client -> reload.isDone(), 500); reload.join(); context.waitTicks(3);
        context.runOnClient(client -> { if (rendered(client, itemId.get()).middle.mesh == oldMesh) throw new AssertionError("PSI retained stale native mesh after reload"); });

        server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 4271.5 10 20.5");
        block(world, F, "create:portable_fluid_interface[facing=south]"); block(world, F.west(), "create:fluid_pipe[east=true,west=true]");
        block(world, F.west(2), "create:mechanical_pump[facing=west]"); block(world, F.west(3), "create:fluid_tank");
        block(world, F.west(2).above(), "create:cogwheel[axis=x]"); setup(world, Q, true);
        block(world, F.west(3).above(), "create:creative_motor[facing=east]");
        var fluidId = new AtomicReference<UUID>();
        // Source starts PSI at 14 ticks, releasing at 4. At 16 RPM a cold pipe's
        // half-flow takes 7 ticks, so this route cannot prime during that window.
        // Preserve/verify that source constraint, then drive the real pump at 128.
        var sawColdStall = new AtomicBoolean();
        waitServer(context, world, level -> {
            var owner = owner(level, Q); if (owner == null) return false; fluidId.set(owner.getUUID());
            if (owner.isStalled()) sawColdStall.set(true);
            return sawColdStall.get() && !owner.isStalled();
        }, 150, "Cold low-speed pipe never exercised the source timeout/release");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            if (water((ControlledContraptionEntity) level.getEntity(fluidId.get())) != WATER || receivedWater(level) != 0)
                throw new AssertionError("Cold low-speed source window changed fluid stock");
            ((KineticBlockEntity) level.getBlockEntity(F.west(3).above())).setGeneratedSpeed(128);
        });
        waitServer(context, world, level -> {
            var owner = (ControlledContraptionEntity) level.getEntity(fluidId.get());
            return owner != null && owner.isStalled() && receivedWater(level) > 0;
        }, 500, "Real pump did not draw mounted tank water through PSI");
        pause(world, Q); server.runOnServer(ignored -> ((KineticBlockEntity) world.getConnection().getServerLevel().getBlockEntity(F.west(3).above())).setGeneratedSpeed(0));
        context.waitTicks(6);
        var waterRemaining = new AtomicLong(); var oldFluids = new AtomicReference<Storage<FluidVariant>>();
        server.runOnServer(ignored -> { var level = world.getConnection().getServerLevel(); waterRemaining.set(water((ControlledContraptionEntity) level.getEntity(fluidId.get()))); oldFluids.set(((PortableFluidInterfaceBlockEntity) level.getBlockEntity(F)).storage()); assertWater(level, fluidId.get(), waterRemaining.get()); });
        context.waitFor(client -> find(client.level, fluidId.get()) instanceof ControlledContraptionEntity owner && owner.getContraption() != null, 300);
        context.runOnClient(client -> { var state = rendered(client, fluidId.get()); if (!state.fluids || state.middle.mesh.size() == 0 || state.top.mesh.size() == 0) throw new AssertionError("Native moving fluid PSI parts missing"); });
        world.getConnection().waitForChunksRender(); look(context, Vec3.atCenterOf(F.west())); screenshot(context, "create-vulkan-portable-fluid-interface-real-pump-transfer");
        server.runCommand("tp @p 5000 10 5000");
        waitServer(context, world, level -> level.getEntity(itemId.get()) == null && level.getEntity(fluidId.get()) == null
            && level.getChunkSource().getChunkNow(P.getX() >> 4, 0) == null && level.getChunkSource().getChunkNow(P.east().getX() >> 4, 0) == null,
            800, "PSI references retained their owners or source chunks");
        server.runCommand("tp @p 4271.5 10 11.5");
        waitServer(context, world, level -> level.getEntity(itemId.get()) instanceof ControlledContraptionEntity && level.getEntity(fluidId.get()) instanceof ControlledContraptionEntity
            && ((PortableStorageInterfaceBlockEntity) level.getBlockEntity(T)).canTransfer() && ((PortableStorageInterfaceBlockEntity) level.getBlockEntity(F)).canTransfer(), 500, "Saved PSI contexts did not reconnect after real unload");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); assertItems(level, itemId.get(), itemRemaining.get()); assertWater(level, fluidId.get(), waterRemaining.get());
            try (var tx = Transaction.openOuter()) {
                if (oldItems.get().extract(ItemVariant.of(Items.DIAMOND), 1, tx) != 0 || oldFluids.get().extract(FluidVariant.of(Fluids.WATER), 1, tx) != 0)
                    throw new AssertionError("Unloaded endpoint handle retained access to stock"); tx.commit();
            }
        });
        context.waitFor(client -> find(client.level, itemId.get()) instanceof ControlledContraptionEntity a && a.getContraption() != null && find(client.level, fluidId.get()) instanceof ControlledContraptionEntity b && b.getContraption() != null, 300);
        world.getConnection().waitForChunksRender(); look(context, Vec3.atCenterOf(F)); screenshot(context, "create-vulkan-portable-interfaces-actual-unload-return");
        disassemble(context, world, P, itemId.get()); disassemble(context, world, Q, fluidId.get());
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            if (containerItems((net.minecraft.world.Container) level.getBlockEntity(P.east())) != itemRemaining.get() || itemOutput(level) + itemRemaining.get() != 64)
                throw new AssertionError("World restoration changed total item stock");
            if (((FluidTankBlockEntity) level.getBlockEntity(Q.east())).getTankInventory().getFluidAmount() != waterRemaining.get() || receivedWater(level) + waterRemaining.get() != WATER)
                throw new AssertionError("World restoration changed exact fluid units");
        });
        server.runCommand("gamemode spectator @p"); look(context, Vec3.atCenterOf(Q)); screenshot(context, "create-vulkan-portable-interfaces-world-stock-restored");
        placementInput(context, world);
    }
    private static void placementInput(ClientGameTestContext context, TestSingleplayerContext world) {
        var support = P.south(5).below(2); var placed = support.south(); var server = world.getServer();
        block(world, support, "minecraft:stone"); server.runCommand("tp @p 4271.5 4 13.5"); server.runCommand("gamemode survival @p");
        server.runCommand("item replace entity @p weapon.mainhand with create:portable_storage_interface");
        context.waitFor(client -> !client.player.isSpectator() && client.player.getMainHandItem().is(AllBlocks.PORTABLE_STORAGE_INTERFACE.asItem()) && Math.abs(client.player.getZ() - 13.5) < .1, 200);
        look(context, Vec3.atCenterOf(support)); context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(support) && hit.getDirection() == Direction.SOUTH, 150); use(context);
        context.waitFor(client -> client.level.getBlockState(placed).is(AllBlocks.PORTABLE_STORAGE_INTERFACE) && client.player.getMainHandItem().isEmpty(), 200);
        waitServer(context, world, level -> level.getBlockState(placed).is(AllBlocks.PORTABLE_STORAGE_INTERFACE)
            && level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().isEmpty(), 200, "Native item placement was not confirmed by the server");
        context.runOnClient(client -> { if (client.level.getBlockState(placed).getValue(PortableStorageInterfaceBlock.FACING) != Direction.SOUTH) throw new AssertionError("Native item placement ignored nearest look direction"); });
        server.runCommand("tp @p 4268.5 4 12.5"); server.runCommand("item replace entity @p weapon.mainhand with create:wrench");
        context.waitFor(client -> Math.abs(client.player.getX() - 4268.5) < .1 && client.player.getMainHandItem().is(AllItems.WRENCH), 200);
        look(context, Vec3.atCenterOf(placed)); context.waitTicks(3);
        screenshot(context, "create-vulkan-portable-interface-placement-before-wrench");
        context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(placed) && hit.getDirection() == Direction.WEST, 150); use(context);
        context.waitFor(client -> client.level.getBlockState(placed).getValue(PortableStorageInterfaceBlock.FACING) != Direction.SOUTH, 200);
        waitServer(context, world, level -> level.getBlockState(placed).is(AllBlocks.PORTABLE_STORAGE_INTERFACE)
            && level.getBlockState(placed).getValue(PortableStorageInterfaceBlock.FACING) != Direction.SOUTH, 200, "Native wrench rotation was not confirmed by the server");
        screenshot(context, "create-vulkan-portable-interface-actual-placement-and-wrench");
        context.getInput().holdKey(options -> options.keyShift);
        try { context.waitTicks(3); use(context); context.waitFor(client -> client.level.getBlockState(placed).isAir(), 200); }
        finally { context.getInput().releaseKey(options -> options.keyShift); }
        waitServer(context, world, level -> {
            int count = 0; var player = level.getServer().getPlayerList().getPlayers().getFirst();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i).is(AllBlocks.PORTABLE_STORAGE_INTERFACE.asItem())) count += player.getInventory().getItem(i).getCount();
            return level.getBlockState(placed).isAir() && count == 1;
        }, 200, "Native Shift-wrench refund was not confirmed by the server");
        context.waitFor(client -> {
            int returned = 0; for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) if (client.player.getInventory().getItem(i).is(AllBlocks.PORTABLE_STORAGE_INTERFACE.asItem())) returned += client.player.getInventory().getItem(i).getCount();
            return returned == 1;
        }, 200);
        screenshot(context, "create-vulkan-portable-interface-native-shift-wrench-refund"); server.runCommand("gamemode spectator @p");
    }
    private static void setup(TestSingleplayerContext world, BlockPos p, boolean fluids) {
        block(world, p, "create:portable_" + (fluids ? "fluid" : "storage") + "_interface[facing=north]");
        block(world, p.east(), fluids ? "create:fluid_tank" : "minecraft:barrel[facing=north]"); block(world, p.below(), "create:mechanical_bearing[facing=up]");
        world.getServer().runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            if (fluids) { try (var tx = Transaction.openOuter()) { ((FluidTankBlockEntity) level.getBlockEntity(p.east())).getTankInventory().insert(FluidVariant.of(Fluids.WATER), WATER, tx); tx.commit(); } }
            else ((BarrelBlockEntity) level.getBlockEntity(p.east())).setItem(0, new ItemStack(Items.DIAMOND, 64));
            ((MechanicalBearingBlockEntity) level.getBlockEntity(p.below())).setMovementMode(IControlContraption.RotationMode.ROTATE_NEVER_PLACE);
            level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(p, p.east())));
        });
        block(world, p.below(2), "create:creative_motor[facing=up]");
    }
    private static void verifyTransforms(ClientGameTestContext context) {
        context.runOnClient(client -> { for (var facing : Direction.values()) {
            var pose = new PoseStack(); PortableStorageInterfaceRenderer.transform(pose, facing, .75f);
            var tip = pose.last().pose().transformPosition(new Vector3f(.5f, 1.5f, .5f)); var expected = new Vec3(.5, .5, .5).add(facing.getUnitVec3().scale(1.75));
            if (new Vec3(tip.x, tip.y, tip.z).distanceTo(expected) > 1e-5) throw new AssertionError("PSI extension points against its block: " + facing);
        } });
    }
    private static PortableStorageInterfaceRenderer.State rendered(net.minecraft.client.Minecraft client, UUID id) {
        var owner = (ControlledContraptionEntity) find(client.level, id); var renderer = (ContraptionEntityRenderer) client.getEntityRenderDispatcher().getRenderer(owner); var state = renderer.createRenderState(owner, .5f);
        if (state.actors.size() != 1 || state.blockEntities.stream().anyMatch(be -> be.pos().equals(BlockPos.ZERO))) throw new AssertionError("Missing or duplicated native moving PSI");
        return ((PortableStorageInterfaceActorRenderer.State) state.actors.getFirst().state()).state();
    }
    private static void assertItems(ServerLevel level, UUID id, long expected) { if (items((ControlledContraptionEntity) level.getEntity(id)) != expected || itemOutput(level) + expected != 64) throw new AssertionError("Native item transfer lost/duplicated stock"); }
    private static void assertWater(ServerLevel level, UUID id, long expected) { if (water((ControlledContraptionEntity) level.getEntity(id)) != expected || receivedWater(level) + expected != WATER) throw new AssertionError("Native fluid transfer lost/duplicated units"); }
    private static long containerItems(net.minecraft.world.Container container) { long n = 0; for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(Items.DIAMOND)) n += container.getItem(slot).getCount(); return n; }
    private static long itemOutput(ServerLevel level) { return containerItems((net.minecraft.world.Container) level.getBlockEntity(T.below())) + containerItems((net.minecraft.world.Container) level.getBlockEntity(T.below(2))); }
    private static long receivedWater(ServerLevel level) { return ((FluidTankBlockEntity) level.getBlockEntity(F.west(3))).getTankInventory().getFluidAmount(); }
    private static long items(ControlledContraptionEntity owner) { long n = 0; for (var view : owner.getContraption().getStorage().getAllItems()) if (view.getResource().isOf(Items.DIAMOND)) n += view.getAmount(); return n; }
    private static long water(ControlledContraptionEntity owner) { long n = 0; for (var view : owner.getContraption().getStorage().getFluids()) n += view.getAmount(); return n; }
    private static ControlledContraptionEntity owner(ServerLevel level, BlockPos p) { return ((MechanicalBearingBlockEntity) level.getBlockEntity(p.below())).getMovedContraption(); }
    private static void block(TestSingleplayerContext world, BlockPos p, String state) { world.getServer().runCommand("setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + state); }
    private static void pause(TestSingleplayerContext world, BlockPos p) { world.getServer().runOnServer(ignored -> ((KineticBlockEntity) world.getConnection().getServerLevel().getBlockEntity(p.below(2))).setGeneratedSpeed(0)); }
    private static void openBarrel(ClientGameTestContext context, TestSingleplayerContext world, UUID id) {
        var origin = context.computeOnClient(client -> ((ControlledContraptionEntity) find(client.level, id)).toGlobalVector(new Vec3(1.5, .5, 3.5), 1));
        world.getServer().runCommand("tp @p " + origin.x + " 4 " + origin.z); world.getServer().runCommand("gamemode survival @p");
        context.waitFor(client -> !client.player.isSpectator() && Math.abs(client.player.getX() - origin.x) < .1, 200);
        look(context, context.computeOnClient(client -> ((ControlledContraptionEntity) find(client.level, id)).toGlobalVector(new Vec3(1.5, .5, .5), 1))); use(context);
        context.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen, 200);
    }
    private static void disassemble(ClientGameTestContext context, TestSingleplayerContext world, BlockPos p, UUID id) {
        world.getServer().runCommand("tp @p " + (p.getX() + .5) + " 4 " + (p.getZ() + 4.5)); world.getServer().runCommand("gamemode survival @p");
        context.waitFor(client -> !client.player.isSpectator() && Math.abs(client.player.getZ() - p.getZ() - 4.5) < .1, 200);
        look(context, Vec3.atCenterOf(p.below())); context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(p.below()), 150); use(context);
        context.waitFor(client -> find(client.level, id) == null, 200);
    }
    private static void waitServer(ClientGameTestContext context, TestSingleplayerContext world, Predicate<ServerLevel> predicate, int ticks, String message) {
        var ready = new AtomicBoolean(); for (int i = 0; i < ticks && !ready.get(); i++) { world.getServer().runOnServer(ignored -> ready.set(predicate.test(world.getConnection().getServerLevel()))); if (!ready.get()) context.waitTicks(1); }
        if (!ready.get()) {
            var diagnostic = new AtomicReference<String>("");
            if (message.startsWith("Real pump")) world.getServer().runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel(); var owner = owner(level, Q); var endpoint = (PortableFluidInterfaceBlockEntity) level.getBlockEntity(F);
                var pump = (KineticBlockEntity) level.getBlockEntity(F.west(2));
                long probe = 0; try (var tx = Transaction.openOuter()) { probe = endpoint.storage().extract(FluidVariant.of(Fluids.WATER), 1, tx); }
                diagnostic.set("owner=" + (owner == null ? "missing" : owner.getAngle(1) + "/stalled=" + owner.isStalled() + "/water=" + water(owner) + "/actor=" + owner.getContraption().getActors().getFirst().right.data)
                    + "; endpoint timer=" + endpoint.getTransferTimer() + "/connected=" + endpoint.canTransfer() + "/probe=" + probe + "; pump RPM=" + pump.getSpeed() + "; received=" + receivedWater(level)
                    + "; pipe=" + level.getBlockState(F.west()));
            });
            throw new AssertionError(message + " " + diagnostic.get());
        }
    }
    private static net.minecraft.world.entity.Entity find(net.minecraft.client.multiplayer.ClientLevel level, UUID id) { if (level != null) for (var entity : level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity; return null; }
    private static void use(ClientGameTestContext context) { context.waitTicks(6); context.getInput().pressKey(options -> options.keyUse); }
    private static void screenshot(ClientGameTestContext context, String name) { context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); context.takeScreenshot(name); }
    private static void look(ClientGameTestContext context, Vec3 target) { var angle = context.computeOnClient(client -> { var d = target.subtract(client.player.getEyePosition()); return new Vec2((float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)))); }); context.getInput().lookAt(angle.x, angle.y); }
}
