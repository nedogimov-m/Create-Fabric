package com.simibubi.create.content.contraptions;

import com.simibubi.create.foundation.utility.NativeEntityAcceptance;

import com.simibubi.create.api.contraption.ContraptionMovementSetting;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.content.contraptions.minecart.capability.MinecartController.LinkSnapshot;
import com.simibubi.create.content.contraptions.mounted.*;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Actual native owner/item transfer. This coordinator keeps off-world proposals private and performs
 * callback-free source revocation, raw native cargo handoff and pure forest binding as one commit.
 */
public final class CartForestTransfer {
    private record Owned(OrientedContraptionEntity entity,Contraption structure,int parent,StabilizedSubContraptions.Location location){}
    private record Cart(AbstractMinecart entity,MinecartController controller,NativeCartSnapshot snapshot){}
    /** Unforgeable source/target capability, live only inside this coordinator's pure validated commit. */
    public static final class Commit {
        private final Set<AbstractMinecart> sources=Collections.newSetFromMap(new IdentityHashMap<>()),targets=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Thread thread=Thread.currentThread();private boolean active;
        private Commit(Collection<AbstractMinecart> sources,Collection<AbstractMinecart> targets){this.sources.addAll(sources);this.targets.addAll(targets);}
        public void requireSourceCart(AbstractMinecart actual){if(!active||Thread.currentThread()!=thread||!sources.contains(actual))throw new IllegalStateException("Actual current source native cargo commit required");}
        public void requireTargetCart(AbstractMinecart actual){if(!active||Thread.currentThread()!=thread||!targets.contains(actual))throw new IllegalStateException("Actual current target native cargo commit required");}
    }
    public record Packed(UUID actualSourceOwner,UUID receipt,ItemStack originalReceipt){}
    /**
     * Transfer succeeds when the complete original receipt is installed on the actual primary cart.
     * Native inventory/drop publication can then consume that same receipt independently/retryably.
     */
    public static Packed pack(AbstractMinecart clicked)throws AssemblyException{return pack(clicked,null);}
    /** An actual output actor may not be part of the forest whose ownership it is receiving. */
    public static Packed pack(AbstractMinecart clicked,AbstractContraptionEntity actualOutputOwner)throws AssemblyException{
        if(!(clicked.level() instanceof ServerLevel level)||Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)return null;
        var clickedController=CapabilityMinecartController.getIfPresent(level,clicked.getUUID());if(clickedController==null||clickedController.nativeBaseIsInactive())return null;
        var rootId=clickedController.getMountedRoot();if(rootId==null||!(level.getEntity(rootId) instanceof OrientedContraptionEntity root)||!(root.getContraption() instanceof MountedContraption)||!root.hasOwnedStructure())return null;
        if(!(root.getVehicle() instanceof AbstractMinecart primary)||!Objects.equals(root.getMountedCartId(),primary.getUUID()))return null;
        var tree=new ArrayList<Owned>();collect(level,root,-1,null,tree,new HashSet<>(),0);
        // Packing has its own forest traversal: it must reject a live printer before stop/snapshot.
        for(var node:tree)for(var actor:node.structure.getActors())
            if(actor.right!=null&&com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(actor.left.state()) instanceof com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour
                &&!com.simibubi.create.content.kinetics.deployer.DeployerSchematicPrinter.prepareDisassembly(actor.right))
                throw AssemblyException.entitiesNotReady(node.entity.blockPosition());
        if(actualOutputOwner!=null&&tree.stream().anyMatch(node->node.entity==actualOutputOwner))return null;
        var bases=new ArrayList<Cart>();var pair=root.getCouplingId()==null?null:root.getCoupledCartsIfPresent();
        if(root.getCouplingId()!=null&&pair==null)return null;
        var primaryController=CapabilityMinecartController.getIfPresent(level,primary.getUUID());if(primaryController==null)return null;
        bases.add(new Cart(primary,primaryController,NativeCartSnapshot.capture(primary)));
        if(pair!=null){var other=pair.getFirst().cart()==primary?pair.getSecond():pair.getFirst();if(pair.getFirst().cart()!=primary&&pair.getSecond().cart()!=primary)return null;bases.add(new Cart(other.cart(),other,NativeCartSnapshot.capture(other.cart())));}
        if(bases.stream().noneMatch(base->base.entity==clicked))return null;
        for(var base:bases){
            NativeCartKind.of(base.entity);if(!Objects.equals(base.controller.getMountedRoot(),root.getUUID())||base.controller.nativeBaseIsInactive()||base.controller.hasPendingRefunds())return null;
            for(boolean leading:new boolean[]{true,false})if(base.controller.getCoupledCart(leading)!=null&&!base.controller.hasContraptionCoupling(leading))return null;
        }
        LinkSnapshot link=pair==null?null:pair.getFirst().inspectLink(true),reciprocal=pair==null?null:pair.getSecond().inspectLink(false);
        if(pair!=null&&(link==null||reciprocal==null||!link.contraption()||!reciprocal.contraption()||!link.actualReceipt().isEmpty()||!reciprocal.actualReceipt().isEmpty()||!link.validate()||!reciprocal.validate()))return null;
        var locked=new ArrayList<StabilizedSubContraptions>();ItemStack original=ItemStack.EMPTY;UUID receipt=UUID.randomUUID();boolean moved=false;
        try{
            for(var node:tree){var graph=node.structure.getStabilizedSubContraptions();if(!graph.enterTransfer())throw AssemblyException.entitiesNotReady(node.entity.blockPosition());locked.add(graph);}
            // Every callback runs while the genuine original forest/base carts still own all value.
            for(var node:tree){if(!node.entity.initializeCapturedPassengers())throw AssemblyException.entitiesNotReady(node.entity.blockPosition());node.structure.stop(level);}
            for(var node:tree)for(var passenger:List.copyOf(node.entity.getPassengers())){
                if(passenger instanceof OrientedContraptionEntity child&&node.structure.getStabilizedSubContraptions().accepts(child))continue;
                if(passenger instanceof AbstractContraptionEntity)throw AssemblyException.entitiesNotReady(passenger.blockPosition());passenger.stopRiding();
            }
            for(var base:bases){
                for(var player:level.players())if(player.containerMenu.slots.stream().anyMatch(slot->slot.container==base.entity))player.closeContainer();
                for(var passenger:List.copyOf(base.entity.getPassengers()))if(passenger!=root){if(passenger instanceof AbstractContraptionEntity)throw AssemblyException.entitiesNotReady(passenger.blockPosition());passenger.stopRiding();}
            }
            var refreshed=new ArrayList<Cart>();for(var base:bases)refreshed.add(new Cart(base.entity,base.controller,NativeCartSnapshot.capture(base.entity)));bases=refreshed;
            for(var node:tree)node.structure.forEachActor(level,(behaviour,context)->behaviour.writeExtraData(context));
            var sourceStates=tree.stream().map(node->OwnedCartStructureSnapshot.capture(node.entity)).toList();
            var values=new ArrayList<PackedCartForest.Node>();
            for(var node:tree){
                var data=node.structure.writeNBT(level.registryAccess(),false);data.remove("SubContraptions");data.remove("GraphRestorationUpdates");data.putBoolean("GraphRecovering",false);
                values.add(new PackedCartForest.Node(node.parent,data,node.entity.getInitialOrientation(),-node.entity.getViewYRot(1),node.entity.getViewXRot(1),node.location));
            }
            var nativeValues=new ArrayList<PackedCartForest.Base>();for(int index=0;index<bases.size();index++){var base=bases.get(index);nativeValues.add(new PackedCartForest.Base(NativeCartKind.of(base.entity),base.snapshot.state(),index==0?Vec3.ZERO:root.reverseRotation(base.entity.position().subtract(primary.position()),1)));}
            var payload=new PackedCartForest(receipt,values,nativeValues,link==null?0:link.length());original=payload.createInactiveItem(level,primary.getCustomName());
            if(sourceStates.stream().anyMatch(snapshot->!snapshot.matchesRaw())||!validatePack(level,root,tree,bases,link,reciprocal))throw AssemblyException.entitiesNotReady(primary.blockPosition());
            var proof=new Commit(bases.stream().map(Cart::entity).toList(),List.of());proof.active=true;
            try{
                // No setContraption/startMoving/data tracker, menu, mount or native removal callback.
                for(var node:tree){node.structure.getStorage().setOwned(false);node.entity.contraption=null;node.entity.setClientRenderState(null);node.structure.entity=null;}
                for(var base:bases){NativeCartState.clearCargo(base.entity,proof);base.controller.setMountedRoot(null);}
                if(link!=null){link.owner().detachLink(true);reciprocal.owner().detachLink(false);}
                primaryController.installPackedReceipt(original);
                for(int index=1;index<bases.size();index++)bases.get(index).controller.retireNativeBaseToPackedOwner(primary,receipt);
                moved=true;
            }finally{proof.active=false;}
            // The one actual original receipt already owns the entire value before these callbacks.
            for(int index=tree.size()-1;index>=0;index--)tree.get(index).entity.discard();
            for(int index=1;index<bases.size();index++)bases.get(index).entity.discard();primaryController.sendData();
            return new Packed(primary.getUUID(),receipt,original);
        }finally{
            try{if(!moved)for(var node:tree)node.structure.resumeAfterPackedItemRefusal();}
            finally{for(var graph:locked)graph.leaveTransfer();}
        }
    }
    private static void collect(ServerLevel level,OrientedContraptionEntity owner,int parent,StabilizedSubContraptions.Location location,List<Owned> result,Set<UUID> seen,int depth)throws AssemblyException{
        if(depth>PackedCartForest.MAX_DEPTH||result.size()>=PackedCartForest.MAX_OWNERS||!seen.add(owner.getUUID())||owner.isRemoved()||level.getEntity(owner.getUUID())!=owner||!LoadedEntityAccess.isReady(level,owner.blockPosition())||!owner.hasOwnedStructure())throw AssemblyException.entitiesNotReady(owner.blockPosition());
        var structure=owner.getContraption();if(parent>=0&&!(structure instanceof com.simibubi.create.content.contraptions.bearing.StabilizedContraption))throw AssemblyException.entitiesNotReady(owner.blockPosition());var graph=structure.getStabilizedSubContraptions();
        if(!ContraptionAssemblyGraph.isTransferPermitted(structure)||graph.isTransferring()||graph.isRecovering()||graph.hasPendingRestoration())throw AssemblyException.entitiesNotReady(owner.blockPosition());
        if(ContraptionMovementSetting.isNoPickup(structure.getBlocks().values()))throw new AssemblyException(net.minecraft.network.chat.Component.translatable("create.contraption.minecart_contraption_illegal_pickup"));
        if(parent<0?owner.getParentId()!=null:owner.getVehicle()!=result.get(parent).entity||!Objects.equals(owner.getParentId(),result.get(parent).entity.getUUID())||!Objects.equals(owner.getParentLocation(),location))throw AssemblyException.entitiesNotReady(owner.blockPosition());
        int index=result.size();result.add(new Owned(owner,structure,parent,location));
        for(var entry:graph.children().entrySet())collect(level,graph.resolve(level,entry.getKey()),index,entry.getValue(),result,seen,depth+1);
        for(var passenger:owner.getPassengers())if(passenger instanceof AbstractContraptionEntity&&!(passenger instanceof OrientedContraptionEntity child&&graph.accepts(child)))throw AssemblyException.entitiesNotReady(passenger.blockPosition());
    }
    private static boolean validatePack(ServerLevel level,OrientedContraptionEntity root,List<Owned> tree,List<Cart> bases,LinkSnapshot link,LinkSnapshot reciprocal){
        if(Transaction.getLifecycle()!=Transaction.Lifecycle.NONE||!root.hasOwnedStructure()||root.getVehicle()!=bases.getFirst().entity)return false;
        for(var node:tree){
            if(node.entity.getContraption()!=node.structure||node.structure.entity!=node.entity||!node.entity.hasOwnedStructure()||node.entity.isRemoved()||level.getEntity(node.entity.getUUID())!=node.entity||!LoadedEntityAccess.isReady(level,node.entity.blockPosition())||!node.structure.getStabilizedSubContraptions().isTransferring()||!node.structure.getInitialPassengers().isEmpty()||!node.structure.getSeatMapping().isEmpty())return false;
            var graph=node.structure.getStabilizedSubContraptions();long expected=tree.stream().filter(child->child.parent>=0&&tree.get(child.parent).entity==node.entity).count();if(graph.children().size()!=expected)return false;
            if(node.parent>=0&&(!tree.get(node.parent).structure.getStabilizedSubContraptions().accepts(node.entity)||node.entity.getVehicle()!=tree.get(node.parent).entity))return false;
        }
        for(var base:bases)if(!base.controller.isPresent()||base.controller.nativeBaseIsInactive()||base.controller.hasPendingRefunds()||!Objects.equals(base.controller.getMountedRoot(),root.getUUID())||!base.snapshot.matchesRaw())return false;
        return link==null||link.validate()&&reciprocal.validate();
    }
    private record Reservation(OrientedContraptionEntity entity,Contraption structure,PackedCartForest.Node node,Vec3 position,float yaw,float pitch){}
    public record Deployment(UUID actualRoot,UUID spentReceipt){}
    /** The original payer was already debited into this actual primary attachment at native insertion. */
    public static Deployment deploy(AbstractMinecart primary,Direction facing)throws AssemblyException{
        if(!(primary.level() instanceof ServerLevel level)||Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)return null;
        var source=CapabilityMinecartController.getIfPresent(level,primary.getUUID());if(source==null)return null;
        var original=source.actualPackedReceipt();if(!source.ownsPackedReceipt(original))return null;
        var expected=PackedCartData.detachedItemCopy(original);var payload=PackedCartForest.forDeployment(original,primary);
        if(payload.bases().getFirst().kind()!=NativeCartKind.of(primary)||!NativeCartState.cargoIsActuallyEmpty(primary))return null;
        var primaryPosition=primary.position();var primaryEmpty=NativeCartSnapshot.capture(primary);var structures=payload.decodeInactiveStructures(level);
        var entities=new ArrayList<Reservation>();var nativeCarts=new ArrayList<AbstractMinecart>();nativeCarts.add(primary);
        var nativeStates=new ArrayList<NativeCartState.Prepared>();var controllers=new ArrayList<MinecartController>();controllers.add(source);
        boolean committed=false;
        try{
            // Genuine constructors create all candidate UUIDs. No proposal is bound to an entity.
            for(int index=0;index<structures.size();index++){
                var node=payload.nodes().get(index);var entity=new OrientedContraptionEntity(com.simibubi.create.AllEntityTypes.ORIENTED_CONTRAPTION,level);
                entity.setInitialOrientation(node.initial());float yaw,pitch;
                if(index==0){yaw=facing==null?entity.getInitialYaw():facing.toYRot();pitch=0;entity.setMountedCartId(primary.getUUID());if(payload.bases().size()==2)entity.setCouplingId(primary.getUUID());entity.setAngles(yaw,pitch);entity.setPos(primary.position());}
                else{
                    var parent=entities.get(node.parent()).entity;var stabilized=(com.simibubi.create.content.contraptions.bearing.StabilizedContraption)structures.get(index);
                    if(stabilized.getFacing()!=node.location().facing())throw new IllegalArgumentException("Packed child facing disagrees with the actual source bearing");
                    yaw=node.location().facing().getAxis().isHorizontal()?net.minecraft.util.Mth.wrapDegrees(entity.getInitialYaw()-parent.getInitialYaw())-parent.getViewYRot(1):node.yaw();pitch=node.pitch();
                    entity.setAngles(yaw,pitch);entity.setParent(parent.getUUID(),node.location());entity.setPos(parent.toGlobalVector(Vec3.atCenterOf(node.location().connected()),1).add(0,-.5,0));
                    structures.get(node.parent()).getStabilizedSubContraptions().remember(entity.getUUID(),node.location());
                }
                entities.add(new Reservation(entity,structures.get(index),node,entity.position(),yaw,pitch));
            }
            var actualRoot=entities.getFirst().entity;
            if(payload.bases().size()==2){
                var second=payload.bases().get(1);var position=primary.position().add(actualRoot.applyRotation(second.localOffset(),1));
                if(!LoadedEntityAccess.isReady(level,BlockPos.containing(position)))return null;
                var cart=second.kind().createEmpty(level,position);var controller=MinecartController.get(cart);nativeCarts.add(cart);controllers.add(controller);
                if(!NativeCartState.cargoIsActuallyEmpty(cart))throw new IllegalStateException("New native secondary reservation already contains cargo");
                try(var scope=NativeEntityAcceptance.prepare(cart,()->source.ownsPackedReceipt(original)&&ItemStack.matches(original,expected)&&NativeCartState.cargoIsActuallyEmpty(cart),proof->controller.reserveNativeBaseAtInsertion(proof,primary,payload.receipt()))){scope.add();if(scope.insertion()==null)return null;}
                if(cart.isRemoved()||level.getEntity(cart.getUUID())!=cart)return null;
            }
            for(int index=0;index<nativeCarts.size();index++){var prepared=payload.bases().get(index).state().prepare(nativeCarts.get(index));nativeStates.add(index==0?prepared.withName(expected.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)):prepared);}
            var secondaryEmpty=nativeCarts.size()==2?NativeCartSnapshot.capture(nativeCarts.get(1)):null;
            for(var reservation:entities){
                if(!LoadedEntityAccess.isReady(level,reservation.entity.blockPosition()))return null;
                if(!level.addFreshEntity(reservation.entity)||reservation.entity.isRemoved()||level.getEntity(reservation.entity.getUUID())!=reservation.entity)return null;
            }
            if(!source.ownsPackedReceipt(original)||!ItemStack.matches(original,expected)||!primary.position().equals(primaryPosition)||!primaryEmpty.matchesRaw()||Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)return null;
            if(secondaryEmpty!=null&&!secondaryEmpty.matchesRaw())return null;
            for(int index=0;index<nativeCarts.size();index++){
                var cart=nativeCarts.get(index);var controller=controllers.get(index);
                if(!controller.isPresent()||!NativeCartState.cargoIsActuallyEmpty(cart)||!cart.getPassengers().isEmpty()||controller.isLeadingCoupling()||controller.isConnectedToCoupling()||controller.getMountedRoot()!=null||controller.hasPendingRefunds())return null;
                if(index>0){var custody=controller.baseCustody().orElse(null);if(custody==null||custody.kind()!=MinecartController.BaseCustodyKind.RESERVED||!custody.actualOwner().equals(primary.getUUID())||!custody.receipt().equals(payload.receipt()))return null;}
            }
            for(int index=0;index<entities.size();index++){
                var reservation=entities.get(index);var owner=reservation.entity;
                if(owner.isRemoved()||level.getEntity(owner.getUUID())!=owner||!LoadedEntityAccess.isReady(level,owner.blockPosition())||owner.getContraption()!=null||!owner.getPassengers().isEmpty()||owner.getVehicle()!=null||!owner.position().equals(reservation.position)||owner.getInitialOrientation()!=reservation.node.initial()||!owner.hasCurrentAngles(reservation.yaw,reservation.pitch)||!reservation.structure.isPackedItemProposal()||reservation.structure.getStorage().ownsContents())return null;
                if(index==0?!Objects.equals(owner.getMountedCartId(),primary.getUUID())||!Objects.equals(owner.getCouplingId(),nativeCarts.size()==2?primary.getUUID():null):!Objects.equals(owner.getParentId(),entities.get(reservation.node.parent()).entity.getUUID())||!Objects.equals(owner.getParentLocation(),reservation.node.location()))return null;
            }
            var proof=new Commit(List.of(),nativeCarts);proof.active=true;
            try{
                // The complete original receipt is spent exactly once; every native owner is installed
                // before any synchronized metadata, actor, bounds, mount or tracking callback runs.
                var spent=source.detachPackedReceipt();if(spent!=original)throw new IllegalStateException("Actual paid receipt changed at the final native handoff");
                for(int index=0;index<nativeCarts.size();index++){
                    if(index>0)controllers.get(index).claimReservedNativeBasePure(primary.getUUID(),payload.receipt());
                    nativeStates.get(index).installCargo(nativeCarts.get(index),proof);
                }
                for(var reservation:entities){reservation.entity.contraption=reservation.structure;reservation.structure.entity=reservation.entity;reservation.structure.claimPackedItemContentsPure();}
                if(nativeCarts.size()==2){float length=payload.structuralLength();controllers.get(0).installLink(true,nativeCarts.get(1).getUUID(),length,true,ItemStack.EMPTY);controllers.get(1).installLink(false,primary.getUUID(),length,true,ItemStack.EMPTY);}
                for(var controller:controllers)controller.setMountedRoot(actualRoot.getUUID());
                original.setCount(0);committed=true;
            }finally{proof.active=false;}
            var result=new Deployment(actualRoot.getUUID(),payload.receipt());
            for(int index=0;index<nativeCarts.size();index++)nativeStates.get(index).publishMetadata(nativeCarts.get(index));
            for(var reservation:entities){reservation.entity.setPos(reservation.position);reservation.structure.activatePackedItemActors();}
            actualRoot.startRiding(primary,true,true);
            for(int index=1;index<entities.size();index++){var reservation=entities.get(index);reservation.entity.startRiding(entities.get(reservation.node.parent()).entity,true,true);}
            controllers.forEach(MinecartController::sendData);
            for(var reservation:entities)for(var player:net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(reservation.entity))ContraptionNetworking.sendFull(reservation.entity,player);
            return result;
        }finally{
            // Rejected empty reservations have no cargo/forest value. Accepted real owners survive a
            // late callback throw/removal, and the old paid item is never reconstructed as a refund.
            for(var reservation:entities)if(reservation.entity.getContraption()==null&&!reservation.entity.isRemoved())reservation.entity.discard();
            if(!committed)for(int index=1;index<nativeCarts.size();index++){
                var cart=nativeCarts.get(index);var controller=controllers.get(index);
                if(controller.baseCustody().filter(c->c.kind()==MinecartController.BaseCustodyKind.RESERVED).isPresent()&&NativeCartState.cargoIsActuallyEmpty(cart))cart.discard();
            }
        }
    }
    private CartForestTransfer(){}
}
