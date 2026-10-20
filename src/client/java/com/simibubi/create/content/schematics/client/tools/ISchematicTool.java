package com.simibubi.create.content.schematics.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/** Source tool lifecycle/actions, with native extraction in place of the removed immediate renderer. */
public interface ISchematicTool {
    void init();
    void updateSelection();
    boolean handleRightClick();
    boolean handleMouseWheel(double delta);
    void extractTool(PoseStack pose,SchematicToolRenderState.Builder output,Vec3 camera,float partial);
    void extractOnSchematic(PoseStack pose,SchematicToolRenderState.Builder output,Vec3 camera,float partial);
    void extractOverlay(GuiGraphicsExtractor gui,float partial,int width,int height);
}
