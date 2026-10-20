package com.simibubi.create.test.mixin;

import com.simibubi.create.content.legacy.LegacyMaterialTestFixtures;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentEntitySectionManager.class)
public abstract class LegacyMaterialEarlySaveFaultMixin {
 @Inject(method="addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/LevelCallback;onCreated(Ljava/lang/Object;)V"))
 private void create$legacyEarly(EntityAccess candidate,boolean loaded,CallbackInfoReturnable<Boolean> callback){if(candidate instanceof Entity entity)LegacyMaterialTestFixtures.early(entity);}
}
