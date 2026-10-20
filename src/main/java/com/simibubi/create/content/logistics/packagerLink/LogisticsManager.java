package com.simibubi.create.content.logistics.packagerLink;

import com.google.common.collect.*;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.mutable.MutableBoolean;

public final class LogisticsManager {
    record CachedSummary(long tick,InventorySummary summary) {}
    private static final Random RANDOM = new Random();
    public static InventorySummary getSummaryOfNetwork(Level level,UUID id,boolean accurate) {
        if(level.isClientSide())return InventorySummary.EMPTY;
        if(net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.getLifecycle()!=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.Lifecycle.NONE)return createSummaryOfNetwork(level,id);
        var manager=GlobalLogisticsManager.get(level);var cache=accurate?manager.accurateSummaries:manager.summaries;var old=cache.get(id);
        if(old!=null&&manager.ticks()-old.tick()<(accurate?1:20))return old.summary();
        var summary=createSummaryOfNetwork(level,id);cache.put(id,new CachedSummary(manager.ticks(),summary));return summary;
    }
    private static InventorySummary createSummaryOfNetwork(Level level,UUID id) {
        var result=new InventorySummary();var seen=new HashSet<InventoryIdentifier>();
        for(var link:LogisticallyLinkedBehaviour.getAllPresent(level,id,false)){
            var inventory=getInventoryIdentifierFromLink(link);if(inventory!=null&&!seen.add(inventory))continue;
            var summary=link.getSummary(null);if(summary==InventorySummary.EMPTY)continue;result.contributingLinks++;result.add(summary);
        }return result;
    }
    public static int getStockOf(Level level,UUID id,ItemStack stack,IdentifiedInventory ignored) {
        int sum=0;var seen=new HashSet<InventoryIdentifier>();for(var link:LogisticallyLinkedBehaviour.getAllPresent(level,id,false)){var inventory=getInventoryIdentifierFromLink(link);if(inventory!=null&&!seen.add(inventory))continue;sum=(int)Math.min(BigItemStack.INF,(long)sum+link.getSummary(ignored).getCountOf(stack));}return sum;
    }
    public static boolean broadcastPackageRequest(Level level,UUID id,LogisticallyLinkedBehaviour.RequestType type,PackageOrderWithCrafts order,IdentifiedInventory ignored,String address) {
        if(level.isClientSide()||order.isEmpty())return false;
        var requests=findPackagersForRequest(level,id,order,ignored,address);
        for(var packager:requests.keySet())if(packager.isTooBusyFor(type))return false;
        performPackageRequests(requests);GlobalLogisticsManager.get(level).invalidate(id);return true;
    }
    /** All requested goods must be boxed in the caller's payment transaction. */
    public static boolean dispatchPurchase(Level level,UUID id,PackageOrderWithCrafts order,String address,net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction){
        if(level.isClientSide()||order.isEmpty())return false;
        var requests=findPackagersForRequest(level,id,order,null,address);var expected=new InventorySummary();var allocated=new InventorySummary();
        for(var entry:order.stacks())expected.add(entry);for(var request:requests.values())allocated.add(request.item(),request.getCount());
        for(var entry:expected.getStacksByCount())if(allocated.getCountOf(entry.stack)!=entry.count)return false;
        for(var packager:requests.keySet())if(packager.isTooBusyFor(LogisticallyLinkedBehaviour.RequestType.PLAYER))return false;
        try(var nested=transaction.openNested()){
            for(var entry:requests.asMap().entrySet())if(!entry.getKey().fulfillRequests(new ArrayList<>(entry.getValue()),nested))return false;
            nested.addOuterCloseCallback(result->{if(result.wasCommitted()){GlobalLogisticsManager.get(level).invalidate(id);for(var packager:requests.keySet()){packager.flashLink();packager.inventoryTracker.reset();packager.notifyUpdate();}}});
            nested.commit();
        }
        return true;
    }
    public static Multimap<PackagerBlockEntity,PackagingRequest> findPackagersForRequest(Level level,UUID id,PackageOrderWithCrafts order,IdentifiedInventory ignored,String address) {
        var stacks=order.stacks().stream().filter(entry->!entry.stack.isEmpty()&&entry.count>0).toList();
        Multimap<PackagerBlockEntity,PackagingRequest> requests=ArrayListMultimap.create();
        var groups=new LinkedHashMap<InventoryIdentifier,List<LogisticallyLinkedBehaviour>>();var available=new ArrayList<LogisticallyLinkedBehaviour>();
        for(var link:LogisticallyLinkedBehaviour.getAllPresent(level,id,true)){var inventory=getInventoryIdentifierFromLink(link);if(inventory==null)available.add(link);else groups.computeIfAbsent(inventory,ignoredId->new ArrayList<>()).add(link);}
        groups.values().forEach(group->available.add(group.get(RANDOM.nextInt(group.size()))));
        // Grouping must retain redstone priority between independent inventories.
        available.sort(Comparator.comparingInt(link->link.redstonePower));
        var used=new ArrayList<LogisticallyLinkedBehaviour>();var finalTracker=new MutableBoolean(false);PackageOrderWithCrafts context=order;int orderId=RANDOM.nextInt();
        var reserved=new IdentityHashMap<LogisticallyLinkedBehaviour,InventorySummary>();
        for(int i=0;i<stacks.size();i++){
            var entry=stacks.get(i);int remaining=entry.count;
            for(var link:available){
                int usedIndex=used.indexOf(link),index=usedIndex<0?used.size():usedIndex;var last=index==used.size()-1?finalTracker:new MutableBoolean(false);
                var reservation=reserved.computeIfAbsent(link,key->new InventorySummary());int already=reservation.getCountOf(entry.stack);
                var request=link.processRequest(entry.stack,remaining+already,address,index,last,orderId,context,ignored);if(request==null)continue;
                int count=Math.min(remaining,Math.max(0,request.getSecond().getCount()-already));if(count==0)continue;request.getSecond().count().setValue(count);reservation.add(entry.stack,count);
                requests.put(request.getFirst(),request.getSecond());
                if(usedIndex<0){context=null;used.add(link);finalTracker=last;}remaining-=count;
                if(remaining>0)continue;if(i==stacks.size()-1)finalTracker.setTrue();break;
            }
        }return requests;
    }
    private static InventoryIdentifier getInventoryIdentifierFromLink(LogisticallyLinkedBehaviour link) {
        if(!(link.blockEntity instanceof PackagerLinkBlockEntity entity))return null;var packager=entity.getPackager();if(packager==null)return null;var inventory=packager.getIdentifiedInventory();return inventory==null?null:inventory.identifier();
    }
    public static void performPackageRequests(Multimap<PackagerBlockEntity,PackagingRequest> requests) {
        for(var entry:requests.asMap().entrySet()){
            var pending=new ArrayList<>(entry.getValue());var packager=entry.getKey();if(!pending.isEmpty())packager.flashLink();
            for(int i=0;i<100&&!pending.isEmpty();i++)packager.attemptToSend(pending);
            packager.triggerStockCheck();packager.notifyUpdate();
        }
    }
    private LogisticsManager() {}
}
