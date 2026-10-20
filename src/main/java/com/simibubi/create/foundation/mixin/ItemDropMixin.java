package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.kinetics.deployer.DeployerDropCapture;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import com.simibubi.create.content.logistics.box.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Decide ownership before converting world drops; independent HEAD injections have no ordering contract. */
@Mixin(ServerLevel.class)
abstract class ItemDropMixin {
    @Inject(method="addFreshEntity",at=@At("HEAD"),cancellable=true)
    private void create$itemDrop(Entity entity,CallbackInfoReturnable<Boolean> callback){
        // The actual owner prepared this exact endpoint before its native add.
        // Capture or replacement here would publish a copy before canonical debit.
        if(NativeEntityAcceptance.isPreparedEndpointInCurrentAdd(entity))return;
        var level=(ServerLevel)(Object)this;
        if(DeployerDropCapture.capture(level,entity)){callback.setReturnValue(true);return;}
        if(!(entity instanceof ItemEntity item)||!PackageItem.isPackage(item.getItem())||((ItemEntityPickupAccessor)item).create$pickupDelay()==32767)return;
        var replacement=PackageEntity.fromDroppedItem(level,item,item.getItem());boolean added=level.addFreshEntity(replacement);
        if(added)item.discard();callback.setReturnValue(added);
    }
}
