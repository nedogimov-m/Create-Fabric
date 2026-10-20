package com.simibubi.create.test;

import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.simibubi.create.content.trains.CubeParticle;
import com.simibubi.create.content.trains.CubeParticleData;
import java.nio.ByteOrder;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/** Real registered particle factory, native RenderPearl mesh and hot stationary physics branch. */
final class CubeParticleRenderingTest {
    static void verify(ClientGameTestContext context,BlockPos source){
        context.runOnClient(client->{
            var spawn=Vec3.atCenterOf(source).add(-1,1,0);
            var particle=client.particleEngine.createParticle(new CubeParticleData(1,0,1,.25f,30,false),spawn.x,spawn.y,spawn.z,0,.04,0);
            if(!(particle instanceof CubeParticle cube)||cube.getLifetime()<22||cube.getLifetime()>38||cube.getLightCoords(0)!=LightCoordsUtil.FULL_BRIGHT)throw new AssertionError("Actual registered native cube factory/lifetime/full-bright source data missing");
            var state=new QuadParticleRenderState();cube.extract(state,client.gameRenderer.mainCamera(),0);
            if(!state.layers().equals(Set.of(SingleQuadParticle.Layer.TRANSLUCENT)))throw new AssertionError("Actual six-side cube did not use native translucent/OIT particle layer");
            var origin=spawn.subtract(client.gameRenderer.mainCamera().position());
            byte[] snapshot=mesh(state,origin,cube.getQuadSize(0));
            cube.setPos(spawn.x+8,spawn.y+8,spawn.z+8);
            if(!Arrays.equals(snapshot,mesh(state,origin,.25f)))throw new AssertionError("Native extracted cube render state retained mutable particle/world geometry");
            cube.remove();
            var hot=client.particleEngine.createParticle(new CubeParticleData(1,1,1,.2f,100,true),spawn.x+.2,spawn.y,spawn.z+.2,0,0,0);
            var cold=client.particleEngine.createParticle(new CubeParticleData(1,1,1,.2f,100,false),spawn.x+.2,spawn.y,spawn.z+.2,0,0,0);
            if(!(hot instanceof CubeParticle)||!(cold instanceof CubeParticle))throw new AssertionError("Real native hot/cold cube factories were not registered");
            var before=hot.getBoundingBox().getCenter();hot.tick();cold.tick();hot.tick();cold.tick();
            var after=hot.getBoundingBox().getCenter();
            if(Math.hypot(after.x-before.x,after.z-before.z)<1e-6||cold.getBoundingBox().getCenter().distanceToSqr(before)>1e-10)throw new AssertionError("Actual native stationary hot billowing/accessor branch did not move while cold original stayed fixed");
            hot.remove();cold.remove();
        });
    }
    private static byte[] mesh(QuadParticleRenderState state,Vec3 origin,float size){
        var format=DefaultVertexFormat.PARTICLE;
        try(var memory=new ByteBufferBuilder(4096)){
            var builder=new BufferBuilder(memory,PrimitiveTopology.QUADS,format);state.buildLayer(SingleQuadParticle.Layer.TRANSLUCENT,builder);
            try(var data=builder.buildOrThrow()){
                if(data.drawState().vertexCount()!=24||data.drawState().primitiveTopology()!=PrimitiveTopology.QUADS)throw new AssertionError("Actual native cube must supply six real four-vertex faces");
                var bytes=data.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());int stride=format.getVertexSize();
                int pos=format.getElement(DefaultVertexFormat.POSITION_SEMANTIC_NAME).offset(),uv=format.getElement(DefaultVertexFormat.UV0_SEMANTIC_NAME).offset(),color=format.getElement(DefaultVertexFormat.COLOR_SEMANTIC_NAME).offset(),light=format.getElement(DefaultVertexFormat.UV2_SEMANTIC_NAME).offset();
                var faces=new HashSet<String>();
                for(int face=0;face<6;face++){
                    var min=new double[]{Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};var max=new double[]{Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
                    float minU=1,maxU=0,minV=1,maxV=0;
                    for(int vertex=0;vertex<4;vertex++){
                        int offset=(face*4+vertex)*stride;
                        for(int axis=0;axis<3;axis++){float value=bytes.getFloat(offset+pos+axis*4);if(!Float.isFinite(value))throw new AssertionError("Non-finite real native cube vertex");min[axis]=Math.min(min[axis],value);max[axis]=Math.max(max[axis],value);}
                        float u=bytes.getFloat(offset+uv),v=bytes.getFloat(offset+uv+4);if(!Float.isFinite(u)||!Float.isFinite(v)||u<0||u>1||v<0||v>1)throw new AssertionError("Actual loaded cube atlas UVs invalid");minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);
                        if((bytes.get(offset+color)&255)!=255||(bytes.get(offset+color+1)&255)!=0||(bytes.get(offset+color+2)&255)!=255||(bytes.get(offset+color+3)&255)!=255||bytes.getInt(offset+light)!=LightCoordsUtil.FULL_BRIGHT)throw new AssertionError("Actual native cube source RGBA/full-bright vertex payload changed");
                    }
                    if(maxU<=minU||maxV<=minV)throw new AssertionError("Real cube sprite has degenerate native UVs");
                    int fixed=-1;
                    for(int axis=0;axis<3;axis++)if(max[axis]-min[axis]<1e-5){if(fixed!=-1)throw new AssertionError("Degenerate native cube face");fixed=axis;}
                    if(fixed<0)throw new AssertionError("Native cube face is not planar");double coordinate=fixed==0?origin.x:fixed==1?origin.y:origin.z;double displacement=(max[fixed]+min[fixed])*.5-coordinate;
                    if(Math.abs(Math.abs(displacement)-size)>1e-4)throw new AssertionError("Native cube face is not centered on its actual source side");faces.add(fixed+":"+(displacement>0));
                }
                if(faces.size()!=6)throw new AssertionError("Actual native particle duplicated a plane instead of drawing all six cube sides");
                byte[] copy=new byte[bytes.remaining()];bytes.get(copy);return copy;
            }
        }
    }
}
