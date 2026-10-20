package com.simibubi.create.content.decoration;

import com.simibubi.create.*;
import com.simibubi.create.foundation.block.connected.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Source scaffold inside/outside/casing and trapdoor contexts on native chunk workers. */
public final class NativeDecorationCTModel extends WrapperBlockStateModel {
    private record Shift(TextureAtlasSprite source,TextureAtlasSprite target,CTSpriteShiftEntry descriptor){}
    private final SpriteFinder finder;private final List<Shift> shifts;private final boolean scaffold;
    private NativeDecorationCTModel(BlockStateModel model,SpriteFinder finder,List<Shift> shifts,boolean scaffold){super(model);this.finder=finder;this.shifts=List.copyOf(shifts);this.scaffold=scaffold;}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
        var state=bake.state();boolean scaffold=state.getBlock() instanceof MetalScaffoldingBlock;
        if(!scaffold&&!state.is(AllBlocks.FRAMED_GLASS_TRAPDOOR)&&!(state.getBlock() instanceof MetalLadderBlock)
                &&state.getBlock()!=AllBlocks.ANDESITE_BARS&&state.getBlock()!=AllBlocks.BRASS_BARS&&state.getBlock()!=AllBlocks.COPPER_BARS)return model;
        var materials=bake.baker().materials();var descriptors=new ArrayList<CTSpriteShiftEntry>();
        if(scaffold){String material=state.is(AllBlocks.ANDESITE_SCAFFOLD)?"andesite":state.is(AllBlocks.BRASS_SCAFFOLD)?"brass":"copper";
            for(var suffix:List.of("","_inside"))descriptors.add(shift("scaffold/"+material+"_scaffold"+suffix,CTSpriteShiftEntry.Mode.HORIZONTAL));
            descriptors.add(shift(material+"_casing",CTSpriteShiftEntry.Mode.OMNIDIRECTIONAL));
        }else if(state.is(AllBlocks.FRAMED_GLASS_TRAPDOOR))descriptors.add(shift("palettes/framed_glass",CTSpriteShiftEntry.Mode.OMNIDIRECTIONAL));
        var shifts=new ArrayList<Shift>();for(var descriptor:descriptors)shifts.add(new Shift(materials.get(new Material(descriptor.original()),()->"Source decoration sprite").sprite(),materials.get(new Material(descriptor.connected()),()->"Source decoration CT sheet").sprite(),descriptor));
        return new NativeDecorationCTModel(model,materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS),shifts,scaffold);
    }));}
    private static CTSpriteShiftEntry shift(String name,CTSpriteShiftEntry.Mode mode){return new CTSpriteShiftEntry(Create.asResource("block/"+name),Create.asResource("block/"+name+"_connected"),mode);}
    private static BlockState appearance(BlockAndTintGetter level,BlockPos pos,BlockPos target,Direction face){var actual=LoadedLevelAccess.blockState(level,target);return actual.getAppearance(level,target,face,LoadedLevelAccess.blockState(level,pos),pos);}
    private static boolean scaffoldConnection(BlockState state,BlockState other){return state.getBlock()==other.getBlock()&&state.getValue(MetalScaffoldingBlock.BOTTOM)&&other.getValue(MetalScaffoldingBlock.BOTTOM);}
    private static boolean connects(BlockAndTintGetter level,BlockPos pos,BlockState state,Direction face,Direction h,Direction v,int dh,int dv,boolean scaffold){
        var target=pos.relative(h,dh).relative(v,dv);var other=appearance(level,pos,target,face);
        if(scaffold){
            if(!scaffoldConnection(state,other))return false;
            if(face.getAxis()!=Direction.Axis.Y)return true;
            var blockingPos=target.relative(face);var blocking=LoadedLevelAccess.blockState(level,blockingPos);
            return !Block.isFaceFull(blocking.getShape(level,blockingPos),face.getOpposite())
                ||!scaffoldConnection(state,LoadedLevelAccess.blockState(level,blockingPos).getAppearance(level,blockingPos,face.getOpposite(),LoadedLevelAccess.blockState(level,pos),pos.relative(face)));
        }
        Direction primary=dh==0?null:dh<0?h.getOpposite():h,secondary=dv==0?null:dv<0?v.getOpposite():v;
        return state.getBlock()==other.getBlock()&&TrainTrapdoorBlock.isConnected(state,other,primary==null?secondary:primary);
    }
    public static OmnidirectionalCT.Context context(BlockAndTintGetter level,BlockPos pos,BlockState state,Direction face){
        boolean scaffold=state.getBlock() instanceof MetalScaffoldingBlock;var basis=OmnidirectionalCT.basis(face);var h=basis.right();var v=basis.up();
        boolean up=connects(level,pos,state,face,h,v,0,1,scaffold),down=connects(level,pos,state,face,h,v,0,-1,scaffold),left=connects(level,pos,state,face,h,v,-1,0,scaffold),right=connects(level,pos,state,face,h,v,1,0,scaffold);
        return new OmnidirectionalCT.Context(up,down,left,right,up&&left&&connects(level,pos,state,face,h,v,-1,1,scaffold),up&&right&&connects(level,pos,state,face,h,v,1,1,scaffold),down&&left&&connects(level,pos,state,face,h,v,-1,-1,scaffold),down&&right&&connects(level,pos,state,face,h,v,1,-1,scaffold));
    }
    private List<OmnidirectionalCT.Context> contexts(BlockAndTintGetter level,BlockPos pos,BlockState state){return shifts.isEmpty()?List.of():Arrays.stream(Direction.values()).map(face->context(level,pos,state,face)).toList();}
    private record Key(NativeDecorationCTModel owner,Object wrapped,List<OmnidirectionalCT.Context> faces){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var key=wrapped.createGeometryKey(level,pos,state,random);return key==null?null:new Key(this,key,contexts(level,pos,state));}
    @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){
        var contexts=contexts(level,pos,state);out.pushTransform(quad->{
            quad.chunkLayer(ChunkSectionLayer.CUTOUT);var source=finder.find(quad);Direction face=quad.lightFace();
            for(var shift:shifts){if(source!=shift.source||scaffold&&(face.getAxis()==Direction.Axis.Y)!=(shift.descriptor.mode()==CTSpriteShiftEntry.Mode.OMNIDIRECTIONAL))continue;
                int size=shift.descriptor.sheetSize(),tile=shift.descriptor.textureIndex(contexts.get(face.ordinal()));
                for(int vertex=0;vertex<4;vertex++){float u=(quad.u(vertex)-source.getU0())/(source.getU1()-source.getU0()),v=(quad.v(vertex)-source.getV0())/(source.getV1()-source.getV0());quad.uv(vertex,shift.target.getU((u+tile%size)/size),shift.target.getV((v+tile/size)/size));}break;
            }return true;
        });try{wrapped.emitQuads(out,level,pos,state,random,cull);}finally{out.popTransform();}
    }
}
