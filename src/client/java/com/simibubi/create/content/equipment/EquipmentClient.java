package com.simibubi.create.content.equipment;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.equipment.extendoGrip.*;
import com.simibubi.create.content.equipment.potatoCannon.*;
import com.simibubi.create.foundation.utility.GlobalRegistryAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class EquipmentClient {
    public static final com.simibubi.create.content.equipment.zapper.ZapperRenderHandler ZAPPER=new com.simibubi.create.content.equipment.zapper.ZapperRenderHandler();
    public static final PotatoCannonRenderHandler POTATO_CANNON=new PotatoCannonRenderHandler();
    public static void register(){
        com.simibubi.create.content.equipment.armor.ArmorClient.register();
        com.simibubi.create.content.equipment.zapper.ZapperClient.register();
        EquipmentConfig.CURRENT_VALUES=()->Minecraft.getInstance().isSameThread()?EquipmentConfig.client():EquipmentConfig.server();
        GlobalRegistryAccess.CLIENT_REGISTRIES=()->{var level=Minecraft.getInstance().level;return level==null?null:level.registryAccess();};
        BacktankUtil.CLIENT_PLAYER=()->Minecraft.getInstance().player;PotatoCannonItem.CLIENT_PLAYER=()->Minecraft.getInstance().player;PotatoCannonItem.DONT_ANIMATE=POTATO_CANNON::dontAnimateItem;
        ExtendoGripItemRenderer.INSTANCE.register("extendo_grip");PotatoCannonItemRenderer.INSTANCE.register("potato_cannon");EntityRenderers.register(AllEntityTypes.POTATO_PROJECTILE,PotatoProjectileRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(client.level!=null&&!client.isPaused()){ExtendoGripRenderHandler.tick();POTATO_CANNON.tick();}});
        ClientPlayNetworking.registerGlobalReceiver(EquipmentConfigPacket.TYPE,(packet,context)->EquipmentConfig.installClient(packet.values()));
        ClientPlayNetworking.registerGlobalReceiver(PotatoCannonPacket.TYPE,(packet,context)->{var client=context.client();var camera=client.getCameraEntity();if(camera==null||camera.position().distanceTo(packet.location)>100)return;POTATO_CANNON.beforeShoot(packet.pitch,packet.location,packet.motion,packet.item);if(packet.self)POTATO_CANNON.shoot(packet.hand,packet.location);else POTATO_CANNON.playSound(packet.hand,packet.location);});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{EquipmentConfig.clearClient();POTATO_CANNON.reset();ExtendoGripRenderHandler.mainHandAnimation=ExtendoGripRenderHandler.lastMainHandAnimation=0;});
    }
    private EquipmentClient(){}
}
