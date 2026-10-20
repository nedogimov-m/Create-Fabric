package com.simibubi.create.content.kinetics.clock;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
/** Source bedtime announcement awards the actual placement owner within32blocks through the native criterion. */
public final class CuckooClockAdvancements {
 private static SimpleCreateTrigger trigger;
 public static void register(){if(trigger==null)trigger=com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(Create.asResource("cuckoo_clock_builtin"));}
 public static void awardNear(CuckooClockBlockEntity clock){if(!(clock.getLevel() instanceof ServerLevel level))return;if(trigger==null)throw new IllegalStateException("Real clock criterion not registered");if(!((clock.advancementOwner()==null?null:level.getPlayerByUUID(clock.advancementOwner())) instanceof net.minecraft.server.level.ServerPlayer player))return;if(player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(clock.getBlockPos()))>32*32)return;trigger.trigger(player);var advancement=level.getServer().getAdvancements().get(Create.asResource("cuckoo_clock"));if(advancement==null||player.getAdvancements().getOrStartProgress(advancement).isDone())clock.clearAdvancementOwner();}
 private CuckooClockAdvancements(){}
}
