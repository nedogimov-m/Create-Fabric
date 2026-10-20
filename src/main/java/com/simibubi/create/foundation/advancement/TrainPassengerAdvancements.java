package com.simibubi.create.foundation.advancement;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Only the source's real native kill/dismount boundaries emit these criteria. */
public final class TrainPassengerAdvancements {
  public static void register() {
    for (var id : new String[] {"train_roadkill", "long_travel"}) {
      var key = Create.asResource(id + "_builtin");
      if (!BuiltInRegistries.TRIGGER_TYPES.containsKey(key))
        com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(key);
    }
  }
  public static void dismounted(ServerPlayer player, Vec3 mount, Vec3 dismount) {
    if (mount != null && !mount.closerThan(dismount, 5000))
      PrerequisiteAdvancements.award(player, "long_travel");
  }
  private TrainPassengerAdvancements() {}
}
