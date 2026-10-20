package com.simibubi.create.content.contraptions.chassis;

import com.simibubi.create.Create;
import java.util.*;
import java.util.function.Predicate;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Original linear chassis omnidirectional sheets and axis-dependent context orientation. */
public final class ChassisCTModel extends WrapperBlockStateModel {
    private static final List<String> SPRITES=List.of("linear_chassis_side","secondary_linear_chassis_side","linear_chassis_end","linear_chassis_end_sticky");
    private final SpriteFinder finder;
    private final List<TextureAtlasSprite> originals,connected;
    private ChassisCTModel(BlockStateModel model,SpriteFinder finder,List<TextureAtlasSprite> originals,List<TextureAtlasSprite> connected){super(model);this.finder=finder;this.originals=originals;this.connected=connected;}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
        if(!(bake.state().getBlock() instanceof LinearChassisBlock))return model;var materials=bake.baker().materials();var originals=new ArrayList<TextureAtlasSprite>();var connected=new ArrayList<TextureAtlasSprite>();
        for(var name:SPRITES){originals.add(materials.get(new Material(Create.asResource("block/"+name)),()->"Chassis connected textures").sprite());connected.add(materials.get(new Material(Create.asResource("block/"+name+"_connected")),()->"Chassis connected textures").sprite());}
        return new ChassisCTModel(model,materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS),List.copyOf(originals),List.copyOf(connected));
    }));}
    private static boolean same(BlockState state,BlockState other){return state.getBlock()==other.getBlock()&&state.getValue(AbstractChassisBlock.AXIS)==other.getValue(AbstractChassisBlock.AXIS);}
    private static boolean connects(BlockAndTintGetter level,BlockPos pos,BlockState state,BlockPos otherPos,Direction face){
        var other=level.getBlockState(otherPos);if(!same(state,other))return false;if(face.getAxis()!=state.getValue(AbstractChassisBlock.AXIS))return true;
        var blockerPos=otherPos.relative(face);var blocker=level.getBlockState(blockerPos);return !same(state,blocker)||!Block.isFaceFull(blocker.getShape(level,blockerPos),face.getOpposite());
    }
    private static boolean connects(BlockAndTintGetter level,BlockPos pos,BlockState state,Direction face,Direction h,Direction v,int x,int y){return connects(level,pos,state,pos.relative(h,x).relative(v,y),face);}
    public static int textureIndex(BlockAndTintGetter level,BlockPos pos,BlockState state,Direction face){
        var axis=state.getValue(AbstractChassisBlock.AXIS);boolean end=face.getAxis()==axis;
        Direction v=end?(face.getAxis().isHorizontal()?Direction.UP:Direction.NORTH):Direction.get(Direction.AxisDirection.POSITIVE,axis);
        Direction h=!end&&axis.isHorizontal()?(face.getAxis().isHorizontal()?Direction.DOWN:axis==Direction.Axis.X?Direction.NORTH:Direction.EAST):face.getAxis()==Direction.Axis.X?Direction.SOUTH:Direction.WEST;
        if(face.getAxisDirection()==Direction.AxisDirection.POSITIVE)h=h.getOpposite();if(face==Direction.DOWN){v=v.getOpposite();h=h.getOpposite();}
        boolean reverse=end&&axis.isHorizontal()&&face.getAxisDirection()==Direction.AxisDirection.POSITIVE||!end&&axis.isHorizontal()&&face==Direction.DOWN;
        int sh=!end&&axis==Direction.Axis.X&&face.getAxis().isHorizontal()||reverse?-1:1,sv=reverse?-1:1;
        boolean up=connects(level,pos,state,face,h,v,0,sv),down=connects(level,pos,state,face,h,v,0,-sv),left=connects(level,pos,state,face,h,v,-sh,0),right=connects(level,pos,state,face,h,v,sh,0);
        boolean tl=up&&left&&connects(level,pos,state,face,h,v,-sh,sv),tr=up&&right&&connects(level,pos,state,face,h,v,sh,sv),bl=down&&left&&connects(level,pos,state,face,h,v,-sh,-sv),br=down&&right&&connects(level,pos,state,face,h,v,sh,-sv);
        int x=(up?1:0)+(down?2:0),y=(left?1:0)+(right?2:0),borders=(up?0:1)+(down?0:1)+(left?0:1)+(right?0:1);
        if(borders==0){x+=(tr?1:0)+(tl?2:0);y+=(br?2:0)+(bl?1:0);}
        if(borders==1){if(!right&&(tl||bl)){y=4;x=-1+(bl?1:0)+(tl?2:0);}if(!left&&(tr||br)){y=5;x=-1+(br?1:0)+(tr?2:0);}if(!down&&(tl||tr)){y=6;x=-1+(tl?1:0)+(tr?2:0);}if(!up&&(bl||br)){y=7;x=-1+(bl?1:0)+(br?2:0);}}
        if(borders==2&&(up&&left&&tl||down&&left&&bl||up&&right&&tr||down&&right&&br))x+=3;return x+8*y;
    }
    private static List<Integer> indices(BlockAndTintGetter level,BlockPos pos,BlockState state){return Arrays.stream(Direction.values()).map(face->textureIndex(level,pos,state,face)).toList();}
    private record GeometryKey(ChassisCTModel model,Object wrappedKey,List<Integer> indices){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var key=wrapped.createGeometryKey(level,pos,state,random);return key==null?null:new GeometryKey(this,key,indices(level,pos,state));}
    @Override public void emitQuads(QuadEmitter emitter,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cullTest){
        var indices=indices(level,pos,state);emitter.pushTransform(quad->{var original=finder.find(quad);int source=originals.indexOf(original);if(source<0)return true;var target=connected.get(source);int tile=indices.get(quad.lightFace().ordinal());for(int i=0;i<4;i++){float u=(quad.u(i)-original.getU0())/(original.getU1()-original.getU0()),v=(quad.v(i)-original.getV0())/(original.getV1()-original.getV0());quad.uv(i,target.getU((u+tile%8)/8),target.getV((v+tile/8)/8));}return true;});
        wrapped.emitQuads(emitter,level,pos,state,random,cullTest);emitter.popTransform();
    }
}
