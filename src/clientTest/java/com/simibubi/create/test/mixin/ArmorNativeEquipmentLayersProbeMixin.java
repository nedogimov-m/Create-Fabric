package com.simibubi.create.test.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.armor.CardboardArmorItem;
import com.simibubi.create.test.ArmorNativeRenderProbe;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EquipmentLayerRenderer.class)
abstract class ArmorNativeEquipmentLayersProbeMixin {
    @Inject(method="renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",at=@At("HEAD"))
    private void createTest$actualNativeEquipment(EquipmentClientInfo.LayerType type,ResourceKey<EquipmentAsset> asset,Model<?> model,Object state,ItemStack stack,PoseStack pose,SubmitNodeCollector collector,int light,Identifier texture,int outline,int color,CallbackInfo ci){if(stack.getItem() instanceof CardboardArmorItem)ArmorNativeRenderProbe.cardboard(stack.has(DataComponents.TRIM));}
}
