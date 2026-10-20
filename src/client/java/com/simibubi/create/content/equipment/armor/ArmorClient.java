package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;

public final class ArmorClient {
    @SuppressWarnings({"rawtypes","unchecked"}) public static void register(){
        BlockEntityRenderers.register(AllBlockEntityTypes.BACKTANK,BacktankRenderer::new);
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type,renderer,helper,context)->helper.register(new BacktankArmorLayer(renderer)));
        HudElementRegistry.attachElementAfter(VanillaHudElements.AIR_BAR,Create.asResource("remaining_air"),(gui,delta)->RemainingAirOverlay.INSTANCE.extractOverlay(gui));
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,Create.asResource("cardboard_stealth"),(gui,delta)->CardboardArmorStealthOverlay.extractOverlay(gui,delta.getGameTimeDeltaPartialTick(false)));
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(!client.isPaused()){CardboardArmorStealthOverlay.clientTick();CardboardArmorHandlerClient.clientTick();}});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->CardboardArmorHandlerClient.clear());
    }
    public static void applyFog(Camera camera,ClientLevel level,FogData data){if(!camera.isInitialized()||!(camera.entity() instanceof LivingEntity entity)||entity.isSpectator()||!DivingHelmetItem.isWornBy(entity))return;var pos=camera.blockPosition();var fluid=level.getFluidState(pos);if(camera.position().y>=pos.getY()+fluid.getHeight(level,pos))return;if(fluid.is(FluidTags.WATER)){data.environmentalEnd*=6.25f;data.skyEnd=data.cloudEnd=data.environmentalEnd;}else if(fluid.is(FluidTags.LAVA)&&NetheriteDivingHandler.isNetheriteDivingHelmet(DivingHelmetItem.getWornItem(entity))){data.environmentalStart=-4;data.environmentalEnd=data.skyEnd=data.cloudEnd=20;}}
    private ArmorClient(){}
}
