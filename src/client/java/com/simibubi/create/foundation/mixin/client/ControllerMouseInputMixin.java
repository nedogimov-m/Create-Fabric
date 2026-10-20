package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.redstone.link.controller.ControllerPhysicalInput;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class ControllerMouseInputMixin{
    @Inject(method="onButton",at=@At("HEAD"),cancellable=true)
    private void create$capturePhysicalMouse(long window,MouseButtonInfo event,int action,CallbackInfo callback){if(window!=0&&net.minecraft.client.Minecraft.getInstance().getWindow().handle()==window){ControllerPhysicalInput.mouse(action,event);if(ControllerPhysicalInput.consumes(event))callback.cancel();}}
}
