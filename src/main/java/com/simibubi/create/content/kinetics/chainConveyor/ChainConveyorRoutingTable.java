package com.simibubi.create.content.kinetics.chainConveyor;

import com.simibubi.create.content.logistics.box.PackageItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Source distance-vector routing, including expiry and wildcard fallback priority. */
public final class ChainConveyorRoutingTable {
    public static final int ENTRY_TIMEOUT = 100, PORT_ENTRY_TIMEOUT = 20;
    public static final class RoutingTableEntry {
        public final String port;
        public final int distance;
        public final BlockPos nextConnection;
        public final boolean endOfRoute;
        public int timeout;
        private RoutingTableEntry(String port, int distance, BlockPos next, int timeout, boolean local) {
            this.port = port; this.distance = distance; this.nextConnection = next.immutable();
            this.timeout = timeout; this.endOfRoute = local;
        }
    }
    private final List<RoutingTableEntry> entries = new ArrayList<>();
    public int lastUpdate;
    public boolean changed;
    public List<RoutingTableEntry> entries() { return List.copyOf(entries); }
    public void tick() {
        entries.forEach(entry -> entry.timeout--);
        changed |= entries.removeIf(entry -> entry.timeout <= 0);
        lastUpdate++;
    }
    public boolean shouldAdvertise() { return changed || lastUpdate > ENTRY_TIMEOUT - 20; }
    public void receivePortInfo(String filter, BlockPos connection) {
        insert(new RoutingTableEntry(filter, filter.equals("*") ? 1000 : 0, connection, PORT_ENTRY_TIMEOUT, true));
    }
    public BlockPos getExitFor(ItemStack box) {
        for (var entry : entries) if (PackageItem.matchAddress(box, entry.port)) return entry.nextConnection;
        return BlockPos.ZERO;
    }
    public void advertiseTo(BlockPos connection, ChainConveyorRoutingTable other) {
        var back = connection.multiply(-1);
        for (var entry : entries) if (entry.endOfRoute || !connection.equals(entry.nextConnection))
            other.insert(new RoutingTableEntry(entry.port, entry.distance + 1, back, ENTRY_TIMEOUT, false));
        other.changed |= other.entries.removeIf(entry -> entry.timeout < ENTRY_TIMEOUT && !entry.endOfRoute && back.equals(entry.nextConnection));
    }
    public void removeConnection(BlockPos connection) {
        changed |= entries.removeIf(entry -> connection.equals(entry.nextConnection));
    }
    public void removePortInfo(String filter,BlockPos connection){changed|=entries.removeIf(entry->entry.endOfRoute&&entry.port.equals(filter)&&entry.nextConnection.equals(connection));}
    private void insert(RoutingTableEntry entry) {
        int index = 0;
        for (var other : entries) {
            if (other.distance > entry.distance) break;
            if (other.port.equals(entry.port)) {
                if (other.distance == entry.distance && other.nextConnection.equals(entry.nextConnection))
                    other.timeout = ENTRY_TIMEOUT;
                return;
            }
            index++;
        }
        entries.add(index, entry);
        changed = true;
    }
}
