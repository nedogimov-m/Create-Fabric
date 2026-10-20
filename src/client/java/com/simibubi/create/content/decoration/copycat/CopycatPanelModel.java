package com.simibubi.create.content.decoration.copycat;

import com.simibubi.create.AllBlocks;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class CopycatPanelModel extends CopycatModel {
    public CopycatPanelModel(BlockStateModel wrapped,SpriteFinder finder){super(wrapped,finder);}
    @Override protected void emitCropped(QuadEmitter out,Mesh source,BlockAndTintGetter level,BlockPos pos,BlockState state,BlockState material,RandomSource random){
        var facing=state.getValue(CopycatPanelBlock.FACING);
        if(CopycatSpecialCases.isTrapdoorMaterial(material)){source.forEach(quad->out.copyFrom(quad).emit());return;}
        if(CopycatSpecialCases.isBarsMaterial(material)){var helper=AllBlocks.COPYCAT_BARS.defaultBlockState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING,facing);var model=getModelOf(helper);if(!(model instanceof CopycatBarsModel bars))throw new IllegalStateException("Actual Copycat Bars helper model was not registered");bars.emitBars(out,source,level,pos,helper,material,random);return;}
        var normal=new Vec3(facing.getStepX(),facing.getStepY(),facing.getStepZ());
        for(boolean front:new boolean[]{true,false}){float contract=16-(front?1:2);var box=new AABB(BlockPos.ZERO).contract(normal.x*contract/16,normal.y*contract/16,normal.z*contract/16);if(!front)box=box.move(normal.scale(14/16f));var crop=box;var shift=normal.scale(front?0:-13/16f);
            source.forEach(quad->{if(front&&quad.lightFace()==facing||!front&&quad.lightFace()==facing.getOpposite())return;out.copyFrom(quad);CopycatGeometry.cropAndMove(out,finder.find(quad),crop,shift);out.emit();});
        }
    }
}
