package com.simibubi.create.content.kinetics.gantry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/** The source shaft's entire part/power/facing mesh rotates through the native submission path. */
public final class GantryShaftRenderer implements BlockEntityRenderer<GantryShaftBlockEntity,KineticRenderState> {
    private final KineticBlockEntityRenderer shaft;
    public GantryShaftRenderer(BlockEntityRendererProvider.Context context){shaft=new KineticBlockEntityRenderer(context);}
    @Override public KineticRenderState createRenderState(){return shaft.createRenderState();}
    @Override public void extractRenderState(GantryShaftBlockEntity entity,KineticRenderState state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){shaft.extractRenderState(entity,state,partial,camera,overlay);}
    @Override public void submit(KineticRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){shaft.submit(state,pose,collector,camera);}
}
