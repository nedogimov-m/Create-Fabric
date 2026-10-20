package com.simibubi.create.foundation.advancement;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

/** Actual device events use the original native criteria registered with the departure ancestor closure. */
public final class NativeDeviceAdvancements {
    private NativeDeviceAdvancements() {}
    private static boolean completed(ServerPlayer player,String sourceId) {
        var holder=player.level().getServer().getAdvancements().get(Create.asResource(sourceId));
        // Original CreateAdvancement treats an advancement removed by datapack as already completed.
        return holder==null || player.getAdvancements().getOrStartProgress(holder).isDone();
    }
    private static void trigger(ServerPlayer player,String sourceId) {
        var actual=BuiltInRegistries.TRIGGER_TYPES.getValue(Create.asResource(sourceId+"_builtin"));
        if(!(actual instanceof SimpleCreateTrigger trigger))throw new IllegalStateException("Actual native device criterion is not registered: "+sourceId);
        trigger.trigger(player);
    }
    public static void removeCompletedClockworkOwner(ClockworkBearingBlockEntity bearing) {
        var owner=bearing.advancementOwner();
        if(owner==null || bearing.getLevel()==null || !(bearing.getLevel().getPlayerByUUID(owner) instanceof ServerPlayer player))return;
        if(completed(player,"clockwork_bearing"))bearing.clearAdvancementOwner(owner);
    }
    public static void clockworkCompleted(ClockworkBearingBlockEntity bearing) {
        var owner=bearing.advancementOwner();
        if(owner==null || bearing.getLevel()==null || !(bearing.getLevel().getPlayerByUUID(owner) instanceof ServerPlayer player))return;
        if(!completed(player,"clockwork_bearing"))trigger(player,"clockwork_bearing");
        removeCompletedClockworkOwner(bearing);
    }
    public static void hauntedBellRung(ServerPlayer player) { trigger(player,"haunted_bell"); }
}
