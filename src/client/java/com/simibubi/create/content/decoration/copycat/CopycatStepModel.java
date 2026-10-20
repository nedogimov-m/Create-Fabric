package com.simibubi.create.content.decoration.copycat;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.*;

public final class CopycatStepModel extends CopycatModel {
    public CopycatStepModel(BlockStateModel wrapped,SpriteFinder finder){super(wrapped,finder);}
    @Override protected void emitCropped(QuadEmitter out,Mesh source,BlockAndTintGetter level,BlockPos pos,BlockState state,BlockState material,RandomSource random){
        var facing=state.getValue(CopycatStepBlock.FACING);boolean upper=state.getValue(CopycatStepBlock.HALF)==Half.TOP;var normal=new Vec3(facing.getStepX(),facing.getStepY(),facing.getStepZ());var initial=new AABB(BlockPos.ZERO).contract(-normal.x*.75,.75,-normal.z*.75);
        for(boolean top:new boolean[]{true,false})for(boolean front:new boolean[]{true,false}){var box=initial;if(front)box=box.move(normal.scale(-.75));if(top)box=box.move(0,.75,0);var move=front?normal.scale(.5):Vec3.ZERO;if(top!=upper)move=move.add(0,upper?.5:-.5,0);var crop=box;var shift=move;
            source.forEach(quad->{var face=quad.lightFace();if(front&&face==facing||!front&&face==facing.getOpposite()||!top&&face==Direction.UP||top&&face==Direction.DOWN)return;out.copyFrom(quad);CopycatGeometry.cropAndMove(out,finder.find(quad),crop,shift);out.emit();});
        }
    }
}
