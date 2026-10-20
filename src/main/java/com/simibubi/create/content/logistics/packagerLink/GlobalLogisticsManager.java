package com.simibubi.create.content.logistics.packagerLink;

import java.util.*;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Persistent networks belong to one server; live links and summary caches are transient. */
public final class GlobalLogisticsManager {
    private static final Map<MinecraftServer,GlobalLogisticsManager> SERVERS = Collections.synchronizedMap(new WeakHashMap<>());
    public final Map<UUID,LogisticsNetwork> logisticsNetworks;
    final Map<UUID,Map<Integer,java.lang.ref.WeakReference<LogisticallyLinkedBehaviour>>> links = new HashMap<>();
    final Map<UUID,LogisticsManager.CachedSummary> accurateSummaries = new HashMap<>(), summaries = new HashMap<>();
    private final LogisticsNetworkSavedData savedData;
    private final Set<PackagerLinkBlockEntity> pendingInitializations=Collections.newSetFromMap(new IdentityHashMap<>());
    private long ticks;
    private GlobalLogisticsManager(LogisticsNetworkSavedData data) { savedData=data;logisticsNetworks=data.getLogisticsNetworks();data.bindCallbacks(); }
    public static GlobalLogisticsManager get(MinecraftServer server) { return SERVERS.computeIfAbsent(server, key -> new GlobalLogisticsManager(LogisticsNetworkSavedData.load(key))); }
    public static GlobalLogisticsManager get(Level level) { return get(Objects.requireNonNull(level.getServer())); }
    public static void unload(MinecraftServer server) { SERVERS.remove(server); }
    public long ticks() { return ticks; }
    public void scheduleInitialization(PackagerLinkBlockEntity link) { pendingInitializations.add(link); }
    public void cancelInitialization(PackagerLinkBlockEntity link) { pendingInitializations.remove(link); }
    private void initializeReadyLinks() {
        var pending=List.copyOf(pendingInitializations);pendingInitializations.clear();
        for(var link:pending){
            var level=link.getLevel();if(level==null||link.isRemoved()||!level.isLoaded(link.getBlockPos()))continue;
            var behaviour=link.getBehaviour(LogisticallyLinkedBehaviour.TYPE);
            if(behaviour!=null&&behaviour.blockEntity==link&&LogisticallyLinkedBehaviour.isValidLink(behaviour)){
                link.initialize();
                // A retained native owner can re-enter FULL without repeating Smart initialization.
                var current=link.getBehaviour(LogisticallyLinkedBehaviour.TYPE);
                if(link.getLevel()==level&&!level.isClientSide()&&current!=null&&current.blockEntity==link
                        &&LogisticallyLinkedBehaviour.isValidLink(current)
                        &&link.getBehaviour(LogisticallyLinkedBehaviour.TYPE)==current)current.keepAlive();
            }
            else if(level.getChunkSource().getChunkNow(link.getBlockPos().getX()>>4,link.getBlockPos().getZ()>>4)==null)pendingInitializations.add(link);
        }
    }
    public void tick() { ticks++;initializeReadyLinks();logisticsNetworks.values().forEach(network -> network.panelPromises.tick());if(ticks%20==0){accurateSummaries.values().removeIf(entry->ticks-entry.tick()>=1);summaries.values().removeIf(entry->ticks-entry.tick()>=20);} }
    public boolean mayInteract(UUID id,Player player) { var n=logisticsNetworks.get(id);return n==null||n.owner==null||!n.locked||n.owner.equals(player.getUUID()); }
    public boolean mayAdministrate(UUID id,Player player) { var n=logisticsNetworks.get(id);return n==null||n.owner==null||n.owner.equals(player.getUUID()); }
    public boolean isLockable(UUID id) { return logisticsNetworks.containsKey(id); }
    public boolean isLocked(UUID id) { var n=logisticsNetworks.get(id);return n!=null&&n.locked; }
    public boolean setLocked(UUID id,Player player,boolean locked) { var n=logisticsNetworks.get(id);if(n==null||!mayAdministrate(id,player))return false;n.locked=locked;markDirty();return true; }
    private LogisticsNetwork network(UUID id) { return logisticsNetworks.computeIfAbsent(id, key -> {var n=new LogisticsNetwork(key);n.panelPromises.setOnChanged(this::markDirty);return n;}); }
    public void linkAdded(UUID id,GlobalPos pos,UUID owner) { var n=network(id);n.totalLinks.add(pos);if(n.owner==null&&owner!=null)n.owner=owner;markDirty();invalidate(id); }
    public void linkLoaded(UUID id,GlobalPos pos) { network(id).loadedLinks.add(pos);invalidate(id); }
    public void linkInvalidated(UUID id,GlobalPos pos) { var n=logisticsNetworks.get(id);if(n!=null)n.loadedLinks.remove(pos);invalidate(id); }
    public void linkRemoved(UUID id,GlobalPos pos) { var n=logisticsNetworks.get(id);if(n==null)return;n.totalLinks.remove(pos);n.loadedLinks.remove(pos);if(n.totalLinks.isEmpty())logisticsNetworks.remove(id);markDirty();invalidate(id); }
    public int getUnloadedLinkCount(UUID id) { var n=logisticsNetworks.get(id);return n==null?0:Math.max(0,n.totalLinks.size()-n.loadedLinks.size()); }
    public RequestPromiseQueue getQueuedPromises(UUID id) { var n=logisticsNetworks.get(id);return n==null?null:n.panelPromises; }
    public boolean hasQueuedPromises(UUID id) { var q=getQueuedPromises(id);return q!=null&&!q.isEmpty(); }
    public void invalidate(UUID id) { accurateSummaries.remove(id);summaries.remove(id); }
    public void markDirty() { savedData.setDirty(); }
}
