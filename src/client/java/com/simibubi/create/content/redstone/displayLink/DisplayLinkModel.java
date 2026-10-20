package com.simibubi.create.content.redstone.displayLink;

import com.google.gson.JsonObject;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperUnbakedModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.model.MeshQuadCollection;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;

/** Actual source cutout metal/details and translucent bulb children, including each child's real texture slots. */
public final class DisplayLinkModel {
    public static void register(){UnbakedModelDeserializer.register(Create.asResource("display_link"),(json,context)->{
        var children=json.getAsJsonObject("children");var value=json.deepCopy();value.remove("fabric:type");value.remove("children");var slots=value.getAsJsonObject("textures");for(var child:children.entrySet())for(var texture:child.getValue().getAsJsonObject().getAsJsonObject("textures").entrySet())slots.add(texture.getKey(),texture.getValue().deepCopy());UnbakedModel base=context.deserialize(value,UnbakedModel.class);UnbakedModel metal=context.deserialize(child(value,children.getAsJsonObject("base")),UnbakedModel.class);UnbakedModel bulb=context.deserialize(child(value,children.getAsJsonObject("bulb")),UnbakedModel.class);
        UnbakedGeometry geometry=(textures,baker,state,debug)->{var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();for(var quad:metal.geometry().bake(textures,baker,state,debug).getAll())emitter.fromBakedQuad(quad).chunkLayer(ChunkSectionLayer.CUTOUT).emit();for(var quad:bulb.geometry().bake(textures,baker,state,debug).getAll())emitter.fromBakedQuad(quad).chunkLayer(ChunkSectionLayer.TRANSLUCENT).emit();return new MeshQuadCollection(mesh.immutableCopy());};return new WrapperUnbakedModel(base){@Override public UnbakedGeometry geometry(){return geometry;}};
    });}
    private static JsonObject child(JsonObject base,JsonObject child){var result=base.deepCopy();result.add("elements",child.getAsJsonArray("elements").deepCopy());return result;}
    private DisplayLinkModel(){}
}
