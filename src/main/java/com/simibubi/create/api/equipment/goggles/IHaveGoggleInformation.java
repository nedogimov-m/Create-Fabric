package com.simibubi.create.api.equipment.goggles;

import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.network.chat.Component;

/** A block entity adds inspected information only when the player wears goggles. */
public non-sealed interface IHaveGoggleInformation extends IHaveCustomOverlayIcon {
    default boolean addToGoggleTooltip(List<Component> tooltip,boolean isPlayerSneaking){return false;}
    default boolean containedFluidTooltip(List<Component> tooltip,boolean isPlayerSneaking,Storage<FluidVariant> handler){return GoggleTooltip.containedFluid(tooltip,handler);}
}
