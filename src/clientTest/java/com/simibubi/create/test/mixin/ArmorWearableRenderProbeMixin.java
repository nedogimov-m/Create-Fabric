package com.simibubi.create.test.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.armor.BacktankArmorLayer;
import com.simibubi.create.test.ArmorNativeRenderProbe;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=BacktankArmorLayer.class,remap=false)
abstract class ArmorWearableRenderProbeMixin {
    @Inject(method="submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",at=@At("HEAD")) private void createTest$wearable(PoseStack pose,SubmitNodeCollector collector,int light,LivingEntityRenderState state,float yaw,float pitch,CallbackInfo ci){var frame=((FabricRenderState)state).getData(BacktankArmorLayer.BACKTANK);if(frame!=null)ArmorNativeRenderProbe.wearable(frame.foil());}
}
