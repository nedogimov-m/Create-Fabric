package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class ExtendoGripPlacementMixin {
    @Inject(method="place",at=@At("RETURN")) private void create$debitActualPlacement(BlockPlaceContext context,CallbackInfoReturnable<InteractionResult> cir){if(cir.getReturnValue().consumesAction())ExtendoGripItem.findAndDamageExtendoGrip(context.getPlayer());}
}
