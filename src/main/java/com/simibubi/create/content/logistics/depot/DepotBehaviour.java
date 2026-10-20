package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ProcessingInventory;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Source Depot transport, processing and insertion policy over one real Fabric inventory.
 * The held transport's stack is a refreshed view of Input0; it is never another stock owner.
 * Incoming stacks have their own transactional owner until their source slide completes.
 */
public class DepotBehaviour extends BlockEntityBehaviour implements Clearable {
    public static final BehaviourType<DepotBehaviour> TYPE = new BehaviourType<>();
    public final ProcessingInventory inventory;
    public final DepotItemHandler itemHandler;
    public final DirectBeltInputBehaviour directInput;
    public final TransportedItemStackHandlerBehaviour transportedHandler;
    private TransportedItemStack heldMetadata;
    private final List<TransportedItemStack> incoming = new ArrayList<>();
    Supplier<Integer> maxStackSize = () -> getHeldItemStack().isEmpty() ? 64 : getHeldItemStack().getMaxStackSize();
    Supplier<Boolean> canAcceptItems = () -> true;
    Predicate<Direction> canFunnelsPullFrom = side -> true;
    Consumer<ItemStack> onHeldInserted = stack -> {};
    Predicate<ItemStack> acceptedItems = stack -> true;
    private boolean allowMerge;

    private record Metadata(TransportedItemStack held, List<TransportedItemStack> incoming) {}
    private final SnapshotParticipant<Metadata> metadata = new SnapshotParticipant<>() {
        @Override protected Metadata createSnapshot() {
            return new Metadata(heldMetadata == null ? null : heldMetadata.copy(),
                    incoming.stream().map(TransportedItemStack::copy).toList());
        }
        @Override protected void readSnapshot(Metadata state) {
            heldMetadata = state.held;
            incoming.clear(); incoming.addAll(state.incoming);
        }
        @Override protected void onFinalCommit() { changed(); }
    };

    public DepotBehaviour(BlockEntity entity) {
        super(entity);
        inventory = new ProcessingInventory(1, 8, variant -> true, this::changed, true, false,
                slots -> new DepotItemHandler(this, slots));
        itemHandler = (DepotItemHandler) inventory.automation;
        directInput = new DirectBeltInputBehaviour(entity).allowingBeltFunnels()
                .setInsertionHandler(this::insertFromSide).considerOccupiedWhen(this::isOccupied);
        transportedHandler = new TransportedItemStackHandlerBehaviour(entity, this::applyToAllItems)
                .withStackPlacement(stack -> Vec3.atCenterOf(getPos()));
    }

    public void enableMerging() { allowMerge = true; }
    public boolean canMergeItems() { return allowMerge; }
    public DepotBehaviour withCallback(Consumer<ItemStack> callback) { onHeldInserted = callback; return this; }
    public DepotBehaviour onlyAccepts(Predicate<ItemStack> predicate) { acceptedItems = predicate; return this; }
    public DepotBehaviour withMaxStackSize(Supplier<Integer> supplier) { maxStackSize = supplier; return this; }
    public DepotBehaviour acceptingWhen(Supplier<Boolean> supplier) { canAcceptItems = supplier; return this; }
    public DepotBehaviour pullingFrom(Predicate<Direction> predicate) { canFunnelsPullFrom = predicate; return this; }
    public boolean isActiveWorldOwner() {
        var world=getWorld();return world!=null&&!blockEntity.isRemoved()
                &&LoadedLevelAccess.blockEntity(world,getPos())==blockEntity
                &&LoadedLevelAccess.blockState(world,getPos()).getBlock()==blockEntity.getBlockState().getBlock();
    }
    public boolean canAcceptItems() { return canAcceptItems.get(); }
    public boolean isItemValid(ItemStack stack) { return acceptedItems.test(stack); }
    public int getConfiguredMaxStackSize() { return maxStackSize.get(); }
    public boolean isOutputEmpty() { return inventory.outputs.stream().allMatch(ProcessingInventory.Slot::isResourceBlank); }
    public boolean isEmpty() { return getHeldItemStack().isEmpty() && isOutputEmpty(); }
    public boolean isOccupied(Direction side) { return !canAcceptItems.get() || !allowMerge && !isEmpty(); }
    public ItemStack getHeldItemStack() { return inventory.inputs.getFirst().stack(); }

    public TransportedItemStack getHeldItem() {
        ItemStack actual = getHeldItemStack();
        if (actual.isEmpty()) return null;
        if (heldMetadata == null) heldMetadata = new TransportedItemStack(actual, .5f);
        heldMetadata.stack = actual;
        return heldMetadata;
    }

