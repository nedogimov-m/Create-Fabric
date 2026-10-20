package com.simibubi.create.test.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.EquipmentItemRenderer;
import com.simibubi.create.test.EquipmentNativeFrameProbe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=EquipmentItemRenderer.class,remap=false)
abstract class EquipmentNativeDrawProbeMixin {
    @WrapOperation(method="submit(Lcom/simibubi/create/content/equipment/EquipmentItemRenderer$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILnet/minecraft/client/resources/model/geometry/ItemQuads;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"))
    private void createTest$draw(SubmitNodeCollector collector,PoseStack pose,ItemDisplayContext display,int light,int overlay,int outline,int[] colors,ItemQuads quads,ItemStackRenderState.FoilType foil,Operation<Void> original){EquipmentNativeFrameProbe.draw(pose,display,quads);original.call(collector,pose,display,light,overlay,outline,colors,quads,foil);}
}
