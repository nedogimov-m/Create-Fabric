package com.simibubi.create.foundation.mixin;
import com.simibubi.create.content.equipment.zapper.ZapperItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Genuine source onEntitySwing hook; no native swing flags are fabricated. */
@Mixin(LivingEntity.class)
abstract class ZapperSwingMixin {
    @Inject(method="swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z",at=@At("HEAD"),cancellable=true)
    private void create$sourceZapperSwing(InteractionHand hand,net.minecraft.world.item.component.SwingAnimation animation,boolean broadcast,CallbackInfoReturnable<Boolean> cir){var entity=(LivingEntity)(Object)this;var stack=entity.getItemInHand(hand);if(stack.getItem() instanceof ZapperItem zapper&&zapper.onEntitySwing(stack,entity))cir.setReturnValue(false);}
}
