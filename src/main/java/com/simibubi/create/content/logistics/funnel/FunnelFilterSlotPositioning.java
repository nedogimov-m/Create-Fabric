package com.simibubi.create.content.logistics.funnel;

import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class FunnelFilterSlotPositioning {
    public static boolean activeSide(BlockState state,Direction side){var facing=AbstractFunnelBlock.getFunnelFacing(state);if(facing==null)return false;if(facing.getAxis().isVertical())return side.getAxis().isHorizontal();return state.getBlock() instanceof BeltFunnelBlock&&state.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.EXTENDED?side==Direction.UP:side==facing;}
    public static Vec3 position(BlockState state,Direction side){var facing=AbstractFunnelBlock.getFunnelFacing(state);double y,z;Direction rotation=side;
        if(state.getBlock() instanceof BeltFunnelBlock){switch(state.getValue(BeltFunnelBlock.SHAPE)){case EXTENDED->{y=15.5;z=13;rotation=facing;}case PULLING,PUSHING->{y=12;z=8.675;}default->{y=13;z=7.5;}}}else if(facing.getAxis().isVertical()){y=facing==Direction.DOWN?14:2;z=15.5;}else{y=12.2;z=8.55;}
        double d=(z-8)/16;return new Vec3(0.5+rotation.getStepX()*d,y/16,0.5+rotation.getStepZ()*d);
    }
    public static boolean testHit(BlockState state,BlockPos pos,BlockHitResult hit){return activeSide(state,hit.getDirection())&&position(state,hit.getDirection()).distanceTo(hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)))<0.25;}
    private FunnelFilterSlotPositioning(){}
}
