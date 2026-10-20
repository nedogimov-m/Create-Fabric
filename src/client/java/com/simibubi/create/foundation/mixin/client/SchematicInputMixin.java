package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.schematics.client.SchematicsClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native actual use key dispatch, independent of backend/SDL mouse button numbering. */
@Mixin(value=Minecraft.class,priority=1100)
public abstract class SchematicInputMixin {
    @Shadow private int rightClickDelay;
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
    private void create$schematicTool(CallbackInfo callback){if(SchematicsClient.onUse((Minecraft)(Object)this)){rightClickDelay=4;callback.cancel();}}
}
