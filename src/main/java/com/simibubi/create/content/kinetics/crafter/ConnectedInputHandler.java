package com.simibubi.create.content.kinetics.crafter;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import java.util.*;

public final class ConnectedInputHandler {
    public static List<MechanicalCrafterBlockEntity> getMembers(MechanicalCrafterBlockEntity crafter) {
        var members = new ArrayList<MechanicalCrafterBlockEntity>();
        for (var pos : crafter.inputGroup) {
            var member = CrafterHelper.getCrafter(crafter.getLevel(), pos);
            if (member == null || !member.inputGroup.equals(crafter.inputGroup)) return List.of();
            members.add(member);
        }
        var right = crafter.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING).getCounterClockWise();
        members.sort(Comparator.comparingInt((MechanicalCrafterBlockEntity c) -> -c.getBlockPos().getY())
                .thenComparingInt(c -> c.getBlockPos().getX() * right.getStepX() + c.getBlockPos().getZ() * right.getStepZ()));
        return members;
    }
    public static Storage<ItemVariant> storage(MechanicalCrafterBlockEntity crafter) {
        return new Storage<>() {
            @Override public long insert(ItemVariant resource, long amount, TransactionContext tx) {
                StoragePreconditions.notBlankNotNegative(resource, amount); long moved = 0;
                for (var member : getMembers(crafter)) { moved += member.inventory.insert(resource, amount - moved, tx); if (moved == amount) break; }
                return moved;
            }
            @Override public long extract(ItemVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); return 0; }
            @Override public Iterator<StorageView<ItemVariant>> iterator() { return getMembers(crafter).stream().<StorageView<ItemVariant>>map(c -> c.inventory).iterator(); }
        };
    }
    private static void assign(Level level, Collection<BlockPos> positions) {
        var group = Set.copyOf(positions);
        for (var pos : group) { var crafter = CrafterHelper.getCrafter(level, pos); if (crafter != null) { crafter.inputGroup = group; crafter.contentsChanged(); } }
    }
    public static boolean toggleConnection(Level level, BlockPos a, BlockPos b) {
        var first = CrafterHelper.getCrafter(level, a); var second = CrafterHelper.getCrafter(level, b);
        if (first == null || second == null || a.distManhattan(b) != 1 || first.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING) != second.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING)) return false;
        var facing = first.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
        if (facing.getAxis().choose(b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ()) != 0
                || getMembers(first).isEmpty() || getMembers(second).isEmpty()) return false;
        if (level.isClientSide()) {
            if (!first.isVirtual() || !second.isVirtual()) return true;
            if (first.getLevel() != level || second.getLevel() != level
                    || java.util.stream.Stream.concat(getMembers(first).stream(), getMembers(second).stream())
                        .anyMatch(member -> !member.isVirtual() || member.getLevel() != level || member.isRemoved())) return false;
        }
        if (!first.inputGroup.equals(second.inputGroup)) {
            var merged = new HashSet<>(first.inputGroup); merged.addAll(second.inputGroup); if (merged.size() > 4096) return false;
            assign(level, merged); return true;
        }
        var available = new HashSet<>(first.inputGroup); available.remove(a); available.remove(b);
        var split = new HashSet<BlockPos>(); split.add(b); var queue = new ArrayDeque<BlockPos>(); queue.add(b);
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            for (var side : Direction.values()) { var next = current.relative(side); if (available.remove(next)) { split.add(next); queue.add(next); } }
        }
        available.add(a); assign(level, available); assign(level, split); return true;
    }
    public static void onRemoved(MechanicalCrafterBlockEntity removed) {
        var remaining = new HashSet<>(removed.inputGroup); remaining.remove(removed.getBlockPos());
        while (!remaining.isEmpty()) {
            var group = new HashSet<BlockPos>(); var queue = new ArrayDeque<BlockPos>(); var start = remaining.iterator().next(); remaining.remove(start); queue.add(start); group.add(start);
            while (!queue.isEmpty()) {
                var current = queue.removeFirst();
                for (var side : Direction.values()) { var next = current.relative(side); if (remaining.remove(next)) { queue.add(next); group.add(next); } }
            }
            assign(removed.getLevel(), group);
        }
    }
    public static void reconcile(MechanicalCrafterBlockEntity crafter) {
        if (crafter.inputGroup.stream().anyMatch(pos -> !crafter.getLevel().isLoaded(pos))) return;
        var retained = new HashSet<BlockPos>();
        for (var pos : crafter.inputGroup) {
            var member = CrafterHelper.getCrafter(crafter.getLevel(), pos);
            if (member != null && member.inputGroup.contains(crafter.getBlockPos())
                    && member.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING) == crafter.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING)) retained.add(pos);
        }
        retained.add(crafter.getBlockPos());
        if (!retained.equals(crafter.inputGroup)) assign(crafter.getLevel(), retained);
    }
    private ConnectedInputHandler() {}
}
