package net.createmod.ponder.test.mixin;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderViewport;
import net.createmod.ponder.test.PonderCustomElementsTest;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=PonderViewport.class,remap=false)
abstract class PonderCustomElementCaptureMixin {
    @Inject(method="extract",at=@At("RETURN"))
    private static void createTest$captured(GuiGraphicsExtractor gui,PonderScene scene,int x,int y,int width,int height,
            double slide,float partial,float flash,CallbackInfoReturnable<PonderViewport.State> callback){
        PonderCustomElementsTest.captured(scene,callback.getReturnValue());
    }
}