    /** Legacy port API: changes transport metadata only, after its canonical slot transaction. */
    public void setTransportMetadata(TransportedItemStack stack) {
        heldMetadata = stack == null ? null : stack.copy();
        blockEntity.setChanged();
    }

    public List<TransportedItemStack> getIncomingItems() { return incoming.stream().map(TransportedItemStack::copy).toList(); }
    public int getPresentStackSize() {
        return getHeldItemStack().getCount() + inventory.outputs.stream().mapToInt(slot -> (int) slot.getAmount()).sum();
    }
    public int getRemainingSpace() {
        int max = maxStackSize.get();
        int nativeMax = getHeldItemStack().isEmpty() ? 64 : getHeldItemStack().getMaxStackSize();
        return Math.min(max == 0 ? 64 : max, nativeMax) - getPresentStackSize()
                - incoming.stream().mapToInt(stack -> stack.stack.getCount()).sum();
    }
    public boolean readyForProcessing() {
        var held = getHeldItem(); return held != null && .5f - held.beltPosition < 1 / 16f;
    }

    long insert(TransportedItemStack transport, long maximum, TransactionContext tx) {
        ItemStack stack = transport.stack;
        if (stack.isEmpty() || maximum == 0 || !canAcceptItems.get() || !acceptedItems.test(stack)) return 0;
        if (!allowMerge && !isEmpty()) return 0;
        long accepted = Math.min(maximum, stack.getMaxStackSize());
        var current = getHeldItem();
        if (allowMerge) {
            accepted = Math.min(accepted, Math.max(0, getRemainingSpace()));
            if (current != null && !ItemStack.isSameItemSameComponents(current.stack, stack)) return 0;
        }
        if (accepted == 0) return 0;
        metadata.updateSnapshots(tx);
        var copy = transport.copy(); copy.stack = stack.copyWithCount((int) accepted);
        if (allowMerge && current != null) {
            incoming.add(copy);
        } else {
            long placed = inventory.inputs.getFirst().insert(ItemVariant.of(stack), accepted, tx);
            if (placed == 0) return 0;
            copy.stack = stack.copyWithCount((int) placed); heldMetadata = copy; accepted = placed;
        }
        if (!allowMerge) {
            ItemStack inserted = copy.stack.copy();
            tx.addOuterCloseCallback(result -> {
                if (!result.wasCommitted()) return;
                onHeldInserted.accept(inserted);
                if (getWorld() != null && !getWorld().isClientSide())
                    getWorld().playSound(null, getPos(), transport.insertedFrom.getAxis().isHorizontal()
                            ? AllSoundEvents.DEPOT_SLIDE : AllSoundEvents.DEPOT_PLOP, SoundSource.BLOCKS, 1, 1);
            });
        }
        return accepted;
    }

    private long insertFromSide(TransportedItemStack transport, Direction side, TransactionContext tx) {
        if (isOccupied(side)) return 0;
        var incoming = transport.copy(); incoming.beltPosition = side.getAxis().isVertical() ? .5f : 0;
        incoming.prevBeltPosition = incoming.beltPosition; incoming.insertedFrom = side;
        incoming.prevSideOffset = incoming.sideOffset;
        return insert(incoming, incoming.stack.getCount(), tx);
    }

    public ItemStack insert(TransportedItemStack stack, boolean simulate) {
        try (var tx = Transaction.openOuter()) {
            long accepted = insert(stack, stack.stack.getCount(), tx);
            if (!simulate) tx.commit();
            return stack.stack.copyWithCount(stack.stack.getCount() - (int) accepted);
        }
    }

    public void setHeldItem(TransportedItemStack item, TransactionContext tx) {
        metadata.updateSnapshots(tx);
        inventory.inputs.getFirst().setStack(item == null ? ItemStack.EMPTY : item.stack, tx);
        heldMetadata = item == null || item.stack.isEmpty() ? null : item.copy();
    }
    public void setHeldItem(TransportedItemStack item) {
        try (var tx = Transaction.openOuter()) { setHeldItem(item, tx); tx.commit(); }
    }
    public void setCenteredHeldItem(TransportedItemStack item) {
        var copy = item.copy(); copy.beltPosition = copy.prevBeltPosition = .5f; setHeldItem(copy);
    }
    public void removeHeldItem() { setHeldItem(null); }

