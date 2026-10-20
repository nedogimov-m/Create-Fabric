package com.simibubi.create.content.trains.display;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperUnbakedModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.model.MeshQuadCollection;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;

/** Native alpha-cutout source board/item geometry; current26.3 exposes CUTOUT instead of the old cutoutMipped layer. */
public final class FlapDisplayModel {
    public static void register(){UnbakedModelDeserializer.register(Create.asResource("flap_display"),(json,context)->{
        var value=json.deepCopy();value.remove("fabric:type");UnbakedModel base=context.deserialize(value,UnbakedModel.class);UnbakedGeometry geometry=(textures,baker,state,debug)->{var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();for(var quad:base.geometry().bake(textures,baker,state,debug).getAll())emitter.fromBakedQuad(quad).chunkLayer(ChunkSectionLayer.CUTOUT).emit();return new MeshQuadCollection(mesh.immutableCopy());};return new WrapperUnbakedModel(base){@Override public UnbakedGeometry geometry(){return geometry;}};
    });}
    private FlapDisplayModel(){}
}
