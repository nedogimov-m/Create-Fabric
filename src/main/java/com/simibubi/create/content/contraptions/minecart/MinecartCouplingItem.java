package com.simibubi.create.content.contraptions.minecart;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.content.contraptions.mounted.CartReceiptPublication;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Source coupling/wrench gestures. A client request cannot substitute for either actual server click. */
public final class MinecartCouplingItem extends Item {
    private static BiConsumer<Player,AbstractMinecart> clientClick=(player,cart)->{};
    private static final Map<Player,Gesture> GESTURES=new WeakHashMap<>();
    private static final class Gesture {
        final WeakReference<AbstractMinecart> first;WeakReference<AbstractMinecart> second;CouplingCreationPacket request;
        Gesture(AbstractMinecart first){this.first=new WeakReference<>(first);}
    }
    public MinecartCouplingItem(Properties properties){super(properties);}
    public static void setClientClickHandler(BiConsumer<Player,AbstractMinecart> callback){clientClick=Objects.requireNonNull(callback);}
    private static boolean holdsCoupling(Player player){return player.getMainHandItem().is(AllItems.MINECART_COUPLING)||player.getOffhandItem().is(AllItems.MINECART_COUPLING);}
    private static boolean realClick(Player player,AbstractMinecart cart){
        return !player.isSpectator()&&player.isAlive()&&player.level()==cart.level()&&player.isWithinEntityInteractionRange(cart,0)
            &&player.level().mayInteract(player,cart.blockPosition())&&CapabilityMinecartController.getIfPresent(cart.level(),cart.getUUID())!=null;
    }
    private static void clicked(Player player,AbstractMinecart cart){
        if(!realClick(player,cart))return;var existing=GESTURES.get(player);var first=existing==null?null:existing.first.get();
        if(first==null||first==cart||first.level()!=cart.level()||first.isRemoved()) {GESTURES.put(player,new Gesture(cart));return;}
        existing.second=new WeakReference<>(cart);
        if(player instanceof FakePlayer){GESTURES.remove(player);CouplingHandler.tryToCoupleCarts(player,cart.level(),first.getId(),cart.getId());}
        else complete(player,existing);
    }
    public static boolean acceptCreation(ServerPlayer player,CouplingCreationPacket packet){
        var gesture=GESTURES.get(player);if(gesture==null||!holdsCoupling(player))return false;var first=gesture.first.get();
        if(first==null||first.getId()!=packet.firstId()||!first.getUUID().equals(packet.firstUuid())||first.level()!=player.level())return false;
        gesture.request=packet;return complete(player,gesture);
    }
    private static boolean complete(Player player,Gesture gesture){
        var packet=gesture.request;var first=gesture.first.get();var second=gesture.second==null?null:gesture.second.get();
        if(packet==null||first==null||second==null)return false;
        if(first.getId()!=packet.firstId()||!first.getUUID().equals(packet.firstUuid())||second.getId()!=packet.secondId()||!second.getUUID().equals(packet.secondUuid())||!realClick(player,second))return false;
        GESTURES.remove(player);return CouplingHandler.tryToCoupleCarts(player,player.level(),first.getId(),second.getId());
    }
    public static InteractionResult interact(Player player,Level world,InteractionHand hand,net.minecraft.world.entity.Entity entity){
        if(!(entity instanceof AbstractMinecart cart))return InteractionResult.PASS;
        var held=player.getItemInHand(hand);if(!held.is(AllItems.MINECART_COUPLING)&&!held.is(AllItems.WRENCH))return InteractionResult.PASS;
        var controller=CapabilityMinecartController.getIfPresent(world,cart.getUUID());if(controller==null||controller.nativeBaseIsInactive())return InteractionResult.PASS;
        if(held.is(AllItems.MINECART_COUPLING)){
            if(controller.isFullyCoupled()){if(!world.isClientSide())CouplingHandler.status(player,"two_couplings_max");return InteractionResult.SUCCESS;}
            if(world.isClientSide())clientClick.accept(player,cart);else clicked(player,cart);return InteractionResult.SUCCESS;
        }
        boolean ordinary=controller.isLeadingCoupling()&&!controller.hasContraptionCoupling(true)||controller.isConnectedToCoupling()&&!controller.hasContraptionCoupling(false);
        if(!ordinary)return InteractionResult.PASS;
        if(world.isClientSide())return InteractionResult.SUCCESS;if(!realClick(player,cart))return InteractionResult.FAIL;
        var receiptOwners=new LinkedHashSet<MinecartController>();
        if(controller.isLeadingCoupling()&&!controller.hasContraptionCoupling(true))receiptOwners.add(controller);
        if(controller.isConnectedToCoupling()&&!controller.hasContraptionCoupling(false)){
            var main=CapabilityMinecartController.getIfPresent(world,controller.getCoupledCart(false));if(main==null)return InteractionResult.FAIL;receiptOwners.add(main);
        }
        if(CouplingHandler.detachOrdinaryLinks(controller,!player.isCreative())){
            CouplingHandler.status(player,"removed");
            // Only these actual detached edges' source owners may publish their exact receipts.
            for(var source:receiptOwners)if(source.hasPendingRefunds())CartReceiptPublication.returnRefunds(source,player);
        }
        return InteractionResult.SUCCESS;
    }
    public static void register(){
        CouplingCreationPacket.register();UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->interact(player,world,hand,entity));
        ServerTickEvents.END_SERVER_TICK.register(server->GESTURES.entrySet().removeIf(entry->{var first=entry.getValue().first.get();return first==null||first.isRemoved()||entry.getKey().isRemoved()||entry.getKey().level()!=first.level()||!holdsCoupling(entry.getKey());}));
    }
}
