package com.simibubi.create.content.fluids.tank;

import com.simibubi.create.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Predicate;

/** Original rectangle connected textures and interior-face culling, using immutable block entity render data. */
public final class FluidTankModel extends WrapperBlockStateModel {
    private final SpriteFinder finder;
    private final Map<TextureAtlasSprite,TextureAtlasSprite> shifts;
    private FluidTankModel(BlockStateModel model,SpriteFinder finder,Map<TextureAtlasSprite,TextureAtlasSprite> shifts){super(model);this.finder=finder;this.shifts=Map.copyOf(shifts);}
    public static void register(){
        ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
            if(!(bake.state().getBlock() instanceof FluidTankBlock tank))return model;
            var materials=bake.baker().materials(); var shifts=new HashMap<TextureAtlasSprite,TextureAtlasSprite>();
            for(String name:tank.creative?new String[]{"creative_fluid_tank","creative_casing"}:new String[]{"fluid_tank","fluid_tank_top","fluid_tank_inner"}) {
                var source=materials.get(new Material(Create.asResource("block/"+name)),()->"Fluid tank connected textures").sprite();
                var dest=materials.get(new Material(Create.asResource("block/"+name+"_connected")),()->"Fluid tank connected textures").sprite(); shifts.put(source,dest);
            }
            return new FluidTankModel(model,materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS),shifts);
        }));
    }
    private boolean connects(BlockAndTintGetter level,BlockPos pos,BlockState state,BlockPos other){
        return level.getBlockState(other).is(state.getBlock())
                &&level.getBlockEntityRenderData(pos) instanceof FluidTankBlockEntity.TankRenderData a
                &&level.getBlockEntityRenderData(other) instanceof FluidTankBlockEntity.TankRenderData b&&a.controller().equals(b.controller());
    }
    private record FaceData(boolean culled,int tile){}
    private List<FaceData> context(BlockAndTintGetter level,BlockPos pos,BlockState state){
        var data=new ArrayList<FaceData>();
        for(var face:Direction.values()){
            Direction h=face.getAxis()==Direction.Axis.X?Direction.SOUTH:Direction.WEST,v=face.getAxis().isHorizontal()?Direction.UP:Direction.NORTH;
            if(face.getAxisDirection()==Direction.AxisDirection.POSITIVE)h=h.getOpposite();if(face==Direction.DOWN){v=v.getOpposite();h=h.getOpposite();}
            boolean up=connects(level,pos,state,pos.relative(v)),down=connects(level,pos,state,pos.relative(v.getOpposite()));
            boolean right=connects(level,pos,state,pos.relative(h)),left=connects(level,pos,state,pos.relative(h.getOpposite()));
            int x=left&&right?2:left?3:right?1:0,y=up&&down?1:up?2:down?0:3;
            data.add(new FaceData(face.getAxis().isHorizontal()&&connects(level,pos,state,pos.relative(face)),x+y*4));
        }
        return List.copyOf(data);
    }
    private record Key(FluidTankModel model,Object wrapped,List<FaceData> data){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var key=wrapped.createGeometryKey(level,pos,state,random);return key==null?null:new Key(this,key,context(level,pos,state));}
    @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cullTest){
        var data=context(level,pos,state);
        out.pushTransform(quad->{
            if(quad.cullFace()!=null&&data.get(quad.cullFace().ordinal()).culled())return false;
            var source=finder.find(quad);var dest=shifts.get(source);if(dest==null)return true;int tile=data.get(quad.lightFace().ordinal()).tile();
            for(int i=0;i<4;i++){float u=(quad.u(i)-source.getU0())/(source.getU1()-source.getU0()),v=(quad.v(i)-source.getV0())/(source.getV1()-source.getV0());quad.uv(i,dest.getU((u+tile%4)/4),dest.getV((v+tile/4)/4));}return true;
        });
        wrapped.emitQuads(out,level,pos,state,random,face->cullTest.test(face)&&!data.get(face.ordinal()).culled());out.popTransform();
    }
}
