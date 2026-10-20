package com.simibubi.create.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItemRenderer;
import com.simibubi.create.test.SymmetryWandNativeProbe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SymmetryWandItemRenderer.class)
public abstract class SymmetryWandNativeDrawProbeMixin {
    @WrapMethod(method="submit(Lcom/simibubi/create/content/equipment/EquipmentItemRenderer$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V")
    private void create$observeWholeNativeWandFrame(com.simibubi.create.content.equipment.EquipmentItemRenderer.State state,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline,Operation<Void> original){SymmetryWandNativeProbe.frameStart();original.call(state,pose,collector,light,overlay,foil,outline);SymmetryWandNativeProbe.frameEnd();}
    @WrapOperation(method="submit(Lcom/simibubi/create/content/equipment/EquipmentItemRenderer$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILnet/minecraft/client/resources/model/geometry/ItemQuads;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"))
    private void create$observeRealWandItem(SubmitNodeCollector collector,PoseStack pose,ItemDisplayContext display,int light,int overlay,int outline,int[] tints,ItemQuads quads,ItemStackRenderState.FoilType foil,Operation<Void> original){SymmetryWandNativeProbe.draw(quads,light);original.call(collector,pose,display,light,overlay,outline,tints,quads,foil);}
}
