package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class PotatoCannonArmPoseMixin {
    @Inject(method="getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",at=@At("HEAD"),cancellable=true)
    private static void create$sourceCannonPose(Avatar avatar,ItemStack stack,InteractionHand hand,CallbackInfoReturnable<ArmPose> cir){if(avatar instanceof Player player&&!player.isSwinging()&&stack.getItem() instanceof PotatoCannonItem)cir.setReturnValue(ArmPose.CROSSBOW_HOLD);}
}
