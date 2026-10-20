package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.Create;
import com.simibubi.create.infrastructure.gui.CreateMenuPanoramaState;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Select the source cube only inside native deferred panorama rendering, with native texture ownership. */
@Mixin(GuiRenderer.class)
public abstract class CreateMenuGuiRendererMixin {
    @Shadow @Final private GuiRenderState renderState;
    @Unique private CubeMap create$menuPanorama;
    @Inject(method="<init>",at=@At("TAIL")) private void create$allocateMenuPanorama(CallbackInfo ci){create$menuPanorama=new CubeMap(Create.asResource("textures/gui/title/background/panorama"));}
    @Inject(method="registerPanoramaTextures",at=@At("TAIL")) private void create$registerMenuTextures(TextureManager textures,CallbackInfo ci){create$menuPanorama.registerTextures(textures);}
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/CubeMap;render(FF)V"))
    private void create$renderMenuPanorama(CubeMap vanilla,float pitch,float yaw,Operation<Void> original){original.call(((CreateMenuPanoramaState)renderState).create$hasMenuPanorama()?create$menuPanorama:vanilla,pitch,yaw);}
    @Inject(method="close",at=@At("TAIL")) private void create$closeMenuPanorama(CallbackInfo ci){if(create$menuPanorama!=null)create$menuPanorama.close();}
}
