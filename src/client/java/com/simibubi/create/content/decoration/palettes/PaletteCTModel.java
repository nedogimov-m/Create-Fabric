package com.simibubi.create.content.decoration.palettes;
import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
/** Original layered Kryppers/pillar rectangle/cap omnidirectional contexts on native chunk worker models. */
public final class PaletteCTModel extends WrapperBlockStateModel {
    private final SpriteFinder finder;private final List<TextureAtlasSprite> originals,connected;private final boolean pillar;
    private PaletteCTModel(BlockStateModel wrapped,SpriteFinder finder,List<TextureAtlasSprite> originals,List<TextureAtlasSprite> connected,boolean pillar){super(wrapped);this.finder=finder;this.originals=originals;this.connected=connected;this.pillar=pillar;}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
        var id=BuiltInRegistries.BLOCK.getKey(bake.state().getBlock());if(!id.getNamespace().equals(Create.ID))return model;String path=id.getPath();boolean pillar=path.endsWith("_pillar"),layered=path.startsWith("layered_");if(!pillar&&!layered)return model;String family=pillar?path.substring(0,path.length()-7):path.substring(8);if(Arrays.stream(AllPaletteStoneTypes.values()).noneMatch(stone->stone.name().equalsIgnoreCase(family)))return model;
        var materials=bake.baker().materials();var originals=new ArrayList<TextureAtlasSprite>();var connected=new ArrayList<TextureAtlasSprite>();for(var part:List.of(pillar?"pillar":"layered","cap")){String texture="block/palettes/stone_types/"+part+"/"+family+"_cut_"+part;originals.add(materials.get(new Material(Create.asResource(texture)),()->"Pinned stone palette source sprite").sprite());connected.add(materials.get(new Material(Create.asResource(texture+"_connected")),()->"Pinned stone palette connected sheet").sprite());}
        return new PaletteCTModel(model,materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS),List.copyOf(originals),List.copyOf(connected),pillar);
    }));}
    private static boolean baseConnection(BlockState state,BlockState other,boolean pillar){return other.getBlock()==state.getBlock()&&(!pillar||other.getValue(LayeredBlock.AXIS)==state.getValue(LayeredBlock.AXIS));}
    private static boolean connects(BlockAndTintGetter world,BlockPos pos,BlockState state,Direction face,Direction h,Direction v,int sh,int sv,boolean pillar){
        var otherPos=pos.relative(h,sh).relative(v,sv);var other=world.getBlockState(otherPos).getAppearance(world,otherPos,face,world.getBlockState(pos),pos);if(!baseConnection(state,other,pillar))return false;
        if(!pillar||face.getAxis()==state.getValue(LayeredBlock.AXIS)){var blockerPos=otherPos.relative(face);var blocker=world.getBlockState(blockerPos);if(Block.isFaceFull(blocker.getShape(world,blockerPos),face.getOpposite())&&blocker.getAppearance(world,blockerPos,face.getOpposite(),world.getBlockState(pos),pos.relative(face)).getBlock()==state.getBlock())return false;}
        if(pillar){if(world.getBlockState(pos).getBlock() instanceof CopycatBlock||world.getBlockState(otherPos).getBlock() instanceof CopycatBlock)return true;var axis=state.getValue(LayeredBlock.AXIS);Direction primary=sh==0?null:sh<0?h.getOpposite():h,secondary=sv==0?null:sv<0?v.getOpposite():v;if(primary!=null&&primary.getAxis()!=axis&&!ConnectedPillarBlock.getConnection(state,primary))return false;if(secondary!=null&&secondary.getAxis()!=axis&&(!ConnectedPillarBlock.getConnection(state,secondary)||!ConnectedPillarBlock.getConnection(other,secondary.getOpposite())))return false;}
        return true;
    }
    public static int textureIndex(BlockAndTintGetter world,BlockPos pos,BlockState state,Direction face){
        boolean pillar=state.getBlock() instanceof ConnectedPillarBlock;var axis=pillar?state.getValue(LayeredBlock.AXIS):Direction.Axis.Y;Direction v=face.getAxis().isHorizontal()?Direction.UP:Direction.NORTH,h=face.getAxis()==Direction.Axis.X?Direction.SOUTH:Direction.WEST;
        if(pillar&&axis!=Direction.Axis.Y){if(face.getAxis().isVertical()&&axis==Direction.Axis.X){v=v.getClockWise();h=h.getClockWise();}else if(face.getAxis()!=axis&&!face.getAxis().isVertical()){v=Direction.fromAxisAndDirection(axis,axis==Direction.Axis.X?Direction.AxisDirection.POSITIVE:Direction.AxisDirection.NEGATIVE);h=Direction.fromAxisAndDirection(Direction.Axis.Y,face.getAxisDirection());}}
        if(face.getAxisDirection()==Direction.AxisDirection.POSITIVE)h=h.getOpposite();if(face==Direction.DOWN){v=v.getOpposite();h=h.getOpposite();}
        boolean reverse=pillar&&(axis==Direction.Axis.X?face.getAxisDirection()==Direction.AxisDirection.NEGATIVE&&face.getAxis()!=Direction.Axis.X:axis==Direction.Axis.Z&&face!=Direction.NORTH&&face.getAxisDirection()!=Direction.AxisDirection.POSITIVE);int sh=reverse?-1:1,sv=reverse&&!(axis==Direction.Axis.X&&face==Direction.NORTH)&&!(axis==Direction.Axis.Z&&face==Direction.WEST)?-1:1;
        boolean left=connects(world,pos,state,face,h,v,-sh,0,pillar),right=connects(world,pos,state,face,h,v,sh,0,pillar);boolean end=face.getAxis()==axis;if(!end&&!pillar)return !right&&!left?0:!right?3:!left?2:1;
        boolean up=connects(world,pos,state,face,h,v,0,sv,pillar),down=connects(world,pos,state,face,h,v,0,-sv,pillar);if(!end){int x=left&&right?2:left?3:right?1:0,y=up&&down?1:up?2:down?0:3;return x+4*y;}
        boolean tl=up&&left&&connects(world,pos,state,face,h,v,-sh,sv,pillar),tr=up&&right&&connects(world,pos,state,face,h,v,sh,sv,pillar),bl=down&&left&&connects(world,pos,state,face,h,v,-sh,-sv,pillar),br=down&&right&&connects(world,pos,state,face,h,v,sh,-sv,pillar);int x=(up?1:0)+(down?2:0),y=(left?1:0)+(right?2:0),borders=(up?0:1)+(down?0:1)+(left?0:1)+(right?0:1);if(borders==0){x+=(tr?1:0)+(tl?2:0);y+=(br?2:0)+(bl?1:0);}if(borders==1){if(!right&&(tl||bl)){y=4;x=-1+(bl?1:0)+(tl?2:0);}if(!left&&(tr||br)){y=5;x=-1+(br?1:0)+(tr?2:0);}if(!down&&(tl||tr)){y=6;x=-1+(tl?1:0)+(tr?2:0);}if(!up&&(bl||br)){y=7;x=-1+(bl?1:0)+(br?2:0);}}if(borders==2&&(up&&left&&tl||down&&left&&bl||up&&right&&tr||down&&right&&br))x+=3;return x+8*y;
    }
    private static List<Integer> indices(BlockAndTintGetter world,BlockPos pos,BlockState state){return Arrays.stream(Direction.values()).map(face->textureIndex(world,pos,state,face)).toList();}
    private record GeometryKey(PaletteCTModel owner,Object wrappedKey,List<Integer> indices){}
    @Override public Object createGeometryKey(BlockAndTintGetter world,BlockPos pos,BlockState state,RandomSource random){var key=wrapped.createGeometryKey(world,pos,state,random);return key==null?null:new GeometryKey(this,key,indices(world,pos,state));}
    @Override public void emitQuads(QuadEmitter emitter,BlockAndTintGetter world,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cullTest){var tiles=indices(world,pos,state);emitter.pushTransform(quad->{var sprite=finder.find(quad);int source=originals.indexOf(sprite);if(source<0)return true;var target=connected.get(source);int sheet=source==1?8:pillar?4:2,tile=tiles.get(quad.lightFace().ordinal());for(int i=0;i<4;i++){float u=(quad.u(i)-sprite.getU0())/(sprite.getU1()-sprite.getU0()),v=(quad.v(i)-sprite.getV0())/(sprite.getV1()-sprite.getV0());quad.uv(i,target.getU((u+tile%sheet)/sheet),target.getV((v+tile/sheet)/sheet));}return true;});wrapped.emitQuads(emitter,world,pos,state,random,cullTest);emitter.popTransform();}
}
