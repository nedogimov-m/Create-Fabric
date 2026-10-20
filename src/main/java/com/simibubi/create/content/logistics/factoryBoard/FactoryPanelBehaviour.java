package com.simibubi.create.content.logistics.factoryBoard;

import com.google.common.collect.*;
import com.simibubi.create.content.logistics.BigItemStack;

import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.stockTicker.*;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;

/** Source per-quadrant task, stock monitoring and recipe/restocker request rules. */
public final class FactoryPanelBehaviour extends FilteringBehaviour {
    public static final BehaviourType<FactoryPanelBehaviour> TOP_LEFT=new BehaviourType<>(),TOP_RIGHT=new BehaviourType<>(),BOTTOM_LEFT=new BehaviourType<>(),BOTTOM_RIGHT=new BehaviourType<>();
    public final Map<FactoryPanelPosition,FactoryPanelConnection> targetedBy=new LinkedHashMap<>();
    public final Map<BlockPos,FactoryPanelConnection> targetedByLinks=new LinkedHashMap<>();
    public final Set<FactoryPanelPosition> targeting=new LinkedHashSet<>();
    public List<ItemStack> activeCraftingArrangement=List.of();
    public boolean satisfied,promisedSatisfied,waitingForNetwork,forceClearPromises,redstonePowered,active;
    public String recipeAddress="";
    public int recipeOutput=1,promiseClearingInterval=-1;
    public UUID network=UUID.randomUUID();
    public final FactoryPanelBlock.PanelSlot slot;
    public RequestPromiseQueue restockerPromises;
    private int timer,lastReportedUnloadedLinks,lastReportedLevelInStorage,lastReportedPromises;
    private float previousBulb,bulb;
    public FactoryPanelBehaviour(FactoryPanelBlockEntity owner,FactoryPanelBlock.PanelSlot slot) { super(owner);this.slot=slot;count=0;restockerPromises=new RequestPromiseQueue(owner::setChanged);setLazyTickRate(40); }
    public static FactoryPanelBehaviour at(BlockGetter level,FactoryPanelPosition pos) { if(level instanceof Level world&&!world.isLoaded(pos.pos()))return null;var be=LoadedLevelAccess.blockEntity(level,pos.pos());if(!(be instanceof FactoryPanelBlockEntity panel))return null;var result=panel.panels.get(pos.slot());return result!=null&&result.active?result:null; }
    public static FactoryPanelBehaviour at(BlockGetter level,FactoryPanelConnection connection) { var result=at(level,connection.from);connection.cachedSource=new java.lang.ref.WeakReference<>(result);return result; }
    public static FactoryPanelSupportBehaviour linkAt(BlockGetter level,FactoryPanelPosition pos) { if(level instanceof Level world&&!world.isLoaded(pos.pos()))return null;return BlockEntityBehaviour.get(level,pos.pos(),FactoryPanelSupportBehaviour.TYPE); }
    public static FactoryPanelSupportBehaviour linkAt(BlockGetter level,FactoryPanelConnection connection) { var result=linkAt(level,connection.from);connection.cachedSource=new java.lang.ref.WeakReference<>(result);return result; }
    public FactoryPanelBlockEntity panelBE() { return (FactoryPanelBlockEntity)blockEntity; }
    public FactoryPanelPosition getPanelPosition() { return new FactoryPanelPosition(getPos(),slot); }
    public void setNetwork(UUID id) { network=Objects.requireNonNull(id);resetTimerSlightly();panelBE().notifyUpdate(); }
    @Override public boolean isActive() { return active; }
    public void enable() { active=true;panelBE().notifyUpdate(); }
    public void disable() { disconnectAll();active=false;count=0;super.setFilter(ItemStack.EMPTY);satisfied=promisedSatisfied=waitingForNetwork=redstonePowered=false;recipeAddress="";recipeOutput=1;restockerPromises=new RequestPromiseQueue(blockEntity::setChanged);activeCraftingArrangement=List.of();panelBE().notifyUpdate(); }
    @Override public boolean setFilter(ItemStack sample) { if(com.simibubi.create.content.logistics.filter.FilterItemStack.of(sample).isFilterItem())return false;filter=com.simibubi.create.content.logistics.filter.FilterItemStack.of(sample.copyWithCount(1));panelBE().notifyUpdate();return true; }
    @Override public void setAmount(int value,boolean items) { count=Math.clamp(value,0,100);upTo=items;resetTimerSlightly();panelBE().notifyUpdate();notifyRedstoneOutputs(); }
    public int demand() { return count*(upTo?1:getFilter().getMaxStackSize()); }
    public boolean isMissingAddress() { return (!targetedBy.isEmpty()||panelBE().restocker)&&count!=0&&recipeAddress.isBlank(); }
    public float getBulb(float partial) { return previousBulb+(bulb-previousBulb)*partial; }
    public void pulse(FactoryPanelPosition from,boolean success) {
        var c=targetedBy.get(from);if(c!=null)c.success=success;
        if(getWorld() instanceof net.minecraft.server.level.ServerLevel level){
            var packet=new FactoryPanelEffectPacket(from,getPanelPosition(),success);
            for(var player:net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(level,getPos()))net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,packet);
        }
    }
    public void receivePulse(FactoryPanelPosition from,boolean success) { previousBulb=bulb=1;var connection=targetedBy.get(from);if(connection!=null)connection.success=success; }
    @Override public void tick() {
        super.tick();if(!active)return;
        if(getWorld().isClientSide()){previousBulb=bulb;bulb+=(redstonePowered||satisfied?1-bulb:-bulb)*.175f;return;}
        if(Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)return;
        tickStorageMonitor();tickRequests();
    }
    @Override public void lazyTick() { if(!getWorld().isClientSide())checkForRedstoneInput(); }
    public void checkForRedstoneInput() { if(!active)return;boolean next=false;for(var connection:targetedByLinks.values()){if(!getWorld().isLoaded(connection.from.pos()))return;var link=linkAt(getWorld(),connection);if(link==null)return;next|=link.shouldPanelBePowered();}if(next!=redstonePowered){redstonePowered=next;timer=1;panelBE().notifyUpdate();} }
    private void notifyRedstoneOutputs() { if(getWorld()==null||getWorld().isClientSide())return;for(var connection:targetedByLinks.values()){var link=linkAt(getWorld(),connection);if(link!=null&&!link.isOutput())link.notifyLink();} }
    private void tickStorageMonitor() {
        int unloaded=getUnloadedLinks();if(!panelBE().restocker&&unloaded==0&&lastReportedUnloadedLinks!=0)GlobalLogisticsManager.get(getWorld()).invalidate(network);
        int stock=getLevelInStorage(),promised=getPromised(),demand=demand();boolean next=getFilter().isEmpty()||stock>=demand,nextPromise=getFilter().isEmpty()||(long)stock+promised>=demand,waiting=unloaded>0;
        if(stock==lastReportedLevelInStorage&&promised==lastReportedPromises&&unloaded==lastReportedUnloadedLinks&&next==satisfied&&nextPromise==promisedSatisfied&&waiting==waitingForNetwork)return;
        boolean notify=next!=satisfied;lastReportedLevelInStorage=stock;lastReportedPromises=promised;lastReportedUnloadedLinks=unloaded;satisfied=next;promisedSatisfied=nextPromise;waitingForNetwork=waiting;panelBE().notifyUpdate();if(notify)notifyRedstoneOutputs();
    }
    private record IngredientGroup(ItemStack item,List<FactoryPanelConnection> connections,int count) {}
    private void tickRequests() {
        if(targetedBy.isEmpty()&&!panelBE().restocker)return;
        if(panelBE().restocker)restockerPromises.tick();
        if(satisfied||promisedSatisfied||waitingForNetwork||redstonePowered)return;
        if(timer>0){timer=Math.min(timer,requestInterval());timer--;return;}resetTimer();
        if(recipeAddress.isBlank()||getFilter().isEmpty())return;
        if(panelBE().restocker){tryRestock();return;}
        var grouped=new LinkedHashMap<UUID,Map<ItemVariant,IngredientGroup>>();
        for(var connection:targetedBy.values()){
            var source=at(getWorld(),connection);if(source==null)return;var item=source.getFilter();var ingredients=grouped.computeIfAbsent(source.network,id->new LinkedHashMap<>());var variant=ItemVariant.of(item);var previous=ingredients.get(variant);var inputs=previous==null?new ArrayList<FactoryPanelConnection>():previous.connections();inputs.add(connection);ingredients.put(variant,new IngredientGroup(item,inputs,(previous==null?0:previous.count())+connection.amount));
        }
        boolean failed=false;var orders=new LinkedHashMap<UUID,List<BigItemStack>>();
        for(var entry:grouped.entrySet()){
            var summary=LogisticsManager.getSummaryOfNetwork(getWorld(),entry.getKey(),true);
            for(var ingredient:entry.getValue().values()){
                boolean success=ingredient.count()>0&&!ingredient.item().isEmpty()&&summary.getCountOf(ingredient.item())>=ingredient.count();for(var connection:ingredient.connections())pulse(connection.from,success);if(!success){failed=true;continue;}orders.computeIfAbsent(entry.getKey(),id->new ArrayList<>()).add(new BigItemStack(ingredient.item().copy(),ingredient.count()));
            }
        }
        if(failed)return;
        var contexts=activeCraftingArrangement.isEmpty()?List.<PackageOrderWithCrafts.CraftingEntry>of():PackageOrderWithCrafts.singleRecipe(activeCraftingArrangement.stream().map(stack->new BigItemStack(stack.copyWithCount(1))).toList()).orderedCrafts();
        var requests=new ArrayList<Multimap<PackagerBlockEntity,PackagingRequest>>();
        for(var entry:orders.entrySet())requests.add(LogisticsManager.findPackagersForRequest(getWorld(),entry.getKey(),new PackageOrderWithCrafts(new PackageOrder(entry.getValue()),contexts),null,recipeAddress));
        for(var request:requests)for(var packager:request.keySet())if(packager.isTooBusyFor(LogisticallyLinkedBehaviour.RequestType.RESTOCK))return;
        for(var request:requests)LogisticsManager.performPackageRequests(request);
        var promises=GlobalLogisticsManager.get(getWorld()).getQueuedPromises(network);if(promises!=null)promises.add(new RequestPromise(new BigItemStack(getFilter().copy(),recipeOutput)));
        panelBE().sourceAdvancements.award("factory_gauge");
    }
    private void tryRestock() {
        var packager=panelBE().getRestockedPackager();if(packager==null||packager.targetInventory()==null)return;var item=getFilter();int available=LogisticsManager.getStockOf(getWorld(),network,item,packager.getIdentifiedInventory());
        if(available==0){pulse(getPanelPosition(),false);return;}int count=Math.min(available,Math.clamp(demand()-getLevelInStorage()-getPromised(),0,item.getMaxStackSize()*9));if(count<=0)return;
        var ordered=new BigItemStack(item.copy(),count);pulse(getPanelPosition(),true);
        if(LogisticsManager.broadcastPackageRequest(getWorld(),network,LogisticallyLinkedBehaviour.RequestType.RESTOCK,PackageOrderWithCrafts.simple(List.of(ordered)),packager.getIdentifiedInventory(),recipeAddress))restockerPromises.add(new RequestPromise(ordered));
    }
    public boolean addConnection(FactoryPanelPosition from) {
        var support=linkAt(getWorld(),from);if(support!=null){if(!getPos().closerThan(from.pos(),16))return false;targetedByLinks.put(from.pos(),new FactoryPanelConnection(from,1));support.connect(this);panelBE().notifyUpdate();return true;}
        var source=at(getWorld(),from);if(source==null||source==this||panelBE().restocker||targetedBy.size()>=9||targetedBy.containsKey(from)||getFilter().isEmpty()||source.getFilter().isEmpty())return false;
        var a=blockEntity.getBlockState();var b=source.blockEntity.getBlockState();var delta=getPos().subtract(from.pos());if(a.setValue(FactoryPanelBlock.POWERED,false).setValue(FactoryPanelBlock.WATERLOGGED,false)!=b.setValue(FactoryPanelBlock.POWERED,false).setValue(FactoryPanelBlock.WATERLOGGED,false)||FactoryPanelBlock.connectedDirection(a).getAxis().choose(delta.getX(),delta.getY(),delta.getZ())!=0||!getPos().closerThan(from.pos(),16))return false;
        targetedBy.put(from,new FactoryPanelConnection(from,1));source.targeting.add(getPanelPosition());source.panelBE().notifyUpdate();panelBE().notifyUpdate();return true;
    }
    public void removeConnection(FactoryPanelPosition from) { targetedBy.remove(from);var source=at(getWorld(),from);if(source!=null){source.targeting.remove(getPanelPosition());source.panelBE().notifyUpdate();}panelBE().notifyUpdate(); }
    public void disconnectAllLinks() { for(var c:targetedByLinks.values()){var source=linkAt(getWorld(),c);if(source!=null)source.disconnect(this);}targetedByLinks.clear();redstonePowered=false; }
    public void disconnectAll() { disconnectAllLinks();for(var c:List.copyOf(targetedBy.values()))removeConnection(c.from);for(var target:List.copyOf(targeting)){var panel=at(getWorld(),target);if(panel!=null){panel.targetedBy.remove(getPanelPosition());panel.panelBE().notifyUpdate();}}targeting.clear(); }
    @Override public void destroy() { disconnectAll(); }
    public int getUnloadedLinks() { if(getWorld().isClientSide())return lastReportedUnloadedLinks;if(panelBE().restocker)return panelBE().getRestockedPackager()==null?1:0;return GlobalLogisticsManager.get(getWorld()).getUnloadedLinkCount(network); }
    public int getLevelInStorage() { if(getWorld().isClientSide())return lastReportedLevelInStorage;if(getFilter().isEmpty())return 0;var packager=panelBE().getRestockedPackager();var summary=panelBE().restocker?packager==null?InventorySummary.EMPTY:packager.getAvailableItems():LogisticsManager.getSummaryOfNetwork(getWorld(),network,false);return summary.getCountOf(getFilter()); }
    public int getPromised() {
        if(getWorld().isClientSide())return lastReportedPromises;if(getFilter().isEmpty())return 0;var queue=panelBE().restocker?restockerPromises:GlobalLogisticsManager.get(getWorld()).getQueuedPromises(network);if(queue==null)return 0;if(forceClearPromises){queue.forceClear(getFilter());forceClearPromises=false;resetTimerSlightly();}return queue.getTotalPromisedAndRemoveExpired(getFilter(),promiseClearingInterval==-1?-1:promiseClearingInterval==0?600:promiseClearingInterval*1200);
    }
    public void resetTimer() { timer=requestInterval(); }
    public void resetTimerSlightly() { timer=requestInterval()/2; }
    public static int requestInterval() { return com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.factoryGaugeTimer.get(); }
    public int getIngredientStatusColor() { return count==0||isMissingAddress()||redstonePowered?0x888898:waitingForNetwork?0x5B3B3B:satisfied?0x9EFF7F:promisedSatisfied?0x22AFAF:0x3D6EBD; }
    @Override public BehaviourType<?> getType() { return getTypeForSlot(slot); }
    public static BehaviourType<FactoryPanelBehaviour> getTypeForSlot(FactoryPanelBlock.PanelSlot slot) { return switch(slot){case TOP_LEFT->TOP_LEFT;case TOP_RIGHT->TOP_RIGHT;case BOTTOM_LEFT->BOTTOM_LEFT;case BOTTOM_RIGHT->BOTTOM_RIGHT;}; }
    @Override public void write(ValueOutput out) {
        if(!active)return;var data=out.child(slot.getSerializedName());super.write(data);data.store("Freq",UUIDUtil.CODEC,network);data.putInt("Timer",timer);data.putInt("LastLevel",lastReportedLevelInStorage);data.putInt("LastPromised",lastReportedPromises);data.putInt("LastUnloadedLinks",lastReportedUnloadedLinks);data.putBoolean("Satisfied",satisfied);data.putBoolean("PromisedSatisfied",promisedSatisfied);data.putBoolean("Waiting",waitingForNetwork);data.putBoolean("RedstonePowered",redstonePowered);data.putString("RecipeAddress",recipeAddress);data.putInt("RecipeOutput",recipeOutput);data.putInt("PromiseClearingInterval",promiseClearingInterval);data.store("Targeting",FactoryPanelPosition.CODEC.listOf(),List.copyOf(targeting));data.store("TargetedBy",FactoryPanelConnection.CODEC.listOf(),List.copyOf(targetedBy.values()));data.store("TargetedByLinks",FactoryPanelConnection.CODEC.listOf(),List.copyOf(targetedByLinks.values()));data.store("Craft",ItemStack.OPTIONAL_CODEC.listOf(),activeCraftingArrangement);
        if(panelBE().restocker)data.store("Promises",RequestPromiseQueue.CODEC,restockerPromises);
    }
    @Override public void read(ValueInput in) {
        var child=in.child(slot.getSerializedName());active=child.isPresent();if(!active)return;var data=child.get();super.read(data);count=Math.clamp(data.getIntOr("FilterAmount",0),0,100);upTo=data.getBooleanOr("UpTo",true);network=data.read("Freq",UUIDUtil.CODEC).orElse(network);timer=Math.clamp(data.getIntOr("Timer",0),0,requestInterval());lastReportedLevelInStorage=data.getIntOr("LastLevel",0);lastReportedPromises=data.getIntOr("LastPromised",0);lastReportedUnloadedLinks=data.getIntOr("LastUnloadedLinks",0);satisfied=data.getBooleanOr("Satisfied",false);promisedSatisfied=data.getBooleanOr("PromisedSatisfied",false);waitingForNetwork=data.getBooleanOr("Waiting",false);redstonePowered=data.getBooleanOr("RedstonePowered",false);recipeAddress=data.getStringOr("RecipeAddress","");recipeOutput=Math.clamp(data.getIntOr("RecipeOutput",1),1,64);promiseClearingInterval=Math.clamp(data.getIntOr("PromiseClearingInterval",-1),-1,30);targeting.clear();targeting.addAll(data.read("Targeting",FactoryPanelPosition.CODEC.listOf()).orElse(List.of()));targetedBy.clear();data.read("TargetedBy",FactoryPanelConnection.CODEC.listOf()).orElse(List.of()).forEach(c->targetedBy.put(c.from,c));targetedByLinks.clear();data.read("TargetedByLinks",FactoryPanelConnection.CODEC.listOf()).orElse(List.of()).forEach(c->targetedByLinks.put(c.from.pos(),c));activeCraftingArrangement=data.read("Craft",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());if(getWorld()==null||!getWorld().isClientSide()){restockerPromises=data.read("Promises",RequestPromiseQueue.CODEC).orElseGet(()->new RequestPromiseQueue(blockEntity::setChanged));restockerPromises.setOnChanged(blockEntity::setChanged);}
    }
}
