package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.trains.entity.CarriagePlayerReferenceHolder;
import com.simibubi.create.content.trains.entity.CarriagePlayerReferences;
import com.simibubi.create.content.trains.entity.CarriagePlayerReferenceTransitions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** UUID-only relationship token in genuine native player save/load; never player/NPC copies. */
@Mixin(ServerPlayer.class)
public abstract class CarriagePlayerReferenceDataMixin implements CarriagePlayerReferenceHolder {
    @Unique private CarriagePlayerReferences.Token create$carriageReferenceToken;
    @Override public CarriagePlayerReferences.Token create$getCarriageReferenceToken(){return create$carriageReferenceToken;}
    @Override public void create$setCarriageReferenceToken(CarriagePlayerReferences.Token token){create$carriageReferenceToken=token;}
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void create$saveActualPlayerRelationship(ValueOutput output,CallbackInfo callback){if(create$carriageReferenceToken!=null)output.store("CreateCarriagePlayerReference",CarriagePlayerReferences.Token.CODEC,create$carriageReferenceToken);}
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void create$readActualPlayerRelationship(ValueInput input,CallbackInfo callback){create$carriageReferenceToken=input.read("CreateCarriagePlayerReference",CarriagePlayerReferences.Token.CODEC).orElse(null);}
    @Inject(method="saveParentVehicle",at=@At("HEAD"),cancellable=true)
    private void create$protectExactTransientNpcMaterialOwner(ValueOutput output,CallbackInfo callback){if(CarriagePlayerReferenceTransitions.protectsNativeTransferRoot(((ServerPlayer)(Object)this).getRootVehicle()))callback.cancel();}
}
