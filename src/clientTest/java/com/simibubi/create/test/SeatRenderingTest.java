package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

final class SeatRenderingTest {
    private static final BlockPos PLAYER_SEAT = new BlockPos(2054, 4, 10);
    private static final BlockPos COW_SEAT = new BlockPos(2048, 4, 3);
    private static final AABB AREA = new AABB(2046, 3, 1, 2065, 9, 12);
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer(); var hidden = context.computeOnClient(client -> client.gui.hud.isHidden());
        server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 2055 8 14");
        context.waitFor(client -> Math.abs(client.player.getX() - 2055.5) < .1 && client.level.isLoaded(new BlockPos(2048, 3, 0)) && client.level.isLoaded(new BlockPos(2063, 3, 15)), 400);
        var ready = new AtomicBoolean();
        for (int i = 0; i < 300 && !ready.get(); i++) {
            server.runOnServer(ignored -> { var level = world.getConnection().getServerLevel(); ready.set(level.isLoaded(new BlockPos(2048, 3, 0)) && level.isLoaded(new BlockPos(2063, 3, 15))); });
            if (!ready.get()) context.waitTicks(1);
        }
        if (!ready.get()) throw new AssertionError("Seat scene chunks unavailable");
        server.runCommand("fill 2048 3 0 2063 3 15 minecraft:smooth_quartz");
        NativeContraptionFixtures.awaitEntityReady(context,world,300,COW_SEAT,COW_SEAT.east(2),PLAYER_SEAT);
        var cowId = new AtomicReference<UUID>(); var vehicleId = new AtomicReference<UUID>(); var catId = new AtomicReference<UUID>();
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            for (var color : DyeColor.values()) {
                int index = color.ordinal(); var pos = new BlockPos(2048 + index % 8 * 2, 4, index < 8 ? 3 : 7);
                level.setBlockAndUpdate(pos, AllBlocks.SEATS.get(color).defaultBlockState());
            }
            level.setBlockAndUpdate(PLAYER_SEAT, AllBlocks.SEATS.get(DyeColor.RED).defaultBlockState());
            var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND); cow.setNoAi(true); cow.setPersistenceRequired(); cow.setPos(Vec3.atCenterOf(COW_SEAT)); level.addFreshEntity(cow);
            NativeContraptionFixtures.sitDown(level, COW_SEAT, cow); cowId.set(cow.getUUID()); vehicleId.set(cow.getVehicle().getUUID());
            var cat = EntityTypes.CAT.create(level, EntitySpawnReason.COMMAND); cat.setNoAi(true); cat.setTame(true, false); cat.setPersistenceRequired(); cat.setPos(Vec3.atCenterOf(COW_SEAT.east(2))); level.addFreshEntity(cat);
            NativeContraptionFixtures.sitDown(level, COW_SEAT.east(2), cat); catId.set(cat.getUUID());
        });
        context.waitFor(client -> client.level.getEntitiesOfClass(SeatEntity.class, AREA).size() == 2 && client.level.getEntitiesOfClass(TamableAnimal.class, AREA).stream().anyMatch(entity -> entity.getUUID().equals(catId.get()) && entity.isPassenger() && entity.isInSittingPose()), 300);
        context.getInput().lookAt(new BlockPos(2055, 4, 5)); world.getConnection().waitForChunksRender(); context.waitTicks(5);
        context.runOnClient(client -> { assertModels(client); assertRider(client, cowId.get(), vehicleId.get()); if (!client.gui.hud.isHidden()) client.gui.hud.toggle(); });
        context.takeScreenshot("create-vulkan-seats-and-passengers");

        server.runCommand("gamemode creative @p"); server.runCommand("tp @p 2054.5 4 12.5");
        context.waitFor(client -> Math.abs(client.player.getX() - 2054.5) < .1 && !client.player.isSpectator(), 200);
        server.runOnServer(ignored -> world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        context.waitFor(client -> client.player.getMainHandItem().isEmpty(), 200); lookAt(context, Vec3.atLowerCornerOf(PLAYER_SEAT).add(.5, .25, .5));
        context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(PLAYER_SEAT), 200);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(client -> client.player.getVehicle() instanceof SeatEntity, 200);
        context.waitTicks(5); context.takeScreenshot("create-vulkan-seated-player");
        context.getInput().holdKey(options -> options.keyShift);
        try { context.waitFor(client -> !client.player.isPassenger(), 200); }
        finally { context.getInput().releaseKey(options -> options.keyShift); }
        server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 2055 8 14"); context.waitFor(client -> client.player.isSpectator(), 200);
        var reload = context.computeOnClient(client -> client.reloadResourcePacks()); context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 600);
        context.runOnClient(client -> { assertModels(client); assertRider(client, cowId.get(), vehicleId.get()); });

        server.runCommand("tp @p 5000 10 5000"); var unloaded = new AtomicBoolean();
        for (int i = 0; i < 120 && !unloaded.get(); i++) {
            context.waitTicks(5); server.runOnServer(ignored -> unloaded.set(!world.getConnection().getServerLevel().isLoaded(COW_SEAT)));
        }
        if (!unloaded.get()) throw new AssertionError("Seated mob kept chunk loaded");
        server.runCommand("tp @p 2055 8 14"); context.waitFor(client -> Math.abs(client.player.getX() - 2055.5) < .1 && client.level.getEntitiesOfClass(SeatEntity.class, AREA).size() == 2, 400);
        context.waitTicks(5); context.runOnClient(client -> assertRider(client, cowId.get(), vehicleId.get()));
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var cat = (TamableAnimal)level.getEntity(catId.get()); var cow = level.getEntity(cowId.get());
            if (cow == null || !(cow.getVehicle() instanceof SeatEntity) || !cow.getVehicle().getUUID().equals(vehicleId.get()) || cat == null || !cat.isPassenger() || !cat.isInSittingPose()) throw new AssertionError("Native chunk save lost vehicle or seated pose");
            if (!(LoadedLevelAccess.blockState(level, COW_SEAT).getBlock() instanceof SeatBlock)) throw new AssertionError("Seat disappeared on chunk return");
        });
        context.getInput().lookAt(new BlockPos(2055, 4, 5)); world.getConnection().waitForChunksRender(); context.takeScreenshot("create-vulkan-seats-after-chunk-return");
        context.runOnClient(client -> { if (client.gui.hud.isHidden() != hidden) client.gui.hud.toggle(); });
    }
    private static void assertModels(Minecraft client) {
        for (var color : DyeColor.values()) {
            int index = color.ordinal(); var pos = new BlockPos(2048 + index % 8 * 2, 4, index < 8 ? 3 : 7); var state = client.level.getBlockState(pos);
            if (!state.is(AllBlocks.SEATS.get(color))) throw new AssertionError("Seat color not synchronized: " + color);
            var model = client.getModelManager().getBlockStateModelSet().get(state); var parts = new ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>(); model.collectParts(net.minecraft.util.RandomSource.create(42), parts);
            int quads = 0;
            for (var part : parts) {
                var faces = new ArrayList<>(part.getQuads(null)); for (var direction : Direction.values()) faces.addAll(part.getQuads(direction));
                for (var face : faces) {
                    var path = face.materialInfo().sprite().contents().name().getPath();
                    if (path.contains("missingno")) throw new AssertionError("Missing seat sprite for " + color);
                    if ((path.startsWith("block/seat/top_") || path.startsWith("block/seat/side_")) && !path.endsWith("_" + color.getSerializedName())) throw new AssertionError("Wrong color material: " + path);
                    quads++;
                }
            }
            if (quads == 0) throw new AssertionError("Seat model is empty: " + color);
        }
    }
    private static void assertRider(Minecraft client, UUID passengerId, UUID vehicleId) {
        var seat = client.level.getEntitiesOfClass(SeatEntity.class, AREA).stream().filter(entity -> entity.getUUID().equals(vehicleId)).findFirst().orElseThrow();
        if (seat.getFirstPassenger() == null || !seat.getFirstPassenger().getUUID().equals(passengerId)) throw new AssertionError("Seat passenger not synchronized");
        if (!(client.getEntityRenderDispatcher().getRenderer(seat) instanceof SeatRenderer)) throw new AssertionError("Native invisible vehicle renderer missing");
        var passenger = seat.getFirstPassenger(); var rendered = client.getEntityRenderDispatcher().extractEntity(passenger, .5f);
        if (rendered == null || Math.abs(passenger.getX() - seat.getX()) > .01 || passenger.getY() < seat.getY() || passenger.getY() > seat.getY() + .5) throw new AssertionError("Passenger rendering or attachment height is wrong");
    }
    private static void lookAt(ClientGameTestContext context, Vec3 point) {
        var rotation = context.computeOnClient(client -> { var delta = point.subtract(client.player.getEyePosition()); return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)), (float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)))); });
        context.getInput().lookAt(rotation.x, rotation.y);
    }
}
