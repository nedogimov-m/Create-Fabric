package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
abstract class DivingBootsLocalPlayerMixin {
    @ModifyReturnValue(method="isUnderWater",at=@At("RETURN")) private boolean create$sourceHeavyBootsSwimming(boolean original){return original&&!DivingBootsItem.isWornBy((LocalPlayer)(Object)this);}
}
