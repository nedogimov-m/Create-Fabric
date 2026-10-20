package com.simibubi.create.content.equipment.toolbox;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

public final class ToolboxRenderer implements BlockEntityRenderer<ToolboxBlockEntity,ToolboxRenderer.State>{
    public ToolboxRenderer(BlockEntityRendererProvider.Context context){}
    public static final class State extends BlockEntityRenderState{public ToolBoxVisual.Geometry geometry;}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(ToolboxBlockEntity box,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){BlockEntityRenderer.super.extractRenderState(box,state,partial,camera,overlay);state.geometry=ToolBoxVisual.extract(box,partial,false);}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){ToolBoxVisual.submit(state.geometry,pose,collector,state.lightCoords,false);}
}
