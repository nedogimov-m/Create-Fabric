package com.simibubi.create.content.logistics.tableCloth;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.stockTicker.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Payment, stock extraction and output parcel ownership commit together. */
public final class ShopTrade {
    private static boolean deny(Player player,StockTickerBlockEntity ticker,String key){player.sendOverlayMessage(Component.translatable("create.stock_keeper."+key).withStyle(net.minecraft.ChatFormatting.RED));player.level().playSound(null,ticker.getBlockPos(),AllSoundEvents.DENY,SoundSource.BLOCKS,.5f,1);return false;}
    public static boolean purchase(Player player,StockTickerBlockEntity ticker){
        var level=player.level();var held=player.getMainHandItem();var list=ShoppingListItem.getList(held);
        if(level.isClientSide()||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,ticker.getBlockPos())||!player.isWithinBlockInteractionRange(ticker.getBlockPos(),2)||!held.is(AllItems.SHOPPING_LIST)||list==null||!ticker.isKeeperPresent())return false;
        if(!ticker.behaviour.freqId.equals(list.shopNetwork()))return deny(player,ticker,"wrong_network");
        var baked=list.bakeEntries(level,null);if(!baked.valid()||baked.goods().isEmpty())return deny(player,ticker,"stock_level_too_low");
        String address=ShoppingListItem.getAddress(held);if(address.length()>25)return false;
        var order=PackageOrderWithCrafts.simple(baked.goods().getStacksByCount());if(!StockTickerMenu.validOrder(order))return false;
        GlobalLogisticsManager.get(level).invalidate(ticker.behaviour.freqId);
        var stock=ticker.getAccurateSummary();for(var entry:order.stacks())if(stock.getCountOf(entry.stack)<entry.count)return deny(player,ticker,"stock_level_too_low");
        var playerStorage=PlayerInventoryStorage.of(player);var main=new CombinedStorage<>(playerStorage.getSlots().subList(0,Math.min(36,playerStorage.getSlotCount())));
        try(var transaction=Transaction.openOuter()){
            for(var payment:baked.payments().getStacksByCount())if(ticker.receivedPayments.automation.insert(ItemVariant.of(payment.stack),payment.count,transaction)!=payment.count)return deny(player,ticker,"cash_register_full");
            for(var payment:baked.payments().getStacksByCount())if(main.extract(ItemVariant.of(payment.stack),payment.count,transaction)!=payment.count)return deny(player,ticker,"too_broke");
            if(playerStorage.getHandSlot(InteractionHand.MAIN_HAND).extract(ItemVariant.of(held),1,transaction)!=1)return false;
            if(!LogisticsManager.dispatchPurchase(level,ticker.behaviour.freqId,order,address,transaction))return deny(player,ticker,"stock_level_too_low");
            transaction.commit();
        }
        ticker.previouslyUsedAddress=address;ticker.notifyUpdate();level.playSound(null,ticker.getBlockPos(),AllSoundEvents.STOCK_TICKER_TRADE,SoundSource.BLOCKS,1,1);return true;
    }
    private ShopTrade(){}
}
