package com.simibubi.create.content.equipment.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/** Consumes the captured actual worn-tank state and native player model; no live player lookup in submission. */
public final class NetheriteBacktankFirstPersonRenderer {
    public static boolean submitHand(AvatarRenderer<?> renderer,PoseStack pose,SubmitNodeCollector collector,boolean right,PlayerRenderState state){if(state.avatarRenderState==null)return false;var frame=((FabricRenderState)state.avatarRenderState).getData(BacktankArmorLayer.BACKTANK);if(frame==null||!frame.netherite())return false;if(!(renderer.getModel() instanceof PlayerModel model))return false;var arm=right?model.rightArm:model.leftArm;var sleeve=right?model.rightSleeve:model.leftSleeve;var saved=arm.storePose();arm.resetPose();arm.xRot=arm.yRot=arm.zRot=0;pose.pushPose();arm.translateAndRotate(pose);sleeve.resetPose();sleeve.visible=true;collector.submitModelPart(sleeve,pose,RenderTypes.entitySolid(Create.asResource("textures/models/armor/netherite_diving_arm.png")),LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,null);pose.popPose();arm.loadPose(saved);return true;}
    private NetheriteBacktankFirstPersonRenderer(){}
}
