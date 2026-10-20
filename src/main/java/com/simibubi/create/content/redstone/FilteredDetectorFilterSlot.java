package com.simibubi.create.content.redstone;

import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Original sided sample positions for directed detectors. */
public final class FilteredDetectorFilterSlot {
    private final boolean bottom;
    public FilteredDetectorFilterSlot(boolean bottom){this.bottom=bottom;}
    public boolean activeSide(BlockState state,Direction side){
        var target=DirectedDirectionalBlock.getTargetDirection(state);if(side==target)return false;if(side==target.getOpposite())return true;
        if(target.getAxis().isHorizontal())return side==Direction.UP||side==Direction.DOWN&&bottom;
        if(target==Direction.UP)side=side.getOpposite();if(!bottom)return side==state.getValue(DirectedDirectionalBlock.FACING);return side.getAxis()==state.getValue(DirectedDirectionalBlock.FACING).getClockWise().getAxis();
    }
    public Vec3 position(Direction side){return new Vec3(.5+side.getStepX()*7.5/16,.5+side.getStepY()*7.5/16,.5+side.getStepZ()*7.5/16);}
    public boolean testHit(BlockState state,BlockPos pos,BlockHitResult hit){return activeSide(state,hit.getDirection())&&position(hit.getDirection()).distanceTo(hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)))<.25;}
}
