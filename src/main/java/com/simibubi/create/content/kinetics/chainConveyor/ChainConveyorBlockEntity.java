package com.simibubi.create.content.kinetics.chainConveyor;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Loaded-only chain network and authoritative ownership of hanging parcels. */
public final class ChainConveyorBlockEntity extends KineticBlockEntity implements Clearable {
    public static final int MAX_LENGTH = 32, MAX_CONNECTIONS = 4, CAPACITY = 20;
    public static int maxLength(){return com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxChainConveyorLength.get();}
    public static int maxConnections(){return com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxChainConveyorConnections.get();}
    public static int capacity(){return com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.chainConveyorCapacity.get();}
    public record ConnectionStats(float tangentAngle, float chainLength, Vec3 start, Vec3 end) {}
    public record ConnectedPort(float chainPosition,BlockPos connection,String filter){}
    private final Map<BlockPos,ConnectedPort> ports=new LinkedHashMap<>();
    private final Set<BlockPos> connections = new LinkedHashSet<>();
    private final Map<BlockPos,UUID> connectionIds = new HashMap<>();
    private final List<ChainConveyorPackage> looping = new ArrayList<>();
    private final Map<BlockPos, List<ChainConveyorPackage>> travelling = new LinkedHashMap<>();
    public final ChainConveyorRoutingTable routingTable = new ChainConveyorRoutingTable();
    private boolean reversed, validate = true, cancelDrops;
    private BlockPos destroyEffect;

