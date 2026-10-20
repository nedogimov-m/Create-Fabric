package com.simibubi.create.foundation.fluid;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** Tiled fluid geometry, captured during extraction without retaining a world or mutable handler. */
public final class FluidBoxRenderState {
    public Mesh mesh;
    private boolean translucent;
    private int light;
    public void clear(){mesh=null;}
    public void extract(FluidVariant fluid,Level level,BlockPos pos,int light,float x0,float y0,float z0,float x1,float y1,float z1,boolean flowingSides){
        clear();if(fluid.isBlank()||x1<=x0||y1<=y0||z1<=z0)return;
        var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        int color=FluidVariantRendering.getColor(fluid,(BlockAndTintGetter)level,pos)|0xFF000000;
        this.light=Math.max(light&0xffff,FluidVariantAttributes.getLuminance(fluid)<<4)|(light&0xffff0000);
        translucent=model.layer()==ChunkSectionLayer.TRANSLUCENT;
        var output=Renderer.get().mutableMesh();var out=output.emitter();
        for(var face:Direction.values()){
            float left=face.getAxis()==Direction.Axis.X?z0:x0,right=face.getAxis()==Direction.Axis.X?z1:x1;
            float low=face.getAxis().isVertical()?z0:y0,high=face.getAxis().isVertical()?z1:y1;
            for(float u=left;u<right;u+=1)for(float v=low;v<high;v+=1){
                float endU=Math.min(u+1,right),endV=Math.min(v+1,high);
                float[][] p=switch(face){
                    case UP->new float[][]{{u,y1,v},{u,y1,endV},{endU,y1,endV},{endU,y1,v}};
                    case DOWN->new float[][]{{u,y0,endV},{u,y0,v},{endU,y0,v},{endU,y0,endV}};
                    case NORTH->new float[][]{{endU,endV,z0},{endU,v,z0},{u,v,z0},{u,endV,z0}};
                    case SOUTH->new float[][]{{u,endV,z1},{u,v,z1},{endU,v,z1},{endU,endV,z1}};
                    case WEST->new float[][]{{x0,endV,u},{x0,v,u},{x0,v,endU},{x0,endV,endU}};
                    case EAST->new float[][]{{x1,endV,endU},{x1,v,endU},{x1,v,u},{x1,endV,u}};
                };
                for(int i=0;i<4;i++)out.pos(i,p[i][0],p[i][1],p[i][2]).normal(i,face.getStepX(),face.getStepY(),face.getStepZ()).color(i,color);
                out.uv(0,0,0).uv(1,0,endV-v).uv(2,endU-u,endV-v).uv(3,endU-u,0).nominalFace(face).cullFace(null).chunkLayer(model.layer())
                    .materialBake(flowingSides&&!face.getAxis().isVertical()?model.flowingMaterial():model.stillMaterial(),MutableQuadView.BAKE_NORMALIZED).emit();
            }
        }
        mesh=output.immutableCopy();
    }
    public void submit(PoseStack pose,SubmitNodeCollector collector){if(mesh!=null)collector.submitBlockModel(pose,ChunkSectionLayerHelper::getMovingBlockRenderType,translucent,List.of(),mesh,new int[0],light,OverlayTexture.NO_OVERLAY,0);}
}
