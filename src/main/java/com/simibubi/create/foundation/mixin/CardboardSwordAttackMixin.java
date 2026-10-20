package com.simibubi.create.foundation.mixin;
import com.simibubi.create.content.equipment.tool.CardboardSwordItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Source highest-priority native attack cancellation, before damage/post-hit/durability paths. */
@Mixin(value=Player.class,priority=1500)
abstract class CardboardSwordAttackMixin {
 @Inject(method="attack",at=@At("HEAD"),cancellable=true)private void create$harmlessNativeCardboard(Entity target,CallbackInfo ci){if(CardboardSwordItem.cardboardSwordsCannotHurtYou((Player)(Object)this,target))ci.cancel();}
}