    /** Source alternate overhead funnel route, including original in-transit incoming owners. */
    public long exportTo(net.fabricmc.fabric.api.transfer.v1.storage.Storage<ItemVariant> target, TransactionContext tx) {
        metadata.updateSnapshots(tx); long total = 0;
        var main = inventory.inputs.getFirst();
        if (!main.isResourceBlank()) {var resource = main.getResource();long moved = target.insert(resource, main.getAmount(), tx);main.extract(resource, moved, tx);total += moved;}
        for (int index = 0; index < incoming.size();) {
            var entry = incoming.get(index);long moved = target.insert(ItemVariant.of(entry.stack),entry.stack.getCount(),tx);total += moved;
            entry.stack = entry.stack.copyWithCount(entry.stack.getCount() - (int) moved);
            if (entry.stack.isEmpty()) incoming.remove(index); else index++;
        }
        for (var output : inventory.outputs) if (!output.isResourceBlank()) {var resource=output.getResource();long moved=target.insert(resource,output.getAmount(),tx);output.extract(resource,moved,tx);total+=moved;}
        return total;
    }
    /** Transfer every real owner to the caller inside the same transaction. */
    public List<ItemStack> takeAll(TransactionContext tx) {
        metadata.updateSnapshots(tx);
        List<ItemStack> result = new ArrayList<>();
        for (var slot : inventory.inputs) takeSlot(slot, result, tx);
        for (var stack : incoming) if (!stack.stack.isEmpty()) result.add(stack.stack.copy());
        incoming.clear(); heldMetadata = null;
        for (var slot : inventory.outputs) takeSlot(slot, result, tx);
        return result;
    }
    private static void takeSlot(ProcessingInventory.Slot slot, List<ItemStack> result, TransactionContext tx) {
        if (slot.isResourceBlank()) return;
        result.add(slot.stack()); slot.extract(slot.getResource(), slot.getAmount(), tx);
    }

    @Override public void tick() {
        super.tick();
        var level = getWorld(); if (level == null) return;
        boolean moved = false;
        for (int index = 0; index < incoming.size();) {
            var item = incoming.get(index);
            if (!tick(item) || level.isClientSide() && !blockEntity.isVirtual()) { index++; continue; }
            try (var tx = Transaction.openOuter()) {
                metadata.updateSnapshots(tx);
                var slot = inventory.inputs.getFirst();
                long accepted = slot.insert(ItemVariant.of(item.stack), item.stack.getCount(), tx);
                if (accepted > 0 && heldMetadata == null) heldMetadata = item.copy();
                ItemStack remainder = item.stack.copyWithCount(item.stack.getCount() - (int) accepted);
                incoming.remove(index);
                if (!remainder.isEmpty()) tx.addOuterCloseCallback(result -> {
                    if (result.wasCommitted()) dropAtDepot(remainder);
                });
                tx.commit();
            }
            moved = true;
        }
        var held = getHeldItem(); if (held == null) { heldMetadata = null; return; }
        if (!tick(held) || level.isClientSide()) return;
        if (moved) changed();
        if (handleBeltFunnelOutput()) return;
        var processor = BlockEntityBehaviour.get(level, getPos().above(2), BeltProcessingBehaviour.TYPE);
        if (processor == null || !held.locked && BeltProcessingBehaviour.isBlocked(level, getPos())) return;
        ItemStack before = held.stack.copy(); boolean locked = held.locked;
        var result = locked ? processor.handleHeldItem(held, transportedHandler)
                : processor.handleReceivedItem(held, transportedHandler);
        if (result == BeltProcessingBehaviour.ProcessingResult.REMOVE) { removeHeldItem(); return; }
        held = getHeldItem(); if (held == null) return;
        held.locked = result == BeltProcessingBehaviour.ProcessingResult.HOLD;
        if (locked != held.locked || !ItemStack.matches(before, held.stack)) changed();
    }

    protected boolean tick(TransportedItemStack item) {
        item.prevBeltPosition = item.beltPosition; item.prevSideOffset = item.sideOffset;
        float difference = .5f - item.beltPosition;
        if (difference > 1 / 512f) {
            if (difference > 1 / 32f && !BeltHelper.isItemUpright(item.stack)) item.angle++;
            item.beltPosition += difference / 4f;
        }
        return difference < 1 / 16f;
    }

    private boolean handleBeltFunnelOutput() {
        var state = LoadedLevelAccess.blockState(getWorld(), getPos().above());
        Direction facing = AbstractFunnelBlock.getFunnelFacing(state);
        if (facing == null || !canFunnelsPullFrom.test(facing.getOpposite())) return false;
        var target = directInput.exportingFunnel(null); if (target == null) return false;
        var order = new ArrayList<>(inventory.outputs); order.addAll(inventory.inputs);
        for (var slot : order) {
            if (slot.isResourceBlank()) continue;
            try (var tx = Transaction.openOuter()) {
                var variant = slot.getResource();
                long transferred = target.insert(variant, slot.getAmount(), tx);
                if (transferred == 0) continue;
                if (slot.extract(variant, transferred, tx) != transferred) throw new IllegalStateException("Depot ownership changed during export");
                tx.commit(); return true;
            }
        }
        return false;
    }

