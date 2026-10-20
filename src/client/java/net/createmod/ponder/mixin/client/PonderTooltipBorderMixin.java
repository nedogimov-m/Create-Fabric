package net.createmod.ponder.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
abstract class PonderTooltipBorderMixin {
    @WrapOperation(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;extractTooltipBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/resources/Identifier;)V"), require = 1)
    private void ponder$nativeBorder(GuiGraphicsExtractor gui, int x, int y, int width, int height, Identifier style,
                                    Operation<Void> original, @Local(argsOnly = true) List<ClientTooltipComponent> components) {
        original.call(gui, x, y, width, height, style);
        PonderTooltipHandler.extractBorder(gui, components, x, y, width, height);
    }
}
