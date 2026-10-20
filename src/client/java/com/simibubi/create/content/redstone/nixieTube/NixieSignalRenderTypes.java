package com.simibubi.create.content.redstone.nixieTube;

import com.mojang.renderpearl.api.pipeline.*;
import com.simibubi.create.Create;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** Native RenderPearl source glow: SRC_ALPHA/ONE, full bright, no diffuse shading or culling. */
public final class NixieSignalRenderTypes {
    // Native26.3 EYES is TRANSLUCENT and BlendFunction.ADDITIVE is ONE/ONE.
    // Neither has the original Create additive equation; retain its exact factors explicitly.
    public static final RenderPipeline ADDITIVE_PIPELINE=RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET,RenderPipelines.EYES_SNIPPET)
            .withLocation(Create.asResource("pipeline/nixie_signal_additive"))
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA,BlendFactor.ONE)))
            .withDepthStencilState(DepthStencilState.DEFAULT).withCull(false).build());
    public static final RenderPipeline CORE_PIPELINE=RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET,RenderPipelines.EYES_SNIPPET)
            .withLocation(Create.asResource("pipeline/nixie_signal_core"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT).withCull(true).build());
    public static final RenderType ADDITIVE=RenderType.create("create_nixie_signal_additive",
        RenderSetup.builder(ADDITIVE_PIPELINE).withTexture("Sampler0",TextureAtlas.LOCATION_BLOCKS).sortOnUpload().createRenderSetup());
    public static final RenderType CORE=RenderType.create("create_nixie_signal_core",
        RenderSetup.builder(CORE_PIPELINE).withTexture("Sampler0",TextureAtlas.LOCATION_BLOCKS).sortOnUpload().createRenderSetup());
    /** Called by model registration during client initialization, before shader resource reload. */
    public static void register(){}
    private NixieSignalRenderTypes(){}
}
