package com.simibubi.create.api.equipment.goggles;

import com.simibubi.create.AllItems;
import net.minecraft.world.item.ItemStack;

public sealed interface IHaveCustomOverlayIcon permits IHaveGoggleInformation,IHaveHoveringInformation {
    default ItemStack getIcon(boolean isPlayerSneaking){return new ItemStack(AllItems.GOGGLES);}
}
