package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionAssemblyGraph;
import com.simibubi.create.content.contraptions.ContraptionNetworking;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A cart pickup goes straight to the actual saved Deployer output, never its copied hand.
 * Preparation may invoke codecs; final validation and installation must not invoke callbacks.
 * The current action scope is selected by DeployerCartItemSource before preparing this sink.
 */
public final class DeployerCartPickupSink {
    public sealed interface Endpoint permits BlockEndpoint,ActorEndpoint {}
    public record BlockEndpoint(ResourceKey<Level> dimension,BlockPos pos) implements Endpoint {}
    public record ActorEndpoint(ResourceKey<Level> dimension,UUID actualEntity,BlockPos localPos) implements Endpoint {}
    private record Observed(ItemStack actual,ItemStack detached,int count) {
        boolean unchanged(ItemStack current){return current==actual&&current.getCount()==count&&ItemStack.matches(current,detached);}
    }
    /** Actual canonical queue and exact value at the instant of installation, retained after draining. */
    private record Installation(Endpoint endpoint,Object owner,List<ItemStack> queue,ItemStack original) {}

    public static Optional<Prepared> prepareStationary(DeployerBlockEntity owner,DeployerFakePlayer actor,ItemStack exactOriginal){
        if(!(owner.getLevel() instanceof ServerLevel level)||!validStationary(owner,actor,level)||!one(exactOriginal))return Optional.empty();
        var prepared=new Prepared(owner,null,actor,level,exactOriginal);
        return prepared.validate()?Optional.of(prepared):Optional.empty();
    }
    public static Optional<Prepared> prepareMoving(MovementContext owner,DeployerFakePlayer actor,ItemStack exactOriginal){
        if(!(owner.world instanceof ServerLevel level)||!validMoving(owner,actor,level)||!one(exactOriginal))return Optional.empty();
        var prepared=new Prepared(null,owner,actor,level,exactOriginal);
        return prepared.validate()?Optional.of(prepared):Optional.empty();
    }
    private static boolean one(ItemStack stack){return !stack.isEmpty()&&stack.getCount()==1;}
    private static boolean validStationary(DeployerBlockEntity owner,DeployerFakePlayer actor,ServerLevel level){
        return level.getServer().isSameThread()&&owner.getLevel()==level&&!owner.isRemoved()
            &&actor.level()==level&&actor.acting&&!actor.isRemoved()&&owner.ownsCartActor(actor)
            &&LoadedLevelAccess.blockEntity(level,owner.getBlockPos())==owner;
    }
    private static boolean validMoving(MovementContext owner,DeployerFakePlayer actor,ServerLevel level){
        if(!level.getServer().isSameThread()||owner.world!=level||actor.level()!=level||!actor.acting||actor.isRemoved()
            ||owner.temporaryData!=actor||owner.contraption==null)return false;
        var structure=owner.contraption;var entity=structure.entity;
        return entity!=null&&entity.level()==level&&entity.getContraption()==structure&&entity.hasOwnedStructure()
            &&level.getEntity(entity.getUUID())==entity&&LoadedEntityAccess.isReady(level,entity.blockPosition())
            &&!ContraptionAssemblyGraph.isRecovering(entity)&&ContraptionAssemblyGraph.isTransferPermitted(structure)
            &&structure.getActors().stream().anyMatch(entry->entry.right==owner&&entry.left.pos().equals(owner.localPos));
    }
    private static Observed observe(ServerLevel level,ItemStack actual){
        // A normal ItemStack.copy shares mutable component values. A codec round-trip gives
        // this read-only comparison a detached snapshot without making another material owner.
        var ops=level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded=ItemStack.OPTIONAL_CODEC.encodeStart(ops,actual).getOrThrow().copy();
        return new Observed(actual,ItemStack.OPTIONAL_CODEC.parse(ops,encoded).getOrThrow(),actual.getCount());
    }
    public static final class Prepared {
        private final DeployerBlockEntity stationary;
        private final MovementContext moving;
        private final DeployerFakePlayer actor;
        private final ServerLevel level;
        private final Endpoint endpoint;
        private final Contraption structure;
        private final AbstractContraptionEntity movingEntity;
        private final ItemStack original;
        private final Observed observedOriginal;
        private final List<ItemStack> oldQueue,nextQueue;
        private final List<Observed> queueState,inventoryState;
        private final net.minecraft.world.entity.player.Inventory inventory;
        private final int selected;
        private final CompoundTag actorData,blockData,actorSnapshot,blockSnapshot;
        private final Tag encodedOverflow;
        private final Installation proposed;
        private Installation installed;

