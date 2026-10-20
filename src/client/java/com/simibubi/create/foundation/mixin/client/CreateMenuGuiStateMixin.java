package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.infrastructure.gui.CreateMenuPanoramaState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class CreateMenuGuiStateMixin implements CreateMenuPanoramaState {
    @Unique private boolean create$menuPanorama;
    @Override public boolean create$hasMenuPanorama(){return create$menuPanorama;}
    @Override public void create$setMenuPanorama(boolean value){create$menuPanorama=value;}
    @Inject(method="reset",at=@At("TAIL")) private void create$clearMenuPanorama(CallbackInfo ci){create$menuPanorama=false;}
}
