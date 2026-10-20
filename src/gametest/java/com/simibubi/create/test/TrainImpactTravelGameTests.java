package com.simibubi.create.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.trains.*;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Proposed native station/body/OBB/hurt/seat callbacks. Compiled only, runtime UNRUN. */
public final class TrainImpactTravelGameTests {
  private static final String STRUCTURE = "create_test:reservoir";
  private static final BlockPos R = new BlockPos(3, 6, 4), T = R.east(), S = T.south(2),
      B = T.east().above(), C = B.east(), WALL = C.east(), SEAT = WALL.above();
  private record Fixture(StationBlockEntity station, Player authorizer, TrainManager manager) {}
  private static NativeSurvivalTestPlayer join(GameTestHelper h) {
    return NativeSurvivalTestPlayer.join(h, Vec3.atCenterOf(h.absolutePos(SEAT)));
  }
  private static Fixture setup(GameTestHelper h) {
    var rail = AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, TrackShape.XO);
    for (int i = 0; i < 25; i++) {
      h.setBlock(R.east(i).below(), Blocks.STONE); h.setBlock(R.east(i), rail);
    }
    for (int i = 0; i < 25; i++) h.assertTrue(TrackPropagator.reconcile(h.getLevel(),
        h.absolutePos(R.east(i)), rail, false).complete(), "Real physical rail discovery completes");
    h.setBlock(S, AllBlocks.TRACK_STATION);
    var station = h.getBlockEntity(S, StationBlockEntity.class);
    h.assertTrue(station.setTarget(new TrackTargetingSelection(
        GlobalPos.of(h.getLevel().dimension(), h.absolutePos(T)), true, new Vec3(1, 0, 0), Optional.empty())),
        "Actual source station binds its genuine rail graph point");
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setPos(Vec3.atCenterOf(station.getBlockPos()).add(0, 0, 1));
    h.assertTrue(station.configure(player, "Actual impact train", true, DoorControl.ALL),
        "Real native station enters assembly mode");
    h.setBlock(B, AllBlocks.SMALL_BOGEY.defaultBlockState().setValue(AbstractBogeyBlock.AXIS, Direction.Axis.X));
    h.setBlock(C, AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING, Direction.EAST));
    h.setBlock(WALL, Blocks.STONE); h.setBlock(SEAT, AllBlocks.SEATS.get(DyeColor.GREEN));
    h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),
        SuperGlueEntity.span(h.absolutePos(B), h.absolutePos(SEAT)))), "Native glue owns the real touching body");
    return new Fixture(station, player, TrainManager.get(h.getLevel()));
  }
  private static void train(GameTestHelper h, int delay, Consumer<CarriageContraptionEntity> action) {
    var fixture = setup(h); var created = new AtomicReference<Train>();
    var started = new AtomicLong(); var checked = new AtomicBoolean();
    h.succeedWhen(() -> {
      var train = created.get();
      if (train == null) {
        h.assertTrue(StationAssemblyPreflight.check(fixture.station, fixture.authorizer).readyForCapture(),
            "Genuine source wheel and native entity readiness");
        h.assertTrue(fixture.station.tryAssemble(fixture.authorizer), "Real station capture completes");
        train = fixture.station.getStation().getPresentTrain();
        h.assertTrue(train != null && fixture.manager.get(train.id) == train, "One actual Train owns the body");
        created.set(train); started.set(h.getLevel().getGameTime());
      }
      if (!checked.get()) {
        h.assertTrue(h.getLevel().getGameTime() - started.get() >= delay, "Actual native ticks pass before collision");
        var front = train.carriages.getFirst().anyAvailableEntity();
        h.assertTrue(front != null && front.hasOwnedStructure() && h.getLevel().getEntity(front.getUUID()) == front,
            "Accepted original native carriage owns its genuine removed blocks");
        var before = front.position();
        try { action.accept(front); } finally { front.setPos(before); }
        checked.set(true);
      }
      if (fixture.manager.get(train.id) == null) return;
      h.assertTrue(train.canDisassemble(), "Original native body can restore after the actual assertion");
      train.disassemble(Direction.EAST, h.absolutePos(T.above()));
      h.assertTrue(fixture.manager.get(train.id) == null, "Actual original Train restores before fixture exit");
    });
  }
  private static void configuredDamage(boolean enabled, Runnable action) {
    try {
      var before = TrainConfig.Values.CODEC.encodeStart(JsonOps.INSTANCE, TrainConfig.server()).getOrThrow();
      var path = Files.createTempFile("create-native-impact-config-", ".json");
      try {
        var changed = before.deepCopy().getAsJsonObject(); changed.addProperty("trainsCauseDamage", enabled);
        Files.writeString(path, changed.toString()); TrainConfig.reload(path); action.run();
      } finally { Files.writeString(path, before.toString()); TrainConfig.reload(path); Files.deleteIfExists(path); }
    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
  }
  private static Vec3 wall(CarriageContraptionEntity owner) {
    return owner.toGlobalVector(Vec3.atLowerCornerOf(WALL.subtract(B)).add(.5, 0, .5), 1);
  }
  private static <E extends Mob> E victim(GameTestHelper h, CarriageContraptionEntity owner, EntityType<E> type) {
    var entity = h.spawnWithNoFreeWill(type, WALL);
    entity.setPos(wall(owner)); entity.setOnGround(true); entity.setDeltaMovement(Vec3.ZERO);
    return entity;
  }
  private static void advance(CarriageContraptionEntity owner, double amount) {
    owner.finishCollisionTick(); owner.setPos(owner.position().add(amount, 0, 0));
  }
  @GameTest(structure = STRUCTURE, maxTicks = 200)
  public void actualNativeNeutralAndMonsterImpactsUseSourceDamageAndClampedImpulse(GameTestHelper h) {
    train(h, 11, owner -> configuredDamage(true, () -> {
      h.assertTrue(owner.nonDamageTicks == 0, "Real native carriage ticks exhaust initial immunity");
      advance(owner, .5); var cow = victim(h, owner, EntityTypes.COW); var monster = victim(h, owner, EntityTypes.CREEPER);
      h.assertTrue(monster.getType().getCategory() == MobCategory.MONSTER && monster.getArmorValue() == 0,
          "Actual native monster has zero natural armor before measuring exact source damage");
      float neutral = cow.getHealth(), hostile = monster.getHealth();
      ContraptionCollider.collideEntities(owner);
      h.assertTrue(neutral - cow.getHealth() == 8 && hostile - monster.getHealth() == 16,
          "Actual accepted OBB contact calls real native hurt with source neutral/monster damage");
      h.assertTrue(cow.getLastDamageSource().is(AllDamageTypes.RUN_OVER)
          && monster.getLastDamageSource().is(AllDamageTypes.RUN_OVER)
          && cow.getDeltaMovement().length() <= 3.000001 && monster.getDeltaMovement().length() <= 3.000001,
          "Genuine registered run-over source and source impulse length cap");
      cow.discard(); monster.discard();
    }));
  }
  @GameTest(structure = STRUCTURE, maxTicks = 200)
  public void actualNewCarriageRetainsItsSourceSpawnDamageGrace(GameTestHelper h) {
    train(h, 0, owner -> configuredDamage(true, () -> {
      h.assertTrue(owner.nonDamageTicks > 0, "Actual newly assembled native carriage starts protected");
      advance(owner, 1); var cow = victim(h, owner, EntityTypes.COW); float health = cow.getHealth();
      ContraptionCollider.collideEntities(owner); h.assertTrue(cow.getHealth() == health, "Real OBB contact cannot bypass source spawn grace"); cow.discard();
    }));
  }
  @GameTest(structure = STRUCTURE, maxTicks = 200)
  public void actualNativeCollisionHonoursServerDamageConfiguration(GameTestHelper h) {
    train(h, 11, owner -> configuredDamage(false, () -> {
      advance(owner, 1); var cow = victim(h, owner, EntityTypes.COW); float health = cow.getHealth();
      ContraptionCollider.collideEntities(owner); h.assertTrue(cow.getHealth() == health, "Actual persisted server setting disables real native impact damage"); cow.discard();
    }));
  }
  @GameTest(structure = STRUCTURE, maxTicks = 200)
  public void actualNativeImpactAtSourceSpeedBoundaryDoesNotDamage(GameTestHelper h) {
    train(h, 11, owner -> configuredDamage(true, () -> {
      advance(owner, .35f); var cow = victim(h, owner, EntityTypes.COW); float health = cow.getHealth();
      ContraptionCollider.collideEntities(owner); h.assertTrue(cow.getHealth() == health, "Source train speed must be strictly greater than .35f"); cow.discard();
    }));
  }
  @GameTest(structure = STRUCTURE, maxTicks = 200)
  public void actualNativeDriverReceivesRoadkillOnlyAfterRealImpactDeath(GameTestHelper h) {
    train(h, 11, owner -> configuredDamage(true, () -> {
      try (var joined = join(h)) {
        var player = joined.player(); var local = C.subtract(B);
        player.setPos(owner.toGlobalVector(Vec3.atCenterOf(local), 1).add(0, 0, 1));
        var key = com.simibubi.create.Create.asResource("train_roadkill");
        var advancement = h.getLevel().getServer().getAdvancements().get(key);
        h.assertTrue(advancement != null && owner.startControlling(local, player), "Actual literal player controls the genuine registered native Train");
        h.assertFalse(player.getAdvancements().getOrStartProgress(advancement).isDone(), "No advance before real harm");
        advance(owner, 2); var cow = victim(h, owner, EntityTypes.COW);
        ContraptionCollider.collideEntities(owner);
        h.assertTrue(!cow.isAlive() && player.getAdvancements().getOrStartProgress(advancement).isDone(),
            "Only the actual native lethal impact awards its actual controlling player");
        owner.stopControlling(local, player); cow.discard();
      }
    }));
  }
  private static void trip(GameTestHelper h, double distance, boolean awarded) {
    train(h, 11, owner -> {
      try (var joined = join(h)) {
        var player = joined.player(); int seat = owner.getContraption().getSeats().indexOf(SEAT.subtract(B));
        h.assertTrue(seat >= 0 && owner.addSittingPassenger(player, seat), "Actual native green seat accepts the literal player");
        var exactDismountOrigin = owner.getPassengerPosition(player, 1);
        player.stopRiding(); player.setPos(exactDismountOrigin);
        h.assertTrue(owner.addSittingPassenger(player, seat), "Real remount captures precisely the native dismount origin");
        owner.setPos(owner.position().add(0, distance, 0));
        var endpoint = owner.getPassengerPosition(player, 1);
        h.assertTrue(Math.abs(endpoint.distanceTo(exactDismountOrigin) - distance) < 1e-6,
            "Real original seat geometry travels the exact source Euclidean distance in the same loaded chunk");
        player.stopRiding();
        var advancement = h.getLevel().getServer().getAdvancements().get(Create.asResource("long_travel"));
        h.assertTrue(advancement != null && !player.isPassenger()
            && player.getAdvancements().getOrStartProgress(advancement).isDone() == awarded,
            "Genuine native dismount consumes real passenger history at the source 5000-block boundary");
        h.assertTrue(ContraptionPassengerData.takeMountLocation(player) == null, "Source mount history is retired once after actual native dismount");
      }
    });
  }
  @GameTest(structure = STRUCTURE, maxTicks = 200)
  public void actualNativeDismountBelowFiveThousandDoesNotAward(GameTestHelper h) { trip(h, 4999, false); }
  @GameTest(structure = STRUCTURE, maxTicks = 200)
  public void actualNativeDismountAtExactlyFiveThousandAwards(GameTestHelper h) { trip(h, 5000, true); }
}
