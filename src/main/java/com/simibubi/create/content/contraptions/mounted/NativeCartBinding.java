package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Recovers the actual predeclared native owner binding after a late assembly callback failure. */
public final class NativeCartBinding {
    public static boolean recover(OrientedContraptionEntity root){
        if(!(root.level() instanceof ServerLevel server)||server.getEntity(root.getUUID())!=root||!root.hasOwnedStructure()||!(root.getContraption() instanceof MountedContraption)||root.getParentId()!=null)return false;
        var primary=CapabilityMinecartController.getIfPresent(server,root.getMountedCartId());if(!available(root,primary))return false;
        if(root.getVehicle()!=null&&root.getVehicle()!=primary.cart())return false;
        var pair=root.getCouplingId()==null?null:root.getCoupledCartsIfPresent();if(root.getCouplingId()!=null&&pair==null)return false;
        var carts=pair==null?List.of(primary):List.of(pair.getFirst(),pair.getSecond());
        if(pair!=null&&pair.getFirst()!=primary&&pair.getSecond()!=primary)return false;
        MinecartController.LinkSnapshot outgoing=pair==null?null:pair.getFirst().inspectLink(true),incoming=pair==null?null:pair.getSecond().inspectLink(false);
        if(carts.stream().anyMatch(cart->!available(root,cart)))return false;
        if(pair!=null&&(outgoing==null||incoming==null||!outgoing.validate()||!incoming.validate()||!outgoing.contraption()||!incoming.contraption()||!outgoing.main().equals(incoming.main())||!outgoing.connected().equals(incoming.connected())||Float.compare(outgoing.length(),incoming.length())!=0||!outgoing.actualReceipt().isEmpty()||!incoming.actualReceipt().isEmpty()))return false;
        if(carts.stream().allMatch(cart->Objects.equals(cart.getMountedRoot(),root.getUUID())))return true;
        // Root has the actual source blocks, and its native UUIDs were recorded before reservation
        // admission and before source removal. No absent owner or replacement cart is invented.
        for(var controller:carts)controller.setMountedRoot(root.getUUID());
        for(var controller:carts)controller.sendData();return true;
    }
    private static boolean available(OrientedContraptionEntity root,MinecartController controller){return controller!=null&&controller.isPresent()&&!controller.nativeBaseIsInactive()&&(controller.getMountedRoot()==null||Objects.equals(controller.getMountedRoot(),root.getUUID()))&&controller.cart().getPassengers().stream().noneMatch(passenger->passenger instanceof AbstractContraptionEntity&&passenger!=root);}
    private NativeCartBinding(){}
}
