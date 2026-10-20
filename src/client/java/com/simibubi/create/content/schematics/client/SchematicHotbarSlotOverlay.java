package com.simibubi.create.content.schematics.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Original schematic slot atlas segment in the actual native HUD extraction. */
public final class SchematicHotbarSlotOverlay {
    public void renderOn(GuiGraphicsExtractor gui,int slot){if(slot>=0&&slot<=8)AllGuiTextures.SCHEMATIC_SLOT.render(gui,gui.guiWidth()/2-88+20*slot,gui.guiHeight()-19);}
}
