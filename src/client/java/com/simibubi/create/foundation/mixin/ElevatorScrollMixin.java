package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.elevator.ElevatorControlsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only a genuine visible cabin controls slot consumes the native wheel event. */
@Mixin(MouseHandler.class)
public abstract class ElevatorScrollMixin {
    @Inject(method="onScroll",at=@At("HEAD"),cancellable=true)
    private void create$scrollElevator(long window,double horizontal,double vertical,CallbackInfo callback){if(window==Minecraft.getInstance().getWindow().handle()&&ElevatorControlsHandler.onScroll(vertical))callback.cancel();}
}
