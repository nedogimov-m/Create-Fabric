package com.simibubi.create;

import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry;
import com.simibubi.create.api.contraption.transformable.MovedBlockTransformerRegistries;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.trains.track.TrackSchematicSupport;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;

/** Safe source extensions for native BE implementations which no longer inherit NeoForge SmartBlockEntity. */
public final class AllSchematicNbt {
    public static void registerDefaults(){
        SafeNbtWriterRegistry.REGISTRY.register(AllBlockEntityTypes.TRACK,TrackSchematicSupport::writeSafe);
        MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(AllBlockEntityTypes.TRACK,TrackSchematicSupport::transform);
        SafeNbtWriterRegistry.REGISTRY.register(AllBlockEntityTypes.MECHANICAL_ARM,(be,tag,registries)->{
            var arm=(com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity)be;
            arm.writeInteractionPoints(tag);tag.putString("SelectionMode",arm.getSelectionMode().name());tag.putInt("ScrollValue",arm.getSelectionMode().ordinal());
        });
        SafeNbtWriterRegistry.REGISTRY.register(AllBlockEntityTypes.FACTORY_PANEL,(be,tag,registries)->{
            var gauge=(FactoryPanelBlockEntity)be;var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,registries);
            for(var panel:gauge.panels.values())if(panel.active){var data=out.child(panel.slot.getSerializedName());data.store("Filter",ItemStack.OPTIONAL_CODEC,panel.getFilter());data.putBoolean("UpTo",panel.upTo);data.putInt("FilterAmount",panel.count);data.store("Freq",UUIDUtil.CODEC,panel.network);data.putString("RecipeAddress",panel.recipeAddress);data.putInt("PromiseClearingInterval",-1);data.putInt("RecipeOutput",1);if(gauge.restocker)data.store("Promises",com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue.CODEC,panel.restockerPromises);}
            tag.merge(out.buildResult());
        });
    }
    private AllSchematicNbt(){}
}
