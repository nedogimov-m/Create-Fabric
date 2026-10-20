package com.simibubi.create.content.fluids.pipes;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class TransparentStraightPipeRenderer implements BlockEntityRenderer<StraightPipeBlockEntity,TransparentStraightPipeRenderer.State>{
    public TransparentStraightPipeRenderer(BlockEntityRendererProvider.Context context){}
    public static final class State extends BlockEntityRenderState{public final com.simibubi.create.foundation.model.PartialModelRenderState bracket=new com.simibubi.create.foundation.model.PartialModelRenderState();public final List<Stream> streams=new ArrayList<>();}
    public record Stream(Mesh mesh,boolean translucent,int light){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(StraightPipeBlockEntity pipe,State state,float partialTick,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(pipe,state,partialTick,camera,overlay);state.streams.clear();state.bracket.clear();var bracketBehaviour=pipe.bracketBehaviour();var bracket=bracketBehaviour==null?null:bracketBehaviour.getBracket();if(bracket!=null)com.simibubi.create.AllPartialModels.extract(bracket,state.bracket);
        var transport=pipe.transport();if(transport==null)return;
        for(var side:Direction.values()){
            var flow=transport.getFlow(side);if(flow==null||flow.fluid.isEmpty())continue;
            float progress=flow.previousProgress+(flow.progress-flow.previousProgress)*partialTick;
            if(progress==0)continue;
            var fluid=flow.fluid.variant();var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
            int color=FluidVariantRendering.getColor(fluid,(net.minecraft.client.renderer.block.BlockAndTintGetter)pipe.getLevel(),pipe.getBlockPos())|0xFF000000;
            var mesh=Renderer.get().mutableMesh();var out=mesh.emitter();
            float[] min={5f/16,5f/16,5f/16},max={11f/16,11f/16,11f/16};int axis=side.getAxis().ordinal();
            float from=flow.inbound?0.5f:0,to=flow.inbound?0.5f-progress*0.5f:progress*0.5f;
            min[axis]=0.5f+Math.min(from*side.getAxisDirection().getStep(),to*side.getAxisDirection().getStep());max[axis]=0.5f+Math.max(from*side.getAxisDirection().getStep(),to*side.getAxisDirection().getStep());
            for(var face:Direction.values()){
                if(face.getAxis()==side.getAxis()&&progress>=1)continue;
                float[][] p=switch(face){
                    case UP->new float[][]{{min[0],max[1],min[2]},{min[0],max[1],max[2]},{max[0],max[1],max[2]},{max[0],max[1],min[2]}};
                    case DOWN->new float[][]{{min[0],min[1],max[2]},{min[0],min[1],min[2]},{max[0],min[1],min[2]},{max[0],min[1],max[2]}};
                    case NORTH->new float[][]{{max[0],max[1],min[2]},{max[0],min[1],min[2]},{min[0],min[1],min[2]},{min[0],max[1],min[2]}};
                    case SOUTH->new float[][]{{min[0],max[1],max[2]},{min[0],min[1],max[2]},{max[0],min[1],max[2]},{max[0],max[1],max[2]}};
                    case WEST->new float[][]{{min[0],max[1],min[2]},{min[0],min[1],min[2]},{min[0],min[1],max[2]},{min[0],max[1],max[2]}};
                    case EAST->new float[][]{{max[0],max[1],max[2]},{max[0],min[1],max[2]},{max[0],min[1],min[2]},{max[0],max[1],min[2]}};
                };
                for(int i=0;i<4;i++)out.pos(i,p[i][0],p[i][1],p[i][2]).normal(i,face.getStepX(),face.getStepY(),face.getStepZ()).color(i,color);
                out.uvUnitSquare().nominalFace(face).chunkLayer(model.layer()).materialBake(face.getAxis()==side.getAxis()?model.stillMaterial():model.flowingMaterial(),MutableQuadView.BAKE_NORMALIZED);out.emit();
            }
            state.streams.add(new Stream(mesh.immutableCopy(),model.layer()==net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT,fluid.getFluid().is(net.minecraft.tags.FluidTags.LAVA)?net.minecraft.util.LightCoordsUtil.FULL_BRIGHT:state.lightCoords));
        }
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){if(!state.bracket.isEmpty())state.bracket.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);for(var stream:state.streams)collector.submitBlockModel(pose,ChunkSectionLayerHelper::getMovingBlockRenderType,stream.translucent(),List.of(),stream.mesh(),new int[0],stream.light(),OverlayTexture.NO_OVERLAY,0);}
}
