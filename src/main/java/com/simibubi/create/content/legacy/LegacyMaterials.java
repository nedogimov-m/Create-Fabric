package com.simibubi.create.content.legacy;

import com.simibubi.create.*;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Actual core registrations; material physics run from genuine native ItemEntity ticks. */
public final class LegacyMaterials {
 public static void register(){AllConfigs.server().recipes.reload();LegacyConfigPacket.register();ServerTickEvents.END_LEVEL_TICK.register(LegacyMaterialDrops::tick);CreativeModeTabEvents.modifyOutputEvent(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,Create.asResource("base"))).register(output->{output.accept(AllItems.CHROMATIC_COMPOUND);output.accept(AllItems.SHADOW_STEEL);output.accept(AllItems.REFINED_RADIANCE);});}
 private LegacyMaterials(){}
}
