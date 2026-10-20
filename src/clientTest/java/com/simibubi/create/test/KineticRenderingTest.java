package com.simibubi.create.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.core.BlockPos;

/** Verifies real model baking, server-to-client RPM updates and rendering on Vulkan. */
public final class KineticRenderingTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("create.test.onlyRailGraphSync")) return;
        if(!Boolean.getBoolean("create.test.onlyPipes")&&!Boolean.getBoolean("create.test.onlyCutting"))CreateMainMenuRenderingTest.run(context);
        if(Boolean.getBoolean("create.test.onlyCreateMenu"))return;
        context.runOnClient(client -> {
            String backend = RenderSystem.getDevice().getDeviceInfo().backendName();
            Create.LOGGER.info("Create rendering integration test backend: {}", backend);
            if (!backend.toLowerCase(java.util.Locale.ROOT).contains("vulkan")) {
                throw new AssertionError("Vulkan test fell back to another graphics API: " + backend);
            }
            var resolver = new BlockModelResolver(client.getModelManager());
            var addedBlocks = new java.util.ArrayList<net.minecraft.world.level.block.Block>(java.util.List.of(
                AllBlocks.MECHANICAL_PISTON, AllBlocks.STICKY_MECHANICAL_PISTON, AllBlocks.PISTON_EXTENSION_POLE, AllBlocks.MECHANICAL_PISTON_HEAD, AllBlocks.SEQUENCED_GEARSHIFT,
                AllBlocks.WINDMILL_BEARING, AllBlocks.CLOCKWORK_BEARING, AllBlocks.SAIL_FRAME,
                AllBlocks.LINEAR_CHASSIS, AllBlocks.SECONDARY_LINEAR_CHASSIS, AllBlocks.RADIAL_CHASSIS,
                AllBlocks.STICKER, AllBlocks.REDSTONE_LINK, AllBlocks.LECTERN_CONTROLLER));
            addedBlocks.addAll(AllBlocks.DYED_SAILS.values());
            for (var block : addedBlocks) for (var state : block.getStateDefinition().getPossibleStates()) {
                if (client.getModelManager().getBlockStateModelSet().get(state) == client.getModelManager().getBlockStateModelSet().missingModel())
                    throw new AssertionError("Missing newly ported block model: " + state);
            }

            for(var block:java.util.List.of(AllBlocks.STEAM_ENGINE,AllBlocks.POWERED_SHAFT,AllBlocks.STEAM_WHISTLE,AllBlocks.STEAM_WHISTLE_EXTENSION))for(var state:block.getStateDefinition().getPossibleStates()){
                var model=new BlockModelRenderState();resolver.update(model,state,BlockDisplayContext.create());
                if(model.isEmpty()||client.getModelManager().getBlockStateModelSet().get(state)==client.getModelManager().getBlockStateModelSet().missingModel())throw new AssertionError("Missing engine/shaft model: "+state);
            }
            for (var block : new net.minecraft.world.level.block.Block[]{
                    AllBlocks.SHAFT, AllBlocks.COGWHEEL, AllBlocks.LARGE_COGWHEEL,
                    AllBlocks.CREATIVE_MOTOR, AllBlocks.HAND_CRANK, AllBlocks.GEARBOX, AllBlocks.GEARSHIFT,
                    AllBlocks.CLUTCH, AllBlocks.ENCASED_CHAIN_DRIVE, AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT,
                    AllBlocks.MILLSTONE, AllBlocks.MECHANICAL_PRESS, AllBlocks.DEPOT, AllBlocks.ANDESITE_CASING,
                    AllBlocks.WATER_WHEEL, AllBlocks.LARGE_WATER_WHEEL, AllBlocks.MECHANICAL_MIXER, AllBlocks.BASIN,
                    AllBlocks.BLAZE_BURNER, AllBlocks.ZINC_ORE, AllBlocks.DEEPSLATE_ZINC_ORE,
                    AllBlocks.ZINC_BLOCK, AllBlocks.RAW_ZINC_BLOCK, AllBlocks.BRASS_BLOCK, AllBlocks.BRASS_CASING, AllBlocks.ENCASED_FAN, AllBlocks.BELT,
                    AllBlocks.CRUSHING_WHEEL, AllBlocks.ASURINE, AllBlocks.CRIMSITE, AllBlocks.OCHRUM, AllBlocks.VERIDIUM, AllBlocks.MECHANICAL_CRAFTER, AllBlocks.MECHANICAL_DRILL, AllBlocks.MECHANICAL_SAW, AllBlocks.FLUID_TANK, AllBlocks.CREATIVE_FLUID_TANK, AllBlocks.FLUID_PIPE, AllBlocks.GLASS_FLUID_PIPE, AllBlocks.ENCASED_FLUID_PIPE, AllBlocks.MECHANICAL_PUMP, AllBlocks.COPPER_CASING, AllBlocks.SMART_FLUID_PIPE, AllBlocks.FLUID_VALVE, AllBlocks.SPOUT, AllBlocks.ITEM_DRAIN, AllBlocks.HOSE_PULLEY}) {
                for (var blockState : block.getStateDefinition().getPossibleStates()) {
                var model = new BlockModelRenderState();
                if (client.getModelManager().getBlockStateModelSet().get(blockState)
                        == client.getModelManager().getBlockStateModelSet().missingModel()) {
                    throw new AssertionError("Missing model for " + block);
                }
                resolver.update(model, blockState, BlockDisplayContext.create());
                if (model.isEmpty() && block != AllBlocks.BELT && block != AllBlocks.FLUID_PIPE) {
                    throw new AssertionError("Empty model for " + block);
                }
                }
            }
            AllPartialModels.extract(AllPartialModels.SHAFT_HALF, new BlockModelRenderState());
            AllPartialModels.extract(AllPartialModels.HAND_CRANK_HANDLE, new BlockModelRenderState());
            AllPartialModels.extract(AllPartialModels.MILLSTONE_COG, new BlockModelRenderState());
            AllPartialModels.extract(AllPartialModels.MECHANICAL_PRESS_HEAD, new BlockModelRenderState());
            assertWheelMeshes();
            for (var key : java.util.List.of(AllPartialModels.MIXER_HEAD, AllPartialModels.MIXER_POLE,
                    AllPartialModels.SHAFTLESS_COGWHEEL, AllPartialModels.BLAZE_INERT, AllPartialModels.BLAZE_ACTIVE,
                    AllPartialModels.BLAZE_RODS, AllPartialModels.BLAZE_FLAME, AllPartialModels.FAN_PROPELLER,
                    AllPartialModels.BELT_START, AllPartialModels.BELT_MIDDLE, AllPartialModels.BELT_END,
                    AllPartialModels.BELT_START_BOTTOM, AllPartialModels.BELT_MIDDLE_BOTTOM, AllPartialModels.BELT_END_BOTTOM,
                    AllPartialModels.BELT_DIAGONAL_START, AllPartialModels.BELT_DIAGONAL_MIDDLE, AllPartialModels.BELT_DIAGONAL_END,
                    AllPartialModels.BELT_PULLEY, AllPartialModels.CRUSHING_WHEEL,
                    AllPartialModels.DRILL_HEAD, AllPartialModels.FLUID_VALVE_POINTER, AllPartialModels.HOSE_COIL, AllPartialModels.HOSE, AllPartialModels.HOSE_HALF, AllPartialModels.HOSE_MAGNET, AllPartialModels.HOSE_HALF_MAGNET, AllPartialModels.SPOUT_TOP, AllPartialModels.SPOUT_MIDDLE, AllPartialModels.SPOUT_BOTTOM, AllPartialModels.MECHANICAL_PUMP_COG, AllPartialModels.FLUID_PIPE_CASING, AllPartialModels.SAW_HORIZONTAL_ACTIVE, AllPartialModels.SAW_HORIZONTAL_INACTIVE,
                    AllPartialModels.SAW_HORIZONTAL_REVERSED, AllPartialModels.SAW_VERTICAL_ACTIVE,
                    AllPartialModels.SAW_VERTICAL_INACTIVE, AllPartialModels.SAW_VERTICAL_REVERSED,
                    AllPartialModels.MECHANICAL_CRAFTER_LID, AllPartialModels.MECHANICAL_CRAFTER_ARROW, AllPartialModels.MECHANICAL_CRAFTER_BELT, AllPartialModels.MECHANICAL_CRAFTER_BELT_FRAME)) {
                var state = new PartialModelRenderState(); AllPartialModels.extract(key, state);
                if (state.mesh == null || state.mesh.size() == 0) throw new AssertionError("Empty partial: " + key);
            }
        });
        try (var world = context.worldBuilder()
                .adjustSettings(creator -> creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
            var server = world.getServer();
            if(Boolean.getBoolean("create.test.onlyCutting")){
                server.runCommand("gamemode spectator @p");context.runOnClient(client->client.gui.hud.toggle());
                server.runCommand("tp @p 139 5 11");
                context.waitFor(client->client.player.getX()>138&&client.player.getX()<140&&client.player.getZ()>10&&client.player.getZ()<12&&client.level.isLoaded(new BlockPos(130,3,0))&&client.level.isLoaded(new BlockPos(149,8,12)),500);
                world.getConnection().waitForChunksRender();
                server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(!com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level,new BlockPos(130,3,0))||!com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level,new BlockPos(149,8,12)))throw new AssertionError("Actual focused cutting scene columns must be loaded before native block-creation commands");});
                runCuttingScene(context,world);
                context.runOnClient(client->client.gui.hud.toggle());return;
            }
            if (!Boolean.getBoolean("create.test.onlyTanks") && !Boolean.getBoolean("create.test.onlyPipes")) {
            server.runCommand("fill 0 3 0 12 3 12 minecraft:smooth_quartz");
            server.runCommand("setblock 2 4 4 create:creative_motor[facing=east]");
            server.runCommand("setblock 3 4 4 create:shaft[axis=x]");
            server.runCommand("setblock 4 4 4 create:large_cogwheel[axis=x]");
            server.runCommand("setblock 4 5 5 create:cogwheel[axis=x]");
            server.runCommand("tp @p 8 4 10");
            var smallCog = new BlockPos(4, 5, 5);
            context.waitFor(client -> client.level.getBlockEntity(smallCog) instanceof KineticBlockEntity cog
                    && cog.getSpeed() == -32);
            context.getInput().lookAt(new BlockPos(4, 4, 4));
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot("create-vulkan-kinetics");
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                ((KineticBlockEntity) level.getBlockEntity(new BlockPos(2, 4, 4))).adjustGeneratedSpeed(true);
            });
            context.waitFor(client -> client.level.getBlockEntity(smallCog) instanceof KineticBlockEntity cog
                    && cog.getSpeed() == 32);
            context.waitTicks(10);
            context.takeScreenshot("create-vulkan-reversed");
            server.runCommand("setblock 6 4 4 create:shaft[axis=y]");
            server.runCommand("setblock 6 5 4 create:hand_crank[facing=up]");
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(6, 5, 4)) instanceof KineticBlockEntity);
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                ((KineticBlockEntity) level.getBlockEntity(new BlockPos(6, 5, 4))).turnCrank(false);
            });
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(6, 4, 4)) instanceof KineticBlockEntity shaft
                    && shaft.getSpeed() == 32);
            context.getInput().lookAt(new BlockPos(5, 4, 4));
            context.waitTicks(2);
            context.takeScreenshot("create-vulkan-hand-crank");
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(6, 4, 4)) instanceof KineticBlockEntity shaft
                    && shaft.getSpeed() == 0);
            server.runCommand("fill 12 3 0 24 3 12 minecraft:smooth_quartz");
            server.runCommand("setblock 14 4 4 create:creative_motor[facing=up]");
            server.runCommand("setblock 14 5 4 create:millstone");
            server.runCommand("setblock 18 6 4 create:mechanical_press[facing=east]");
            server.runCommand("setblock 17 6 4 create:creative_motor[facing=east]");
            server.runCommand("setblock 18 4 4 create:depot");
            server.runCommand("setblock 21 4 4 create:gearbox[axis=y]");
            server.runCommand("setblock 20 4 4 create:creative_motor[facing=east]");
            server.runCommand("setblock 22 4 4 create:clutch[axis=x]");
            server.runCommand("setblock 23 4 4 create:shaft[axis=x]");
            server.runCommand("tp @p 19 4 10");
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                var mill = (MillstoneBlockEntity) level.getBlockEntity(new BlockPos(14, 5, 4));
                var depot = (DepotBlockEntity) level.getBlockEntity(new BlockPos(18, 4, 4));
                try (Transaction transaction = Transaction.openOuter()) {
                    mill.inventory.automation.insert(ItemVariant.of(net.minecraft.world.item.Items.WHEAT), 1, transaction);
                    depot.inventory.automation.insert(ItemVariant.of(net.minecraft.world.item.Items.GOLD_INGOT), 1, transaction);
                    transaction.commit();
                }
            });
            context.waitFor(client -> client.player.getX() > 18 && client.player.getX() < 21);
            context.getInput().lookAt(new BlockPos(18, 5, 4));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(23, 4, 4)) instanceof KineticBlockEntity shaft
                    && shaft.getSpeed() == -16);
            world.getConnection().waitForChunksRender();
            context.waitTicks(35);
            context.takeScreenshot("create-vulkan-machines");
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(18, 4, 4)) instanceof DepotBlockEntity depot
                    && depot.getHeldItem().is(AllItems.GOLDEN_SHEET), 300);
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(14, 5, 4)) instanceof MillstoneBlockEntity mill
                    && mill.inventory.outputs.stream().anyMatch(slot -> slot.getResource().equals(ItemVariant.of(AllItems.WHEAT_FLOUR))), 300);
            server.runCommand("setblock 22 5 4 minecraft:redstone_block");
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(23, 4, 4)) instanceof KineticBlockEntity shaft
                    && shaft.getSpeed() == 0);
            context.takeScreenshot("create-vulkan-processed");
            var oldPartial = context.computeOnClient(client -> client.getModelManager().getModel(AllPartialModels.MECHANICAL_PRESS_HEAD));
            var oldMesh = context.computeOnClient(client -> {
                var state = new PartialModelRenderState();
                AllPartialModels.extract(AllPartialModels.waterWheel(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState()), state);
                return state.mesh;
            });
            var reload = context.computeOnClient(client -> client.reloadResourcePacks());
            context.waitFor(client -> reload.isDone() && client.gui.overlay() == null);
            context.waitTicks(5);
            context.runOnClient(client -> {
                AllPartialModels.extract(AllPartialModels.SHAFT_HALF, new BlockModelRenderState());
                AllPartialModels.extract(AllPartialModels.HAND_CRANK_HANDLE, new BlockModelRenderState());
                AllPartialModels.extract(AllPartialModels.MILLSTONE_COG, new BlockModelRenderState());
                AllPartialModels.extract(AllPartialModels.MECHANICAL_PRESS_HEAD, new BlockModelRenderState());
                if (client.getModelManager().getModel(AllPartialModels.MECHANICAL_PRESS_HEAD) == oldPartial) {
                    throw new AssertionError("Partial models did not refresh after resource reload");
                }
                assertWheelMeshes();
                var state = new PartialModelRenderState();
                AllPartialModels.extract(AllPartialModels.waterWheel(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState()), state);
                if (state.mesh == oldMesh) throw new AssertionError("Wheel mesh cache survived a model reload");
            });
            server.runCommand("fill 25 3 0 36 3 12 minecraft:smooth_quartz");
            server.runCommand("setblock 29 5 4 create:water_wheel[facing=east]");
            server.runCommand("setblock 30 5 4 create:shaft[axis=x]");
            server.runCommand("setblock 29 4 5 minecraft:soul_sand");
            server.runCommand("setblock 29 5 5 minecraft:bubble_column[drag=false]");
            server.runCommand("tp @p 33 4 10");
            context.waitFor(client -> client.player.getX() > 32);
            context.getInput().lookAt(new BlockPos(29, 5, 4));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(29, 5, 4)) instanceof WaterWheelBlockEntity wheel
                    && Math.abs(wheel.getSpeed()) == 8);
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> client.gui.hud.toggle());
            context.waitTicks(10);
            context.takeScreenshot("create-vulkan-water-wheel");
            server.runOnServer(ignored -> ((WaterWheelBlockEntity) world.getConnection().getServerLevel()
                    .getBlockEntity(new BlockPos(29, 5, 4))).setMaterial(net.minecraft.world.level.block.Blocks.BIRCH_PLANKS.defaultBlockState()));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(29, 5, 4)) instanceof WaterWheelBlockEntity wheel
                    && wheel.getMaterial().is(net.minecraft.world.level.block.Blocks.BIRCH_PLANKS));
            context.waitTicks(5);
            context.takeScreenshot("create-vulkan-water-wheel-birch");
            server.runCommand("setblock 35 6 4 create:large_water_wheel[axis=x]");
            server.runCommand("setblock 36 6 4 create:large_water_wheel[axis=x]");
            server.runCommand("setblock 34 6 4 create:shaft[axis=x]");
            server.runCommand("setblock 35 5 6 minecraft:soul_sand");
            server.runCommand("setblock 35 6 6 minecraft:bubble_column[drag=false]");
            server.runCommand("tp @p 41 5 11");
            context.waitFor(client -> client.player.getX() > 40);
            context.getInput().lookAt(new BlockPos(35, 6, 4));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(35, 6, 4)) instanceof WaterWheelBlockEntity wheel
                    && Math.abs(wheel.getSpeed()) == 4);
            world.getConnection().waitForChunksRender();
            context.waitTicks(5);
            context.takeScreenshot("create-vulkan-large-water-wheels");
            server.runCommand("fill 40 3 0 56 3 12 minecraft:smooth_quartz");
            server.runCommand("setblock 44 4 4 create:blaze_burner[blaze=smouldering]");
            server.runCommand("setblock 44 5 4 create:basin");
            server.runCommand("setblock 44 7 4 create:mechanical_mixer");
            server.runCommand("setblock 43 7 4 create:cogwheel[axis=y]");
            server.runCommand("setblock 43 6 4 create:creative_motor[facing=up]");
            server.runCommand("setblock 50 5 4 create:basin");
            server.runCommand("setblock 50 7 4 create:mechanical_press[facing=east]");
            server.runCommand("setblock 49 7 4 create:creative_motor[facing=east]");
            server.runCommand("gamemode spectator @p");
            server.runCommand("tp @p 48 8 11");
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                ((KineticBlockEntity) level.getBlockEntity(new BlockPos(43, 6, 4))).adjustGeneratedSpeed(false);
                ((BlazeBurnerBlockEntity) level.getBlockEntity(new BlockPos(44, 4, 4))).tryFuel(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL), false, false);
                var basin = (BasinBlockEntity) level.getBlockEntity(new BlockPos(44, 5, 4));
                var pressedBasin = (BasinBlockEntity) level.getBlockEntity(new BlockPos(50, 5, 4));
                try (Transaction transaction = Transaction.openOuter()) {
                    basin.inventory.automation.insert(ItemVariant.of(net.minecraft.world.item.Items.COPPER_INGOT), 2, transaction);
                    basin.inventory.automation.insert(ItemVariant.of(AllItems.ZINC_INGOT), 2, transaction);
                    basin.fluids.automation.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER), 81000, transaction);
                    pressedBasin.inventory.automation.insert(ItemVariant.of(net.minecraft.world.item.Items.FLINT), 4, transaction);
                    pressedBasin.inventory.automation.insert(ItemVariant.of(net.minecraft.world.item.Items.GRAVEL), 2, transaction);
                    pressedBasin.fluids.automation.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.LAVA), 81000, transaction);
                    transaction.commit();
                }
            });
            context.waitFor(client -> client.player.getX() > 47 && client.player.getY() > 7);
            context.getInput().lookAt(new BlockPos(47, 5, 4));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(44, 7, 4)) instanceof KineticBlockEntity mixer && mixer.getSpeed() == -32);
            world.getConnection().waitForChunksRender();
            context.waitTicks(25);
            context.takeScreenshot("create-vulkan-basins-and-burner");
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(44, 5, 4)) instanceof BasinBlockEntity basin
                    && basin.inventory.outputs.stream().anyMatch(slot -> slot.getResource().equals(ItemVariant.of(AllItems.BRASS_INGOT)) && slot.getAmount() == 4), 300);
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(50, 5, 4)) instanceof BasinBlockEntity basin
                    && basin.inventory.outputs.stream().anyMatch(slot -> slot.getResource().equals(ItemVariant.of(net.minecraft.world.item.Items.ANDESITE)) && slot.getAmount() == 2), 300);
            context.takeScreenshot("create-vulkan-basin-results");
            server.runCommand("setblock 54 4 8 create:basin");
            server.runOnServer(ignored -> {
                var basin = (BasinBlockEntity) world.getConnection().getServerLevel().getBlockEntity(new BlockPos(54, 4, 8));
                try (Transaction transaction = Transaction.openOuter()) {
                    var water = net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER);
                    var lava = net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.LAVA);
                    basin.fluids.automation.insert(water, 81000, transaction);
                    basin.fluids.automation.insert(lava, 81000, transaction);
                    basin.fluids.insertOutput(water, 81000, transaction);
                    basin.fluids.insertOutput(lava, 81000, transaction);
                    basin.inventory.automation.insert(ItemVariant.of(AllItems.BRASS_INGOT), 1, transaction);
                    transaction.commit();
                }
            });
            server.runCommand("tp @p 54 7 11");
            context.waitFor(client -> client.player.getX() > 53 && client.player.getY() > 6);
            context.getInput().lookAt(new BlockPos(54, 4, 8));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(54, 4, 8)) instanceof BasinBlockEntity basin
                    && basin.fluids.outputs.stream().allMatch(t -> t.getAmount() == 81000));
            context.waitTicks(5);
            context.takeScreenshot("create-vulkan-basin-fluid-surfaces");
            server.runCommand("fill 56 3 0 68 3 12 minecraft:smooth_quartz");
            server.runCommand("setblock 58 5 4 create:creative_motor[facing=east]");
            server.runCommand("setblock 59 5 4 create:encased_fan[facing=east]");
            server.runCommand("setblock 60 4 4 minecraft:stone");
            server.runCommand("setblock 60 5 4 minecraft:water");
            server.runCommand("setblock 61 4 4 create:depot");
            server.runOnServer(ignored -> {
                var depot = (DepotBlockEntity) world.getConnection().getServerLevel().getBlockEntity(new BlockPos(61, 4, 4));
                try (Transaction transaction = Transaction.openOuter()) {
                    depot.inventory.automation.insert(ItemVariant.of(AllItems.WHEAT_FLOUR), 2, transaction); transaction.commit();
                }
            });
            server.runCommand("tp @p 64 7 10");
            context.waitFor(client -> client.player.getX() > 63);
            context.getInput().lookAt(new BlockPos(60, 5, 4));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(59, 5, 4)) instanceof KineticBlockEntity fan && fan.getSpeed() == 16);
            world.getConnection().waitForChunksRender(); context.waitTicks(10);
            context.takeScreenshot("create-vulkan-fan-washing");
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(61, 4, 4)) instanceof DepotBlockEntity depot && depot.getHeldItem().is(AllItems.DOUGH), 300);
            context.takeScreenshot("create-vulkan-fan-result");
            server.runCommand("fill 68 3 0 96 3 20 minecraft:smooth_quartz");
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                createBelt(level, new BlockPos(71, 4, 3), new BlockPos(77, 4, 3), net.minecraft.core.Direction.Axis.Z, true);
                ((BeltBlockEntity) level.getBlockEntity(new BlockPos(71, 4, 3))).applyColor(net.minecraft.world.item.DyeColor.BLUE);
                createBelt(level, new BlockPos(71, 4, 6), new BlockPos(75, 8, 6), net.minecraft.core.Direction.Axis.Z, true);
                createBelt(level, new BlockPos(79, 8, 6), new BlockPos(83, 4, 6), net.minecraft.core.Direction.Axis.Z, true);
                ((BeltBlockEntity) level.getBlockEntity(new BlockPos(79, 8, 6))).applyColor(net.minecraft.world.item.DyeColor.RED);
                createBelt(level, new BlockPos(86, 4, 6), new BlockPos(86, 8, 6), net.minecraft.core.Direction.Axis.Z, true);
                createBelt(level, new BlockPos(88, 4, 6), new BlockPos(92, 4, 6), net.minecraft.core.Direction.Axis.Y, true);
                createBelt(level, new BlockPos(71, 4, 10), new BlockPos(77, 4, 10), net.minecraft.core.Direction.Axis.Z, true);
                ((BeltBlockEntity) level.getBlockEntity(new BlockPos(73, 4, 10))).setCasingType(BeltBlockEntity.CasingType.ANDESITE);
                ((BeltBlockEntity) level.getBlockEntity(new BlockPos(74, 4, 10))).setCasingType(BeltBlockEntity.CasingType.BRASS);
                ((BeltBlockEntity) level.getBlockEntity(new BlockPos(75, 4, 10))).setCasingType(BeltBlockEntity.CasingType.BRASS);
                level.setBlockAndUpdate(new BlockPos(75, 5, 10), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(74, 6, 10), AllBlocks.MECHANICAL_PRESS.defaultBlockState()
                        .setValue(com.simibubi.create.content.kinetics.press.MechanicalPressBlock.FACING, net.minecraft.core.Direction.EAST));
                level.setBlockAndUpdate(new BlockPos(73, 6, 10), AllBlocks.CREATIVE_MOTOR.defaultBlockState()
                        .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, net.minecraft.core.Direction.EAST));
                level.setBlockAndUpdate(new BlockPos(78, 4, 10), AllBlocks.DEPOT.defaultBlockState());
                var belt = (BeltBlockEntity) level.getBlockEntity(new BlockPos(71, 4, 10));
                try (Transaction tx = Transaction.openOuter()) {
                    belt.getInventory().segment(2, net.minecraft.core.Direction.UP).insert(ItemVariant.of(net.minecraft.world.item.Items.IRON_INGOT), 2, tx); tx.commit();
                }
                createBelt(level, new BlockPos(80, 4, 14), new BlockPos(84, 4, 14), net.minecraft.core.Direction.Axis.Z, false);
                level.setBlockAndUpdate(new BlockPos(82, 5, 16), AllBlocks.ENCASED_FAN.defaultBlockState().setValue(com.simibubi.create.content.kinetics.fan.EncasedFanBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(new BlockPos(82, 5, 17), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(new BlockPos(82, 4, 15), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(82, 5, 15), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
                var washed = (BeltBlockEntity) level.getBlockEntity(new BlockPos(80, 4, 14));
                try (Transaction tx = Transaction.openOuter()) {
                    washed.getInventory().segment(2, net.minecraft.core.Direction.UP).insert(ItemVariant.of(AllItems.WHEAT_FLOUR), 3, tx);
                    washed.getInventory().segment(0, net.minecraft.core.Direction.UP).insert(ItemVariant.of(net.minecraft.world.item.Items.HONEY_BOTTLE), 1, tx);
                    washed.getInventory().segment(4, net.minecraft.core.Direction.UP).insert(ItemVariant.of(net.minecraft.world.item.Items.IRON_BLOCK), 1, tx); tx.commit();
                }
            });
            server.runCommand("tp @p 81 12 20");
            context.waitFor(client -> client.player.getX() > 80 && client.player.getY() > 11);
            context.getInput().lookAt(new BlockPos(81, 6, 6));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(75, 8, 6)) instanceof BeltBlockEntity belt && belt.getSpeed() == -16);
            world.getConnection().waitForChunksRender(); context.waitTicks(10);
            context.takeScreenshot("create-vulkan-belt-slopes");
            server.runCommand("tp @p 75 9 15");
            context.waitFor(client -> client.player.getX() < 76);
            context.getInput().lookAt(new BlockPos(74, 4, 10));
            context.waitTicks(15);
            context.runOnClient(client -> {
                for (var position : java.util.List.of(new BlockPos(71, 4, 3), new BlockPos(73, 6, 6), new BlockPos(81, 6, 6), new BlockPos(86, 6, 6), new BlockPos(90, 4, 6), new BlockPos(75, 4, 10))) {
                    var belt = (BeltBlockEntity) client.level.getBlockEntity(position);
                    if (belt == null) throw new AssertionError("Missing client test belt " + position);
                    var extracted = client.getBlockEntityRenderDispatcher().tryExtractRenderState(belt, 0.25f, null, false);
                    if (!(extracted instanceof BeltRenderer.State state) || state.top.mesh == null || state.top.mesh.size() == 0) throw new AssertionError("Missing belt surface: " + position);
                    state.top.mesh.forEach(quad -> {
                        for (int vertex = 0; vertex < 4; vertex++) if (!Float.isFinite(quad.u(vertex)) || !Float.isFinite(quad.v(vertex))) throw new AssertionError("Non-finite belt UV");
                    });
                    if (position.equals(new BlockPos(75, 4, 10)) && (state.casing.mesh == null || state.casing.mesh.size() == 0 || state.cover.mesh == null || state.cover.mesh.size() == 0)) throw new AssertionError("Casing or cover mesh missing");
                }
                var atlas = client.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
                for (var color : net.minecraft.world.item.DyeColor.values()) for (var suffix : new String[]{"_scroll", "_diagonal_scroll"}) {
                    var id = Create.asResource("block/belt/" + color.getSerializedName() + suffix);
                    if (atlas.getSprite(id) == atlas.missingSprite()) throw new AssertionError("Unstitched dyed belt sprite " + id);
                }
            });
            context.takeScreenshot("create-vulkan-belt-press");
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(78, 4, 10)) instanceof DepotBlockEntity depot && depot.getHeldItem().is(AllItems.IRON_SHEET), 500);
            context.takeScreenshot("create-vulkan-belt-press-result");
            server.runCommand("tp @p 87 9 20");
            context.waitFor(client -> client.player.getX() > 86);
            context.getInput().lookAt(new BlockPos(82, 4, 14));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(80, 4, 14)) instanceof BeltBlockEntity belt
                    && belt.getInventory().getTransportedItems().stream().anyMatch(s -> s.stack.is(AllItems.DOUGH) && s.stack.getCount() == 3), 300);
            world.getConnection().waitForChunksRender(); context.waitTicks(5);
            context.takeScreenshot("create-vulkan-belt-fan-result");
            server.runCommand("fill 96 3 0 116 3 16 minecraft:smooth_quartz");
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                createCrusher(level, new BlockPos(101, 6, 6), net.minecraft.core.Direction.Axis.Z);
                createCrusher(level, new BlockPos(110, 6, 6), net.minecraft.core.Direction.Axis.Y);
                createBelt(level, new BlockPos(99, 5, 6), new BlockPos(103, 5, 6), net.minecraft.core.Direction.Axis.Z, true);
                ((BeltBlockEntity) level.getBlockEntity(new BlockPos(101, 5, 6))).setCasingType(BeltBlockEntity.CasingType.ANDESITE);
                level.setBlockAndUpdate(new BlockPos(104, 5, 6), AllBlocks.DEPOT.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(110, 5, 5), AllBlocks.DEPOT.defaultBlockState());
                var first = (com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity) level.getBlockEntity(new BlockPos(101, 6, 6));
                var second = (com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity) level.getBlockEntity(new BlockPos(110, 6, 6));
                try (Transaction tx = Transaction.openOuter()) {
                    first.inventory.insert(ItemVariant.of(AllItems.RAW_ZINC), 32, tx);
                    second.inventory.insert(ItemVariant.of(net.minecraft.world.item.Items.RAW_IRON), 16, tx); tx.commit();
                }
            });
            server.runCommand("tp @p 106 10 16");
            context.waitFor(client -> client.player.getX() > 105);
            context.getInput().lookAt(new BlockPos(106, 6, 6));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(101, 6, 6)) instanceof com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity crusher
                    && crusher.getCrushingSpeed() > 0);
            world.getConnection().waitForChunksRender(); context.waitTicks(10);
            context.runOnClient(client -> {
                var state = new PartialModelRenderState(); AllPartialModels.extract(AllPartialModels.CRUSHING_WHEEL, state);
                if (state.mesh == null || state.mesh.size() != 268) throw new AssertionError("Crushing wheel OBJ face count changed: " + (state.mesh == null ? 0 : state.mesh.size()));
                var belt = (BeltBlockEntity) client.level.getBlockEntity(new BlockPos(101, 5, 6));
                var extracted = client.getBlockEntityRenderDispatcher().tryExtractRenderState(belt, 0, null, false);
                if (!(extracted instanceof BeltRenderer.State beltState) || !beltState.cover.isEmpty()) throw new AssertionError("Crushing controller incorrectly covers belt underneath");
            });
            context.takeScreenshot("create-vulkan-crushing-wheels");
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(104, 5, 6)) instanceof DepotBlockEntity depot
                    && depot.getHeldItem().is(AllItems.CRUSHED_ZINC) && depot.getHeldItem().getCount() == 32, 300);
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(110, 5, 5)) instanceof DepotBlockEntity depot
                    && depot.getHeldItem().is(AllItems.CRUSHED_IRON) && depot.getHeldItem().getCount() == 16, 300);
            context.takeScreenshot("create-vulkan-crushing-results");
            server.runCommand("fill 116 3 0 132 3 18 minecraft:smooth_quartz");
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                var origin = new BlockPos(120, 4, 7);
                String[] pattern = {" AAA ", "AAPAA", "APSPA", "AAPAA", " AAA "};
                for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
                    var pointing = x == 4 && y > 0 ? net.createmod.catnip.math.Pointing.DOWN : net.createmod.catnip.math.Pointing.RIGHT;
                    var pos = origin.offset(x, y, 0);
                    level.setBlockAndUpdate(pos, AllBlocks.MECHANICAL_CRAFTER.defaultBlockState()
                            .setValue(com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH)
                            .setValue(com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock.POINTING, pointing));
                    var crafter = (com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity) level.getBlockEntity(pos);
                    char symbol = pattern[4 - y].charAt(x);
                    if (symbol == ' ') { crafter.covered = true; crafter.contentsChanged(); }
                    else try (Transaction tx = Transaction.openOuter()) {
                        crafter.storage().insert(ItemVariant.of(symbol == 'A' ? AllItems.ANDESITE_ALLOY : symbol == 'P' ? net.minecraft.world.item.Items.OAK_PLANKS : net.minecraft.world.item.Items.STONE), 1, tx); tx.commit();
                    }
                }
                level.setBlockAndUpdate(origin.east(5), AllBlocks.DEPOT.defaultBlockState());
                level.setBlockAndUpdate(origin.west(), AllBlocks.COGWHEEL.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS, net.minecraft.core.Direction.Axis.Z));
                level.setBlockAndUpdate(origin.west().north(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, net.minecraft.core.Direction.SOUTH));
            });
            server.runCommand("tp @p 123 8 18");
            context.waitFor(client -> client.player.getX() > 122 && client.player.getZ() > 17);
            context.getInput().lookAt(new BlockPos(122, 6, 7));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(120, 4, 7)) instanceof com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity crafter && crafter.getSpeed() != 0);
            world.getConnection().waitForChunksRender(); context.waitTicks(15);
            context.takeScreenshot("create-vulkan-mechanical-crafters");
            context.runOnClient(client -> {
                var crafter = (com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity) client.level.getBlockEntity(new BlockPos(120, 4, 7));
                var extracted = client.getBlockEntityRenderDispatcher().tryExtractRenderState(crafter, 0.5f, null, false);
                if (!(extracted instanceof com.simibubi.create.content.kinetics.crafter.MechanicalCrafterRenderer.State state) || state.cog.mesh == null || state.cog.mesh.size() == 0 || state.lid.mesh == null) throw new AssertionError("Crafter render state incomplete");
            });
            server.runOnServer(ignored -> {
                var motor = (KineticBlockEntity) world.getConnection().getServerLevel().getBlockEntity(new BlockPos(119, 4, 6));
                for (int i = 0; i < 4; i++) motor.adjustGeneratedSpeed(false);
            });
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(124, 4, 7)) instanceof com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity crafter && crafter.phase == com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity.Phase.CRAFTING, 400);
            context.takeScreenshot("create-vulkan-crafter-assembly");
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(125, 4, 7)) instanceof DepotBlockEntity depot
                    && depot.getHeldItem().is(AllBlocks.CRUSHING_WHEEL.asItem()) && depot.getHeldItem().getCount() == 2, 300);
            context.takeScreenshot("create-vulkan-crafter-result");
            var connectionKey = context.computeOnClient(client -> {
                var pos = new BlockPos(120, 4, 7); var block = client.level.getBlockState(pos);
                return client.getModelManager().getBlockStateModelSet().get(block).createGeometryKey((net.minecraft.client.renderer.block.BlockAndTintGetter) client.level, pos, block, net.minecraft.util.RandomSource.create(42));
            });
            server.runOnServer(ignored -> com.simibubi.create.content.kinetics.crafter.ConnectedInputHandler.toggleConnection(world.getConnection().getServerLevel(), new BlockPos(120, 4, 7), new BlockPos(121, 4, 7)));
            context.waitFor(client -> client.level.getBlockEntity(new BlockPos(120, 4, 7)) instanceof com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity crafter
                    && ((com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity.InputRenderData) crafter.getRenderData()).members().size() == 2);
            context.runOnClient(client -> {
                var pos = new BlockPos(120, 4, 7); var block = client.level.getBlockState(pos);
                var model = client.getModelManager().getBlockStateModelSet().get(block);
                var key = model.createGeometryKey((net.minecraft.client.renderer.block.BlockAndTintGetter) client.level, pos, block, net.minecraft.util.RandomSource.create(42));
                if (key == null || key.equals(connectionKey)) throw new AssertionError("Connected crafter geometry key did not change");
                var mesh = net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();
                model.emitQuads(mesh.emitter(), (net.minecraft.client.renderer.block.BlockAndTintGetter) client.level, pos, block, net.minecraft.util.RandomSource.create(42), face -> false);
                if (mesh.immutableCopy().size() == 0) throw new AssertionError("Connected crafter mesh missing");
            });
            world.getConnection().waitForChunksRender(); context.waitTicks(5);
            context.takeScreenshot("create-vulkan-crafter-connected-inputs");
            runCuttingScene(context,world);
            }
            if (!Boolean.getBoolean("create.test.onlyPipes")) {
            server.runCommand("gamemode spectator @p");
            context.runOnClient(client -> { if (Boolean.getBoolean("create.test.onlyTanks")) client.gui.hud.toggle(); });
            server.runCommand("tp @p 162 7 14");
            context.waitFor(client->client.player.getX()>161&&client.level.isLoaded(new BlockPos(154,4,5))&&client.level.isLoaded(new BlockPos(175,4,5)),500);
            server.runCommand("fill 152 3 0 175 3 15 minecraft:smooth_quartz");
            server.runCommand("fill 154 4 5 155 7 6 create:fluid_tank");
            server.runCommand("fill 159 4 5 161 6 7 create:fluid_tank");
            server.runCommand("fill 166 4 5 166 6 5 create:creative_fluid_tank");
            server.runCommand("tp @p 162 7 14");
            context.waitFor(client->client.player.getX()>161&&client.level.getBlockEntity(new BlockPos(154,4,5)) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank&&tank.getWidth()==2&&tank.getHeight()==4);
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();
                var water=(com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(new BlockPos(154,4,5));
                var lava=(com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(new BlockPos(159,4,5));
                try(var tx=Transaction.openOuter()){
                    if(water.storage().insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER),64*81000L,tx)!=64*81000L)throw new AssertionError("Water tank rejected test fill");
                    lava.storage().insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.LAVA),108*81000L,tx);tx.commit();
                }
                var creative=(com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity)level.getBlockEntity(new BlockPos(166,4,5));
                ((com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank)creative.getTankInventory()).setContainedFluid(new com.simibubi.create.foundation.fluid.FluidStack(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER),81000));
            });
            context.waitFor(client->client.level.getBlockEntity(new BlockPos(159,4,5)) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank&&tank.getFillState()==0.5f);
            context.waitFor(client->client.level.getBlockEntity(new BlockPos(154,4,5)) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank&&tank.getFillState()==0.5f&&tank.getFluidLevel(1)>0.45f);
            context.getInput().lookAt(new BlockPos(160,6,6)); world.getConnection().waitForChunksRender();context.waitTicks(12);
            context.runOnClient(client->{
                for(var pos:java.util.List.of(new BlockPos(154,4,5),new BlockPos(159,4,5),new BlockPos(166,4,5))){
                    var be=client.level.getBlockEntity(pos);var extracted=client.getBlockEntityRenderDispatcher().tryExtractRenderState(be,0.5f,null,true);
                    if(!(extracted instanceof com.simibubi.create.content.fluids.tank.FluidTankRenderer.State state)||state.mesh==null||state.mesh.size()==0||state.fillHeight<=0)throw new AssertionError("Tank fluid render state missing: "+pos+", extracted="+extracted+", fluid="+((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)be).getTankInventory()+", windows="+((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)be).hasWindows()+", height="+((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)be).getFluidLevel(1));
                }
                var pos=new BlockPos(159,4,5);var state=client.level.getBlockState(pos);var model=client.getModelManager().getBlockStateModelSet().get(state);
                var view=(net.minecraft.client.renderer.block.BlockAndTintGetter)client.level;
                var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),view,pos,state,net.minecraft.util.RandomSource.create(42),face->false);
                if(mesh.immutableCopy().size()==0)throw new AssertionError("Tank connected model empty");
            });
            context.takeScreenshot("create-vulkan-fluid-tanks");
            server.runOnServer(ignored->((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)world.getConnection().getServerLevel().getBlockEntity(new BlockPos(155,6,6))).toggleWindows());
            context.waitFor(client->client.level.getBlockState(new BlockPos(154,4,5)).getValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE)==com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.PLAIN);
            context.waitTicks(5);context.takeScreenshot("create-vulkan-tank-windows-closed");
            server.runOnServer(ignored->((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)world.getConnection().getServerLevel().getBlockEntity(new BlockPos(154,4,5))).toggleWindows());
            // Real chunk unload: keep the western tank chunk loaded while the eastern half leaves memory.
            server.runOnServer(ignored->world.getConnection().getServerLevel().getChunkSource().addTicket(new net.minecraft.server.level.Ticket(net.minecraft.server.level.TicketType.FORCED,33),new net.minecraft.world.level.ChunkPos(9,0)));
            server.runCommand("tp @p 5000 10 5000");
            var easternUnloaded=new java.util.concurrent.atomic.AtomicBoolean();
            for(int attempt=0;attempt<120&&!easternUnloaded.get();attempt++){
                context.waitTicks(5);
                server.runOnServer(ignored->easternUnloaded.set(!world.getConnection().getServerLevel().isLoaded(new BlockPos(160,4,5))));
            }
            if(!easternUnloaded.get())throw new AssertionError("Eastern tank chunk did not unload");
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var tank=(com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(new BlockPos(159,4,5));
                try(var tx=Transaction.openOuter()){
                    if(tank.storage().extract(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.LAVA),81000,tx)!=0)throw new AssertionError("Partially unloaded tank exposed fluid");
                }
                level.setBlockAndUpdate(new BlockPos(159,4,5),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                if(!com.simibubi.create.content.fluids.tank.TankRecoveryData.get(level).contains(new BlockPos(159,5,5)))throw new AssertionError("Unloaded split did not preserve recovery data");
                if(level.isLoaded(new BlockPos(160,4,5)))throw new AssertionError("Recovery forced eastern chunk load");
            });
            server.runCommand("tp @p 162 7 14");
            context.waitFor(client->client.player.getX()<200&&client.level.getBlockEntity(new BlockPos(160,4,5)) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity,500);
            context.waitTicks(15);
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var masters=new java.util.HashSet<BlockPos>();long amount=0;
                for(int x=159;x<=161;x++)for(int y=4;y<=6;y++)for(int z=5;z<=7;z++)if(level.getBlockEntity(new BlockPos(x,y,z)) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity part){var master=part.getControllerBE();if(master!=null&&masters.add(master.getBlockPos()))amount+=master.getTankInventory().getFluidAmount();}
                if(amount!=108*81000L)throw new AssertionError("Unloaded split lost/duplicated fluid: "+amount);
                if(com.simibubi.create.content.fluids.tank.TankRecoveryData.get(level).contains(new BlockPos(159,5,5)))throw new AssertionError("Tank recovery did not finish after chunk load");
            });
            server.runOnServer(ignored->world.getConnection().getServerLevel().getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.FORCED,new net.minecraft.world.level.ChunkPos(9,0),0));
            context.getInput().lookAt(new BlockPos(160,6,6));world.getConnection().waitForChunksRender();context.waitTicks(10);context.takeScreenshot("create-vulkan-tank-recovered-after-unload");
            }
            if (!Boolean.getBoolean("create.test.onlyTanks")) PipeRenderingTest.run(context, world);
            var dataReload = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
            var recipeBeforeReload = new java.util.concurrent.atomic.AtomicReference<net.minecraft.world.item.crafting.Recipe<?>>();
            var runtimeKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Create.asResource("cutting/runtime_generated/compat/minecraft/stripped_oak_log_to_oak_planks"));
            server.runOnServer(ignored -> {
                var actualServer = world.getConnection().getServerLevel().getServer();
                recipeBeforeReload.set(actualServer.getRecipeManager().byKey(runtimeKey).orElseThrow().value());
                dataReload.set(actualServer.reloadResources(actualServer.getPackRepository().getSelectedIds()));
            });
            context.waitFor(client -> dataReload.get() != null && dataReload.get().isDone(), 500);
            server.runOnServer(ignored -> {
                dataReload.get().join();
                var actualServer = world.getConnection().getServerLevel().getServer();
                var after = actualServer.getRecipeManager().byKey(runtimeKey).orElseThrow().value();
                if (after == recipeBeforeReload.get() || !(after instanceof com.simibubi.create.content.kinetics.saw.CuttingRecipe cutting) || cutting.results().getFirst().count() != 6) throw new AssertionError("Runtime recipe did not reload correctly");
                if (!actualServer.getPackRepository().getSelectedIds().iterator().next().equals("create:runtime_generated")) throw new AssertionError("Runtime datapack lost its low priority after reload");
            });
            context.runOnClient(client -> client.gui.hud.toggle());
        }
    }

    /** Same actual cutting/drill/tree scene in focused iteration and the complete Vulkan run. */
    private static void runCuttingScene(ClientGameTestContext context,net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world){
        var server=world.getServer();
        server.runCommand("fill 130 3 0 149 3 12 minecraft:smooth_quartz");
        server.runCommand("setblock 132 4 5 create:creative_motor[facing=east]");
        server.runCommand("setblock 133 4 5 create:mechanical_drill[facing=east]");
        server.runCommand("setblock 134 4 5 minecraft:iron_block");
        server.runCommand("setblock 138 4 4 create:creative_motor[facing=south]");
        server.runCommand("setblock 138 4 5 create:mechanical_saw[facing=up,axis_along_first=false]");
        server.runCommand("setblock 139 4 5 create:depot");
        server.runCommand("setblock 142 4 4 create:creative_motor[facing=south]");
        server.runCommand("setblock 142 4 5 create:mechanical_saw[facing=up,axis_along_first=false]");
        server.runCommand("setblock 141 4 5 create:depot");
        server.runCommand("setblock 145 4 5 create:mechanical_saw[facing=east]");
        server.runCommand("setblock 146 4 5 minecraft:oak_log");
        server.runCommand("setblock 146 5 5 minecraft:oak_log");
        server.runCommand("setblock 146 6 5 minecraft:oak_log");
        server.runCommand("setblock 146 7 5 minecraft:oak_log");
        server.runCommand("fill 145 8 4 147 8 6 minecraft:oak_leaves[persistent=true]");
        server.runCommand("tp @p 139 5 11");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            var saw = (com.simibubi.create.content.kinetics.saw.SawBlockEntity) level.getBlockEntity(new BlockPos(138, 4, 5));
            var reverse = (com.simibubi.create.content.kinetics.saw.SawBlockEntity) level.getBlockEntity(new BlockPos(142, 4, 5));
            ((KineticBlockEntity) level.getBlockEntity(new BlockPos(142, 4, 4))).adjustGeneratedSpeed(true);
            saw.setFilter(new net.minecraft.world.item.ItemStack(AllBlocks.SHAFT));
            reverse.setFilter(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SLAB));
            try (var tx = Transaction.openOuter()) {
                long alloyPaid=saw.inventory.insert(ItemVariant.of(AllItems.ANDESITE_ALLOY), 1, tx);
                long stonePaid=reverse.inventory.insert(ItemVariant.of(net.minecraft.world.item.Items.STONE), 1, tx);
                if(alloyPaid!=1||stonePaid!=1)throw new AssertionError("Actual cutting scene must pay exactly one alloy and one stone through native transfer receipts");
                tx.commit();
            }
            if(!saw.getFilter().is(AllBlocks.SHAFT.asItem())||!Create.asResource("cutting/andesite_alloy").equals(saw.inventory.selectedRecipe())||saw.inventory.recipeDuration!=200f)throw new AssertionError("Actual shaft output filter must select the original six-shaft cutting recipe and two-hundred-tick source duration");
        });
        context.waitFor(client -> client.player.getX() > 138 && client.level.getBlockEntity(new BlockPos(138, 4, 5)) instanceof com.simibubi.create.content.kinetics.saw.SawBlockEntity saw && !saw.inventory.isEmpty()
                && client.level.getBlockEntity(new BlockPos(142, 4, 5)) instanceof KineticBlockEntity reverse && reverse.getSpeed() < 0);
        context.getInput().lookAt(new BlockPos(139, 4, 5));
        world.getConnection().waitForChunksRender(); context.waitTicks(5);
        context.runOnClient(client -> {
            for (var pos : java.util.List.of(new BlockPos(133, 4, 5), new BlockPos(138, 4, 5), new BlockPos(142, 4, 5), new BlockPos(145, 4, 5))) {
                var entity = client.level.getBlockEntity(pos);
                var extracted = client.getBlockEntityRenderDispatcher().tryExtractRenderState(entity, 0.5f, null, false);
                if (!(extracted instanceof com.simibubi.create.content.kinetics.base.CuttingToolRenderer.State state) || state.tool.mesh == null || state.tool.mesh.size() == 0) throw new AssertionError("Cutting tool mesh missing: " + pos);
                if (pos.getX() == 138 && state.items.isEmpty()) throw new AssertionError("Saw item render state missing");
                if (pos.getX() == 142 && state.filter.isEmpty()) throw new AssertionError("Saw filter render state missing");
            }
        });
        context.takeScreenshot("create-vulkan-drill-and-saws");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            for (var pos : java.util.List.of(new BlockPos(132, 4, 5), new BlockPos(138, 4, 4), new BlockPos(142, 4, 4))) {
                var motor = (KineticBlockEntity) level.getBlockEntity(pos); for (int i = 0; i < 4; i++) motor.adjustGeneratedSpeed(false);
            }
        });
        context.waitFor(client -> client.level.getBlockEntity(new BlockPos(139, 4, 5)) instanceof DepotBlockEntity depot && depot.getHeldItem().is(AllBlocks.SHAFT.asItem()) && depot.getHeldItem().getCount() == 6, 300);
        context.waitFor(client -> client.level.getBlockEntity(new BlockPos(141, 4, 5)) instanceof DepotBlockEntity depot && depot.getHeldItem().is(net.minecraft.world.item.Items.STONE_SLAB) && depot.getHeldItem().getCount() == 2, 300);
        context.waitFor(client -> client.level.getBlockState(new BlockPos(134, 4, 5)).isAir(), 300);
        context.waitTicks(5);
        context.takeScreenshot("create-vulkan-cutting-results");
        server.runCommand("tp @p 148 5 11");
        context.waitFor(client -> client.player.getX() > 147); context.getInput().lookAt(new BlockPos(146, 6, 5));
        context.takeScreenshot("create-vulkan-tree-before");
        server.runCommand("setblock 144 4 5 create:creative_motor[facing=east]");
        server.runOnServer(ignored -> { var motor = (KineticBlockEntity) world.getConnection().getServerLevel().getBlockEntity(new BlockPos(144, 4, 5)); for (int i = 0; i < 4; i++) motor.adjustGeneratedSpeed(false); });
        context.waitFor(client -> client.level.getBlockState(new BlockPos(146, 4, 5)).isAir() && client.level.getBlockState(new BlockPos(146, 7, 5)).isAir() && client.level.getBlockState(new BlockPos(146, 8, 5)).isAir(), 300);
        context.waitTicks(10); context.takeScreenshot("create-vulkan-tree-after");
    }

    private static void createCrusher(net.minecraft.server.level.ServerLevel level, BlockPos center, net.minecraft.core.Direction.Axis axis) {
        var state = AllBlocks.CRUSHING_WHEEL.defaultBlockState().setValue(com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock.AXIS, axis);
        level.setBlockAndUpdate(center.west(), state); level.setBlockAndUpdate(center.east(), state);
        com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock.updateControllers(level, center.west(), state);
        var facing = net.minecraft.core.Direction.get(net.minecraft.core.Direction.AxisDirection.POSITIVE, axis);
        for (var pos : java.util.List.of(center.west(), center.east())) {
            var motorPos = pos.relative(facing.getOpposite());
            level.setBlockAndUpdate(motorPos, AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, facing));
            var motor = (KineticBlockEntity) level.getBlockEntity(motorPos);
            for (int i = 0; i < 4; i++) motor.adjustGeneratedSpeed(false);
            if (axis == net.minecraft.core.Direction.Axis.Z && pos.equals(center.west()) || axis == net.minecraft.core.Direction.Axis.Y && pos.equals(center.east())) motor.adjustGeneratedSpeed(true);
        }
    }

    private static void createBelt(net.minecraft.server.level.ServerLevel level, BlockPos start, BlockPos end, net.minecraft.core.Direction.Axis axis, boolean motor) {
        var shaft = AllBlocks.SHAFT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS, axis);
        level.setBlockAndUpdate(start, shaft); level.setBlockAndUpdate(end, shaft);
        if (!BeltConnectorItem.createBelts(level, start, end)) throw new AssertionError("Cannot create client test belt " + start + " -> " + end);
        if (motor) {
            var facing = net.minecraft.core.Direction.get(net.minecraft.core.Direction.AxisDirection.POSITIVE, axis);
            level.setBlockAndUpdate(start.relative(facing.getOpposite()), AllBlocks.CREATIVE_MOTOR.defaultBlockState()
                    .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, facing));
            ((KineticBlockEntity) level.getBlockEntity(start.relative(facing.getOpposite()))).adjustGeneratedSpeed(true);
        }
    }

    private static void assertWheelMeshes() {
        var material = net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int variant = 0; variant < 3; variant++) {
            var key = AllPartialModels.waterWheel(material, variant > 0, variant == 2);
            var first = new PartialModelRenderState();
            var second = new PartialModelRenderState();
            AllPartialModels.extract(key, first);
            AllPartialModels.extract(key, second);
            if (first.mesh == null || first.mesh.size() < 1000) {
                throw new AssertionError("Water wheel mesh geometry was lost: " + key);
            }
            if (first.mesh != second.mesh) throw new AssertionError("Unchanged wheel mesh rebuilt every frame");
        }
    }
}
