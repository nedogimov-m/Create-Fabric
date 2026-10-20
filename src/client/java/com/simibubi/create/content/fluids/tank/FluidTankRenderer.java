package com.simibubi.create.content.fluids.tank;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Fluid geometry is extracted once per frame and submitted through the active RenderPearl backend. */
public final class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderer.State> {
    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {}
    public static final class State extends BlockEntityRenderState {
        public Mesh mesh;
        public float fillHeight;
        public boolean translucent;
        int fluidLight;
        public final com.simibubi.create.foundation.model.PartialModelRenderState gauge=new com.simibubi.create.foundation.model.PartialModelRenderState(),dial=new com.simibubi.create.foundation.model.PartialModelRenderState();
        public final java.util.List<Direction> gaugeSides=new java.util.ArrayList<>();
        public float gaugeProgress;public int width;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public void extractRenderState(FluidTankBlockEntity tank, State state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(tank,state,partialTick,cameraPos,overlay); state.mesh=null; state.fillHeight=0;state.gaugeSides.clear();
        if(tank.isController()&&!tank.hasWindows()&&tank.boiler.isActive()){
            state.width=tank.getWidth();state.gaugeProgress=tank.boiler.getGauge(partialTick);
            com.simibubi.create.AllPartialModels.extract(com.simibubi.create.AllPartialModels.BOILER_GAUGE,state.gauge);com.simibubi.create.AllPartialModels.extract(com.simibubi.create.AllPartialModels.BOILER_GAUGE_DIAL,state.dial);
            for(var side:Direction.Plane.HORIZONTAL){
                var box=new net.minecraft.world.phys.AABB(tank.getBlockPos()).move(state.width/2f-0.5f,0,state.width/2f-0.5f).deflate(5f/8);
                box=box.move(side.getStepX()*(state.width/2f+0.25f),0,side.getStepZ()*(state.width/2f+0.25f)).inflate(Math.abs(side.getStepZ())/2f,0.25f,Math.abs(side.getStepX())/2f);
                if(tank.getLevel().noCollision(box))state.gaugeSides.add(side);
            }return;
        }
        if (!tank.isController() || !tank.hasWindows() || tank.getTankInventory().isEmpty()) return;
        float height=tank.getHeight()-0.5f-1f/16, fill=Math.clamp(tank.getFluidLevel(partialTick),0,1)*height;
        if (fill<1f/512) return;
        state.fillHeight=fill;
        var fluid=tank.getTankInventory().getResource().getFluid();
        var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
        int color=net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering.getColor(tank.getTankInventory().getResource(),(net.minecraft.client.renderer.block.BlockAndTintGetter)tank.getLevel(),tank.getBlockPos())|0xFF000000;
        state.translucent=model.layer()==net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT;
        state.fluidLight=fluid.is(net.minecraft.tags.FluidTags.LAVA)?net.minecraft.util.LightCoordsUtil.FULL_BRIGHT:state.lightCoords;
        var mesh=Renderer.get().mutableMesh(); var out=mesh.emitter();
        float min=1f/16+1f/128, max=tank.getWidth()-min, bottom=0.25f+1f/16, top=bottom+fill;
        if(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.isLighterThanAir(tank.getTankInventory().getResource())){ bottom+=height-fill;top+=height-fill; }
        // Tile the original fluid sprites in block units instead of stretching across a large tank.
        for(var face:Direction.values()) {
            float left=min,right=max,low=bottom,high=top;
            if(face.getAxis().isVertical()){ low=min;high=max; }
            for(float u=left;u<right;u+=1) for(float v=low;v<high;v+=1) {
                float u1=Math.min(u+1,right),v1=Math.min(v+1,high);
                float[][] points=switch(face){
                    case UP -> new float[][]{{u,top,v},{u,top,v1},{u1,top,v1},{u1,top,v}};
                    case DOWN -> new float[][]{{u,bottom,v1},{u,bottom,v},{u1,bottom,v},{u1,bottom,v1}};
                    case NORTH -> new float[][]{{u1,v1,min},{u1,v,min},{u,v,min},{u,v1,min}};
                    case SOUTH -> new float[][]{{u,v1,max},{u,v,max},{u1,v,max},{u1,v1,max}};
                    case WEST -> new float[][]{{min,v1,u},{min,v,u},{min,v,u1},{min,v1,u1}};
                    case EAST -> new float[][]{{max,v1,u1},{max,v,u1},{max,v,u},{max,v1,u}};
                };
                float du=u1-u,dv=v1-v;
                for(int i=0;i<4;i++) out.pos(i,points[i][0],points[i][1],points[i][2]).normal(i,face.getStepX(),face.getStepY(),face.getStepZ()).color(i,color);
                out.uv(0,0,0).uv(1,0,dv).uv(2,du,dv).uv(3,du,0).nominalFace(face).cullFace(null).chunkLayer(model.layer()).materialBake(model.stillMaterial(),MutableQuadView.BAKE_NORMALIZED).emit();
            }
        }
        state.mesh=mesh.immutableCopy();
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if(state.mesh!=null) collector.submitBlockModel(pose,ChunkSectionLayerHelper::getMovingBlockRenderType,state.translucent,List.of(),state.mesh,new int[0],state.fluidLight,OverlayTexture.NO_OVERLAY,0);
        for(var side:state.gaugeSides){
            pose.pushPose();pose.translate(state.width/2f,0.5,state.width/2f);pose.rotateDegrees(com.mojang.math.Axis.YP,-side.toYRot()-90);pose.translate(-0.5,-0.5,-0.5);pose.translate(state.width/2f-6f/16,0,0);
            state.gauge.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.translate(0,6f/16,8f/16);pose.rotateDegrees(com.mojang.math.Axis.XP,-145*state.gaugeProgress+90);pose.translate(0,-6f/16,-8f/16);state.dial.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        }
    }
}
