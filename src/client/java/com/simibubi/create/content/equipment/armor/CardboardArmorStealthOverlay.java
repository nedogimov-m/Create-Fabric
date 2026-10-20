package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.Create;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

public final class CardboardArmorStealthOverlay {
    private static final LerpedFloat OPACITY=LerpedFloat.linear().startWithValue(0).chase(0,.25f,LerpedFloat.Chaser.EXP);
    public static void clientTick(){var player=Minecraft.getInstance().player;if(player==null){OPACITY.startWithValue(0);return;}OPACITY.tickChaser();OPACITY.updateChaseTarget(CardboardArmorHandler.testForStealth(player)?1:0);}
    public static void extractOverlay(GuiGraphicsExtractor gui,float partial){var client=Minecraft.getInstance();if(client.player==null||!client.options.getCameraType().isFirstPerson())return;float opacity=OPACITY.getValue(partial);if(opacity<=0)return;gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/misc/package_blur.png"),0,0,0,0,gui.guiWidth(),gui.guiHeight(),gui.guiWidth(),gui.guiHeight(),((int)(opacity*255)<<24)|0xFFFFFF);}
    private CardboardArmorStealthOverlay(){}
}
