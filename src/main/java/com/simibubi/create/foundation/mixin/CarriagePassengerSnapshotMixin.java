package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.trains.entity.CarriagePassengerSnapshots;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps original world mounts intact while each genuine native NPC serializer supplies one flat node. */
@Mixin(Entity.class)
public abstract class CarriagePassengerSnapshotMixin {
    @WrapMethod(method="saveWithoutId")
    private void create$scopeActualNativePassengerSnapshot(ValueOutput output,Operation<Void> original){
        try(var scope=CarriagePassengerSnapshots.enterNativeSave((Entity)(Object)this)){original.call(output);}
    }
    @ModifyExpressionValue(method="saveWithoutId",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;getPassengers()Ljava/util/List;"))
    private List<Entity> create$onlyOuterExactSnapshotOmitsChildren(List<Entity> actual){return CarriagePassengerSnapshots.nativeSavePassengers((Entity)(Object)this,actual);}
}
