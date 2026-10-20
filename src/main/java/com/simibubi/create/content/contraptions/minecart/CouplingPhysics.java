package com.simibubi.create.content.contraptions.minecart;

import com.simibubi.create.content.contraptions.minecart.capability.MinecartController;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/** Source soft/hard constraint solving, on genuine loaded endpoints and the actual native movement behavior. */
public final class CouplingPhysics {
    public static void tick(Level level){CouplingHandler.forEachLoadedCoupling(level,pair->tickCoupling(level,pair));}
    public static void tickCoupling(Level level,Couple<MinecartController> controllers){
        if(!controllers.getFirst().isPresent()||!controllers.getSecond().isPresent())return;var carts=controllers.map(MinecartController::cart);
        if(level.tickRateManager().isEntityFrozen(carts.getFirst())&&level.tickRateManager().isEntityFrozen(carts.getSecond()))return;
        if(controllers.getFirst().nativeBaseIsInactive()||controllers.getSecond().nativeBaseIsInactive()||controllers.getFirst().missingMountedRoot()||controllers.getSecond().missingMountedRoot())return;
        float length=controllers.getFirst().getCouplingLength(true);softCollisionStep(level,carts,length);if(!level.isClientSide())hardCollisionStep(level,carts,length);
    }
    public static void hardCollisionStep(Level level,Couple<AbstractMinecart> carts,double length){
        if(!MinecartSim2020.canAddMotion(carts.get(false))&&MinecartSim2020.canAddMotion(carts.get(true)))carts=carts.swap();boolean firstLoop=true;
        for(boolean current:new boolean[]{true,false,true}){
            var cart=carts.get(current);var other=carts.get(!current);float stress=(float)(length-cart.position().distanceTo(other.position()));if(Math.abs(stress)<1/8f)continue;
            var railPos=cart.getCurrentBlockPosOrRailBelow();if(!LoadedLevelAccess.isLoaded(level,railPos))continue;var rail=LoadedLevelAccess.blockState(level,railPos);var shape=MinecartSim2020.railShape(rail);
            var pos=cart.position();var link=other.position().subtract(pos);float magnitude=firstLoop?-stress/2:-stress;if(!MinecartSim2020.canAddMotion(cart))magnitude/=2;
            var correction=shape==null?link.normalize().scale(magnitude):followLinkOnRail(link,pos,magnitude,MinecartSim2020.getRailVec(shape)).subtract(pos);correction=CartMath.clamp(correction,Math.min(1.75,MinecartSim2020.maxSpeed(cart)));
            if(!LoadedLevelAccess.isLoaded(level,BlockPos.containing(pos.add(correction))))continue;
            if(shape!=null)MinecartSim2020.moveCartAlongTrack(cart,correction,railPos,rail);else{cart.move(MoverType.SELF,correction);cart.setDeltaMovement(cart.getDeltaMovement().scale(.95));}firstLoop=false;
        }
    }
    public static void softCollisionStep(Level level,Couple<AbstractMinecart> carts,double length){
        var speed=carts.map(MinecartSim2020::maxSpeed);var canAdd=carts.map(MinecartSim2020::canAddMotion);var motion=carts.map(cart->CartMath.clamp(cart.getDeltaMovement(),1));var next=carts.map(MinecartSim2020::predictNextPositionOf);
        Couple<RailShape> shapes=carts.mapWithContext((cart,current)->{var pos=BlockPos.containing(next.get(current));if(!LoadedLevelAccess.isLoaded(level,pos))return null;var state=LoadedLevelAccess.blockState(level,pos);if(MinecartSim2020.railShape(state)==null&&LoadedLevelAccess.isLoaded(level,pos.below()))state=LoadedLevelAccess.blockState(level,pos.below());return MinecartSim2020.railShape(state);});
        float stress=(float)(length-next.getFirst().distanceTo(next.getSecond()));if(Mth.equal(stress,0))return;
        for(boolean current:new boolean[]{true,false}){
            var pos=next.get(current);var link=next.get(!current).subtract(pos);float magnitude=-stress/2;if(canAdd.get(current)!=canAdd.get(!current))magnitude=!canAdd.get(current)?0:magnitude*2;if(!canAdd.get(current))continue;
            var shape=shapes.get(current);var correction=shape==null?link.normalize().scale(magnitude):followLinkOnRail(link,pos,magnitude,MinecartSim2020.getRailVec(shape)).subtract(pos);motion.set(current,motion.get(current).add(CartMath.clamp(correction,speed.get(current))));
        }
        for(boolean current:new boolean[]{true,false})carts.get(current).setDeltaMovement(CartMath.clamp(motion.get(current),speed.get(current)));
    }
    public static Vec3 followLinkOnRail(Vec3 link,Vec3 cart,float reduce,Vec3 railAxis){double dot=railAxis.dot(link);if(Double.isNaN(dot)||dot==0||reduce==0)return cart;var axis=railAxis.scale(-Math.signum(dot));var intersection=CartMath.intersectSphere(cart,axis,cart.add(link),link.length()-reduce);return intersection==null?cart.add(CartMath.project(link,axis)):intersection;}
    private CouplingPhysics(){}
}
