package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.trains.entity.CarriagePlayerReferenceTransitions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend the existing false frontend guard only across its exact native NPC source transfer. */
@Mixin(Entity.class)
public abstract class CarriagePassengerNativeSaveGuardMixin {
    @Inject(method="hasExactlyOnePlayerPassenger",at=@At("HEAD"),cancellable=true)
    private void create$doNotCopyNpcSourceIntoPlayerData(CallbackInfoReturnable<Boolean> callback){if(CarriagePlayerReferenceTransitions.protectsNativeTransferRoot((Entity)(Object)this))callback.setReturnValue(false);}
    @Inject(method="shouldBeSaved",at=@At("HEAD"),cancellable=true)
    private void create$doNotCopyTemporarySourceIntoNativeChunk(CallbackInfoReturnable<Boolean> callback){if(CarriagePlayerReferenceTransitions.protectsNativeTransferRoot((Entity)(Object)this))callback.setReturnValue(false);}
    @Inject(method="save",at=@At("HEAD"),cancellable=true)
    private void create$withholdTransientOrPaidNativeStorageSource(ValueOutput output,CallbackInfoReturnable<Boolean> callback){var actual=(Entity)(Object)this;if(actual instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity front&&front.hasOwnedStructure()&&!front.isIndependentNativeOwner()||CarriagePlayerReferenceTransitions.protectsNativeTransferRoot(actual)||com.simibubi.create.content.trains.entity.CarriageNativeSources.alreadyRetired(actual))callback.setReturnValue(false);}
    @Inject(method="saveAsPassenger",at=@At("HEAD"),cancellable=true)
    private void create$withholdAlreadyPaidNativeChild(ValueOutput output,CallbackInfoReturnable<Boolean> callback){var actual=(Entity)(Object)this;if(actual instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity front&&front.hasOwnedStructure()&&!front.isIndependentNativeOwner()||com.simibubi.create.content.trains.entity.CarriageNativeSources.alreadyRetired(actual))callback.setReturnValue(false);}
}

