package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.trains.entity.CarriagePlayerReferenceTransitions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Preserve only native unload/final world-eject source edges; later actions still supersede tokens. */
@Mixin(Entity.class)
public abstract class CarriagePlayerReferenceNativeDetachMixin {
    @WrapMethod(method="setRemoved")
    private void create$retainExactNativeSourceEdges(Entity.RemovalReason reason,Operation<Void> original){if(!com.simibubi.create.content.trains.entity.CarriageNpcMaterialOwners.beforeNonDestroyUnload((Entity)(Object)this,reason))return;try(var source=CarriagePlayerReferenceTransitions.nativeRemoval((Entity)(Object)this,reason)){original.call(reason);}}
    @WrapMethod(method="ejectPassengers")
    private void create$retainExactWorldHandoffEdges(Operation<Void> original){try(var source=CarriagePlayerReferenceTransitions.worldEjection((Entity)(Object)this)){original.call();}}
}
