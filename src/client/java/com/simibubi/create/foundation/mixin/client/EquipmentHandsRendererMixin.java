package com.simibubi.create.foundation.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.EquipmentHands;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class EquipmentHandsRendererMixin {
    @Inject(method="submitArmWithItem",at=@At("HEAD"),cancellable=true)
    private void create$submitEquipmentSnapshot(PlayerRenderState player,FirstPersonHandsAndItemsRenderState hands,float partial,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack pose,SubmitNodeCollector collector,int light,CallbackInfo ci){if(EquipmentHands.submit(player,hands,hand,pose,collector,light))ci.cancel();}
}
