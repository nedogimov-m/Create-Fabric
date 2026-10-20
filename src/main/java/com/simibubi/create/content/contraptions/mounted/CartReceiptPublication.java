package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.foundation.utility.NativeEntityAcceptance;

import com.simibubi.create.content.contraptions.minecart.capability.MinecartController;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Actual component-preserving coupling refunds; an unavailable owner never becomes a generic item. */
public final class CartReceiptPublication {
    public static int returnRefunds(MinecartController source,net.minecraft.world.entity.player.Player player){
        int returned=0;
        while(returned<1024&&source.hasPendingRefunds()&&returnFirstRefund(source,player))returned++;
        return returned;
    }
    public static boolean returnFirstRefund(MinecartController source,net.minecraft.world.entity.player.Player player){
        var original=source.actualFirstRefund();if(!source.ownsFirstRefund(original))return false;
        if(player instanceof net.fabricmc.fabric.api.entity.FakePlayer){
            var prepared=com.simibubi.create.content.kinetics.deployer.DeployerCartItemSource.prepareRefund(player,original);if(prepared.isEmpty())return false;
            var sink=prepared.get();if(!source.ownsFirstRefund(original)||!sink.validate())return false;
            var actual=source.detachRefund();if(actual!=original)throw new IllegalStateException("Actual paid Deployer refund identity changed");
            sink.installActualPure(actual);if(!sink.ownsInstalled(actual))throw new IllegalStateException("Actual canonical Deployer refund installation was not witnessed");source.sendData();sink.notifyNative();return true;
        }
        var inventory=player instanceof ServerPlayer actual?CartInventoryPublication.plan(actual,original):java.util.Optional.<CartInventoryPublication.Plan>empty();
        if(inventory.isPresent()){
            var sink=inventory.get();if(!source.ownsFirstRefund(original)||!sink.validate())return false;
            // All final predicates are pure. No external callback occurs between these two writes.
            var actual=source.detachRefund();if(actual!=original)throw new IllegalStateException("Actual paid refund identity changed");
            sink.installActual(actual);source.sendData();sink.notifyNative();return true;
        }
        if(!(source.cart().level() instanceof ServerLevel level)||!LoadedEntityAccess.isReady(level,source.cart().blockPosition()))return false;
        var pos=source.cart().position().add(0,.5,0);var entity=new ItemEntity(level,pos.x,pos.y,pos.z,original);
        entity.setDefaultPickUpDelay();if(player!=null&&player.level()==level)entity.setTarget(player.getUUID());
        try(var scope=NativeEntityAcceptance.prepare(entity,()->source.ownsFirstRefund(original)&&entity.getItem()==original&&!entity.isRemoved(),proof->{
            proof.requireCommitting(entity);var actual=source.detachRefund();if(actual!=original)throw new IllegalStateException("Actual paid refund identity changed at native insertion");
        })){
            try{scope.add();return scope.insertion()!=null;}
            finally{if(scope.insertion()!=null)source.sendData();}
        }
    }
    public static boolean returnPackedReceipt(MinecartController source,ServerPlayer player){
        var original=source.actualPackedReceipt();if(!source.ownsPackedReceipt(original))return false;
        var prepared=CartInventoryPublication.plan(player,original);if(prepared.isEmpty())return dropPackedReceipt(source);
        var sink=prepared.get();var retirement=source.preparePackedRetirement(sink);if(!retirement.validate()||!sink.validate())return false;
        retirement.commitPure();sink.installActual(original);
        // The real native inventory owns the value before any menu/tracking/removal callback.
        try{source.sendData();sink.notifyNative();}finally{source.cart().discard();}return true;
    }
    public static boolean returnPackedReceipt(MinecartController source,com.simibubi.create.content.kinetics.deployer.DeployerCartPickupSink.Prepared sink){
        var original=source.actualPackedReceipt();if(!source.ownsPackedReceipt(original))return false;
        var retirement=source.preparePackedRetirement(sink);if(!retirement.validate()||!sink.validate())return false;
        retirement.commitPure();sink.installActualPure(original);if(!sink.ownsInstalled(original))throw new IllegalStateException("Actual canonical Deployer packed receipt installation was not witnessed");
        // Canonical BE/actor queue installation is complete before generic avatar collection.
        try{source.sendData();sink.notifyNative();}finally{source.cart().discard();}return true;
    }
    /** Original complete packed value moves to one real ItemEntity, before its first onCreated callback. */
    public static boolean dropPackedReceipt(MinecartController source){
        var original=source.actualPackedReceipt();if(!source.ownsPackedReceipt(original)||!(source.cart().level() instanceof ServerLevel level)||!LoadedEntityAccess.isReady(level,source.cart().blockPosition()))return false;
        PackedCartData.receiptId(original);var pos=source.cart().position();var entity=new ItemEntity(level,pos.x,pos.y+.5,pos.z,original);entity.setDefaultPickUpDelay();
        try(var scope=NativeEntityAcceptance.prepare(entity,()->source.ownsPackedReceipt(original)&&entity.getItem()==original&&!entity.isRemoved(),proof->source.retirePackedToActualItem(proof,entity))){
            try{scope.add();return scope.insertion()!=null;}
            finally{
                if(scope.insertion()!=null){
                    // Even a callback that throws or destroys the target cannot resurrect the source.
                    // Its concrete retirement ledger suppresses a second vanilla base-cart drop.
                    source.sendData();source.cart().discard();
                }
            }
        }
    }
    private CartReceiptPublication(){}
}
