package com.simibubi.create;

import com.simibubi.create.api.contraption.ContraptionMovementSetting;
import com.simibubi.create.content.contraptions.ContraptionSettings;
import net.minecraft.world.level.block.Blocks;

public final class AllContraptionMovementSettings {
    public static void registerDefaults(){
        ContraptionMovementSetting.REGISTRY.register(Blocks.SPAWNER,()->ContraptionSettings.spawnerMovement);
        ContraptionMovementSetting.REGISTRY.register(Blocks.BUDDING_AMETHYST,()->ContraptionSettings.amethystMovement);
        ContraptionMovementSetting.REGISTRY.register(Blocks.OBSIDIAN,()->ContraptionSettings.obsidianMovement);
        ContraptionMovementSetting.REGISTRY.register(Blocks.CRYING_OBSIDIAN,()->ContraptionSettings.obsidianMovement);
        ContraptionMovementSetting.REGISTRY.register(Blocks.RESPAWN_ANCHOR,()->ContraptionSettings.obsidianMovement);
        ContraptionMovementSetting.REGISTRY.register(Blocks.REINFORCED_DEEPSLATE,()->ContraptionSettings.reinforcedDeepslateMovement);
    }
    private AllContraptionMovementSettings(){}
}
