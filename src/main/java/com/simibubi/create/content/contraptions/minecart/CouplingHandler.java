package com.simibubi.create.content.contraptions.minecart;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.content.contraptions.minecart.capability.MinecartController.LinkSnapshot;
import com.simibubi.create.content.contraptions.mounted.CartItemPayment;
import java.util.*;
import java.util.function.Consumer;
import net.createmod.catnip.data.Couple;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Source topology/limits with actual loaded endpoints and one component-preserving paid owner per ordinary link. */
public final class CouplingHandler {
    private record Edge(LinkSnapshot main,LinkSnapshot connected){
        boolean validate(){return main.validate()&&connected.validate()
            &&main.leading()&&!connected.leading()&&main.main().equals(connected.main())&&main.connected().equals(connected.connected())
            &&Float.compare(main.length(),connected.length())==0&&main.contraption()==connected.contraption()&&main.creative()==connected.creative()&&main.legacyUnreceipted()==connected.legacyUnreceipted()
            &&connected.actualReceipt().isEmpty()&&(main.contraption()||main.creative()||main.legacyUnreceipted()?main.actualReceipt().isEmpty():!main.actualReceipt().isEmpty()&&main.actualReceipt().getCount()==1&&main.actualReceipt().is(AllItems.MINECART_COUPLING));}
    }
    private record StructuralDirection(com.simibubi.create.content.contraptions.OrientedContraptionEntity root,Edge edge){
        boolean validate(){
            var first=edge.main.owner();var second=edge.connected.owner();
            if(!(first.cart().level() instanceof net.minecraft.server.level.ServerLevel server)||server.getEntity(root.getUUID())!=root||root.isRemoved()||!root.hasOwnedStructure()
                ||!(root.getContraption() instanceof com.simibubi.create.content.contraptions.mounted.MountedContraption)
                ||!Objects.equals(root.getCouplingId(),edge.main.main())||!Objects.equals(first.getMountedRoot(),root.getUUID())||!Objects.equals(second.getMountedRoot(),root.getUUID()))return false;
            return root.getVehicle()==first.cart()&&Objects.equals(root.getMountedCartId(),first.cart().getUUID())||root.getVehicle()==second.cart()&&Objects.equals(root.getMountedCartId(),second.cart().getUUID());
        }
    }
    private static List<StructuralDirection> structuralDirections(Chain chain){
        var result=new ArrayList<StructuralDirection>();
        for(var edge:chain.edges)if(edge.main.contraption()){
            var id=edge.main.owner().getMountedRoot();
            if(id==null||!(edge.main.owner().cart().level() instanceof net.minecraft.server.level.ServerLevel server)||!(server.getEntity(id) instanceof com.simibubi.create.content.contraptions.OrientedContraptionEntity root))return null;
            var direction=new StructuralDirection(root,edge);if(!direction.validate())return null;result.add(direction);
        }
        return List.copyOf(result);
    }
    private record Chain(List<MinecartController> carts,List<Edge> edges){
        Chain{carts=List.copyOf(carts);edges=List.copyOf(edges);}
        boolean validate(){return carts.stream().allMatch(CouplingHandler::available)&&edges.stream().allMatch(Edge::validate);}
    }
    private static boolean available(MinecartController controller){return controller!=null&&controller.isPresent()&&!controller.nativeBaseIsInactive()&&!controller.missingMountedRoot();}
    public static void forEachLoadedCoupling(Level world,Consumer<Couple<MinecartController>> consumer){
        if(world==null)return;for(var main:CapabilityMinecartController.loaded(world)){
            if(!main.isPresent()||!main.isLeadingCoupling())continue;var other=CapabilityMinecartController.getIfPresent(world,main.getCoupledCart(true));
            if(other==null||!Objects.equals(other.getCoupledCart(false),main.cart().getUUID())||main.hasContraptionCoupling(true)!=other.hasContraptionCoupling(false)||Float.compare(main.getCouplingLength(true),other.getCouplingLength(false))!=0)continue;
            consumer.accept(Couple.create(main,other));
        }
    }
    public static MinecartController getNextInCouplingChain(Level world,MinecartController controller,boolean forward){
        var next=controller.getCoupledCart(forward);return next==null?MinecartController.EMPTY:CapabilityMinecartController.getIfPresent(world,next);
    }
    /** Every actual edge and paid/refund owner is checked before a single source mutation. */
    private static Chain chain(MinecartController start){
        var left=start;var seen=Collections.newSetFromMap(new IdentityHashMap<MinecartController,Boolean>());
        for(int safety=0;;safety++){
            if(safety>=1000||!available(left)||!seen.add(left))return null;
            if(!left.isConnectedToCoupling())break;
            var previous=CapabilityMinecartController.getIfPresent(left.cart().level(),left.getCoupledCart(false));
            if(previous==null||!Objects.equals(previous.getCoupledCart(true),left.cart().getUUID()))return null;left=previous;
        }
        var nodes=new ArrayList<MinecartController>();var edges=new ArrayList<Edge>();seen.clear();
        for(int safety=0;;safety++){
            if(safety>=1000||!available(left)||!seen.add(left))return null;nodes.add(left);
            if(!left.isLeadingCoupling())break;
            var next=CapabilityMinecartController.getIfPresent(left.cart().level(),left.getCoupledCart(true));if(next==null)return null;
            var outgoing=left.inspectLink(true);var incoming=next.inspectLink(false);if(outgoing==null||incoming==null)return null;
            var edge=new Edge(outgoing,incoming);if(!edge.validate())return null;edges.add(edge);left=next;
        }
        var result=new Chain(nodes,edges);return result.validate()?result:null;
    }
    public static boolean tryToCoupleCarts(Player player,Level world,int firstId,int secondId){
        return couple(player,world,firstId,secondId,false);
    }
    /** Native gamemaster permission explicitly authorizes one ordinary, non-refundable command edge. */
    public static boolean tryToCoupleCartsByCommand(net.minecraft.commands.CommandSourceStack source,AbstractMinecart first,AbstractMinecart second){
        if(!net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(source.permissions())||first.level()!=second.level())return false;
        return couple(null,first.level(),first.getId(),second.getId(),true);
    }
    private static boolean couple(Player player,Level world,int firstId,int secondId,boolean commandMaterialization){
        if(world.isClientSide()||firstId==secondId||!(world.getEntity(firstId) instanceof AbstractMinecart first)||!(world.getEntity(secondId) instanceof AbstractMinecart second))return false;
        if(player!=null&&(player.level()!=world||player.isRemoved()||!player.isAlive()))return false;
        int distance=(int)first.position().distanceTo(second.position());boolean structural=player==null&&!commandMaterialization,creative=commandMaterialization;
        if(distance<2){if(structural)return false;distance=2;}
        if(distance>MinecartConfig.maxCouplingLength()){status(player,"too_far");return false;}
        var main=CapabilityMinecartController.getIfPresent(world,first.getUUID());var connected=CapabilityMinecartController.getIfPresent(world,second.getUUID());
        if(!available(main)||!available(connected)){status(player,"unloaded");return false;}
        if(main.isFullyCoupled()||connected.isFullyCoupled()){status(player,"two_couplings_max");return false;}
        var a=chain(main);var b=chain(connected);if(a==null||b==null){status(player,"unloaded");return false;}
        if(a.carts.stream().anyMatch(b.carts::contains)){status(player,"no_loops");return false;}
        CartItemPayment payment=null;
        if(!structural&&!commandMaterialization){
            for(var hand:InteractionHand.values()){
                if(!player.getItemInHand(hand).is(AllItems.MINECART_COUPLING))continue;
                payment=CartItemPayment.prepare(player,hand).orElse(null);if(payment!=null)break;
            }
            if(payment==null||!payment.proposedReceipt().is(AllItems.MINECART_COUPLING))return false;
        }
        boolean reverseA=main.isLeadingCoupling(),reverseB=connected.isConnectedToCoupling();
        var directionsA=reverseA?structuralDirections(a):List.<StructuralDirection>of();var directionsB=reverseB?structuralDirections(b):List.<StructuralDirection>of();
        if(directionsA==null||directionsB==null||!a.validate()||!b.validate()||directionsA.stream().anyMatch(d->!d.validate())||directionsB.stream().anyMatch(d->!d.validate())||payment!=null&&!payment.validate())return false;
        creative|=payment!=null&&payment.isCreativeMaterialization();
        var changed=new LinkedHashSet<MinecartController>();changed.addAll(a.carts);changed.addAll(b.carts);
        ItemStack original=payment==null||creative?ItemStack.EMPTY:payment.debit();
        if(reverseA)reversePure(a,directionsA);
        if(reverseB)reversePure(b,directionsB);
        main.installLink(true,second.getUUID(),distance,structural,creative,original);connected.installLink(false,first.getUUID(),distance,structural,creative,ItemStack.EMPTY);
        for(var direction:directionsA)direction.root.syncCouplingLeadingCart();for(var direction:directionsB)direction.root.syncCouplingLeadingCart();
        for(var controller:changed)controller.sendData();if(payment!=null&&!creative)payment.notifyNative();return true;
    }
    private static void reversePure(Chain chain,List<StructuralDirection> directions){
        // Detach every old topology copy before installing the reversed graph. The original receipt
        // travels from the former main to the new main; no external callback observes two owners.
        for(var edge:chain.edges){edge.main.owner().detachLink(true);edge.connected.owner().detachLink(false);}
        for(var edge:chain.edges){edge.connected.owner().installLink(true,edge.main.main(),edge.main.length(),edge.main.contraption(),edge.main.creative(),edge.main.legacyUnreceipted(),edge.main.actualReceipt());edge.main.owner().installLink(false,edge.main.connected(),edge.main.length(),edge.main.contraption(),edge.main.creative(),edge.main.legacyUnreceipted(),ItemStack.EMPTY);}
        for(var direction:directions)direction.root.replaceCouplingLeadingCartPure(direction.edge.main.main(),direction.edge.main.connected());
    }
    /** Remove paid ordinary links atomically; exact receipts remain durable on their former actual main owners. */
    public static boolean detachOrdinaryLinks(MinecartController selected){return detachOrdinaryLinks(selected,true);}
    public static boolean detachOrdinaryLinks(MinecartController selected,boolean returnPaidReceipts){
        if(!available(selected))return false;var edges=new ArrayList<Edge>();
        for(boolean leading:new boolean[]{true,false}){
            var local=selected.inspectLink(leading);if(local==null||local.contraption())continue;
            var peer=CapabilityMinecartController.getIfPresent(selected.cart().level(),leading?local.connected():local.main());if(peer==null)return false;
            var other=peer.inspectLink(!leading);if(other==null)return false;var edge=leading?new Edge(local,other):new Edge(other,local);if(!edge.validate())return false;edges.add(edge);
        }
        if(edges.isEmpty()||!edges.stream().allMatch(Edge::validate))return false;
        var changed=new LinkedHashSet<MinecartController>();
        for(var edge:edges){var paid=edge.main.owner().detachLink(true);edge.connected.owner().detachLink(false);if(!paid.isEmpty()&&returnPaidReceipts)edge.main.owner().retainRefund(paid);else if(!paid.isEmpty())paid.setCount(0);changed.add(edge.main.owner());changed.add(edge.connected.owner());}
        changed.forEach(MinecartController::sendData);return true;
    }
    /** A command cannot sever a live mounted structure; its genuine world restoration retires that edge. */
    public static boolean detachByCommand(net.minecraft.commands.CommandSourceStack source,MinecartController selected,UUID peer){
        if(!net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(source.permissions())||!available(selected))return false;
        boolean changed=false;
        for(boolean leading:new boolean[]{true,false}){
            var local=selected.inspectLink(leading);if(local==null||peer!=null&&!Objects.equals(leading?local.connected():local.main(),peer))continue;
            var other=CapabilityMinecartController.getIfPresent(selected.cart().level(),leading?local.connected():local.main());if(other==null)return false;
            var reciprocal=other.inspectLink(!leading);if(reciprocal==null)return false;
            var edge=leading?new Edge(local,reciprocal):new Edge(reciprocal,local);if(!edge.validate())return false;
            if(local.contraption()){
                var direction=new StructuralDirection(resolveMountedRoot(selected),edge);if(direction.root==null||!direction.validate())return false;
                if(!direction.root.disassemble())return false;
                if(selected.inspectLink(leading)!=null||selected.getMountedRoot()!=null)return false;
            }else{
                var receipt=edge.main.owner().detachLink(true);edge.connected.owner().detachLink(false);
                if(!receipt.isEmpty())edge.main.owner().retainRefund(receipt);
                edge.main.owner().sendData();edge.connected.owner().sendData();
            }
            changed=true;
        }
        return changed;
    }
    private static com.simibubi.create.content.contraptions.OrientedContraptionEntity resolveMountedRoot(MinecartController controller){
        if(controller.getMountedRoot()==null||!(controller.cart().level() instanceof net.minecraft.server.level.ServerLevel server))return null;
        return server.getEntity(controller.getMountedRoot()) instanceof com.simibubi.create.content.contraptions.OrientedContraptionEntity root?root:null;
    }
    /** Assembly rollback/restoration only: there must be no remaining live mounted owner on either endpoint. */
    public static boolean detachRetiredStructuralPair(MinecartController main,MinecartController connected){
        if(main==null||connected==null||!main.isPresent()||!connected.isPresent())return false;
        var outgoing=main.inspectLink(true);var incoming=connected.inspectLink(false);if(outgoing==null||incoming==null||!outgoing.contraption())return false;
        var edge=new Edge(outgoing,incoming);if(!edge.validate())return false;
        for(var controller:List.of(main,connected)){
            var id=controller.getMountedRoot();
            if(id!=null&&controller.cart().level() instanceof net.minecraft.server.level.ServerLevel server){
                var owner=server.getEntity(id);
                if(owner instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity structure&&structure.hasOwnedStructure())return false;
                // An unresolved saved owner is not proof that the real mounted value retired.
                if(owner==null)return false;
            }
        }
        main.detachLink(true);connected.detachLink(false);return true;
    }
    public static void status(Player player,String key){if(player!=null)player.sendOverlayMessage(Component.translatable("create.minecart_coupling."+key));}
    private CouplingHandler(){}
}
