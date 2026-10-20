package com.simibubi.create.content.contraptions.piston;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/** Only the native shaft rotates; the piston casing remains the ordinary world block model. */
public final class MechanicalPistonRenderer implements BlockEntityRenderer<MechanicalPistonBlockEntity,KineticRenderState> {
    private final KineticBlockEntityRenderer shaft;
    private final BlockModelResolver models;
    private final BlockDisplayContext display=BlockDisplayContext.create();
    public MechanicalPistonRenderer(BlockEntityRendererProvider.Context context){shaft=new KineticBlockEntityRenderer(context);models=context.blockModelResolver();}
    @Override public KineticRenderState createRenderState(){return shaft.createRenderState();}
    @Override public void extractRenderState(MechanicalPistonBlockEntity piston,KineticRenderState state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        shaft.extractRenderState(piston,state,partial,camera,overlay);models.update(state.model,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,state.axis),display);
    }
    @Override public void submit(KineticRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){shaft.submit(state,pose,collector,camera);}
}
