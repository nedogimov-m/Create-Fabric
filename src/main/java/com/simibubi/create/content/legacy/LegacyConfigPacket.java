package com.simibubi.create.content.legacy;

import com.simibubi.create.Create;
import com.simibubi.create.infrastructure.config.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The actual server's source settings drive remote client bars and source particle probabilities. */
public record LegacyConfigPacket(CRecipes.Settings settings) implements CustomPacketPayload {
 public static final Type<LegacyConfigPacket> TYPE=new Type<>(Create.asResource("legacy_material_recipes"));
 private static final StreamCodec<RegistryFriendlyByteBuf,CRecipes.Settings> SETTINGS=StreamCodec.composite(ByteBufCodecs.VAR_INT,CRecipes.Settings::lightSourceCountForRefinedRadiance,ByteBufCodecs.BOOL,CRecipes.Settings::enableRefinedRadianceRecipe,ByteBufCodecs.BOOL,CRecipes.Settings::enableShadowSteelRecipe,CRecipes.Settings::new);
 public static final StreamCodec<RegistryFriendlyByteBuf,LegacyConfigPacket> CODEC=SETTINGS.map(LegacyConfigPacket::new,LegacyConfigPacket::settings);
 @Override public Type<LegacyConfigPacket> type(){return TYPE;}
 public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->ServerPlayNetworking.send(handler.player,new LegacyConfigPacket(AllConfigs.server().recipes.serverSettings())));}
}
