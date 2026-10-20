package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.armor.NetheriteBacktankFirstPersonRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class NetheriteBacktankHandsMixin {
    @WrapOperation(method="renderPlayerHand",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V")) private void create$rightSleeve(AvatarRenderer<?> renderer,PoseStack pose,SubmitNodeCollector collector,int light,Identifier texture,boolean showSleeve,Operation<Void> original,@Local(argsOnly=true) PlayerRenderState state){if(!NetheriteBacktankFirstPersonRenderer.submitHand(renderer,pose,collector,true,state))original.call(renderer,pose,collector,light,texture,showSleeve);}
    @WrapOperation(method="renderPlayerHand",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V")) private void create$leftSleeve(AvatarRenderer<?> renderer,PoseStack pose,SubmitNodeCollector collector,int light,Identifier texture,boolean showSleeve,Operation<Void> original,@Local(argsOnly=true) PlayerRenderState state){if(!NetheriteBacktankFirstPersonRenderer.submitHand(renderer,pose,collector,false,state))original.call(renderer,pose,collector,light,texture,showSleeve);}
}
