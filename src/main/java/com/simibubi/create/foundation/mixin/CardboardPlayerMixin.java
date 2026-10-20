package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.equipment.armor.CardboardArmorHandler;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class CardboardPlayerMixin {
    @ModifyReturnValue(method="canPlayerFitWithinBlocksAndEntitiesWhen",at=@At("RETURN")) private boolean create$boxRemainsCrouched(boolean original,Pose pose){return original||pose==Pose.CROUCHING&&CardboardArmorHandler.testForStealth((Player)(Object)this);}
}
