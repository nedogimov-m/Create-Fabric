package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.equipment.armor.NetheriteDivingHandler;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
abstract class ArmorEntityMixin {
    @ModifyReturnValue(method="fireImmune",at=@At("RETURN")) private boolean create$nativeDivingImmunity(boolean original){return original||(Object)this instanceof LivingEntity entity&&NetheriteDivingHandler.isFireImmune(entity);}
}