    public ChainConveyorBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.CHAIN_CONVEYOR, pos, state); }
    public Set<BlockPos> getConnections() { return Collections.unmodifiableSet(connections); }
    /** Original scene connection mutation, scoped to two current local virtual owners. */
    public boolean setVirtualConnection(BlockPos offset, boolean connect) {
        if (!isVirtual() || level == null || isRemoved() || LoadedLevelAccess.blockEntity(level, worldPosition) != this
                || !validStoredConnection(offset)
                || !(LoadedLevelAccess.blockEntity(level, worldPosition.offset(offset)) instanceof ChainConveyorBlockEntity other)
                || !other.isVirtual() || other.isRemoved() || other.getLevel() != level) return false;
        var opposite = offset.multiply(-1);
        if (connect) {
            if (isConnectedTo(other)) return true;
            var identity = UUID.randomUUID();
            connections.add(offset.immutable()); other.connections.add(opposite.immutable());
            connectionIds.put(offset.immutable(), identity); other.connectionIds.put(opposite.immutable(), identity);
        } else {
            if (!travelling.getOrDefault(offset, List.of()).isEmpty()
                    || !other.travelling.getOrDefault(opposite, List.of()).isEmpty()) return false;
            connections.remove(offset); other.connections.remove(opposite);
            connectionIds.remove(offset); other.connectionIds.remove(opposite);
            routingTable.removeConnection(offset); other.routingTable.removeConnection(opposite);
        }
        notifyUpdate(); other.notifyUpdate(); return true;
    }

    /** Move the original local parcel between the same canonical lists, retaining it on refusal. */
    public boolean moveLoopingPackageToConnection(ChainConveyorPackage parcel, BlockPos offset) {
        if (!isVirtual() || level == null || isRemoved() || LoadedLevelAccess.blockEntity(level, worldPosition) != this
                || parcel.owner != this) return false;
        int index = looping.indexOf(parcel); if (index < 0) return false;
        float position = parcel.chainPosition;
        looping.remove(index); parcel.owner = null; parcel.chainPosition = 0;
        if (addTravellingPackage(parcel, offset)) return true;
        parcel.chainPosition = position; parcel.owner = this; looping.add(index, parcel); return false;
    }

    public UUID getConnectionId(BlockPos offset) { return connectionIds.get(offset); }
    public List<ChainConveyorPackage> getLoopingPackages() { return Collections.unmodifiableList(looping); }
    public Map<BlockPos,List<ChainConveyorPackage>> getTravellingPackages() {
        var view = new LinkedHashMap<BlockPos,List<ChainConveyorPackage>>();
        travelling.forEach((target, list) -> view.put(target, Collections.unmodifiableList(list)));
        return Collections.unmodifiableMap(view);
    }
    public boolean isReversed() { return getSpeed() == 0 ? reversed : getSpeed() < 0; }
    public int packageCount() { return looping.size() + travelling.values().stream().mapToInt(List::size).sum(); }
    public boolean canAcceptMorePackages() { return packageCount() < capacity(); }
    public void registerPort(BlockPos pos,float chainPosition,BlockPos connection,String filter){
        var next=new ConnectedPort(chainPosition,connection,filter);var previous=ports.get(pos);
        if(previous!=null&&!previous.equals(next))removePort(pos);
        ports.put(pos.immutable(),next);routingTable.receivePortInfo(filter,connection==null?BlockPos.ZERO:connection);
    }
    public void removePort(BlockPos pos){var previous=ports.remove(pos);if(previous!=null&&ports.values().stream().noneMatch(port->port.filter.equals(previous.filter)&&Objects.equals(port.connection,previous.connection)))routingTable.removePortInfo(previous.filter,previous.connection==null?BlockPos.ZERO:previous.connection);}
    public int registeredPortCount(){return ports.size();}
    private com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity portAt(BlockPos pos,ConnectedPort registration){
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity port)||port.isRemoved())return null;
        if(!(port.target instanceof com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget target)||target.conveyor(port)!=this||!Objects.equals(target.connection,registration.connection)||!Objects.equals(port.getFilterString(),registration.filter))return null;
        return port;
    }
    private void refreshPorts(){for(var entry:List.copyOf(ports.entrySet())){
        if(LoadedLevelAccess.blockEntity(level,entry.getKey()) instanceof com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity port)port.refreshTarget();
        if(portAt(entry.getKey(),entry.getValue())==null)removePort(entry.getKey());
    }}
    private boolean exportAtPort(ChainConveyorPackage parcel,BlockPos connection,float previous,float current,float anticipate){
        for(var entry:List.copyOf(ports.entrySet())){
            var registration=entry.getValue();if(!Objects.equals(connection,registration.connection)||!PackageItem.matchAddress(parcel.item,registration.filter))continue;
            boolean arrived=connection==null?loopThresholdCrossed(current,previous,registration.chainPosition,isReversed()):previous<=registration.chainPosition&&current>=registration.chainPosition;
            boolean soon=connection==null?loopThresholdCrossed(anticipate,previous,registration.chainPosition,isReversed()):previous<=registration.chainPosition&&anticipate>=registration.chainPosition;
            if(!arrived&&!soon)continue;var port=portAt(entry.getKey(),registration);if(port==null)continue;
            if(!arrived){port.sendAnticipate();continue;}
            if(port.tryReceive(parcel.item)){parcel.owner=null;return true;}
        }
        return false;
    }
    public boolean isConnectedTo(ChainConveyorBlockEntity other) {
        var offset = other.worldPosition.subtract(worldPosition);
        return connections.contains(offset) && other.connections.contains(offset.multiply(-1))
                && Objects.equals(connectionIds.get(offset),other.connectionIds.get(offset.multiply(-1)));
    }
    public ChainConveyorBlockEntity getConnected(BlockPos offset) {
        if (level != null && LoadedLevelAccess.blockEntity(level, worldPosition.offset(offset)) instanceof ChainConveyorBlockEntity other && isConnectedTo(other)) return other;
        return null;
    }
    public static boolean validConnection(BlockPos offset) {return validStoredConnection(offset)&&Vec3.atLowerCornerOf(offset).lengthSqr()<(double)maxLength()*maxLength();}
    /** A reduced construction limit never destroys an already owned connection or its parcels. */
    private static boolean validStoredConnection(BlockPos offset) {
        var diff = Vec3.atLowerCornerOf(offset);
        double horizontal = Math.hypot(diff.x, diff.z) - 1.5;
        return diff.lengthSqr() >= 2.5 * 2.5
                && horizontal > 0 && Math.abs(diff.y) <= horizontal;
    }
    public static int getChainCost(BlockPos offset) { return Math.max(1, (int) Math.round(Vec3.atLowerCornerOf(offset).length() / 2.5)); }

    /** Validates both endpoints before spending anything; duplicate connections never spend or refund. */
    public boolean connectTo(ChainConveyorBlockEntity other, Player player) {
        if (!(level instanceof ServerLevel) || other == this || other.level != level || isRemoved() || other.isRemoved()) return false;
        var offset = other.worldPosition.subtract(worldPosition);
        if (!validConnection(offset) || connections.size() >= maxConnections() || other.connections.size() >= maxConnections()
                || connections.contains(offset) || other.connections.contains(offset.multiply(-1))) return false;
        if (player != null && (!player.mayBuild() || player.isSpectator() || !level.mayInteract(player,worldPosition) || !level.mayInteract(player,other.worldPosition))) return false;
        int cost = getChainCost(offset);
        if (player != null && !player.isCreative() && !getChainsFromInventory(player, cost, true)) return false;
        if (player != null && !player.isCreative()) getChainsFromInventory(player, cost, false);
        connections.add(offset.immutable()); other.connections.add(offset.multiply(-1));
        var id=UUID.randomUUID();connectionIds.put(offset.immutable(),id);other.connectionIds.put(offset.multiply(-1),id);
        notifyUpdate(); other.notifyUpdate(); KineticNetworks.markDirty((ServerLevel)level);
        return true;
    }
    public static boolean getChainsFromInventory(Player player, int cost, boolean simulate) {
        if (cost < 0) return false;
        var inventory = player.getInventory();
        var slots = new ArrayList<Integer>();
        for (int i = 0; i < 36; i++) if (i != inventory.getSelectedSlot()) slots.add(i);
        slots.add(inventory.getSelectedSlot()); slots.add(40);
        int found = 0;
        for (int slot : slots) if (inventory.getItem(slot).is(Items.IRON_CHAIN)) found += inventory.getItem(slot).getCount();
        if (found < cost) return false;
        if (!simulate) for (int slot : slots) {
            var stack = inventory.getItem(slot);
            if (!stack.is(Items.IRON_CHAIN)) continue;
            int take = Math.min(cost, stack.getCount()); stack.shrink(take); cost -= take;
            if (cost == 0) break;
        }
        if (!simulate) inventory.setChanged();
        return true;
    }
    public boolean disconnect(BlockPos offset, Player refundTo) {
        if (!(level instanceof ServerLevel) || !connections.contains(offset)) return false;
        var other = getConnected(offset);
        if (refundTo != null && (!refundTo.mayBuild() || refundTo.isSpectator() || !level.mayInteract(refundTo,worldPosition)
                || !level.mayInteract(refundTo,worldPosition.offset(offset)))) return false;
        boolean refund=closeConnection(offset);
        removeLocal(offset);
        if (other != null) other.removeLocal(offset.multiply(-1));
        if (refund && refundTo == null) dropChains(offset);
        else if (refund && !refundTo.isCreative()) refundTo.getInventory().placeItemBackInInventory(new ItemStack(Items.IRON_CHAIN,getChainCost(offset)),net.minecraft.util.Prediction.SERVER_ONLY);
        return true;
    }
    private boolean closeConnection(BlockPos offset) {
        if(!(level instanceof ServerLevel server))return false;
        var target=worldPosition.offset(offset);var id=connectionIds.get(offset);if(id==null)return false;
        return ChainConveyorSavedData.get(server).close(id,target,server.getChunkSource().getChunkNow(target.getX()>>4,target.getZ()>>4)!=null);
    }
    private void removeLocal(BlockPos offset) {
        var list = travelling.remove(offset);
        if (list != null) for (var parcel : list) drop(parcel,offset);
        var id=connectionIds.remove(offset);if(id!=null&&level instanceof ServerLevel server)ChainConveyorSavedData.get(server).acknowledge(id,worldPosition);
        connections.remove(offset); routingTable.removeConnection(offset);
        destroyEffect=offset.immutable();
        notifyUpdate();
        if (level instanceof ServerLevel server) KineticNetworks.markDirty(server);
    }
    public void notifyUpdate() { setChanged(); sync(); }
    public boolean addLoopingPackage(ChainConveyorPackage parcel) {
        if (!canAcceptMorePackages() || !PackageItem.isPackage(parcel.item) || contains(parcel) || !Float.isFinite(parcel.chainPosition)) return false;
        parcel.owner = this; parcel.chainPosition = wrapAngle(parcel.chainPosition); looping.add(parcel); updatePosition(parcel,null); notifyUpdate(); return true;
    }
    public boolean addTravellingPackage(ChainConveyorPackage parcel, BlockPos offset) {
        var other = getConnected(offset);
        if (!connections.contains(offset) || other == null || other.looping.size() >= capacity() || !canAcceptMorePackages()
                || !PackageItem.isPackage(parcel.item) || contains(parcel) || !Float.isFinite(parcel.chainPosition)) return false;
        parcel.owner = this; parcel.reversed = isReversed();
        parcel.chainPosition = Mth.clamp(parcel.chainPosition,0,stats(offset,parcel.reversed).chainLength());
        travelling.computeIfAbsent(offset.immutable(), ignored -> new ArrayList<>()).add(parcel);
        updatePosition(parcel,offset); notifyUpdate(); return true;
    }
    private boolean contains(ChainConveyorPackage parcel) {
        return parcel.owner != null;
    }
    public ItemStack takePackage(int netId) {
        for (var list : allLists()) for (var it = list.iterator(); it.hasNext();) {
            var parcel = it.next();
            if (parcel.netId == netId) { it.remove(); parcel.owner = null; notifyUpdate(); return parcel.item; }
        }
        return ItemStack.EMPTY;
    }
    private List<List<ChainConveyorPackage>> allLists() { var lists = new ArrayList<>(travelling.values()); lists.add(looping); return lists; }

    public static void tick(Level level, BlockPos pos, BlockState state, ChainConveyorBlockEntity conveyor) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, conveyor)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,conveyor);conveyor.consumeDestroyEffect(); conveyor.tickTransport();
    
        }
    }
    private void tickTransport() {
        if (level.isClientSide() && !isVirtual()) {
            float factor=com.simibubi.create.foundation.utility.ServerSpeedProvider.get();
            float distance = Math.abs(getSpeed()/360f)*factor, degrees = getSpeed()/360f/(Mth.PI*1.5f)*360f*factor;
            looping.forEach(parcel -> parcel.chainPosition = wrapAngle(parcel.chainPosition+degrees));
            travelling.forEach((offset,list) -> list.forEach(parcel -> {
                if (parcel.reversed == isReversed()) parcel.chainPosition = Math.min(stats(offset,parcel.reversed).chainLength(),parcel.chainPosition+distance);
            }));
            updateBoxWorldPositions(); return;
        }
        if (!level.isClientSide()) {
        if (validate || level.getGameTime() % 20 == 0) { validate = false; removeInvalidConnections(); }
        refreshPorts();
        routingTable.tick();
        if (routingTable.shouldAdvertise()) {
            for (var offset : connections) { var other = getConnected(offset); if (other != null) routingTable.advertiseTo(offset,other.routingTable); }
            routingTable.changed = false; routingTable.lastUpdate = 0;
        }
        }
        if (getSpeed() == 0) { updateBoxWorldPositions(); return; }
        reversed = getSpeed() < 0;
        float distance = Math.abs(getSpeed() / 360f), degrees = getSpeed() / 360f / (Mth.PI * 1.5f) * 360f;
        long now = isVirtual() && level instanceof com.simibubi.create.foundation.utility.VirtualLevelClock clock
                ? clock.virtualGameTime() : level.getGameTime();
        boolean changed = false, ownershipChanged = false;
        // Snapshot lists: a reversal may add to another conveyor which ticks later this tick.
        for (var entry : List.copyOf(travelling.entrySet())) {
            var offset = entry.getKey(); var other = getConnected(offset);
            for (var it = entry.getValue().iterator(); it.hasNext();) {
                var parcel = it.next(); if (parcel.lastTick == now) continue; parcel.lastTick = now;
                var stats = stats(offset,parcel.reversed);
                if (parcel.reversed != reversed) {
                    if (other == null) continue;
                    parcel.chainPosition = Math.max(0,stats.chainLength() - parcel.chainPosition);
                    parcel.reversed = reversed;
                    parcel.owner = other;
                    other.travelling.computeIfAbsent(offset.multiply(-1), ignored -> new ArrayList<>()).add(parcel);
                    it.remove(); other.updatePosition(parcel,offset.multiply(-1)); other.notifyUpdate(); changed = true; ownershipChanged = true; continue;
                }
                float previous=parcel.chainPosition;
                float next = Math.min(stats.chainLength(),parcel.chainPosition + distance);
                changed |= next != parcel.chainPosition; parcel.chainPosition = next;
                if(exportAtPort(parcel,offset,previous,next,Math.min(stats.chainLength(),next+distance*4))){it.remove();changed=ownershipChanged=true;continue;}
                if (parcel.chainPosition < stats.chainLength() || other == null || other.looping.size() >= capacity()) continue;
                parcel.chainPosition = wrapAngle(stats.tangentAngle() + 180 + 70 * (reversed ? -1 : 1));
                parcel.owner = other; other.looping.add(parcel); it.remove(); other.updatePosition(parcel,null); other.notifyUpdate(); changed = true; ownershipChanged = true;
            }
        }
        travelling.values().removeIf(List::isEmpty);
        for (var it = looping.iterator(); it.hasNext();) {
            var parcel = it.next(); if (parcel.lastTick == now) continue; parcel.lastTick = now;
            float previous = parcel.chainPosition;
            parcel.chainPosition = wrapAngle(previous + degrees); changed = true;
            if (level.isClientSide()) continue; // Original source virtual scenes script their loop departures.
            if(exportAtPort(parcel,null,previous,parcel.chainPosition,wrapAngle(parcel.chainPosition+degrees*4))){it.remove();ownershipChanged=true;continue;}
            var exit = routingTable.getExitFor(parcel.item);
            if (!connections.contains(exit)) continue;
            var other = getConnected(exit);
            if (other == null || other.looping.size() >= capacity() || !loopThresholdCrossed(parcel.chainPosition,previous,stats(exit,reversed).tangentAngle(),reversed)) continue;
            parcel.chainPosition = 0; parcel.reversed = reversed;
            travelling.computeIfAbsent(exit, ignored -> new ArrayList<>()).add(parcel); it.remove(); ownershipChanged = true;
        }
        updateBoxWorldPositions();
        if (changed) { setChanged(); if (ownershipChanged || now % 5 == 0) sync(); }
    }
    public void removeInvalidConnections() {
        if (!(level instanceof ServerLevel server)) return;
        for (var entry : List.copyOf(travelling.entrySet())) if (!connections.contains(entry.getKey())) {
            entry.getValue().forEach(parcel -> drop(parcel,entry.getKey())); travelling.remove(entry.getKey()); notifyUpdate();
        }
        for (var offset : List.copyOf(connections)) {
            var target = worldPosition.offset(offset);
            if(ChainConveyorSavedData.get(server).isClosed(connectionIds.get(offset))){removeLocal(offset);continue;}
            // A missing FULL chunk is a suspended edge, never a deleted connection.
            if (server.getChunkSource().getChunkNow(target.getX() >> 4,target.getZ() >> 4) == null) continue;
            if (getConnected(offset) == null) removeLocal(offset);
        }
    }
    public ConnectionStats getConnectionStats(BlockPos offset) { return connections.contains(offset) ? stats(offset,isReversed()) : null; }
    private ConnectionStats stats(BlockPos offset, boolean reverse) {
        float sign = reverse ? -1 : 1;
        float angle = wrapAngle((float)Math.toDegrees(Math.atan2(offset.getX(),offset.getZ())) - 35 * sign);
        float opposite = wrapAngle(angle + 180 + 70 * sign);
        var start = Vec3.atBottomCenterOf(worldPosition).add(rotate(1.25,angle)).add(0,6/16d,0);
        var end = Vec3.atBottomCenterOf(worldPosition.offset(offset)).add(rotate(1.25,opposite)).add(0,6/16d,0);
        return new ConnectionStats(angle,(float)start.distanceTo(end),start,end);
    }
    private void spawnDestroyParticles(BlockPos offset){
        if(level==null||!level.isClientSide())return;
        var shape=stats(offset,isReversed());var start=shape.start();var direction=shape.end().subtract(start);
        var origin=Vec3.atCenterOf(worldPosition);var normal=direction.cross(new Vec3(0,1,0)).normalize();
        var side=start.subtract(origin);var second=origin.add(side.add(normal.scale(-2*normal.dot(side))));
        int positions=(int)Math.round(Vec3.atLowerCornerOf(offset).length()*8);
        var particle=new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.IRON_CHAIN.defaultBlockState());
        for(int chain=0;chain<2;chain++){
            int steps=positions/2+(chain==0?positions%2:0);
            for(int i=0;i<steps;i++){
                var at=(chain==0?start:second).add(direction.scale((.5+i)/steps));
                level.addParticle(particle,at.x,at.y,at.z,0,0,0);
            }
        }
    }
    private void consumeDestroyEffect(){if(destroyEffect!=null&&level!=null&&level.isClientSide()){var effect=destroyEffect;destroyEffect=null;spawnDestroyParticles(effect);}}
    @Override public void remove(){super.remove();if(level!=null&&level.isClientSide())for(var connection:connections)spawnDestroyParticles(connection);}
    public Vec3 getPackagePosition(float position, BlockPos offset) { return getPackagePosition(position,offset,isReversed()); }
    private Vec3 getPackagePosition(float position, BlockPos offset, boolean reverse) {
        if (offset == null || offset.equals(BlockPos.ZERO)) return Vec3.atBottomCenterOf(worldPosition).add(rotate(.875,position)).add(0,6/16d,0);
        var stats = stats(offset,reverse);
        return stats.start().add(stats.end().subtract(stats.start()).normalize().scale(Mth.clamp(position,0,stats.chainLength())));
    }
    private static Vec3 rotate(double radius, float degrees) { double radians = Math.toRadians(degrees); return new Vec3(Math.sin(radians)*radius,0,Math.cos(radians)*radius); }
    private void updatePosition(ChainConveyorPackage parcel, BlockPos offset) {
        parcel.worldPosition = getPackagePosition(parcel.chainPosition,offset,parcel.reversed);
        if (offset == null) parcel.yaw = parcel.chainPosition + (isReversed()?180:0);
        else { var stats = stats(offset,parcel.reversed); var diff = stats.end().subtract(stats.start()); parcel.yaw = (float)Math.toDegrees(Math.atan2(diff.x,diff.z))-90; }
    }
    public void updateBoxWorldPositions() {
        looping.forEach(parcel -> updatePosition(parcel,null));
        travelling.forEach((target,list) -> list.forEach(parcel -> updatePosition(parcel,target)));
    }
    public static float wrapAngle(float angle) { return Mth.positiveModulo(angle,360); }
    public static boolean loopThresholdCrossed(float current, float previous, float target, boolean reversed) {
        float step = reversed ? wrapAngle(previous-current) : wrapAngle(current-previous);
        float distance = reversed ? wrapAngle(previous-target) : wrapAngle(target-previous);
        return step > 0 && distance > 0 && distance <= step;
    }
    private void drop(ChainConveyorPackage parcel, BlockPos offset) {
        if (level == null || level.isClientSide()) return;
        parcel.owner = null; updatePosition(parcel,offset); level.addFreshEntity(PackageEntity.fromItemStack(level,parcel.worldPosition.subtract(0,.5,0),parcel.item));
    }
    private void dropChains(BlockPos offset) { Block.popResource(level,worldPosition,new ItemStack(Items.IRON_CHAIN,getChainCost(offset))); }
    public void cancelChainDrops() { cancelDrops = true; }
    public void refundChains(Player player) {
        if (cancelDrops) return;
        cancelDrops = true;
        for(var offset:connections){boolean refund=closeConnection(offset);if(refund&&!player.isCreative())player.getInventory().placeItemBackInInventory(new ItemStack(Items.IRON_CHAIN,getChainCost(offset)),net.minecraft.util.Prediction.SERVER_ONLY);}
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel) {
            for (var offset : List.copyOf(connections)) {
                var other = getConnected(offset); boolean refund=closeConnection(offset);if (!cancelDrops&&refund) dropChains(offset);
                if (other != null) other.removeLocal(offset.multiply(-1));
                var id=connectionIds.get(offset);if(id!=null)ChainConveyorSavedData.get((ServerLevel)level).acknowledge(id,worldPosition);
            }
            looping.forEach(parcel -> drop(parcel,null));
            travelling.forEach((offset,list) -> list.forEach(parcel -> drop(parcel,offset)));
            clearContent();
        }
        super.preRemoveSideEffects(pos,state);
    }
    @Override public void clearContent() { allLists().forEach(list -> list.forEach(parcel -> parcel.owner = null)); looping.clear(); travelling.clear(); connections.clear(); connectionIds.clear(); }
    @Override protected void write(ValueOutput out, boolean clientPacket) {
        super.write(out, clientPacket); out.store("Connections",BlockPos.CODEC.listOf(),List.copyOf(connections)); out.putBoolean("Reversed",isReversed());
        var ids=out.childrenList("ConnectionIds");connectionIds.forEach((target,id)->{var entry=ids.addChild();entry.store("Target",BlockPos.CODEC,target);entry.store("Id",net.minecraft.core.UUIDUtil.CODEC,id);});
        var loops = out.childrenList("LoopingPackages"); looping.forEach(parcel -> parcel.write(loops.addChild()));
        var travels = out.childrenList("TravellingPackages");
        travelling.forEach((offset,list) -> { var entry = travels.addChild(); entry.store("Target",BlockPos.CODEC,offset); var packages = entry.childrenList("Packages"); list.forEach(parcel -> parcel.write(packages.addChild())); });
        if(clientPacket&&destroyEffect!=null){out.store("DestroyEffect",BlockPos.CODEC,destroyEffect);destroyEffect=null;}
    }
    @Override protected void read(ValueInput in, boolean clientPacket) {
        super.read(in, clientPacket); connections.clear(); connectionIds.clear(); travelling.clear(); looping.clear();
        for (var offset : in.read("Connections",BlockPos.CODEC.listOf()).orElse(List.of())) if (validStoredConnection(offset)) {
            connections.add(offset.immutable());var target=worldPosition.offset(offset);long first=Math.min(worldPosition.asLong(),target.asLong()),second=Math.max(worldPosition.asLong(),target.asLong());
            connectionIds.put(offset.immutable(),UUID.nameUUIDFromBytes((first+":"+second).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        for(var entry:in.childrenListOrEmpty("ConnectionIds")){var target=entry.read("Target",BlockPos.CODEC).orElse(BlockPos.ZERO);var id=entry.read("Id",net.minecraft.core.UUIDUtil.CODEC);if(connections.contains(target)&&id.isPresent())connectionIds.put(target,id.get());}
        reversed = in.getBooleanOr("Reversed",getSpeed()<0);
        for (var entry : in.childrenListOrEmpty("LoopingPackages")) { var parcel = ChainConveyorPackage.read(entry,reversed,clientPacket); if (parcel != null) { parcel.owner = this; parcel.chainPosition = wrapAngle(parcel.chainPosition); looping.add(parcel); } }
        for (var entry : in.childrenListOrEmpty("TravellingPackages")) {
            var target = entry.read("Target",BlockPos.CODEC).orElse(BlockPos.ZERO); if (!validStoredConnection(target)) continue;
            for (var data : entry.childrenListOrEmpty("Packages")) { var parcel = ChainConveyorPackage.read(data,reversed,clientPacket); if (parcel != null) { parcel.owner = this; travelling.computeIfAbsent(target,ignored -> new ArrayList<>()).add(parcel); } }
        }
        validate = true; updateBoxWorldPositions();
        destroyEffect=clientPacket?in.read("DestroyEffect",BlockPos.CODEC).orElse(null):null;consumeDestroyEffect();
    }
}
