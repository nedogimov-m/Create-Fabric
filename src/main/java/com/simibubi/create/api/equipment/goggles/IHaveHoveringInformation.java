package com.simibubi.create.api.equipment.goggles;

import java.util.List;
import net.minecraft.network.chat.Component;

/** Hover information remains visible without goggles, for example overstress warnings. */
public non-sealed interface IHaveHoveringInformation extends IHaveCustomOverlayIcon {
    default boolean addToTooltip(List<Component> tooltip,boolean isPlayerSneaking){return false;}
    default boolean addToTooltip(List<Component> tooltip,boolean isPlayerSneaking,net.minecraft.world.entity.player.Player viewer){return addToTooltip(tooltip,isPlayerSneaking);}
}
