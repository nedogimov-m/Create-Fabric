package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;

/** A world-retired real root stays durable until its actual loaded native attachments can retire. */
public final class NativeCartRetirement {
    public static boolean tryFinish(OrientedContraptionEntity root){
        if(root.isNativeCartRetirementComplete())return true;
        if(!(root.level() instanceof ServerLevel server)||server.getEntity(root.getUUID())!=root
            ||!root.isNativeCartRetirementPending()&&(!(root.getContraption() instanceof MountedContraption structure)||!structure.disassembled||structure.getStorage().ownsContents()))return false;
        var primary=CapabilityMinecartController.getIfPresent(server,root.getMountedCartId());if(!matches(root,primary))return false;
        List<MinecartController> carts;MinecartController.LinkSnapshot outgoing=null,incoming=null;
        if(root.getCouplingId()!=null){
            var main=CapabilityMinecartController.getIfPresent(server,root.getCouplingId());if(!matches(root,main))return false;
            var other=CapabilityMinecartController.getIfPresent(server,main.getCoupledCart(true));if(!matches(root,other))return false;
            outgoing=main.inspectLink(true);incoming=other.inspectLink(false);
            if(outgoing==null||incoming==null||!outgoing.validate()||!incoming.validate()||!outgoing.contraption()||!incoming.contraption()
                ||!outgoing.main().equals(incoming.main())||!outgoing.connected().equals(incoming.connected())||Float.compare(outgoing.length(),incoming.length())!=0
                ||!outgoing.actualReceipt().isEmpty()||!incoming.actualReceipt().isEmpty()||primary!=main&&primary!=other)return false;
            carts=List.of(main,other);
        }else carts=List.of(primary);
        if(carts.stream().anyMatch(cart->!matches(root,cart))||outgoing!=null&&(!outgoing.validate()||!incoming.validate()))return false;
        // Actual world placement already revoked the root storage. All remaining native topology
        // and the durable completion record change before sync/removal callbacks can save/reenter.
        if(outgoing!=null){outgoing.owner().detachLink(true);incoming.owner().detachLink(false);}
        for(var controller:carts){controller.releaseMountedRootPure(root.getUUID());if(controller.cart() instanceof MinecartFurnace furnace)furnace.push=controller.cart().getDeltaMovement().multiply(1,0,1);}
        root.markNativeCartRetirementCompletePure();
        for(var controller:carts)controller.sendData();return true;
    }
    private static boolean matches(OrientedContraptionEntity root,MinecartController controller){return controller!=null&&controller.isPresent()&&!controller.nativeBaseIsInactive()&&Objects.equals(controller.getMountedRoot(),root.getUUID());}
    private NativeCartRetirement(){}
}
