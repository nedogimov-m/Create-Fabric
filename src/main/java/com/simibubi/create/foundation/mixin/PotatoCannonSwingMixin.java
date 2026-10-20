package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PotatoCannonSwingMixin {
    @Inject(method="swing",at=@At("HEAD"),cancellable=true)
    private void create$sourceGadgetSwing(InteractionHand hand,SwingAnimation animation,boolean broadcast,CallbackInfoReturnable<Boolean> cir){if(((LivingEntity)(Object)this).getItemInHand(hand).getItem() instanceof PotatoCannonItem)cir.setReturnValue(false);}
}
