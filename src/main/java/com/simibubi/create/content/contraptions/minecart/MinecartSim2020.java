package com.simibubi.create.content.contraptions.minecart;

import com.simibubi.create.content.contraptions.minecart.capability.MinecartController;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/** Pinned source coupling helpers; the game's actual Old/New behavior is retained, never replaced. */
public final class MinecartSim2020 {
    public static Vec3 predictNextPositionOf(AbstractMinecart cart){return cart.position().add(CartMath.clamp(cart.getDeltaMovement(),1));}
    public static boolean canAddMotion(AbstractMinecart cart){
        if(cart instanceof MinecartFurnace furnace&&furnace.push.horizontalDistanceSqr()>1e-12)return false;
        var controller=MinecartController.get(cart);return controller.isPresent()&&!controller.isStalled()&&!controller.ownsPackedReceipt();
    }
    public static double maxSpeed(AbstractMinecart cart){return cart.level() instanceof ServerLevel server?((com.simibubi.create.foundation.mixin.accessor.MinecartMaxSpeedAccessor)cart).create$getMaxSpeed(server):MinecartController.get(cart).getKnownMaxSpeed();}
    public static RailShape railShape(BlockState state){return state.getBlock() instanceof BaseRailBlock rail?state.getValue(rail.getShapeProperty()):null;}
    public static void moveCartAlongTrack(AbstractMinecart cart,Vec3 forced,BlockPos railPos,BlockState rail){
        if(forced.lengthSqr()==0||!(cart.level() instanceof ServerLevel)||!LoadedLevelAccess.isLoaded(cart.level(),railPos)||!LoadedLevelAccess.isLoaded(cart.level(),BlockPos.containing(cart.position().add(forced))))return;
        var shape=railShape(rail);if(shape==null)return;var previous=cart.getDeltaMovement();cart.fallDistance=0;
        try{
            cart.setDeltaMovement(forced);
            if(cart.getBehavior() instanceof NewMinecartBehavior modern){
                double remaining=forced.horizontalDistance();var position=railPos;var current=rail;int traversed=0;
                // Source hard corrections are at most1.75 blocks; every actual crossed rail stays loaded.
                while(remaining>1e-5&&traversed++<8){double next=modern.stepAlongTrack(position,railShape(current),remaining);if(next>=remaining-1e-8)break;remaining=next;if(remaining<=1e-5)break;position=cart.getCurrentBlockPosOrRailBelow();if(!LoadedLevelAccess.isLoaded(cart.level(),position))break;current=LoadedLevelAccess.blockState(cart.level(),position);if(railShape(current)==null)break;}
            }else if(cart.getBehavior() instanceof OldMinecartBehavior old){
                // Actual native Old.stepAlongTrack is a no-op. Use its real rail projection and native collision move.
                var projected=CartMath.project(forced,getRailVec(shape));cart.move(MoverType.SELF,projected);var position=cart.position();
                if(LoadedLevelAccess.isLoaded(cart.level(),BlockPos.containing(position))){var snapped=old.getPos(position.x,position.y,position.z);if(snapped!=null)cart.setPos(snapped.x,snapped.y,snapped.z);}
            }else cart.move(MoverType.SELF,forced);
        }finally{cart.setDeltaMovement(previous);}
    }
    public static Vec3 getRailVec(RailShape shape){return switch(shape){case ASCENDING_NORTH,ASCENDING_SOUTH,NORTH_SOUTH->new Vec3(0,0,1);case ASCENDING_EAST,ASCENDING_WEST,EAST_WEST->new Vec3(1,0,0);case NORTH_EAST,SOUTH_WEST->new Vec3(1,0,1).normalize();case NORTH_WEST,SOUTH_EAST->new Vec3(1,0,-1).normalize();};}
    private MinecartSim2020(){}
}