        private Prepared(DeployerBlockEntity stationary,MovementContext moving,DeployerFakePlayer actor,ServerLevel level,ItemStack original){
            this.stationary=stationary;this.moving=moving;this.actor=actor;this.level=level;this.original=original;
            inventory=actor.getInventory();selected=inventory.getSelectedSlot();
            structure=moving==null?null:moving.contraption;
            movingEntity=structure==null?null:structure.entity;
            endpoint=stationary!=null?new BlockEndpoint(level.dimension(),stationary.getBlockPos().immutable())
                :new ActorEndpoint(level.dimension(),movingEntity.getUUID(),moving.localPos.immutable());
            oldQueue=stationary!=null?stationary.overflowItems:actor.movingOverflow;
            var originalQueue=List.copyOf(oldQueue);
            var next=new ArrayList<>(originalQueue);next.add(original);nextQueue=next;
            queueState=originalQueue.stream().map(stack->observe(level,stack)).toList();
            var slots=new ArrayList<Observed>();for(int i=0;i<inventory.getContainerSize();i++)slots.add(observe(level,inventory.getItem(i)));inventoryState=List.copyOf(slots);
            observedOriginal=observe(level,original);
            if(moving!=null){
                actorData=moving.data;blockData=moving.blockEntityData;actorSnapshot=actorData.copy();blockSnapshot=blockData.copy();
                var outputs=new ArrayList<ItemStack>();for(int i=0;i<inventory.getContainerSize();i++)if(i!=selected&&!inventory.getItem(i).isEmpty())outputs.add(inventory.getItem(i));
                outputs.addAll(nextQueue.stream().filter(stack->!stack.isEmpty()).toList());
                encodedOverflow=ItemStack.CODEC.listOf().encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),outputs).getOrThrow().copy();
            }else{actorData=null;blockData=null;actorSnapshot=null;blockSnapshot=null;encodedOverflow=null;}
            proposed=new Installation(endpoint,stationary!=null?stationary:moving,nextQueue,original);
        }
        public Endpoint endpoint(){return endpoint;}
        public AbstractContraptionEntity movingOwner(){return movingEntity;}
        public boolean validate(){
            if(installed!=null||!observedOriginal.unchanged(original)||!one(original)||actor.getInventory()!=inventory||inventory.getSelectedSlot()!=selected)return false;
            if(stationary!=null){if(!validStationary(stationary,actor,level)||stationary.overflowItems!=oldQueue||stationary.heldItem==original)return false;}
            else if(!validMoving(moving,actor,level)||moving.contraption!=structure||structure.entity!=movingEntity||actor.movingOverflow!=oldQueue
                ||moving.data!=actorData||moving.blockEntityData!=blockData||!actorSnapshot.equals(actorData)||!blockSnapshot.equals(blockData))return false;
            if(oldQueue.size()!=queueState.size()||inventory.getContainerSize()!=inventoryState.size())return false;
            for(int i=0;i<oldQueue.size();i++)if(oldQueue.get(i)==original||!queueState.get(i).unchanged(oldQueue.get(i)))return false;
            for(int i=0;i<inventoryState.size();i++)if(inventory.getItem(i)==original||!inventoryState.get(i).unchanged(inventory.getItem(i)))return false;
            return true;
        }
        /** Called immediately after source retirement in the same callback-free interval. */
        public void installActualPure(ItemStack exactOriginal){
            if(exactOriginal!=original||!validate())throw new IllegalStateException("Actual prepared Deployer output changed before installation");
            if(stationary!=null)stationary.overflowItems=nextQueue;
            else{actor.movingOverflow=nextQueue;blockData.put("Overflow",encodedOverflow);}
            installed=proposed;
        }
        /** Historical actual installation evidence; legitimate later extraction cannot revive the source. */
        public boolean ownsInstalled(ItemStack exactOriginal){
            return installed==proposed&&installed!=null&&installed.original==exactOriginal&&installed.endpoint==endpoint
                &&installed.queue==nextQueue&&installed.owner==(stationary!=null?stationary:moving);
        }
        public void notifyNative(){
            if(!ownsInstalled(original))throw new IllegalStateException("No canonical Deployer output installation");
            if(stationary!=null)stationary.inventoryChanged();else ContraptionNetworking.syncActorData(moving);
        }
    }
    private DeployerCartPickupSink(){}
}
