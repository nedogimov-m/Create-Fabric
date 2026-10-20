package com.simibubi.create.content.decoration.bracket;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

public final class BracketRenderer implements BlockEntityRenderer<FluidPipeBlockEntity,BracketRenderer.State>{
    public static final class State extends BlockEntityRenderState{public final PartialModelRenderState bracket=new PartialModelRenderState();}
    public BracketRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(FluidPipeBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);state.bracket.clear();var bracket=entity.bracketBehaviour().getBracket();if(bracket!=null)AllPartialModels.extract(bracket,state.bracket);}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){if(!state.bracket.isEmpty())state.bracket.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);}
}
