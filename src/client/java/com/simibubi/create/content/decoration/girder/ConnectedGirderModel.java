package com.simibubi.create.content.decoration.girder;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Source2x2 vertical pole sheet and actual horizontal connector bracket meshes in native chunk rendering. */
public final class ConnectedGirderModel extends WrapperBlockStateModel {
    private final TextureAtlasSprite original,connected;
    private final SpriteFinder finder;
    private ConnectedGirderModel(BlockStateModel wrapped,TextureAtlasSprite original,TextureAtlasSprite connected,SpriteFinder finder){super(wrapped);this.original=original;this.connected=connected;this.finder=finder;}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
        if(!(bake.state().getBlock()instanceof GirderBlock)&&!(bake.state().getBlock()instanceof GirderEncasedShaftBlock))return model;var materials=bake.baker().materials();
        var original=materials.get(new Material(Create.asResource("block/girder_pole_side")),()->"Create source girder pole").sprite();var target=materials.get(new Material(Create.asResource("block/girder_pole_side_connected")),()->"Create source vertical girder sheet").sprite();
        return new ConnectedGirderModel(model,original,target,materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS));
    }));}
    private static boolean pole(BlockState state){return state.getBlock()instanceof GirderBlock&&!state.getValue(GirderBlock.X)&&!state.getValue(GirderBlock.Z);}
    public static int poleTile(BlockAndTintGetter level,BlockPos pos,BlockState state){if(!pole(state))return-1;return(pole(LoadedLevelAccess.blockState(level,pos.above()))?1:0)+(pole(LoadedLevelAccess.blockState(level,pos.below()))?2:0);}
    public static int connections(BlockAndTintGetter level,BlockPos pos,BlockState state){int mask=0;for(var side:Direction.Plane.HORIZONTAL)if(GirderBlock.isConnected(level,pos,state,side))mask|=1<<side.ordinal();return mask;}
    private record Key(ConnectedGirderModel model,Object wrapped,int tile,int connections){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var key=wrapped.createGeometryKey(level,pos,state,random);return key==null?null:new Key(this,key,poleTile(level,pos,state),connections(level,pos,state));}
    @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){
        int tile=poleTile(level,pos,state);if(tile>=0)out.pushTransform(quad->{if(quad.lightFace().getAxis()==Direction.Axis.Y||finder.find(quad)!=original)return true;for(int i=0;i<4;i++){float u=(quad.u(i)-original.getU0())/(original.getU1()-original.getU0()),v=(quad.v(i)-original.getV0())/(original.getV1()-original.getV0());quad.uv(i,connected.getU((u+tile%2)/2),connected.getV((v+tile/2)/2));}return true;});
        try{wrapped.emitQuads(out,level,pos,state,random,cull);}finally{if(tile>=0)out.popTransform();}
        for(var side:Direction.Plane.HORIZONTAL)if(GirderBlock.isConnected(level,pos,state,side)){
            var model=Minecraft.getInstance().getModelManager().getModel(GirderModels.BRACKETS.get(side));if(model==null)throw new IllegalStateException("Native girder bracket not baked: "+side);model.emitQuads(out,level,pos,state,random,cull);
        }
    }
}
