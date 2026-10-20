package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.*;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.processing.basin.*;
import com.simibubi.create.content.processing.burner.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;

/** Real entity/storage tracking, scripted moving poses, native extraction, input and two-chunk unload. */
final class ContainerActorRenderingTest {
    private static final BlockPos P = new BlockPos(4607, 11, 5), BARREL = new BlockPos(1, 0, 0), BASIN = new BlockPos(2, 0, 0), BURNER = new BlockPos(3, 0, 0), TANK = new BlockPos(4, 0, 0), COLLECT = new BlockPos(5, 0, 0), EXTRACT = new BlockPos(6, 0, 0);
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer(); server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 4610.5 12 11.5");
        context.waitFor(client -> Math.abs(client.player.getX() - 4610.5) < .1 && client.level.isLoaded(P.east(6)), 400);
        server.runCommand("fill 4598 2 -5 4623 2 16 minecraft:smooth_quartz");
        block(world, P, "minecraft:stone"); block(world, P.east(), "minecraft:barrel[facing=south]"); block(world, P.east(2), "create:basin");
        block(world, P.east(3), "create:blaze_burner[blaze=kindled]"); block(world, P.east(4), "create:fluid_tank");
        block(world, P.east(5), "create:andesite_funnel[facing=up,extracting=false]"); block(world, P.east(6), "create:brass_funnel[facing=up,extracting=true]");
        var id = new AtomicReference<UUID>();
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); ((BarrelBlockEntity) level.getBlockEntity(P.east())).setItem(0, new ItemStack(Items.DIAMOND, 7));
            var basin = (BasinBlockEntity) level.getBlockEntity(P.east(2));
            try (var tx = Transaction.openOuter()) { basin.inventory.inputs.getFirst().insert(ItemVariant.of(named(Items.EMERALD, 1)), 7, tx); basin.inventory.outputs.getLast().insert(ItemVariant.of(Items.GOLD_INGOT), 11, tx); basin.fluids.inputs.getFirst().insert(FluidVariant.of(Fluids.WATER), 17001, tx); tx.commit(); }
            var burner = (BlazeBurnerBlockEntity) level.getBlockEntity(P.east(3)); burner.tryFuel(new ItemStack(Items.COAL), false, false); burner.goggles = true; burner.trainHat = true;
            ((FluidTankBlockEntity) level.getBlockEntity(P.east(4))).getTankInventory().setFluid(new com.simibubi.create.foundation.fluid.FluidStack(FluidVariant.of(Fluids.WATER), 17001));
            var funnel = (FunnelBlockEntity) level.getBlockEntity(P.east(6)); funnel.filtering.setFilter(new ItemStack(Items.DIAMOND)); funnel.filtering.setAmount(2, false);
            level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(P, P.east(6))));
        });
        // Loaded block chunks may precede their native entity-section visibility after teleport.
        // Wait for the newly added glue to be queryable before capturing the cross-chunk row.
        waitServer(context, world, level -> SuperGlueEntity.isGlued(level,P,Direction.EAST,null), 200, "Container fixture glue did not become visible");
        NativeContraptionFixtures.awaitCaptureReady(context,world,200,P,P.east(6));
        server.runOnServer(ignored -> {
            var level=world.getConnection().getServerLevel();
            Create.LOGGER.info("Container fixture glue visible before seven-block capture");
            var structure = new BearingContraption(false, Direction.SOUTH);
            try {
                if(!structure.assemble(level,P.north())||structure.getBlocks().size()!=7)throw new AssertionError("Container fixture capture did not include all seven blocks: "+structure.getBlocks());
                if(!structure.tryRemoveBlocksFromWorld(level,BlockPos.ZERO))throw new AssertionError("Container capture failed");
            } catch (AssemblyException failure) { throw new AssertionError(failure); }
            var owner = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, level); owner.setContraption(structure); owner.setPos(Vec3.atLowerCornerOf(P)); owner.setRotationAxis(Direction.Axis.Z);
            structure.setActorsActive(new ItemStack(AllBlocks.BRASS_FUNNEL), false); level.addFreshEntity(owner); id.set(owner.getUUID());
        });
        try {
            context.waitFor(client -> find(client.level, id.get()) instanceof ControlledContraptionEntity owner && owner.getContraption() != null && owner.getContraption().getBlocks().size() == 7, 300);
        } catch(AssertionError failure) {
            server.runOnServer(ignored -> {
                var level=world.getConnection().getServerLevel();var entity=level.getEntity(id.get());
                Create.LOGGER.error("Container fixture server owner {}: {}, blocks {}, tracked {}, player {}",id.get(),entity,entity instanceof ControlledContraptionEntity owner?owner.getContraption().getBlocks():null,entity==null?List.of():net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(entity),level.getServer().getPlayerList().getPlayers().getFirst().position());
            });
            context.runOnClient(client->{var entity=find(client.level,id.get());Create.LOGGER.error("Container fixture client owner {}: {}, blocks {}, player {}",id.get(),entity,entity instanceof ControlledContraptionEntity owner&&owner.getContraption()!=null?owner.getContraption().getBlocks():null,client.player.position());});
            throw failure;
        }
        look(context, Vec3.atCenterOf(P.east(3))); world.getConnection().waitForChunksRender();
        context.runOnClient(client -> {
            var owner = owner(client, id.get()); var state = rendered(client, id.get());
            if (state.actors.size() != 1 || state.blockEntities.stream().anyMatch(be -> be.pos().equals(BURNER))) throw new AssertionError("Burner missing or rendered twice");
            var burner = (BlazeBurnerRenderer.State) state.actors.getFirst().state();
            if (!burner.hasHead() || burner.hasFlame() || burner.inert || burner.goggles.isEmpty() || burner.hat.isEmpty() || burner.hatYawOffset() != 180) throw new AssertionError("Source moving burner visuals/accessories/orientation missing");
            if (!client.getModelManager().getModel(AllPartialModels.TRAIN_HAT).particleMaterial().sprite().contents().name().equals(Create.asResource("block/train_hat"))) throw new AssertionError("Train hat has a missing sprite");
            if (basinState(state).itemCount() != 2 || basinState(state).fluidCount() != 1) throw new AssertionError("Native basin contents missing");
            var ctx = actor(owner, BURNER); var camera = owner.toGlobalVector(new Vec3(7.5, .5, .5), 1); var expected = BlazeBurnerActorRenderer.targetAngle(ctx, camera);
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(expected + 90)) > .01) throw new AssertionError("Burner camera is not in contraption-local space");
        }); screenshot(context,world, "create-vulkan-container-actors-initial-native-contents");
        // Put real cargo at the next transformed collecting point, then move the owner through it.
        var collected = new AtomicReference<UUID>();
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var owner = (ControlledContraptionEntity) level.getEntity(id.get()); owner.setAngle(20);
            var target = owner.toGlobalVector(Vec3.atCenterOf(COLLECT).add(0, .65, 0), 1); var item = new ItemEntity(level, target.x, target.y, target.z, named(Items.IRON_INGOT, 3)); item.setNoGravity(true); item.setDeltaMovement(Vec3.ZERO); level.addFreshEntity(item); collected.set(item.getUUID()); owner.tickActors();
            if (item.isAlive() || count(owner, Items.IRON_INGOT) != 3) throw new AssertionError("Moving collection did not transfer real cargo exactly once");
            owner.getContraption().setActorsActive(new ItemStack(AllBlocks.BRASS_FUNNEL), true); owner.setAngle(40); owner.tickActors(); owner.getContraption().setActorsActive(new ItemStack(AllBlocks.BRASS_FUNNEL), false);
            if (count(owner, Items.DIAMOND) != 5) throw new AssertionError("Moving exact ejection failed");
            var entities = level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(2), e -> e.getItem().is(Items.DIAMOND));
            if (entities.size() != 1 || entities.getFirst().getItem().getCount() != 2) throw new AssertionError("Wrong physical ejection batch");
            entities.getFirst().setNoGravity(true); entities.getFirst().setDeltaMovement(Vec3.ZERO);
        });
        context.waitFor(client -> Math.abs(owner(client, id.get()).getAngle(1) - 40) < .1, 200); context.waitTicks(8); screenshot(context,world, "create-vulkan-moving-funnel-collection-and-exact-ejection");
        // Fill interpolation continues while the finite-tank actor is disabled.
        server.runOnServer(ignored -> {
            var owner = (ControlledContraptionEntity) world.getConnection().getServerLevel().getEntity(id.get()); owner.getContraption().setActorsActive(new ItemStack(AllBlocks.FLUID_TANK), false);
            var storage = owner.getContraption().getStorage().getFluids().storages.get(TANK); try (var tx = Transaction.openOuter()) { if (storage.insert(FluidVariant.of(Fluids.WATER), 81000, tx) != 81000) throw new AssertionError("Mounted tank fill failed"); tx.commit(); } ContraptionNetworking.syncStorage(owner);
        });
        context.waitFor(client -> { var tank = tank(client, id.get()); return tank.getTankInventory().getFluidAmount() == 98001 && tank.getFluidLevel(1) > tank.getFluidLevel(0) && tank.getFluidLevel(1) < tank.getFillState(); }, 200);
        context.waitTicks(16); context.runOnClient(client -> { var owner = owner(client, id.get()); var tank = tank(client, id.get()); if (!actor(owner, TANK).disabled || Math.abs(tank.getFluidLevel(1) - tank.getFillState()) > .001) throw new AssertionError("Disabled tank failed smooth chase"); });
        // The existing render view must be updated by an actor delta, without a full snapshot replacement.
        var originalView = context.computeOnClient(client -> owner(client, id.get()).getClientRenderState());
        server.runOnServer(ignored -> { var owner = (ControlledContraptionEntity) world.getConnection().getServerLevel().getEntity(id.get()); owner.setAngle(180); owner.tickActors(); if (!actor(owner, BASIN).data.getBooleanOr(BasinMovementBehaviour.DUMPED, false)) throw new AssertionError("Basin did not dump upside down"); });
        context.waitFor(client -> actor(owner(client, id.get()), BASIN).data.getBooleanOr(BasinMovementBehaviour.DUMPED, false) && Math.abs(owner(client, id.get()).getAngle(1) - 180) < .01 && basinState(rendered(client, id.get())).itemCount() == 0, 200);
        context.waitTicks(3);
        context.runOnClient(client -> {
            var owner = owner(client, id.get()); if (owner.getClientRenderState() != originalView || basinState(rendered(client, id.get())).fluidCount() != 1) throw new AssertionError("Basin delta replaced view or spilled fluid");
            var ctx = actor(owner, BURNER); var right = owner.toGlobalVector(Vec3.atCenterOf(BURNER).add(4, 0, 0), 1); if (Math.abs(net.minecraft.util.Mth.wrapDegrees(BlazeBurnerActorRenderer.targetAngle(ctx, right) + 90)) > .01) throw new AssertionError("Rotated burner gaze failed");
        }); look(context, Vec3.atCenterOf(P).add(-3, 0, 0)); screenshot(context,world, "create-vulkan-inverted-basin-cleared-items-preserved-fluid");
        // Item entities are saved too; make no duplicate after a real owner and both source chunks unload.
        var fuel = new AtomicInteger(); server.runOnServer(ignored -> { var level = world.getConnection().getServerLevel(); var owner = (ControlledContraptionEntity) level.getEntity(id.get()); fuel.set(actor(owner, BURNER).blockEntityData.getIntOr("burnTimeRemaining", 0)); for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(P).inflate(12))) { item.setNoGravity(true); item.setDeltaMovement(Vec3.ZERO); } });
        server.runCommand("tp @p 5300 15 5300"); waitServer(context, world, level -> level.getEntity(id.get()) == null && level.getChunkSource().getChunkNow(P.getX() >> 4, 0) == null && level.getChunkSource().getChunkNow(P.east().getX() >> 4, 0) == null, 800, "Container owner/source chunks did not unload");
        server.runCommand("tp @p 4604.5 15 15.5"); waitServer(context, world, level -> level.getEntity(id.get()) instanceof ControlledContraptionEntity, 500, "Container owner did not return");
        context.waitFor(client -> find(client.level, id.get()) instanceof ControlledContraptionEntity owner && owner.getContraption() != null, 300);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var owner = (ControlledContraptionEntity) level.getEntity(id.get());
            if (Math.abs(owner.getAngle(1) - 180) > .01 || count(owner, Items.DIAMOND) != 5 || count(owner, Items.IRON_INGOT) != 3 || actor(owner, BURNER).blockEntityData.getIntOr("burnTimeRemaining", 0) != fuel.get()) throw new AssertionError("Unload changed pose/cargo/fuel");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(P).inflate(12)); int emeralds = 0, gold = 0, diamonds = 0;
            for (var e : drops) { if (e.getItem().is(Items.EMERALD)) emeralds += e.getItem().getCount(); if (e.getItem().is(Items.GOLD_INGOT)) gold += e.getItem().getCount(); if (e.getItem().is(Items.DIAMOND)) diamonds += e.getItem().getCount(); }
            if (emeralds != 7 || gold != 11 || diamonds != 2) throw new AssertionError("Real reload duplicated/lost physical drops: " + emeralds + "/" + gold + "/" + diamonds);
        });
        context.runOnClient(client -> { var state = rendered(client, id.get()); if (basinState(state).itemCount() != 0 || tank(client, id.get()).getTankInventory().getFluidAmount() != 98001) throw new AssertionError("Tracking snapshot restored stale container contents"); });
        var mesh = context.computeOnClient(client -> ((BlazeBurnerRenderer.State) rendered(client, id.get()).actors.getFirst().state()).hat.mesh);
        var reload = context.computeOnClient(client -> client.reloadResourcePacks()); context.waitFor(client -> reload.isDone(), 500); reload.join(); context.waitTicks(3);
        context.runOnClient(client -> { if (((BlazeBurnerRenderer.State) rendered(client, id.get()).actors.getFirst().state()).hat.mesh == mesh) throw new AssertionError("Actor retained stale hat mesh after resource reload"); }); screenshot(context,world, "create-vulkan-container-actors-unload-return-reload");
        // Restore upright, open the real moving barrel using normal input, then return world ownership.
        server.runOnServer(ignored -> { var owner = (ControlledContraptionEntity) world.getConnection().getServerLevel().getEntity(id.get()); owner.setAngle(0); owner.tickActors(); });
        block(world, P.east().south(3).below(2), "minecraft:smooth_quartz");
        server.runCommand("tp @p 4608.5 10 8.5"); server.runCommand("gamemode survival @p"); context.waitFor(client -> !client.player.isSpectator() && client.player.onGround() && Math.abs(owner(client, id.get()).getAngle(1)) < .01 && Math.abs(client.player.getZ() - 8.5) < .1, 200); look(context, Vec3.atCenterOf(P.east())); context.waitTicks(6);
        waitServer(context, world, level -> { var owner = (ControlledContraptionEntity) level.getEntity(id.get()); var player = level.getServer().getPlayerList().getPlayers().getFirst(); var eye = player.getEyePosition(); var hit = owner.rayTrace(eye, eye.add(player.getViewVector(1).scale(5)), 1); return hit != null && hit.getBlockPos().equals(BARREL); }, 150, "Actual player ray did not reach moving barrel");
        context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen && client.player.containerMenu instanceof ChestMenu, 200);
        context.waitFor(client -> client.player.containerMenu.getSlot(0).getItem().is(Items.DIAMOND) && client.player.containerMenu.getSlot(0).getItem().getCount() == 5 && client.player.containerMenu.getSlot(1).getItem().is(Items.IRON_INGOT) && client.player.containerMenu.getSlot(1).getItem().has(DataComponents.CUSTOM_NAME), 200);
        screenshot(context,world, "create-vulkan-container-actors-real-moving-barrel-menu"); context.getInput().pressKey(InputConstants.KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null, 100);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var owner = (ControlledContraptionEntity) level.getEntity(id.get()); if (!owner.disassemble()) throw new AssertionError("Container disassembly failed");
            var basin = (BasinBlockEntity) level.getBlockEntity(P.east(2)); var burner = (BlazeBurnerBlockEntity) level.getBlockEntity(P.east(3)); var tank = (FluidTankBlockEntity) level.getBlockEntity(P.east(4)); var barrel = (BarrelBlockEntity) level.getBlockEntity(P.east());
            if (basin.allItems().stream().anyMatch(slot -> !slot.isResourceBlank()) || basin.fluids.inputs.getFirst().getAmount() != 17001 || burner.getRemainingBurnTime() != fuel.get() || !burner.goggles || !burner.trainHat || tank.getTankInventory().getFluidAmount() != 98001 || barrel.getItem(0).getCount() != 5 || barrel.getItem(1).getCount() != 3) throw new AssertionError("World restoration changed actual contents/accessories/fuel");
        }); server.runCommand("gamemode spectator @p"); look(context, Vec3.atCenterOf(P.east(3))); screenshot(context,world, "create-vulkan-container-actors-world-restored");
    }
    private static MovementContext actor(ControlledContraptionEntity owner, BlockPos pos) { return owner.getContraption().getActorAt(pos).right; }
    private static ItemStack named(Item item, int amount) { var stack = new ItemStack(item, amount); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Moving cargo")); return stack; }
    private static ControlledContraptionEntity owner(net.minecraft.client.Minecraft client, UUID id) { return (ControlledContraptionEntity) find(client.level, id); }
    private static ContraptionEntityRenderer.State rendered(net.minecraft.client.Minecraft client, UUID id) { var owner = owner(client, id); return ((ContraptionEntityRenderer) client.getEntityRenderDispatcher().getRenderer(owner)).createRenderState(owner, .5f); }
    private static BasinRenderer.State basinState(ContraptionEntityRenderer.State state) { return (BasinRenderer.State) state.blockEntities.stream().filter(be -> be.pos().equals(BASIN)).findFirst().orElseThrow().state(); }
    private static FluidTankBlockEntity tank(net.minecraft.client.Minecraft client, UUID id) { rendered(client, id); return (FluidTankBlockEntity) ((VirtualRenderWorld) owner(client, id).getClientRenderState()).getBlockEntity(TANK); }
    private static long count(ControlledContraptionEntity owner, Item item) { long n = 0; for (var view : owner.getContraption().getStorage().getAllItems()) if (view.getResource().isOf(item)) n += view.getAmount(); return n; }
    private static net.minecraft.world.entity.Entity find(net.minecraft.client.multiplayer.ClientLevel level, UUID id) { if (level != null) for (var entity : level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity; return null; }
    private static void waitServer(ClientGameTestContext context, TestSingleplayerContext world, Predicate<ServerLevel> predicate, int ticks, String message) { var ready = new AtomicBoolean(); for (int i = 0; i < ticks && !ready.get(); i++) { world.getServer().runOnServer(ignored -> ready.set(predicate.test(world.getConnection().getServerLevel()))); if (!ready.get()) context.waitTicks(1); } if (!ready.get()) throw new AssertionError(message); }
    private static void block(TestSingleplayerContext world, BlockPos pos, String state) { world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state); }
    private static void screenshot(ClientGameTestContext context,TestSingleplayerContext world,String name){
        NativeScreenshotReadiness.await(context,world);
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); context.takeScreenshot(name);
    }
    private static void look(ClientGameTestContext context, Vec3 target) { var angle = context.computeOnClient(client -> { var d = target.subtract(client.player.getEyePosition()); return new Vec2((float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)))); }); context.getInput().lookAt(angle.x, angle.y); }
}
