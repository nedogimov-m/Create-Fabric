package com.simibubi.create.test.mixin;

import com.simibubi.create.content.legacy.LegacyMaterialTestFixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LegacyMaterialDestroyFaultMixin {
 @Inject(method="destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",at=@At("HEAD"),cancellable=true)
 private void create$legacyDestroy(BlockPos pos,boolean drops,Entity actor,int recursion,CallbackInfoReturnable<Boolean> callback){if(LegacyMaterialTestFixtures.destroy((Level)(Object)this,pos))callback.setReturnValue(false);}
}
