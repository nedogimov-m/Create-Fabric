package com.simibubi.create.content.trains;

import com.simibubi.create.Create;
import java.util.*;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Real native source builtin triggers and the complete parent advancement resource closure. */
public final class TrainAdvancements {
  public record Award(String sourceId) {
    public void awardTo(Player player) {
      if (!(player instanceof ServerPlayer server)) return;
      var trigger = triggers.get(sourceId + "_builtin");
      if (trigger == null)
        throw new IllegalStateException(
            "Actual source advancement trigger was not registered: " + sourceId);
      trigger.trigger(server);
    }
  }

  private static final Map<String, SimpleCreateTrigger> triggers = new LinkedHashMap<>();
  private static boolean registered;
  public static final Award TRAIN_PORTAL = new Award("train_portal");
  public static final Award TRAIN_CRASH = new Award("train_crash");
  public static final Award TRAIN_CRASH_BACKWARDS = new Award("train_crash_backwards");
  public static final Award RED_SIGNAL = new Award("red_signal");
  public static final Award CONDUCTOR = new Award("conductor");
  public static final Award TRAIN_WHISTLE = new Award("train_whistle");
  public static final Award LONG_TRAIN = new Award("long_train");
  public static final Award TRAIN = new Award("train");
  public static final Award SIGNAL = new Award("track_signal");
  public static final Award CONTRAPTION_ACTORS = new Award("contraption_actors");

  public static void register() {
    if (registered) return;
    // The same registered source trigger is used by native device hooks.
    for (var id :
        List.of(
            "andesite_casing_builtin",
            "backtank_builtin",
            "belt_builtin",
            "brass_casing_builtin",
            "chute_builtin",
            "compacting_builtin",
            "conductor_builtin",
            "contraption_actors_builtin",
            "copper_casing_builtin",
            "crushing_wheel_builtin",
            "deployer_builtin",
            "diving_suit_builtin",
            "drain_builtin",
            "encased_fan_builtin",
            "fan_processing_builtin",
            "funnel_builtin",
            "long_train_builtin",
            "mechanical_arm_builtin",
            "mechanical_crafter_builtin",
            "mechanical_mixer_builtin",
            "mechanical_press_builtin",
            "millstone_builtin",
            "red_signal_builtin",
            "saw_processing_builtin",
            "shifting_gears_builtin",
            "speed_controller_builtin",
            "spout_builtin",
            "steam_engine_builtin",
            "steam_whistle_builtin",
            "super_glue_builtin",
            "track_crafting_factory_builtin",
            "track_signal_builtin",
            "train_builtin",
            "train_casing_00_builtin",
            "train_crash_backwards_builtin",
            "train_crash_builtin",
            "train_portal_builtin",
            "train_whistle_builtin",
            "water_wheel_builtin",
            "windmill_builtin")) {
      var trigger =
          com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(Create.asResource(id));
      triggers.put(id, trigger);
    }
    registered = true;
  }

  private TrainAdvancements() {}
}
