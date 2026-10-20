package com.simibubi.create.content.trains.bogey;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;

/** Pinned small/large shafts, wheels, drive pin/piston and scrolling belt, extracted into native meshes. */
public class StandardBogeyRenderer implements BogeyRenderer {
    public static final float FRAME_SCALE=1-1/512f;
    private record LayeredMesh(Mesh original,Mesh cutout){}
    private static final Map<Object,LayeredMesh> LAYERS=new LinkedHashMap<>();
    private static float radians(float angle){return(float)Math.toRadians(angle);}
    private static PartialModelRenderState cutout(Object key,PartialModelRenderState state){
        var cached=LAYERS.get(key);if(cached==null||cached.original()!=state.mesh){var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();state.mesh.forEach(quad->{emitter.copyFrom(quad).chunkLayer(ChunkSectionLayer.CUTOUT).emit();});cached=new LayeredMesh(state.mesh,mesh.immutableCopy());LAYERS.put(key,cached);}state.mesh=cached.cutout();state.translucent=false;return state;
    }
    private static PartialModelRenderState partial(ExtraModelKey<BlockStateModel> key){var state=new PartialModelRenderState();AllPartialModels.extract(key,state);return cutout(key,state);}
    private static PartialModelRenderState shaft(Direction.Axis axis){var state=new PartialModelRenderState();var block=AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,axis);AllPartialModels.extract(block,state);return cutout(block,state);}
    @Override public void extract(CompoundTag data,float wheelAngle,float partialTick,BogeyRenderState state,boolean inContraption){
        float angle=radians(wheelAngle);
        for(int i=0;i<2;i++)state.add("primary_shaft_"+i,shaft(Direction.Axis.Z),new Matrix4f().translate(-.5f,.25f,-i).translate(.5f,.5f,.5f).rotateZ(angle).translate(-.5f,-.5f,-.5f));
    }
    public static final class Small extends StandardBogeyRenderer {
        @Override public void extract(CompoundTag data,float wheelAngle,float partialTick,BogeyRenderState state,boolean inContraption){
            super.extract(data,wheelAngle,partialTick,state,inContraption);state.add("frame",partial(AllPartialModels.BOGEY_FRAME),new Matrix4f().scale(FRAME_SCALE));
            for(int side:new int[]{1,-1})state.add("small_wheels_"+side,partial(AllPartialModels.SMALL_BOGEY_WHEELS),new Matrix4f().translate(0,12/16f,side).rotateX(radians(wheelAngle)));
        }
    }
    public static final class Large extends StandardBogeyRenderer {
        public static final float BELT_RADIUS_PX=5,BELT_RADIUS_IN_UV_SPACE=BELT_RADIUS_PX/16;
        @Override public void extract(CompoundTag data,float wheelAngle,float partialTick,BogeyRenderState state,boolean inContraption){
            super.extract(data,wheelAngle,partialTick,state,inContraption);float angle=radians(wheelAngle);
            for(int i=0;i<2;i++)state.add("secondary_shaft_"+i,shaft(Direction.Axis.X),new Matrix4f().translate(-.5f,.25f,.5f-2*i).translate(.5f,.5f,.5f).rotateX(angle).translate(-.5f,-.5f,-.5f));
            state.add("drive",partial(AllPartialModels.BOGEY_DRIVE),new Matrix4f().scale(FRAME_SCALE));
            var belt=partial(AllPartialModels.BOGEY_DRIVE_BELT);var atlas=Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            var original=atlas.getSprite(Create.asResource("block/bogey/belt"));var target=atlas.getSprite(Create.asResource("block/bogey/belt_scroll"));
            float phase=BELT_RADIUS_IN_UV_SPACE*angle;phase-=(float)Math.floor(phase);state.beltScroll=phase*(target.getV1()-target.getV0())*.5f;
            var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();
            // Source Catnip shiftUVScrolling preserves pixel span: it offsets atlas UVs, without stretching16px to32px.
            belt.mesh.forEach(quad->{emitter.copyFrom(quad);for(int i=0;i<4;i++)emitter.uv(i,quad.u(i)-original.getU0()+target.getU0(),quad.v(i)-original.getV0()+target.getV0()+state.beltScroll);emitter.emit();});belt.mesh=mesh.immutableCopy();
            state.add("drive_belt",belt,new Matrix4f().scale(FRAME_SCALE));
            state.pistonOffset=.25f*(float)Math.sin(angle);state.add("drive_piston",partial(AllPartialModels.BOGEY_PISTON),new Matrix4f().translate(0,0,state.pistonOffset));
            state.add("large_wheels",partial(AllPartialModels.LARGE_BOGEY_WHEELS),new Matrix4f().translate(0,1,0).rotateX(angle));
            state.add("drive_pin",partial(AllPartialModels.BOGEY_PIN),new Matrix4f().translate(0,1,0).rotateX(angle).translate(0,.25f,0).rotateX(-angle));
        }
    }
}
