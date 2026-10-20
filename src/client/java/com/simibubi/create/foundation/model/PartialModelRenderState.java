package com.simibubi.create.foundation.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import java.util.List;

/** Extracted Fabric geometry; submit never consults live models or world state. */
public final class PartialModelRenderState extends BlockModelRenderState {
    public Mesh mesh;
    public boolean translucent;

    @Override public void clear() { super.clear(); mesh = null; translucent = false; }

    @Override public boolean isEmpty() { return mesh == null ? super.isEmpty() : mesh.size() == 0; }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline) {
        if (mesh == null) super.submit(pose, collector, light, overlay, outline);
        else collector.submitBlockModel(pose, ChunkSectionLayerHelper::getMovingBlockRenderType, translucent, List.of(), mesh,
                EMPTY_TINTS, light, overlay, outline);
    }
}
