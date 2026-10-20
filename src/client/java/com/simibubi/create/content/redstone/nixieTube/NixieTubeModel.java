package com.simibubi.create.content.redstone.nixieTube;

import com.google.gson.JsonObject;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperUnbakedModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.model.MeshQuadCollection;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;

/** Actual source composite: opaque metal connectors and translucent glass baked into native layered quads. */
public final class NixieTubeModel {
    public static void register(){UnbakedModelDeserializer.register(Create.asResource("nixie_tube"),(json,context)->{
        JsonObject baseJson=json.deepCopy();baseJson.remove("fabric:type");baseJson.remove("children");UnbakedModel base=context.deserialize(baseJson,UnbakedModel.class);
        var connectors=child(baseJson,json.getAsJsonObject("children").getAsJsonObject("connectors"));var tubes=child(baseJson,json.getAsJsonObject("children").getAsJsonObject("tubes"));
        UnbakedModel solid=context.deserialize(connectors,UnbakedModel.class);UnbakedModel glass=context.deserialize(tubes,UnbakedModel.class);
        UnbakedGeometry geometry=(textures,baker,state,debug)->{
            var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();
            for(var quad:solid.geometry().bake(textures,baker,state,debug).getAll())emitter.fromBakedQuad(quad).chunkLayer(ChunkSectionLayer.SOLID).emit();
            for(var quad:glass.geometry().bake(textures,baker,state,debug).getAll())emitter.fromBakedQuad(quad).chunkLayer(ChunkSectionLayer.TRANSLUCENT).emit();
            return new MeshQuadCollection(mesh.immutableCopy());
        };
        return new WrapperUnbakedModel(base){@Override public UnbakedGeometry geometry(){return geometry;}};
    });}
    private static JsonObject child(JsonObject base,JsonObject child){var result=base.deepCopy();result.add("elements",child.getAsJsonArray("elements").deepCopy());return result;}
    private NixieTubeModel(){}
}
