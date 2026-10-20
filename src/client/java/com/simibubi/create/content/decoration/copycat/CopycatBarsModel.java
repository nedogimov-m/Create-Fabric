package com.simibubi.create.content.decoration.copycat;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Source helper geometry is retained; only its actual baked material UVs/layer are adapted. */
public final class CopycatBarsModel extends CopycatModel {
    public CopycatBarsModel(BlockStateModel wrapped,SpriteFinder finder){super(wrapped,finder);}
    @Override protected void emitCropped(QuadEmitter out,Mesh source,BlockAndTintGetter level,BlockPos pos,BlockState state,BlockState material,RandomSource random){emitBars(out,source,level,pos,state,material,random);}
    void emitBars(QuadEmitter out,Mesh source,BlockAndTintGetter level,BlockPos pos,BlockState state,BlockState material,RandomSource random){
        var helper=Renderer.get().mutableMesh();wrapped.emitQuads(helper.emitter(),level,pos,state,random,side->false);
        var particle=getModelOf(material).particleMaterial(level,pos,material);var upward=new Material.Baked[]{null};
        source.forEach(quad->{
            if(quad.cullFace()==null&&quad.lightFace()==Direction.UP&&upward[0]==null){
                var sprite=finder.find(quad);
                if(sprite!=null)upward[0]=new Material.Baked(sprite,particle.forceTranslucent()||quad.chunkLayer().translucent());
            }
        });
        boolean vertical=state.getValue(DirectionalBlock.FACING).getAxis()==Direction.Axis.Y;
        helper.immutableCopy().forEach(quad->{
            var side=quad.cullFace();var targetMaterial=side!=null&&(vertical||side.getAxis()==Direction.Axis.Y)&&upward[0]!=null?upward[0]:particle;
            var target=targetMaterial.sprite();var original=finder.find(quad);out.copyFrom(quad);out.ambientOcclusion(TriState.FALSE);
            for(int vertex=0;vertex<4;vertex++){float u=(quad.u(vertex)-original.getU0())/(original.getU1()-original.getU0()),v=(quad.v(vertex)-original.getV0())/(original.getV1()-original.getV0());out.uv(vertex,target.getU(u),target.getV(v));}
            // Changing UV coverage can change native alpha classification even within the same source sprite.
            out.postMaterialBake(targetMaterial).emit();
        });
    }
}