    private void applyToAllItems(float maximumDistance, Function<TransportedItemStack, TransportedResult> function) {
        var held = getHeldItem(); if (held == null || .5f - held.beltPosition > maximumDistance) return;
        var processed = held.copy(); var result = function.apply(processed);
        if ((result == null || result.didntChangeFrom(held.stack)) && sameMetadata(held,processed) && ItemStack.matches(held.stack,processed.stack)) return;
        try (var tx = Transaction.openOuter()) {
            metadata.updateSnapshots(tx);
            if (result == null || result.didntChangeFrom(held.stack)) {
                if (ItemStack.matches(held.stack, processed.stack)) heldMetadata = processed.copy();
                else setHeldItem(processed, tx);
                tx.commit(); return;
            }
            applyResult(result, tx);
            tx.commit();
        }
    }
    private static boolean sameMetadata(TransportedItemStack first,TransportedItemStack second) {
        if (!first.id.equals(second.id)||first.beltPosition!=second.beltPosition||first.prevBeltPosition!=second.prevBeltPosition
                ||first.sideOffset!=second.sideOffset||first.prevSideOffset!=second.prevSideOffset||first.angle!=second.angle
                ||first.insertedAt!=second.insertedAt||first.insertedFrom!=second.insertedFrom||first.locked!=second.locked
                ||first.lockedExternally!=second.lockedExternally||!java.util.Objects.equals(first.heldBy,second.heldBy)
                ||first.processedBy!=second.processedBy||!java.util.Objects.equals(first.unresolvedFanProcessingType,second.unresolvedFanProcessingType)||!first.pendingFanInput.equals(second.pendingFanInput)||first.pendingFanCount!=second.pendingFanCount||first.processingTime!=second.processingTime||first.lastProcessingTick!=second.lastProcessingTick) return false;
        var a=first.pendingFanResults;var b=second.pendingFanResults;if(a==null||b==null)return a==b;
        if(a.size()!=b.size())return false;for(int index=0;index<a.size();index++)if(!ItemStack.matches(a.get(index),b.get(index)))return false;return true;
    }
    /** Exact source held → eight output slots → genuine world remainder routing.
     * Every canonical debit/credit and transport change participates in the caller's transaction;
     * overflow becomes a real ItemEntity only after the outer transaction commits.
     */
    public void applyResult(TransportedResult result, TransactionContext tx) {
        if (result == null || result.doesNothing()) return;
        metadata.updateSnapshots(tx);
        setHeldItem(result.hasHeldOutput() ? centered(result.getHeldOutput()) : null, tx);
        var buffer = new CombinedStorage<ItemVariant, ProcessingInventory.Slot>(inventory.outputs);
        for (var output : result.getOutputs()) {
            if (output.stack.isEmpty()) continue;
            var variant = ItemVariant.of(output.stack);
            long placed;
            if (getHeldItemStack().isEmpty()) {
                setHeldItem(centered(output), tx); placed = output.stack.getCount();
            } else placed = buffer.insert(variant, output.stack.getCount(), tx);
            if (placed < output.stack.getCount()) {
                var remainder = output.stack.copyWithCount(output.stack.getCount() - (int) placed);
                tx.addOuterCloseCallback(closed -> { if (closed.wasCommitted()) dropAtDepot(remainder); });
            }
        }
    }
    public boolean applyRecipeOutputs(int consumed, List<ItemStack> outputs, boolean lockOutputs, TransactionContext tx) {
        var held = getHeldItem(); if (held == null || consumed <= 0 || consumed > held.stack.getCount()) return false;
        var left = held.copy(); left.stack.shrink(consumed); left.clearFanProcessingData();
        List<TransportedItemStack> converted = new ArrayList<>();
        for (var stack : outputs) {
            if (stack.isEmpty()) continue;
            var copy = left.copy(); copy.stack = stack.copy(); copy.locked = lockOutputs;
            copy.angle = BeltHelper.isItemUpright(stack) ? 180 : getWorld().getRandom().nextInt(360);
            converted.add(copy);
        }
        applyResult(TransportedResult.convertToAndLeaveHeld(converted, left.stack.isEmpty() ? null : left), tx);
        return true;
    }
    private static TransportedItemStack centered(TransportedItemStack stack) {
        var copy = stack.copy(); copy.beltPosition = copy.prevBeltPosition = .5f; return copy;
    }
    private void dropAtDepot(ItemStack stack) {
        if (stack.isEmpty() || getWorld() == null || getWorld().isClientSide()) return;
        var center = Vec3.atCenterOf(getPos()); Containers.dropItemStack(getWorld(), center.x, center.y + .5, center.z, stack);
    }

