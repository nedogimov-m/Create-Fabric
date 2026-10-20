package com.simibubi.create.foundation.mixin;
import com.simibubi.create.content.equipment.TreeFertilizerSimulation;
import java.util.List;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Source no-op dream notifications; genuine entity admission is deferred until accepted real replay. */
@Mixin(value=ServerLevel.class,priority=2000)
abstract class FertilizerServerLevelMixin {
 @Inject(method="addFreshEntity",at=@At("HEAD"),cancellable=true)private void create$deferTreeEntity(Entity entity,CallbackInfoReturnable<Boolean> ci){var dream=TreeFertilizerSimulation.active(this);if(dream!=null)ci.setReturnValue(dream.defer(entity));}
 @Inject(method={"levelEvent","gameEvent","destroyBlockProgress","blockEvent","sendBlockUpdated"},at=@At("HEAD"),cancellable=true,require=5)private void create$noTreeNotifications(CallbackInfo ci){if(TreeFertilizerSimulation.active(this)!=null)ci.cancel();}
 @Inject(method={
  "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
  "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V"
 },at=@At("HEAD"),cancellable=true,require=2)private void create$noTreeSound(CallbackInfo ci){if(TreeFertilizerSimulation.active(this)!=null)ci.cancel();}
 @Inject(method="players",at=@At("HEAD"),cancellable=true)private void create$noTreePlayers(CallbackInfoReturnable<List<ServerPlayer>> ci){if(TreeFertilizerSimulation.active(this)!=null)ci.setReturnValue(List.of());}
 @Inject(method="getEntity(I)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),cancellable=true)private void create$noTreeEntity(int id,CallbackInfoReturnable<Entity> ci){if(TreeFertilizerSimulation.active(this)!=null)ci.setReturnValue(null);}
}
