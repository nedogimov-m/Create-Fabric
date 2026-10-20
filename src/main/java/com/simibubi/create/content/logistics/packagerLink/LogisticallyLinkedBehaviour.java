package com.simibubi.create.content.logistics.packagerLink;

import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;
import org.apache.commons.lang3.mutable.MutableBoolean;

public final class LogisticallyLinkedBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<LogisticallyLinkedBehaviour> TYPE = new BehaviourType<>();
    public static final AtomicInteger LINK_ID_GENERATOR = new AtomicInteger();
    private static final Map<Level,Map<UUID,Map<Integer,WeakReference<LogisticallyLinkedBehaviour>>>> CLIENT_LINKS = Collections.synchronizedMap(new WeakHashMap<>());
    public enum RequestType { RESTOCK, REDSTONE, PLAYER }
    public final int linkId = LINK_ID_GENERATOR.getAndIncrement();
    public int redstonePower;
    public UUID freqId = UUID.randomUUID();
    private final boolean global;
    private UUID registeredFreq;
    private boolean addedGlobally;
    public LogisticallyLinkedBehaviour(BlockEntity owner,boolean global) { super(owner);this.global=global; }
    private GlobalPos globalPos() { return GlobalPos.of(getWorld().dimension(),getPos()); }
    private Map<UUID,Map<Integer,WeakReference<LogisticallyLinkedBehaviour>>> liveLinks() {
        return getWorld().isClientSide()?CLIENT_LINKS.computeIfAbsent(getWorld(),ignored->new HashMap<>()):GlobalLogisticsManager.get(getWorld()).links;
    }
    @Override public void initialize() { keepAlive(); }
    @Override public void lazyTick() { keepAlive(); }
    public void keepAlive() {
        if(getWorld()==null||!isValidLink(this))return;
        if(registeredFreq!=null&&!registeredFreq.equals(freqId))detach(true);
        if(registeredFreq==null){
            registeredFreq=freqId;
            if(global&&!getWorld().isClientSide()){
                var manager=GlobalLogisticsManager.get(getWorld());
                var owner=blockEntity instanceof PackagerLinkBlockEntity link?link.placedBy:null;
                manager.linkAdded(freqId,globalPos(),owner);manager.linkLoaded(freqId,globalPos());addedGlobally=true;blockEntity.setChanged();
            }
        }
        if(redstonePower==15){removeLive();return;}
        liveLinks().computeIfAbsent(freqId,ignored->new HashMap<>()).put(linkId,new WeakReference<>(this));
    }
    private void removeLive() { var map=liveLinks().get(registeredFreq==null?freqId:registeredFreq);if(map!=null){map.remove(linkId);if(map.isEmpty())liveLinks().remove(registeredFreq==null?freqId:registeredFreq);} }
    private void detach(boolean removed) {
        if(getWorld()==null||registeredFreq==null)return;
        removeLive();
        if(global&&!getWorld().isClientSide()){
            var manager=GlobalLogisticsManager.get(getWorld());
            if(removed)manager.linkRemoved(registeredFreq,globalPos());else manager.linkInvalidated(registeredFreq,globalPos());
        }
        registeredFreq=null;
    }
    @Override public void unload() { detach(false); }
    @Override public void destroy() {
        if(registeredFreq==null&&global&&getWorld()!=null&&!getWorld().isClientSide())GlobalLogisticsManager.get(getWorld()).linkRemoved(freqId,globalPos());
        detach(true);
    }
    public void setFrequency(UUID id) { if(freqId.equals(id))return;detach(true);freqId=Objects.requireNonNull(id);addedGlobally=false;keepAlive();changed(); }
    public void redstonePowerChanged(int power) {
        power=Math.clamp(power,0,15);if(power==redstonePower)return;redstonePower=power;keepAlive();
        if(getWorld()!=null&&!getWorld().isClientSide())GlobalLogisticsManager.get(getWorld()).invalidate(freqId);changed();
    }
    public static boolean isValidLink(LogisticallyLinkedBehaviour link) {
        return link!=null&&link.getWorld()!=null&&!link.blockEntity.isRemoved()&&link.getWorld().isLoaded(link.getPos())&&LoadedLevelAccess.blockEntity(link.getWorld(),link.getPos())==link.blockEntity;
    }
    public static List<LogisticallyLinkedBehaviour> getAllPresent(Level level,UUID freq,boolean sorted) {
        var networks=level.isClientSide()?CLIENT_LINKS.get(level):GlobalLogisticsManager.get(level).links;
        if(networks==null)return List.of();var entries=networks.get(freq);if(entries==null)return List.of();
        var result=new ArrayList<LogisticallyLinkedBehaviour>();
        for(var reference:List.copyOf(entries.values())){var link=reference.get();if(isValidLink(link)&&link.freqId.equals(freq)&&link.redstonePower<15)result.add(link);}
        if(sorted)result.sort(Comparator.comparingInt(link->link.redstonePower));return result;
    }
    public Pair<PackagerBlockEntity,PackagingRequest> processRequest(ItemStack stack,int amount,String address,int index,MutableBoolean last,int order,PackageOrderWithCrafts context,IdentifiedInventory ignored) {
        return blockEntity instanceof PackagerLinkBlockEntity link?link.processRequest(stack,amount,address,index,last,order,context,ignored):null;
    }
    public InventorySummary getSummary(IdentifiedInventory ignored) { return blockEntity instanceof PackagerLinkBlockEntity link?link.fetchSummaryFromPackager(ignored):InventorySummary.EMPTY; }
    public boolean mayInteract(Player player) { return getWorld().isClientSide()||GlobalLogisticsManager.get(getWorld()).mayInteract(freqId,player); }
    public boolean mayInteractMessage(Player player) { boolean allowed=mayInteract(player);if(!allowed)player.sendOverlayMessage(Component.translatable("create.logistically_linked.protected").withStyle(ChatFormatting.RED));return allowed; }
    public boolean mayAdministrate(Player player) { return !getWorld().isClientSide()&&GlobalLogisticsManager.get(getWorld()).mayAdministrate(freqId,player); }
    @Override public boolean isSafeNBT() { return true; }
    @Override public void write(ValueOutput out) { out.store("Freq",UUIDUtil.CODEC,freqId);out.putInt("Power",redstonePower);out.putBoolean("Added",addedGlobally); }
    @Override public void read(ValueInput in) { freqId=in.read("Freq",UUIDUtil.CODEC).orElse(freqId);redstonePower=Math.clamp(in.getIntOr("Power",0),0,15);addedGlobally=in.getBooleanOr("Added",false); }
    @Override public BehaviourType<?> getType() { return TYPE; }
}
