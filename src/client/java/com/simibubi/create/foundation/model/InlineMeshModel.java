package com.simibubi.create.foundation.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperUnbakedModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.model.MeshQuadCollection;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Loader for migrated OBJ faces. Uses Fabric meshes with original vertex normals. */
public final class InlineMeshModel {
    private record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}
    private record Face(String material, List<Vertex> vertices) {}

    public static void register() {
        UnbakedModelDeserializer.register(Create.asResource("mesh"), (json, context) -> {
            JsonObject baseJson = json.deepCopy();
            baseJson.remove("fabric:type");
            baseJson.remove("mesh");
            UnbakedModel base = context.deserialize(baseJson, UnbakedModel.class);
            List<Face> faces = new ArrayList<>();
            for (var element : json.getAsJsonArray("mesh")) {
                var face = element.getAsJsonObject();
                List<Vertex> vertices = new ArrayList<>();
                for (var vertex : face.getAsJsonArray("vertices")) {
                    JsonArray data = vertex.getAsJsonArray();
                    if (data.size() != 8) throw new IllegalArgumentException("Mesh vertex needs position, UV and normal");
                    vertices.add(new Vertex(data.get(0).getAsFloat(), data.get(1).getAsFloat(), data.get(2).getAsFloat(),
                            data.get(3).getAsFloat(), data.get(4).getAsFloat(), data.get(5).getAsFloat(), data.get(6).getAsFloat(), data.get(7).getAsFloat()));
                }
                if (vertices.size() != 4) throw new IllegalArgumentException("Mesh faces must have four vertices");
                faces.add(new Face(face.get("texture").getAsString(), List.copyOf(vertices)));
            }
            UnbakedGeometry geometry = (textures, baker, state, debugName) -> {
                var mesh = Renderer.get().mutableMesh();
                var emitter = mesh.emitter();
                var transform = state.transformation().getMatrix();
                var normalTransform = transform.get3x3(new Matrix3f()).invert().transpose();
                for (Face face : faces) {
                    emitter.clear();
                    var material = baker.materials().resolveSlot(textures, face.material(), debugName);
                    for (int i = 0; i < 4; i++) {
                        Vertex vertex = face.vertices().get(i);
                        var position = new Vector3f(vertex.x(), vertex.y(), vertex.z()).sub(0.5f, 0.5f, 0.5f)
                                .mulPosition(transform).add(0.5f, 0.5f, 0.5f);
                        var normal = new Vector3f(vertex.nx(), vertex.ny(), vertex.nz()).mul(normalTransform).normalize();
                        emitter.pos(i, position).normal(i, normal).color(i, -1).uv(i, vertex.u(), vertex.v());
                    }
                    Vertex vertex = face.vertices().getFirst();
                    var normal = new Vector3f(vertex.nx(), vertex.ny(), vertex.nz()).mul(normalTransform);
                    emitter.nominalFace(Direction.getNearest((int) (normal.x * 10000), (int) (normal.y * 10000), (int) (normal.z * 10000), Direction.UP));
                    emitter.materialBake(material, MutableQuadView.BAKE_NORMALIZED).emit();
                }
                return new MeshQuadCollection(mesh.immutableCopy());
            };
            return new WrapperUnbakedModel(base) {
                @Override public UnbakedGeometry geometry() { return geometry; }
            };
        });
    }

    private InlineMeshModel() {}
}
