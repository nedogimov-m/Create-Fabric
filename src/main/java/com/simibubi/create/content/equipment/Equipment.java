package com.simibubi.create.content.equipment;

import com.simibubi.create.api.equipment.potatoCannon.PotatoCannonProjectileType;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.equipment.extendoGrip.*;
import com.simibubi.create.content.equipment.potatoCannon.*;
import com.simibubi.create.foundation.utility.GlobalRegistryAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.item.enchantment.Enchantments;

/** Original equipment registries boot before actual world/datapack loading. */
public final class Equipment {
    private static boolean registered;
    public static void register(){if(registered)return;registered=true;
        AllPotatoProjectileRenderModes.init();AllPotatoProjectileEntityHitActions.init();AllPotatoProjectileBlockHitActions.init();
        DynamicRegistries.registerSynced(CreateRegistries.POTATO_PROJECTILE_TYPE,PotatoCannonProjectileType.CODEC);
        EquipmentConfig.reload();GlobalRegistryAccess.register();EquipmentAdvancements.register();
        com.simibubi.create.content.equipment.zapper.terrainzapper.ConfigureWorldshaperPacket.register();com.simibubi.create.content.equipment.zapper.ZapperBeamPacket.register();com.simibubi.create.content.equipment.zapper.ZapperSelectPacket.register();ExtendoGripItem.register();ExtendoGripInteractionPacket.register();PotatoCannonPacket.register();EquipmentConfigPacket.register();
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->ServerPlayNetworking.send(handler.player,new EquipmentConfigPacket(EquipmentConfig.server())));
        EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment,stack,context)->stack.getItem() instanceof com.simibubi.create.content.equipment.armor.BacktankItem tank&&!tank.supportsEnchantment(stack,enchantment)||stack.getItem() instanceof com.simibubi.create.content.equipment.armor.DivingHelmetItem helmet&&!helmet.supportsEnchantment(stack,enchantment)?TriState.FALSE:TriState.DEFAULT);
        EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment,stack,context)->stack.getItem() instanceof PotatoCannonItem?(enchantment.is(Enchantments.INFINITY)?TriState.FALSE:enchantment.is(Enchantments.LOOTING)?TriState.TRUE:TriState.DEFAULT):TriState.DEFAULT);
    }
    private Equipment(){}
}
