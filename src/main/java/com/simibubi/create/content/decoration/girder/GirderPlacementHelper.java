package com.simibubi.create.content.decoration.girder;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Bounded source placement assistance, using native BlockItem placement/cost instead of a world-state injection. */
public final class GirderPlacementHelper {
    public static final int DEFAULT_RANGE=12;
    public static boolean canExtendToward(BlockState state,Direction side){
        if(state.getBlock()instanceof GirderBlock){boolean x=state.getValue(GirderBlock.X),z=state.getValue(GirderBlock.Z);return !x&&!z?side.getAxis()==Direction.Axis.Y:x&&z||side.getAxis()==(x?Direction.Axis.X:Direction.Axis.Z);}
        return state.getBlock()instanceof GirderEncasedShaftBlock&&side.getAxis()!=Direction.Axis.Y&&side.getAxis()!=state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS);
    }
    public static BlockPos target(Level level,BlockState state,BlockPos pos,BlockHitResult hit){
        var directions=new ArrayList<Direction>();for(var side:Direction.values())if(canExtendToward(state,side))directions.add(side);directions.sort(Comparator.comparingDouble(side->Vec3.atCenterOf(pos).relative(side,.5).distanceToSqr(hit.getLocation())));
        for(var side:directions){int count=0;var next=pos.relative(side);while(count<DEFAULT_RANGE&&GirderBlock.available(level,next)&&canExtendToward(LoadedLevelAccess.blockState(level,next),side)){count++;next=next.relative(side);}if(count>=DEFAULT_RANGE||!GirderBlock.available(level,next)||!LoadedLevelAccess.blockState(level,next).canBeReplaced())continue;return next;}return null;
    }
    public static InteractionResult place(Player player,InteractionHand hand,Level level,BlockState state,BlockPos pos,BlockHitResult hit){
        if(player.isSpectator()||!player.mayBuild()||player.isShiftKeyDown()||!level.mayInteract(player,pos)||!(player.getItemInHand(hand).getItem()instanceof BlockItem item))return InteractionResult.PASS;
        var target=target(level,state,pos,hit);if(target==null)return InteractionResult.PASS;var delta=target.subtract(pos);var direction=Direction.getNearest(delta.getX(),delta.getY(),delta.getZ(),Direction.UP);var support=target.relative(direction.getOpposite());
        if(!level.mayInteract(player,target))return InteractionResult.FAIL;
        var placementHit=new BlockHitResult(Vec3.atCenterOf(support).relative(direction,.5),direction,support,false);
        return item.place(new BlockPlaceContext(new UseOnContext(player,hand,placementHit)));
    }
    private GirderPlacementHelper(){}
}
