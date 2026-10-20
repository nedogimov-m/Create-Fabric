package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

/** Actual source placement-owner advancement behavior, using native registered criteria and progress. */
public final class DisplayLinkAdvancements {
    public enum Award {
        DISPLAY_LINK("display_link"), DISPLAY_BOARD("display_board_0");
        private final String sourceId;
        private SimpleCreateTrigger trigger;
        Award(String sourceId) { this.sourceId = sourceId; }
    }
    private static boolean registered;
    private DisplayLinkAdvancements() {}

    public static void register() {
        if (registered) return;
        for (var award : Award.values()) award.trigger = com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(Create.asResource(award.sourceId + "_builtin"));
        // Exact original parent resources also require their genuine native criteria.
        // Their device callback parity is separate from this actual Display Link owner.
        for (String sourceId : java.util.List.of("clockwork_bearing", "haunted_bell"))
            com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(Create.asResource(sourceId + "_builtin"));
        registered = true;
    }
    private static boolean completed(ServerPlayer player, Award award) {
        var holder = player.level().getServer().getAdvancements().get(Create.asResource(award.sourceId));
        // Original CreateAdvancement treats a datapack-removed advancement as already completed.
        return holder == null || player.getAdvancements().getOrStartProgress(holder).isDone();
    }
    public static void removeCompletedOwner(DisplayLinkBlockEntity link) {
        var owner = link.advancementOwner();
        if (owner == null || link.getLevel() == null
                || !(link.getLevel().getPlayerByUUID(owner) instanceof ServerPlayer player)) return;
        if (completed(player, Award.DISPLAY_LINK) && completed(player, Award.DISPLAY_BOARD))
            link.clearAdvancementOwner(owner);
    }
    public static void award(DisplayLinkBlockEntity link, Award award) {
        var owner = link.advancementOwner();
        if (owner == null || link.getLevel() == null
                || !(link.getLevel().getPlayerByUUID(owner) instanceof ServerPlayer player)) return;
        if (!completed(player, award)) {
            if (award.trigger == null) throw new IllegalStateException("Actual native display advancement trigger is unregistered");
            award.trigger.trigger(player);
        }
        removeCompletedOwner(link);
    }
}
