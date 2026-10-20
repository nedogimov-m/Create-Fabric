package com.simibubi.create.test.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.test.EquipmentNativeGuiProbe;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(GuiItemAtlas.class)
abstract class EquipmentNativeGuiAtlasProbeMixin {
    @Shadow @Final private int slotTextureSize;
    @Shadow @Final private Projection projection;
    @WrapOperation(method="drawToSlot",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void createTest$actualGuiSlot(ItemStackRenderState state,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,int outline,Operation<Void> original,int x,int y,boolean clear,ItemStackRenderState source){EquipmentNativeGuiProbe.nativeSlot(x,y,slotTextureSize,projection,state,pose);try{original.call(state,pose,collector,light,overlay,outline);}finally{EquipmentNativeGuiProbe.finishSlot();}}
}
