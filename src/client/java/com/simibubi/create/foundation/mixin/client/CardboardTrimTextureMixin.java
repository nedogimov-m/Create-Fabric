package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.Create;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Preserves native dynamic material palettes while selecting the pinned cardboard pattern UVs. */
@Mixin(targets="net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer$TrimTextureKey")
abstract class CardboardTrimTextureMixin {
    @Shadow @Final private EquipmentClientInfo equipmentInfo;
    @ModifyExpressionValue(method="getOrPrepareTexture",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/equipment/trim/TrimPattern;assetId()Lnet/minecraft/resources/Identifier;"))
    private Identifier create$sourceCardboardTrim(Identifier original){boolean cardboard=equipmentInfo.layers().values().stream().flatMap(java.util.Collection::stream).anyMatch(layer->layer.textureId().equals(Create.asResource("cardboard")));return cardboard?Create.asResource("card_"+original.getPath()):original;}
}
