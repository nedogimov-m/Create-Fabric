package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.content.contraptions.minecart.capability.MinecartController;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

/** All gates derive from actual persisted receipt/reservation/retirement custody, never fake empty cargo. */
public final class NativeCartLifecycle {
    public static MinecartController attached(AbstractMinecart cart){return ((AttachmentTarget)cart).getAttached(MinecartController.ATTACHMENT);}
    public static boolean inactive(AbstractMinecart cart){var controller=attached(cart);return controller!=null&&controller.nativeBaseIsInactive();}
    public static boolean suspendNativeTick(AbstractMinecart cart){
        var controller=attached(cart);if(controller==null||!controller.nativeBaseIsInactive())return false;
        cart.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if(!cart.level().isClientSide()&&!controller.ownsPackedReceipt()&&controller.baseCustody().filter(c->c.kind()==MinecartController.BaseCustodyKind.RETIRED).isPresent()&&!controller.hasPendingRefunds())cart.discard();
        return true;
    }
    /** Intercept both container and generic vehicle destruction before any vanilla drop can run. */
    public static boolean beforeDestroy(AbstractMinecart cart){
        var controller=attached(cart);if(controller==null)return false;
        if(!controller.nativeBaseIsInactive())return protectActiveValue(cart,controller);
        if(cart.level().isClientSide())return true;
        if(controller.ownsPackedReceipt())CartReceiptPublication.dropPackedReceipt(controller);
        else if(!controller.hasPendingRefunds())cart.discard();
        return true;
    }
    /** Chunk unload saves the real attachment normally. Destroying removal must publish packed value first. */
    public static boolean beforeRemove(AbstractMinecart cart,Entity.RemovalReason reason){
        var controller=attached(cart);if(controller==null||!reason.shouldDestroy())return false;
        if(controller.ownsPackedReceipt()){if(!cart.level().isClientSide())CartReceiptPublication.dropPackedReceipt(controller);return true;}
        return !controller.nativeBaseIsInactive()&&protectActiveValue(cart,controller);
    }
    /** A genuine active mounted root/coupling receipt must retire before native destructive drops. */
    private static boolean protectActiveValue(AbstractMinecart cart,MinecartController controller){
        if(cart.level().isClientSide())return false;
        if(controller.getMountedRoot()!=null){
            if(!(cart.level() instanceof net.minecraft.server.level.ServerLevel server)
                ||!(server.getEntity(controller.getMountedRoot()) instanceof com.simibubi.create.content.contraptions.OrientedContraptionEntity root))return true;
            if(root.hasOwnedStructure()){if(!root.disassemble())return true;}
            else if(root.isNativeCartRetirementPending()){if(!NativeCartRetirement.tryFinish(root))return true;root.discard();}
            else return true;
            if(controller.getMountedRoot()!=null)return true;
        }
        boolean ordinary=controller.isLeadingCoupling()&&!controller.hasContraptionCoupling(true)||controller.isConnectedToCoupling()&&!controller.hasContraptionCoupling(false);
        if(controller.isCoupledThroughContraption())return true;
        if(ordinary&&!com.simibubi.create.content.contraptions.minecart.CouplingHandler.detachOrdinaryLinks(controller))return true;
        if(controller.hasPendingRefunds())CartReceiptPublication.returnRefunds(controller,com.simibubi.create.content.kinetics.deployer.DeployerCartItemSource.activeActor());
        return controller.hasPendingRefunds();
    }
    /** An actual saved mounted owner that is currently unresolved must not drift while held. */
    public static boolean suspendMissingMountedOwner(AbstractMinecart cart){
        var controller=attached(cart);if(controller==null||!controller.missingMountedRoot())return false;
        cart.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);return true;
    }
    private NativeCartLifecycle(){}
}
