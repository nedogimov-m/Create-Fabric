package com.simibubi.create.content.decoration.palettes;
import com.simibubi.create.*;
import com.simibubi.create.content.decoration.RoofBlockCTBehaviour;
import com.simibubi.create.foundation.block.connected.OmnidirectionalCT;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
/** Pinned window/pane and copper roof contexts on actual native Fabric chunk-worker models. */
public final class WindowRoofCTModel extends WrapperBlockStateModel {
 private enum Mode{OMNI,HORIZONTAL,VERTICAL,RECTANGLE,ROOF}
 private record Shift(TextureAtlasSprite source,TextureAtlasSprite target,Mode mode,int sheet,boolean sideOnly){}
 private record Definition(String source,String target,Mode mode,int sheet,boolean sideOnly){}
 private final SpriteFinder finder;private final List<Shift> shifts;private final boolean pane,weathered,roof;private final ChunkSectionLayer layer;
 private WindowRoofCTModel(BlockStateModel model,SpriteFinder finder,List<Shift> shifts,boolean pane,boolean weathered,boolean roof,ChunkSectionLayer layer){super(model);this.finder=finder;this.shifts=List.copyOf(shifts);this.pane=pane;this.weathered=weathered;this.roof=roof;this.layer=layer;}
 private static Definition def(String original,String target,Mode mode,boolean sideOnly){return new Definition("block/"+original,"block/"+target+"_connected",mode,mode==Mode.OMNI?8:mode==Mode.RECTANGLE||mode==Mode.ROOF?4:2,sideOnly);}
 public static void register(){
  for(var block:AllPaletteBlocks.blocks().values())if(block instanceof ConnectedGlassBlock)FluidRenderingRegistry.setBlockTransparency(block,true);
  ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
   var state=bake.state();var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());if(!id.getNamespace().equals(Create.ID))return model;String name=id.getPath();boolean roof=AllBlocks.COPPER_SHINGLES.blocks().contains(state.getBlock())||AllBlocks.COPPER_TILES.blocks().contains(state.getBlock());if(!roof&&!AllPaletteBlocks.blocks().containsKey(name))return model;
   boolean pane=state.getBlock() instanceof GlassPaneBlock,weathered=name.startsWith("weathered_iron_window");String base=pane?name.substring(0,name.length()-5):name;var defs=new ArrayList<Definition>();
   if(roof){String prefix=name.startsWith("waxed_")?name.substring(6):name;prefix=prefix.startsWith("exposed_")?"exposed_":prefix.startsWith("weathered_")?"weathered_":prefix.startsWith("oxidized_")?"oxidized_":"";String family=name.contains("shingle")?"copper_shingles":"copper_tiles";defs.add(def("copper/"+prefix+"copper_roof_top","copper/"+prefix+family+"_top",Mode.ROOF,false));}
   else if(base.equals("horizontal_framed_glass")){defs.add(def("palettes/framed_glass","palettes/horizontal_framed_glass",Mode.HORIZONTAL,!pane));if(!pane)defs.add(def("palettes/framed_glass","palettes/framed_glass",Mode.OMNI,false));}
   else if(base.equals("vertical_framed_glass"))defs.add(def("palettes/framed_glass","palettes/vertical_framed_glass",Mode.VERTICAL,!pane));
   else if(base.equals("framed_glass"))defs.add(def("palettes/framed_glass","palettes/framed_glass",Mode.OMNI,false));
   else if(base.equals("industrial_iron_window"))defs.add(def("palettes/industrial_iron_window","palettes/industrial_iron_window",Mode.RECTANGLE,!pane));
   else if(weathered)for(int i=1;i<=4;i++)defs.add(def("palettes/weathered_iron_window","palettes/weathered_iron_window_"+i,Mode.RECTANGLE,true));
   else if(!base.equals("tiled_glass"))defs.add(def("palettes/"+base,"palettes/"+base,Mode.VERTICAL,!pane));
   var materials=bake.baker().materials();var shifts=new ArrayList<Shift>();for(var def:defs){var original=materials.get(new Material(Create.asResource(def.source)),()->"Pinned window/roof source sprite").sprite();var target=materials.get(new Material(Create.asResource(def.target)),()->"Pinned window/roof connected sheet").sprite();shifts.add(new Shift(original,target,def.mode,def.sheet,def.sideOnly));}
   var layer=roof?ChunkSectionLayer.SOLID:base.equals("birch_window")||weathered?ChunkSectionLayer.TRANSLUCENT:ChunkSectionLayer.CUTOUT;return new WindowRoofCTModel(model,materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS),shifts,pane,weathered,roof,layer);
  }));
 }
 private boolean connects(BlockAndTintGetter level,BlockPos pos,BlockState state,Direction face,Direction h,Direction v,int x,int y){var otherPos=pos.relative(h,x).relative(v,y);return pane?level.getBlockState(otherPos).getAppearance(level,otherPos,face,level.getBlockState(pos),pos).getBlock()==state.getBlock():OmnidirectionalCT.connects(level,pos,state,otherPos,face);}
 private OmnidirectionalCT.Context context(BlockAndTintGetter level,BlockPos pos,BlockState state,Direction face){var basis=OmnidirectionalCT.basis(face);var h=basis.right();var v=basis.up();int sh=pane&&face.getAxisDirection()==Direction.AxisDirection.NEGATIVE?-1:1;boolean up=connects(level,pos,state,face,h,v,0,1),down=connects(level,pos,state,face,h,v,0,-1),left=connects(level,pos,state,face,h,v,-sh,0),right=connects(level,pos,state,face,h,v,sh,0);return new OmnidirectionalCT.Context(up,down,left,right,up&&left&&connects(level,pos,state,face,h,v,-sh,1),up&&right&&connects(level,pos,state,face,h,v,sh,1),down&&left&&connects(level,pos,state,face,h,v,-sh,-1),down&&right&&connects(level,pos,state,face,h,v,sh,-1));}
 public static int index(OmnidirectionalCT.Context c,String mode){return switch(Mode.valueOf(mode)){case OMNI->c.textureIndex();case HORIZONTAL->!c.right()&&!c.left()?0:!c.right()?3:!c.left()?2:1;case VERTICAL->(c.up()?1:0)+(c.down()?2:0);case RECTANGLE->{int x=c.left()&&c.right()?2:c.left()?3:c.right()?1:0,y=c.up()&&c.down()?1:c.up()?2:c.down()?0:3;yield x+4*y;}case ROOF->RoofBlockCTBehaviour.roofIndex(c);};}
 private List<Integer> keys(BlockAndTintGetter level,BlockPos pos,BlockState state){if(roof)return List.of(RoofBlockCTBehaviour.textureIndex(level,pos,state));var result=new ArrayList<Integer>();for(var face:Direction.values()){var c=context(level,pos,state,face);for(var mode:Mode.values())result.add(roof&&mode==Mode.ROOF?RoofBlockCTBehaviour.textureIndex(level,pos,state):index(c,mode.name()));}return List.copyOf(result);}
 private record Key(WindowRoofCTModel owner,Object wrappedKey,List<Integer> contexts){}
 @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){if(weathered)return null;var key=wrapped.createGeometryKey(level,pos,state,random);return key==null?null:new Key(this,key,keys(level,pos,state));}
 @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){var indices=keys(level,pos,state);out.pushTransform(quad->{quad.chunkLayer(layer);var sprite=finder.find(quad);var face=quad.lightFace();if(roof&&face!=Direction.UP)return true;Shift selected=null;if(weathered){if(face.getAxis().isVertical())return true;selected=shifts.get(random.nextInt(shifts.size()));if(sprite!=selected.source)return true;}else for(var shift:shifts)if(sprite==shift.source&&(!shift.sideOnly||face.getAxis().isHorizontal())){selected=shift;break;}if(selected==null)return true;int tile=roof?indices.getFirst():indices.get(face.ordinal()*Mode.values().length+selected.mode.ordinal());var target=selected.target;int sheet=selected.sheet;for(int vertex=0;vertex<4;vertex++){float u=(quad.u(vertex)-sprite.getU0())/(sprite.getU1()-sprite.getU0()),v=(quad.v(vertex)-sprite.getV0())/(sprite.getV1()-sprite.getV0());quad.uv(vertex,target.getU((u+tile%sheet)/sheet),target.getV((v+tile/sheet)/sheet));}return true;});try{wrapped.emitQuads(out,level,pos,state,random,cull);}finally{out.popTransform();}}
}
