package com.simibubi.create.foundation.mixin.client;

import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Genuine native selected-item name timer used by source track placement's extra-key hint. */
@Mixin(Hud.class)
public interface HudTrackPlacementAccessor {
    @Accessor("toolHighlightTimer")int create$trackToolHighlightTimer();
}
