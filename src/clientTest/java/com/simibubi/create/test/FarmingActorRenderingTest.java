package com.simibubi.create.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.harvester.*;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.UUID;
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

/** Real harvesting, ploughing, native blade extraction, menus and two-chunk save/unload. */
final class FarmingActorRenderingTest {
    private static final BlockPos P = new BlockPos(4111, 5, 5), Q = P.south(8);
    private static final BlockPos LOCAL_TOOL = new BlockPos(0, 0, -1), LOCAL_BARREL = new BlockPos(1, 0, 0);

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        verifyBladeTransforms(context);
        var server = world.getServer();
        server.runCommand("gamemode spectator @p");
        server.runCommand("tp @p 4111.5 9 11.5");
        context.waitFor(client -> Math.abs(client.player.getX() - 4111.5) < .1
            && client.level.isLoaded(P) && client.level.isLoaded(P.east()), 400);
        server.runCommand("fill 4105 3 -1 4120 3 21 minecraft:smooth_quartz");
        server.runCommand("setblock " + xyz(P.west().below()) + " minecraft:farmland[moisture=7]");
        server.runCommand("setblock " + xyz(P.west(2).below()) + " minecraft:water");
        server.runCommand("setblock " + xyz(P.west()) + " minecraft:wheat[age=7]");
        setup(world, P, "mechanical_harvester");
        var harvester = new AtomicReference<UUID>();
        waitServer(context, world, level -> {
            var owner = ((MechanicalBearingBlockEntity) level.getBlockEntity(P.below())).getMovedContraption();
            if (owner == null) return false;
            harvester.set(owner.getUUID());
            return count(owner, Items.WHEAT) == 1 && level.getBlockState(P.west()).is(Blocks.WHEAT)
                && level.getBlockState(P.west()).getValue(net.minecraft.world.level.block.CropBlock.AGE) < 7;
        }, 300, "Actual moving harvester did not collect/replant wheat");
        context.waitFor(client -> find(client.level, harvester.get()) instanceof ControlledContraptionEntity owner
            && owner.getContraption() != null, 300);
        look(context, Vec3.atCenterOf(P));
        var angle = context.computeOnClient(client -> bladeState(client, harvester.get()).angle);
        context.waitTicks(2);
        context.runOnClient(client -> {
            var state = bladeState(client, harvester.get());
            if (state.blade.mesh == null || state.blade.mesh.size() == 0)
                throw new AssertionError("Native moving harvester blade geometry is missing");
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(state.angle - angle)) < 1)
                throw new AssertionError("Moving harvester blade does not animate");
        });
        screenshot(context, "create-vulkan-harvester-native-blade-and-replanted-crop");
        pause(world, P);
        context.waitTicks(8);
        openBarrel(context, world, harvester.get());
        context.waitFor(client -> client.player.containerMenu instanceof ChestMenu menu
            && menu.getSlot(0).getItem().is(Items.WHEAT) && menu.getSlot(0).getItem().getCount() == 1
            && menu.getSlot(4).getItem().is(Items.DIAMOND) && menu.getSlot(4).getItem().getCount() == 7, 200);
        screenshot(context, "create-vulkan-harvester-collected-native-barrel-menu");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null, 100);
        var mesh = context.computeOnClient(client -> bladeState(client, harvester.get()).blade.mesh);
        var reload = context.computeOnClient(client -> client.reloadResourcePacks());
        context.waitFor(client -> reload.isDone(), 500);
        reload.join();
        context.waitTicks(3);
        context.runOnClient(client -> {
            if (bladeState(client, harvester.get()).blade.mesh == mesh)
                throw new AssertionError("Native blade retained stale mesh after resource reload");
        });

        server.runCommand("gamemode spectator @p");
        server.runCommand("tp @p 4111.5 9 18.5");
        context.waitFor(client -> client.player.getZ() > 18 && client.level.isLoaded(Q), 200);
        server.runCommand("setblock " + xyz(Q.west().below()) + " minecraft:grass_block");
        server.runCommand("setblock " + xyz(Q.west(2).below()) + " minecraft:water");
        server.runCommand("setblock " + xyz(Q.west().north().below()) + " minecraft:stone");
        server.runCommand("setblock " + xyz(Q.west().north()) + " minecraft:snow[layers=3]");
        server.runCommand("setblock " + xyz(Q.west().south().below()) + " minecraft:stone");
        server.runCommand("setblock " + xyz(Q.west().south()) + " minecraft:dandelion");
        setup(world, Q, "mechanical_plough");
        var plough = new AtomicReference<UUID>();
        waitServer(context, world, level -> {
            var owner = ((MechanicalBearingBlockEntity) level.getBlockEntity(Q.below())).getMovedContraption();
            if (owner == null) return false;
            plough.set(owner.getUUID());
            return level.getBlockState(Q.west().below()).is(Blocks.FARMLAND)
                && level.getBlockState(Q.west().north()).isAir()
                && level.getBlockState(Q.west().south()).isAir()
                && count(owner, Items.SNOWBALL) == 3 && count(owner, Items.DANDELION) == 1;
        }, 400, "Actual plough failed soil tilling or snow/flower collection");
        pause(world, Q);
        context.waitFor(client -> find(client.level, plough.get()) instanceof ControlledContraptionEntity owner
            && owner.getContraption() != null && client.level.getBlockState(Q.west().below()).is(Blocks.FARMLAND), 300);
        world.getConnection().waitForChunksRender();
        look(context, Vec3.atCenterOf(Q.west()));
        screenshot(context, "create-vulkan-plough-tilled-soil-and-cleared-snow-flowers");

        server.runCommand("tp @p 5000 10 5000");
        waitServer(context, world, level -> level.getEntity(harvester.get()) == null && level.getEntity(plough.get()) == null
            && level.getChunkSource().getChunkNow(P.getX() >> 4, 0) == null
            && level.getChunkSource().getChunkNow(P.east().getX() >> 4, 0) == null,
            800, "Farming actors or transient hoe player retained their source chunks");
        server.runCommand("tp @p 4111.5 9 11.5");
        context.waitFor(client -> find(client.level, harvester.get()) instanceof ControlledContraptionEntity a
            && a.getContraption() != null && find(client.level, plough.get()) instanceof ControlledContraptionEntity b
            && b.getContraption() != null, 500);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            var a = (ControlledContraptionEntity) level.getEntity(harvester.get());
            var b = (ControlledContraptionEntity) level.getEntity(plough.get());
            if (count(a, Items.WHEAT) != 1 || count(a, Items.DIAMOND) != 7
                || count(b, Items.SNOWBALL) != 3 || count(b, Items.DANDELION) != 1 || count(b, Items.DIAMOND) != 7)
                throw new AssertionError("Farming loot changed across real entity/chunk unload");
        });
        world.getConnection().waitForChunksRender();
        look(context, Vec3.atCenterOf(P));
        context.runOnClient(client -> {
            if (bladeState(client, harvester.get()).blade.mesh.size() == 0)
                throw new AssertionError("Blade mesh absent after actual unload and return");
        });
        screenshot(context, "create-vulkan-farming-actors-real-unload-return");
        disassemble(context, world, P, harvester.get(), Items.WHEAT, 1);
        disassemble(context, world, Q, plough.get(), Items.SNOWBALL, 3);
        server.runCommand("gamemode spectator @p");
    }

    private static void setup(TestSingleplayerContext world, BlockPos p, String actor) {
        var server = world.getServer();
        server.runCommand("setblock " + xyz(p) + " minecraft:stone");
        server.runCommand("setblock " + xyz(p.north()) + " create:" + actor + "[facing=north]");
        server.runCommand("setblock " + xyz(p.east()) + " minecraft:barrel[facing=north]");
        server.runCommand("setblock " + xyz(p.below()) + " create:mechanical_bearing[facing=up]");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            ((BarrelBlockEntity) level.getBlockEntity(p.east())).setItem(4, new ItemStack(Items.DIAMOND, 7));
            ((MechanicalBearingBlockEntity) level.getBlockEntity(p.below()))
                .setMovementMode(IControlContraption.RotationMode.ROTATE_NEVER_PLACE);
            level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(p.north(), p.east())));
        });
        server.runCommand("setblock " + xyz(p.below(2)) + " create:creative_motor[facing=up]");
    }

    private static void verifyBladeTransforms(ClientGameTestContext context) {
        context.runOnClient(client -> {
            for (var direction : Direction.Plane.HORIZONTAL) {
                var pose = new PoseStack();
                HarvesterRenderer.transformBlade(pose, direction, 90);
                var pivot = pose.last().pose().transformPosition(new Vector3f(.5f, 6 / 16f, 9 / 16f));
                var expected = new Vec3(.5, 6 / 16d, .5).add(direction.getUnitVec3().scale(1 / 16d));
                if (new Vec3(pivot.x, pivot.y, pivot.z).distanceTo(expected) > 1e-5)
                    throw new AssertionError("Native blade pivot moved: " + direction);
                var upper = pose.last().pose().transformPosition(new Vector3f(.5f, 10 / 16f, 9 / 16f));
                if (new Vec3(upper.x, upper.y, upper.z).distanceTo(expected.subtract(direction.getUnitVec3().scale(.25))) > 1e-5)
                    throw new AssertionError("Blade rotation or facing differs from source: " + direction);
            }
        });
    }

    private static HarvesterRenderer.State bladeState(net.minecraft.client.Minecraft client, UUID id) {
        var owner = (ControlledContraptionEntity) find(client.level, id);
        var renderer = (ContraptionEntityRenderer) client.getEntityRenderDispatcher().getRenderer(owner);
        var state = renderer.createRenderState(owner, .5f);
        if (state.actors.size() != 1 || state.blockEntities.stream().anyMatch(be -> be.pos().equals(LOCAL_TOOL)))
            throw new AssertionError("Missing or duplicate native harvester blade");
        return ((HarvesterActorRenderer.State) state.actors.getFirst().state()).state();
    }

    private static void pause(TestSingleplayerContext world, BlockPos p) {
        world.getServer().runOnServer(ignored -> ((KineticBlockEntity) world.getConnection().getServerLevel()
            .getBlockEntity(p.below(2))).setGeneratedSpeed(0));
    }

    private static void openBarrel(ClientGameTestContext context, TestSingleplayerContext world, UUID id) {
        var origin = context.computeOnClient(client -> ((ControlledContraptionEntity) find(client.level, id))
            .toGlobalVector(Vec3.atCenterOf(LOCAL_BARREL).add(0, 0, -3), 1));
        world.getServer().runCommand("tp @p " + origin.x + " 4 " + origin.z);
        world.getServer().runCommand("gamemode survival @p");
        context.waitFor(client -> !client.player.isSpectator() && Math.abs(client.player.getX() - origin.x) < .1
            && Math.abs(client.player.getZ() - origin.z) < .1, 200);
        look(context, context.computeOnClient(client -> ((ControlledContraptionEntity) find(client.level, id))
            .toGlobalVector(Vec3.atCenterOf(LOCAL_BARREL), 1)));
        context.waitTicks(2);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen, 200);
    }

    private static void disassemble(ClientGameTestContext context, TestSingleplayerContext world, BlockPos p,
                                    UUID id, Item item, int amount) {
        var barrelPos = new AtomicReference<BlockPos>();
        world.getServer().runOnServer(ignored -> {
            var owner = (ControlledContraptionEntity) world.getConnection().getServerLevel().getEntity(id);
            barrelPos.set(new StructureTransform(p, 0, owner.getAngle(1), 0).apply(LOCAL_BARREL));
        });
        world.getServer().runCommand("tp @p " + (p.getX() + .5) + " 4 " + (p.getZ() + 4.5));
        world.getServer().runCommand("gamemode survival @p");
        context.waitFor(client -> !client.player.isSpectator() && Math.abs(client.player.getZ() - p.getZ() - 4.5) < .1, 200);
        look(context, Vec3.atCenterOf(p.below()));
        context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(p.below()), 150);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(client -> find(client.level, id) == null, 200);
        world.getServer().runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            if (!(level.getBlockEntity(barrelPos.get()) instanceof BarrelBlockEntity barrel))
                throw new AssertionError("Farming world barrel was not restored");
            int loot = 0, diamonds = 0;
            for (int i = 0; i < barrel.getContainerSize(); i++) {
                if (barrel.getItem(i).is(item)) loot += barrel.getItem(i).getCount();
                if (barrel.getItem(i).is(Items.DIAMOND)) diamonds += barrel.getItem(i).getCount();
            }
            if (loot != amount || diamonds != 7)
                throw new AssertionError("Farming world restoration changed loot: " + loot + "/" + diamonds);
        });
        look(context, Vec3.atCenterOf(p));
        screenshot(context, "create-vulkan-" + (p.equals(P) ? "harvester" : "plough") + "-world-restoration");
    }

    private static void waitServer(ClientGameTestContext context, TestSingleplayerContext world,
                                   Predicate<ServerLevel> predicate, int ticks, String message) {
        var ready = new AtomicBoolean();
        for (int i = 0; i < ticks && !ready.get(); i++) {
            world.getServer().runOnServer(ignored -> ready.set(predicate.test(world.getConnection().getServerLevel())));
            if (!ready.get()) context.waitTicks(1);
        }
        if (!ready.get()) throw new AssertionError(message);
    }

    private static net.minecraft.world.entity.Entity find(net.minecraft.client.multiplayer.ClientLevel level, UUID id) {
        if (level != null) for (var entity : level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity;
        return null;
    }

    private static long count(ControlledContraptionEntity owner, Item item) {
        long total = 0;
        for (var view : owner.getContraption().getStorage().getAllItems())
            if (view.getResource().isOf(item)) total += view.getAmount();
        return total;
    }

    private static String xyz(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
        context.takeScreenshot(name);
    }

    private static void look(ClientGameTestContext context, Vec3 target) {
        var angle = context.computeOnClient(client -> {
            var d = target.subtract(client.player.getEyePosition());
            return new Vec2((float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
        });
        context.getInput().lookAt(angle.x, angle.y);
    }
}
