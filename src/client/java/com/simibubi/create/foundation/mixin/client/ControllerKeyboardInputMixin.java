package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.redstone.link.controller.ControllerPhysicalInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class ControllerKeyboardInputMixin{
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void create$capturePhysicalKey(long window,int action,KeyEvent event,CallbackInfo callback){if(window!=0&&net.minecraft.client.Minecraft.getInstance().getWindow().handle()==window){ControllerPhysicalInput.key(action,event);if(ControllerPhysicalInput.consumes(event))callback.cancel();}}
}