    @Override public void clearContent() {
        try (var tx = Transaction.openOuter()) { takeAll(tx); tx.commit(); }
    }
    @Override public void destroy() {
        if (blockEntity.isRemoved() || getWorld() == null || getWorld().isClientSide()) return;
        try (var tx = Transaction.openOuter()) {
            var stacks = takeAll(tx);
            tx.addOuterCloseCallback(result -> { if (result.wasCommitted()) stacks.forEach(this::dropAtDepot); }); tx.commit();
        }
    }
    @Override protected void changed() {
        super.changed();
        if (getWorld() != null && !getWorld().isClientSide())
            getWorld().updateNeighbourForOutputSignal(getPos(), blockEntity.getBlockState().getBlock());
    }

    @Override public void write(ValueOutput output) {
        inventory.save(output.child("Inventory"));
        var held = getHeldItem(); if (held != null) output.store("HeldItem", TransportedItemStack.CODEC, held);
        if (allowMerge && !incoming.isEmpty()) output.store("Incoming", TransportedItemStack.CODEC.listOf(), incoming);
    }
    @Override public void read(ValueInput input) {
        boolean canonical = input.child("Inventory").isPresent();
        inventory.load(input.childOrEmpty("Inventory"));
        heldMetadata = readTransported(input, "HeldItem"); incoming.clear();
        if (!canonical) {
            try (var tx = Transaction.openOuter()) {
                if (heldMetadata != null) inventory.inputs.getFirst().setStack(heldMetadata.stack, tx);
                for (var entry : input.childOrEmpty("OutputBuffer").childrenListOrEmpty("Items")) {
                    int slot = entry.getIntOr("Slot", -1);
                    var stack = entry.read(ItemStack.MAP_CODEC).orElse(ItemStack.EMPTY);
                    if (slot >= 0 && slot < inventory.outputs.size() && !stack.isEmpty())
                        inventory.outputs.get(slot).insert(ItemVariant.of(stack), stack.getCount(), tx);
                }
                tx.commit();
            }
        }
        if (allowMerge) for (var entry : input.childrenListOrEmpty("Incoming")) {
            var transported = readTransported(entry); if (transported != null) incoming.add(transported);
        }
        if (getHeldItemStack().isEmpty()) heldMetadata = null;
    }
    private static TransportedItemStack readTransported(ValueInput input, String name) {
        return input.child(name).map(DepotBehaviour::readTransported).orElse(null);
    }
    private static TransportedItemStack readTransported(ValueInput value) {
        if (value.read("Id", UUIDUtil.CODEC).isPresent()) return value.read(TransportedItemStack.MAP_CODEC).orElse(null);
        var stack = value.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY); if (stack.isEmpty()) return null;
        var transported = new TransportedItemStack(stack, value.getFloatOr("Pos", 0));
        transported.prevBeltPosition = value.getFloatOr("PrevPos", transported.beltPosition);
        transported.sideOffset = value.getFloatOr("Offset", 0); transported.prevSideOffset = value.getFloatOr("PrevOffset", 0);
        transported.angle = value.getIntOr("Angle", 0); transported.insertedAt = value.getIntOr("InSegment", 0);
        transported.insertedFrom = Direction.from3DDataValue(value.getIntOr("InDirection", 1));
        transported.locked = value.getBooleanOr("Locked", false); transported.lockedExternally = value.getBooleanOr("LockedExternally", false);
        value.getString("FanProcessingType").ifPresent(name -> {
            transported.processedBy=com.simibubi.create.content.kinetics.fan.processing.FanProcessing.resolve(name);
            transported.unresolvedFanProcessingType=transported.processedBy==null?name:null;
            transported.processingTime=value.getIntOr("FanProcessingTime",0);
        });
        return transported;
    }
    @SuppressWarnings("unchecked")
    public <T extends BlockEntityBehaviour> T getBehaviour(BehaviourType<T> type) {
        if (type == TYPE) return (T) this;
        if (type == DirectBeltInputBehaviour.TYPE) return (T) directInput;
        if (type == TransportedItemStackHandlerBehaviour.TYPE) return (T) transportedHandler;
        return null;
    }
    @Override public BehaviourType<?> getType() { return TYPE; }
}
