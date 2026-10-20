package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.schematics.client.SchematicsClient;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class SchematicScrollMixin {
    @Inject(method="onScroll",at=@At("HEAD"),cancellable=true)
    private void create$schematicScroll(long window,double horizontal,double vertical,CallbackInfo callback){if(window==Minecraft.getInstance().getWindow().handle()&&SchematicsClient.onScroll(vertical))callback.cancel();}
}
