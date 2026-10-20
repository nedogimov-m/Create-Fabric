package com.simibubi.create.content.contraptions.gantry;

import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;

/** Powered rack/pinion is a real signed kinetic edge; unpowered rack motion remains independent. */
public final class GantryKinetics {
    public static double speedModifier(BlockPos fromPos,BlockState from,BlockPos toPos,BlockState to){
        var diff=toPos.subtract(fromPos);if(diff.distManhattan(BlockPos.ZERO)!=1)return 0;var direction=Direction.getApproximateNearest(diff.getX(),diff.getY(),diff.getZ());
        if(from.getBlock() instanceof GantryShaftBlock&&to.getBlock() instanceof GantryCarriageBlock&&from.getValue(GantryShaftBlock.POWERED)&&to.getValue(GantryCarriageBlock.FACING)==direction)return GantryCarriageBlockEntity.getGantryPinionModifier(from.getValue(GantryShaftBlock.FACING),to.getValue(GantryCarriageBlock.FACING));
        if(to.getBlock() instanceof GantryShaftBlock&&from.getBlock() instanceof GantryCarriageBlock&&to.getValue(GantryShaftBlock.POWERED)&&from.getValue(GantryCarriageBlock.FACING)==direction.getOpposite())return GantryCarriageBlockEntity.getGantryPinionModifier(to.getValue(GantryShaftBlock.FACING),from.getValue(GantryCarriageBlock.FACING));
        return 0;
    }
    private GantryKinetics(){}
}
