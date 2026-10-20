package com.simibubi.create.test.mixin;

import com.simibubi.create.test.CarriagePassengerRemovalTestFixtures;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueOutput;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class CarriagePassengerRemovalObservationMixin {
    @Inject(method="setRemoved",at=@At("HEAD"))
    private void create$observeActualSourceBeforeReason(Entity.RemovalReason reason,CallbackInfo callback){if(reason==Entity.RemovalReason.UNLOADED_TO_CHUNK)CarriagePassengerRemovalTestFixtures.before((Entity)(Object)this);}
    @Inject(method="setRemoved",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/EntityInLevelCallback;onRemove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
    private void create$observeActualPaidSourceBeforeWorldCallback(Entity.RemovalReason reason,CallbackInfo callback){if(reason==Entity.RemovalReason.UNLOADED_TO_CHUNK)CarriagePassengerRemovalTestFixtures.after((Entity)(Object)this);}
    @Inject(method="removeVehicle",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/Entity;vehicle:Lnet/minecraft/world/entity/Entity;",opcode=Opcodes.PUTFIELD,shift=At.Shift.AFTER))
    private void create$observeActualNpcSourceAfterVehicleClear(CallbackInfo callback){CarriagePassengerRemovalTestFixtures.unlinked((Entity)(Object)this);}
    @Inject(method="saveWithoutId",at=@At("HEAD"))
    private void create$observeActualNpcNativeSerializer(ValueOutput output,CallbackInfo callback){CarriagePassengerRemovalTestFixtures.saving((Entity)(Object)this);}
}
