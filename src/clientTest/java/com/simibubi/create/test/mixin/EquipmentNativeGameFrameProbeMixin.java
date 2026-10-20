package com.simibubi.create.test.mixin;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.simibubi.create.test.EquipmentNativeFrameProbe;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
abstract class EquipmentNativeGameFrameProbeMixin {
    @Shadow @Final private GameRenderState gameRenderState;
    @Shadow @Final private Projection hudProjection;
    @Inject(method="renderItemInHand",at=@At("HEAD")) private void createTest$frame(CameraRenderState camera,PlayerRenderState player,GpuTextureView target,CallbackInfo ci){EquipmentNativeFrameProbe.frame(camera,player,gameRenderState,hudProjection);}
    @Inject(method="renderItemInHand",at=@At("RETURN")) private void createTest$finish(CallbackInfo ci){EquipmentNativeFrameProbe.finishFrame();}
}
