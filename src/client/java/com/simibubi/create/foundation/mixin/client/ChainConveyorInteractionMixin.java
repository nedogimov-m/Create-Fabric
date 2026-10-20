package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Minecraft.class)
public abstract class ChainConveyorInteractionMixin {
    @Shadow private int rightClickDelay;
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
    private void create$attachParcel(CallbackInfo ci){if(com.simibubi.create.content.trains.entity.TrainRelocatorClient.onClicked((Minecraft)(Object)this)||com.simibubi.create.content.trains.track.CurvedTrackInteraction.onUse((Minecraft)(Object)this)||com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler.onUse((Minecraft)(Object)this)||com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorOverlay.cancelUse((Minecraft)(Object)this)||com.simibubi.create.content.contraptions.ContraptionClient.onUse((Minecraft)(Object)this)||ChainConveyorInteractionHandler.onUse((Minecraft)(Object)this)){rightClickDelay=4;ci.cancel();}}
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void create$takeParcel(CallbackInfoReturnable<Boolean> ci){if(com.simibubi.create.content.trains.entity.TrainRelocatorClient.onClicked((Minecraft)(Object)this)||com.simibubi.create.content.trains.track.CurvedTrackInteraction.onAttack((Minecraft)(Object)this)||com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler.onAttack((Minecraft)(Object)this)||ChainConveyorInteractionHandler.onAttack((Minecraft)(Object)this))ci.setReturnValue(false);}
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void create$protectBlockBehindParcel(boolean attacking,CallbackInfo ci){if(com.simibubi.create.content.trains.track.CurvedTrackInteraction.suppressAttack((Minecraft)(Object)this,attacking)||com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler.suppressAttack((Minecraft)(Object)this)||ChainConveyorInteractionHandler.suppressAttack((Minecraft)(Object)this))ci.cancel();}
}
