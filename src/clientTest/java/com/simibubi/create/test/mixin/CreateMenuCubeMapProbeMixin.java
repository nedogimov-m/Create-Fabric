package com.simibubi.create.test.mixin;

import com.simibubi.create.test.CreateMenuNativeProbe;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CubeMap.class)
public abstract class CreateMenuCubeMapProbeMixin {
    @Shadow @Final private Identifier location;
    @Inject(method="render",at=@At("HEAD")) private void createTest$actualCube(float pitch,float yaw,CallbackInfo ci){CreateMenuNativeProbe.panorama(location,yaw);}
}
