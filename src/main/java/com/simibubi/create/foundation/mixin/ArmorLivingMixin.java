package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.armor.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ArmorLivingMixin implements ArmorLivingAccess {
    @Shadow protected boolean jumping;
    @Unique private boolean create$lavaGrounded;
    @Inject(method="readAdditionalSaveData",at=@At("TAIL")) private void create$readActualLavaGrounded(net.minecraft.world.level.storage.ValueInput input,CallbackInfo callback){create$lavaGrounded=input.getBooleanOr("CreateArmorLavaGrounded",false);}
    @Inject(method="addAdditionalSaveData",at=@At("TAIL")) private void create$saveActualLavaGrounded(net.minecraft.world.level.storage.ValueOutput output,CallbackInfo callback){output.putBoolean("CreateArmorLavaGrounded",create$lavaGrounded);}
    @Override public boolean create$isArmorJumping(){return jumping;}
    @Override public boolean create$wasLavaGrounded(){return create$lavaGrounded;}
    @Override public void create$setLavaGrounded(boolean value){create$lavaGrounded=value;}
    @Inject(method="tick",at=@At("HEAD")) private void create$heavyBootsTick(CallbackInfo ci){DivingBootsItem.accelerateDescentUnderwater((LivingEntity)(Object)this);}
    @Inject(method="baseTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;baseTick()V",shift=At.Shift.AFTER)) private void create$lavaBreathing(CallbackInfo ci){DivingHelmetItem.breatheLava((LivingEntity)(Object)this);}
    @WrapOperation(method="baseTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;decreaseAirSupply(I)I"))
    private int create$sourceTankBreathing(LivingEntity entity,int air,Operation<Integer> original){return DivingHelmetItem.breatheUnderwater(entity)?entity.getMaxAirSupply():original.call(entity,air);}
    @Inject(method="travelInLava",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",shift=At.Shift.AFTER))
    private void create$netheriteBootLavaTravel(Vec3 vector,double gravity,boolean falling,double previousY,CallbackInfo ci){var entity=(LivingEntity)(Object)this;if(DivingBootsItem.getWornItem(entity).is(AllItems.NETHERITE_DIVING_BOOTS))entity.setDeltaMovement(entity.getDeltaMovement().multiply(DivingBootsItem.getMovementMultiplier(entity)));}
    @Inject(method="onEquipItem",at=@At("RETURN")) private void create$cardboardEquipment(EquipmentSlot slot,ItemStack previous,ItemStack next,CallbackInfo ci){if(slot.isArmor())CardboardArmorHandler.playerChangesEquipment((LivingEntity)(Object)this);}
    @ModifyReturnValue(method="getDimensions",at=@At("RETURN")) private EntityDimensions create$cardboardDimensions(EntityDimensions original,Pose pose){var entity=(LivingEntity)(Object)this;return pose==Pose.CROUCHING&&CardboardArmorHandler.testForStealth(entity)?CardboardArmorHandler.stealthDimensions(entity):original;}
    @ModifyReturnValue(method="getVisibilityPercent",at=@At("RETURN")) private double create$cardboardVisibility(double original){return CardboardArmorHandler.testForStealth((LivingEntity)(Object)this)?0:original;}
}
