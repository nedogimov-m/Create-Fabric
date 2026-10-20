package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.AllItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

public final class Blueprints {
 public static void register(){BlueprintData.register();BlueprintMenu.register();BlueprintPackets.register();ServerTickEvents.END_LEVEL_TICK.register(BlueprintDelivery::tick);CreativeModeTabEvents.modifyOutputEvent(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,com.simibubi.create.Create.asResource("base"))).register(output->output.accept(AllItems.CRAFTING_BLUEPRINT));}
 private Blueprints(){}
}
