package com.simibubi.create.foundation.mixin.client;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Real native subtitle overlay for source continuously sounding train horns. */
@Mixin(Hud.class)
public interface CarriageHudSubtitleAccessor {
  @Accessor("subtitleOverlay")
  SubtitleOverlay create$getSubtitleOverlay();
}
