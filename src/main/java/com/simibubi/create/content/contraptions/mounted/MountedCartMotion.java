package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.minecart.*;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.foundation.mixin.accessor.MinecartFurnaceAccessor;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Pinned mounted orientation, rotation pause/stall and coal feeding against the actual native carts. */
public final class MountedCartMotion {
    private static final Map<OrientedContraptionEntity,Float> TARGET_YAW=new WeakHashMap<>();
    private static final Map<MountedContraption,List<java.lang.ref.WeakReference<AbstractMinecart>>> ATTACHED=new WeakHashMap<>();
    public static boolean updateOrientation(OrientedContraptionEntity entity){
        if(!(entity.getContraption() instanceof MountedContraption structure))throw new IllegalArgumentException("Actual mounted source type required");
        AbstractMinecart cart=entity.getVehicle() instanceof AbstractMinecart actual?actual:null;
        if(cart==null){
            if(!entity.level().isClientSide()&&entity.getMountedCartId()!=null&&entity.level() instanceof ServerLevel server&&server.getEntity(entity.getMountedCartId()) instanceof AbstractMinecart candidate){
                var controller=CapabilityMinecartController.getIfPresent(server,candidate.getUUID());
                if(controller!=null&&Objects.equals(controller.getMountedRoot(),entity.getUUID())&&!controller.ownsPackedReceipt())entity.startRiding(candidate,true,true);
            }
            if(!(entity.getVehicle() instanceof AbstractMinecart)){entity.setStalled(true);return false;}
            cart=(AbstractMinecart)entity.getVehicle();
        }
        var controller=CapabilityMinecartController.getIfPresent(entity.level(),cart.getUUID());
        if(controller==null||controller.ownsPackedReceipt()||!entity.level().isClientSide()&&!Objects.equals(controller.getMountedRoot(),entity.getUUID())){entity.setStalled(true);return false;}
        var coupled=entity.getCouplingId()==null?null:entity.getCoupledCartsIfPresent();
        if(entity.getCouplingId()!=null&&coupled==null){entity.setStalled(true);return false;}
        if(coupled!=null){
            var difference=coupled.getFirst().cart().position().subtract(coupled.getSecond().cart().position());
            float yaw=(float)Math.toDegrees(Math.atan2(difference.z,difference.x));float pitch=(float)Math.toDegrees(Math.atan2(difference.y,difference.horizontalDistance()));
            if(entity.getCouplingId().equals(cart.getUUID())){pitch=-pitch;yaw+=180;}entity.setAngles(yaw,pitch);
            for(var actual:coupled.map(MinecartController::cart))attach(structure,actual);return true;
        }
        attach(structure,cart);
        if(structure.rotationMode==CartAssemblerBlockEntity.CartMovementMode.ROTATION_LOCKED||entity.isStalled())return true;
        var motion=cart.getDeltaMovement().normalize();var railPos=cart.getCurrentBlockPosOrRailBelow();
        if(LoadedLevelAccess.isLoaded(entity.level(),railPos)){var rail=MinecartSim2020.railShape(LoadedLevelAccess.blockState(entity.level(),railPos));if(rail!=null)motion=CartMath.project(motion,MinecartSim2020.getRailVec(rail));}
        float yaw=-entity.getViewYRot(1);float target=TARGET_YAW.getOrDefault(entity,yaw);
        if(motion.lengthSqr()>0){target=270+(float)Math.toDegrees(Math.atan2(motion.z,motion.x));TARGET_YAW.put(entity,target);}
        float maximum=(float)(motion.length()*12/Math.max(1,entity.getBoundingBox().getXsize()/6));float difference=Mth.wrapDegrees(target-yaw);
        var displacement=cart.position().subtract(cart.xo,cart.yo,cart.zo);
        if(Math.abs(Math.abs(difference)-180)<1e-5&&displacement.lengthSqr()>0){float hint=Mth.wrapDegrees(270+(float)Math.toDegrees(Math.atan2(displacement.z,displacement.x))-yaw);if(hint!=0)difference=Math.copySign(180,hint);}
        yaw+=Mth.clamp(difference,-maximum,maximum);boolean rotating=Math.abs(Mth.wrapDegrees(target-yaw))>=1;if(!rotating)yaw=target;
        entity.setAngles(yaw,entity.getViewXRot(1));return !rotating||structure.rotationMode!=CartAssemblerBlockEntity.CartMovementMode.ROTATE_PAUSED;
    }
    private static void attach(MountedContraption structure,AbstractMinecart cart){
        var set=ATTACHED.computeIfAbsent(structure,key->new ArrayList<>());set.removeIf(reference->reference.get()==null);if(set.stream().noneMatch(reference->reference.get()==cart)){set.add(new java.lang.ref.WeakReference<>(cart));structure.addExtraInventories(cart);}
    }
    public static void afterActors(OrientedContraptionEntity entity){
        if(entity.level().isClientSide())return;
        var pair=entity.getCouplingId()==null?null:entity.getCoupledCartsIfPresent();
        var carts=pair==null?entity.getVehicle() instanceof AbstractMinecart cart?List.of(cart):List.<AbstractMinecart>of():List.of(pair.getFirst().cart(),pair.getSecond().cart());
        for(var cart:carts){var controller=CapabilityMinecartController.getIfPresent(entity.level(),cart.getUUID());if(controller==null)continue;controller.setStalledExternally(entity.isStalled());if(!entity.isStalled())feedFurnace(entity,cart);}
    }
    private static void feedFurnace(OrientedContraptionEntity owner,AbstractMinecart cart){
        if(!(cart instanceof MinecartFurnace furnace)||owner.getContraption()==null||!owner.hasOwnedStructure()||Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)return;
        var accessor=(MinecartFurnaceAccessor)furnace;int fuel=accessor.create$getFuel();var railPos=cart.getCurrentBlockPosOrRailBelow();
        if(LoadedLevelAccess.isLoaded(owner.level(),railPos)&&MinecartSim2020.railShape(LoadedLevelAccess.blockState(owner.level(),railPos))!=null&&fuel>1)cart.setDeltaMovement(cart.getDeltaMovement().normalize());
        if(fuel>=5)return;var items=owner.getContraption().getStorage().getFuelItems();if(items==null)return;
        // The native int fuel assignment shares the transaction's no-callback commit; source coal
        // cannot reappear from rollback after fuel has already become an actual cart value.
        try(var transaction=Transaction.openOuter()){
            boolean extracted=false;
            for(var view:items){if(view.isResourceBlank())continue;var variant=view.getResource();if(variant.getItem()!=Items.COAL&&variant.getItem()!=Items.CHARCOAL)continue;if(view.extract(variant,1,transaction)==1){extracted=true;break;}}
            if(!extracted)return;
            // The root storage's final-commit observers may publish. Install the actual native fuel
            // before those callbacks, and restore it if the enclosing transaction aborts.
            FUEL_TRANSFER.update(furnace,accessor,fuel,transaction);transaction.commit();
        }
    }
    private static final FuelTransfer FUEL_TRANSFER=new FuelTransfer();
    private static final class FuelTransfer extends net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant<Map<MinecartFurnace,Integer>>{
        private final Map<MinecartFurnace,Integer> active=new IdentityHashMap<>();
        @Override protected Map<MinecartFurnace,Integer> createSnapshot(){var copy=new IdentityHashMap<MinecartFurnace,Integer>();for(var cart:active.keySet())copy.put(cart,((MinecartFurnaceAccessor)cart).create$getFuel());return copy;}
        @Override protected void readSnapshot(Map<MinecartFurnace,Integer> snapshot){for(var cart:active.keySet())((MinecartFurnaceAccessor)cart).create$setFuel(snapshot.getOrDefault(cart,active.get(cart)));active.clear();}
        void update(MinecartFurnace cart,MinecartFurnaceAccessor accessor,int before,Transaction transaction){active.putIfAbsent(cart,before);updateSnapshots(transaction);accessor.create$setFuel(before+3600);}
        @Override protected void onFinalCommit(){active.clear();}
    }
    private MountedCartMotion(){}
}
