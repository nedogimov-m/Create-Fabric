package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.content.contraptions.ContraptionAssemblyGraph;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.mounted.CartItemPayment;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayDeque;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Explicit action context; no fake-profile UUID, scans, guessed owner or hand-preview payment. */
public final class DeployerCartItemSource implements AutoCloseable {
    private static final ThreadLocal<ArrayDeque<DeployerCartItemSource>> ACTIVE=ThreadLocal.withInitial(ArrayDeque::new);
    private final DeployerFakePlayer player;
    private final DeployerBlockEntity stationary;
    private final MovementContext moving;
    private boolean closed;
    private DeployerCartItemSource(DeployerFakePlayer player,DeployerBlockEntity stationary,MovementContext moving){
        this.player=player;this.stationary=stationary;this.moving=moving;ACTIVE.get().push(this);
    }
    public static DeployerCartItemSource stationary(DeployerBlockEntity owner,DeployerFakePlayer player){return new DeployerCartItemSource(player,owner,null);}
    public static DeployerCartItemSource moving(MovementContext owner,DeployerFakePlayer player){return new DeployerCartItemSource(player,null,owner);}
    private boolean valid(){
        if(closed||ACTIVE.get().peek()!=this||!player.acting||player.isRemoved())return false;
        if(stationary!=null)return stationary.getLevel()==player.level()&&!stationary.isRemoved()
            &&LoadedLevelAccess.blockEntity(player.level(),stationary.getBlockPos())==stationary
            &&stationary.ownsCartActor(player)&&ItemStack.matches(stationary.heldItem,player.getMainHandItem());
        if(moving==null||moving.world!=player.level()||moving.temporaryData!=player||moving.contraption==null)return false;
        var structure=moving.contraption;var owner=structure.entity;
        return owner!=null&&owner.level()==moving.world&&owner.getContraption()==structure&&owner.hasOwnedStructure()
            &&moving.world instanceof ServerLevel server&&server.getEntity(owner.getUUID())==owner
            &&LoadedEntityAccess.isReady(server,owner.blockPosition())&&!ContraptionAssemblyGraph.isRecovering(owner)
            &&ContraptionAssemblyGraph.isTransferPermitted(structure)
            &&structure.getActors().stream().anyMatch(actor->actor.right==moving&&actor.left.pos().equals(moving.localPos));
    }
    public static DeployerFakePlayer activeActor(){var scope=ACTIVE.get().peek();return scope!=null&&scope.valid()?scope.player:null;}
    public static java.util.Optional<DeployerCartPickupSink.Prepared> prepareRefund(Player actual,ItemStack original){
        var scope=ACTIVE.get().peek();if(scope==null||scope.player!=actual||!scope.valid())return java.util.Optional.empty();
        return scope.stationary!=null?DeployerCartPickupSink.prepareStationary(scope.stationary,scope.player,original):DeployerCartPickupSink.prepareMoving(scope.moving,scope.player,original);
    }
    private static DeployerCartItemSource pickupScope(Player actual){
        var scope=ACTIVE.get().peek();return scope!=null&&scope.player==actual&&scope.valid()&&!(scope.moving!=null&&scope.moving.contraption instanceof com.simibubi.create.content.contraptions.mounted.MountedContraption)?scope:null;
    }
    public static boolean canPickup(Player actual){return pickupScope(actual)!=null;}
    public static com.simibubi.create.content.contraptions.AbstractContraptionEntity activeMovingOwner(Player actual){var scope=pickupScope(actual);return scope==null||scope.moving==null?null:scope.moving.contraption.entity;}
    public static java.util.Optional<DeployerCartPickupSink.Prepared> preparePickup(Player actual,ItemStack original){
        var scope=pickupScope(actual);if(scope==null)return java.util.Optional.empty();
        return scope.stationary!=null?DeployerCartPickupSink.prepareStationary(scope.stationary,scope.player,original):DeployerCartPickupSink.prepareMoving(scope.moving,scope.player,original);
    }
    public static CartItemPayment.Source active(Player actual,InteractionHand hand){
        var scope=ACTIVE.get().peek();if(scope==null||scope.player!=actual||hand!=InteractionHand.MAIN_HAND||!scope.valid())return null;
        var inventory=scope.player.getInventory();int selected=inventory.getSelectedSlot();
        return new CartItemPayment.Source(){
            @Override public ItemStack actual(){return scope.stationary!=null?scope.stationary.heldItem:inventory.getNonEquipmentItems().get(selected);}
            @Override public boolean valid(){return scope.valid()&&scope.player.getInventory()==inventory&&inventory.getSelectedSlot()==selected;}
            @Override public void replacePure(ItemStack remaining,Tag encoded){
                if(scope.stationary!=null){
                    // Both the saved BE hand and its disposable avatar change before native callbacks.
                    scope.stationary.heldItem=remaining;
                    inventory.getNonEquipmentItems().set(selected,remaining.isEmpty()?ItemStack.EMPTY:remaining.copy());
                }else{
                    // writeActors flushes this actual hand again before root storage/block serialization.
                    inventory.getNonEquipmentItems().set(selected,remaining);
                    scope.moving.data.put("HeldItem",encoded.copy());scope.moving.blockEntityData.put("HeldItem",encoded.copy());
                }
            }
            @Override public void notifyNative(){
                if(scope.stationary!=null){scope.stationary.inventoryChanged();}
                else if(scope.moving.contraption.entity!=null)com.simibubi.create.content.contraptions.ContraptionNetworking.syncActorData(scope.moving);
            }
        };
    }
    @Override public void close(){if(closed)return;if(ACTIVE.get().peek()!=this)throw new IllegalStateException("Deployer action scopes must close in source order");ACTIVE.get().pop();closed=true;if(ACTIVE.get().isEmpty())ACTIVE.remove();}
}
