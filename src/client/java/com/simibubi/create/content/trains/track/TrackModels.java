package com.simibubi.create.content.trains.track;

import com.simibubi.create.Create;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;

/** Standalone source tie/rail models baked by the existing native Fabric mesh loader after every reload. */
public final class TrackModels {
    public record Holder(ExtraModelKey<BlockStateModel> tie,ExtraModelKey<BlockStateModel> leftSegment,ExtraModelKey<BlockStateModel> rightSegment){}
    private static final Map<Identifier,Holder> MATERIALS=new LinkedHashMap<>();
    private static final Map<ExtraModelKey<BlockStateModel>,Identifier> MODELS=new LinkedHashMap<>();
    private static ExtraModelKey<BlockStateModel> model(Identifier id){var key=ExtraModelKey.<BlockStateModel>create(id::toString);MODELS.put(key,id);return key;}
    public static void register(){
        for(var material:TrackMaterial.ALL.values()){
            String prefix=material==TrackMaterial.ANDESITE?"block/track/":"block/track/"+material.resourceName()+"/";
            MATERIALS.put(material.id,new Holder(model(Identifier.fromNamespaceAndPath(material.id.getNamespace(),prefix+"tie")),model(Identifier.fromNamespaceAndPath(material.id.getNamespace(),prefix+"segment_left")),model(Identifier.fromNamespaceAndPath(material.id.getNamespace(),prefix+"segment_right"))));
        }
        ModelLoadingPlugin.register(context->MODELS.forEach((key,id)->context.addModel(key,SimpleUnbakedExtraModel.blockStateModel(id))));
    }
    public static Holder get(TrackMaterial material){return Objects.requireNonNull(MATERIALS.get(material.id),"Unregistered native track models for "+material.id);}
    private TrackModels(){}
}
