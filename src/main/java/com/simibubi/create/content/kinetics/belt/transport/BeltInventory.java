package com.simibubi.create.content.kinetics.belt.transport;

import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltItemProcessor;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.*;

/** One controller inventory with transactional per-segment views. Positions use original belt units. */
public final class BeltInventory extends SnapshotParticipant<List<TransportedItemStack>> {
    private final BeltBlockEntity owner;
    private final List<TransportedItemStack> items = new ArrayList<>();
    public BeltInventory(BeltBlockEntity owner) { this.owner = owner; }
    public List<TransportedItemStack> getTransportedItems() { return List.copyOf(items); }
    public TransportedItemStack byId(UUID id) { return items.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null); }
    /** Exact legacy light cost; preserve source transport/fan metadata while the real slot transaction pays. */
    public boolean extractLegacyLight(TransportedItemStack actual,ItemVariant expected,int count,TransactionContext transaction){
        if(byId(actual.id)!=actual||actual.stack.getCount()!=count||!expected.matches(actual.stack)||count<1)return false;
        updateSnapshots(transaction);actual.stack.shrink(1);if(actual.stack.isEmpty())items.remove(actual);return true;
    }
    public List<TransportedItemStack> takeForReconfiguration() {
        var cargo = new ArrayList<>(items); items.clear(); owner.setChanged(); return cargo;
    }
    public void acceptReconfigured(List<TransportedItemStack> cargo) {
        items.addAll(cargo); changed();
    }
    public void changed() { owner.contentsChanged(); }
    @Override protected List<TransportedItemStack> createSnapshot() { return items.stream().map(TransportedItemStack::copy).toList(); }
    @Override protected void readSnapshot(List<TransportedItemStack> snapshot) { items.clear(); snapshot.forEach(s -> items.add(s.copy())); }
    @Override protected void onFinalCommit() { changed(); }

    public Storage<ItemVariant> segment(int segment, Direction side) {
        return new Storage<>() {
            @Override public long insert(ItemVariant variant, long amount, TransactionContext tx) {
                StoragePreconditions.notBlankNotNegative(variant, amount);
                if (amount == 0 || !BeltBlock.canTransportObjects(owner.getBlockState()) || !owner.chainLoaded()) return 0;
                float movement = owner.getDirectionAwareBeltMovementSpeed();
                var facing = owner.getBlockState().getValue(BeltBlock.HORIZONTAL_FACING);
                Direction motion = movement >= 0 ? facing : facing.getOpposite();
                if (side != null && movement != 0 && side == motion) return 0;
                var tunnel=BeltTunnelInteractionHandler.getTunnelOnPosition(owner.getLevel(),BeltHelper.getPositionForOffset(owner,segment));
                if(tunnel instanceof com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity brass&&brass.hasDistributionBehaviour())return brass.accept(variant.toStack((int)Math.min(amount,variant.toStack().getMaxStackSize())),side,tx);
                float position = segment + 0.5f;
                if (side != null && side.getAxis() == facing.getAxis()) position = side == facing.getOpposite() ? segment : segment + 1;
                else position += movement >= 0 ? -1f / 16 : 1f / 16;
                final float at = position;
                if (items.stream().anyMatch(s -> !s.stack.isEmpty() && Math.abs(s.beltPosition - at) < 1 - 1e-5)) return 0;
                long inserted = Math.min(amount, variant.toStack().getMaxStackSize());
                updateSnapshots(tx);
                var transported = new TransportedItemStack(variant.toStack((int) inserted), position);
                transported.insertedAt = segment; transported.insertedFrom = side == null ? Direction.UP : side;
                items.add(transported); return inserted;
            }
            @Override public long extract(ItemVariant variant, long amount, TransactionContext tx) {
                StoragePreconditions.notBlankNotNegative(variant, amount);
                long extracted = 0;
                for (var item : List.copyOf(items)) {
                    if (item.beltPosition < segment || item.beltPosition >= segment + 1 || !ItemVariant.of(item.stack).equals(variant)) continue;
                    long taken = Math.min(amount - extracted, item.stack.getCount());
                    if (taken == 0) continue;
                    updateSnapshots(tx); item.stack.shrink((int) taken); extracted += taken;
                    item.clearFanProcessingData();
                    if (item.stack.isEmpty()) items.remove(item);
                    if (extracted == amount) break;
                }
                return extracted;
            }
            @Override public Iterator<StorageView<ItemVariant>> iterator() {
                var views = new ArrayList<StorageView<ItemVariant>>();
                for (var item : items) {
                    if (item.beltPosition < segment || item.beltPosition >= segment + 1 || item.stack.isEmpty()) continue;
                    UUID id = item.id;
                    views.add(new StorageView<>() {
                        private TransportedItemStack current() {
                            var current = byId(id);
                            return current == null || current.beltPosition < segment || current.beltPosition >= segment + 1 ? null : current;
                        }
                        @Override public boolean isResourceBlank() { return current() == null || current().stack.isEmpty(); }
                        @Override public ItemVariant getResource() { return isResourceBlank() ? ItemVariant.blank() : ItemVariant.of(current().stack); }
                        @Override public long getAmount() { return isResourceBlank() ? 0 : current().stack.getCount(); }
                        @Override public long getCapacity() { return isResourceBlank() ? 64 : current().stack.getMaxStackSize(); }
                        @Override public long extract(ItemVariant variant, long amount, TransactionContext tx) {
                            StoragePreconditions.notBlankNotNegative(variant, amount);
                            var current = current();
                            if (current == null || !getResource().equals(variant)) return 0;
                            int taken = (int) Math.min(amount, current.stack.getCount());
                            if (taken != 0) { updateSnapshots(tx); current.stack.shrink(taken); current.clearFanProcessingData(); if (current.stack.isEmpty()) items.remove(current); }
                            return taken;
                        }
                    });
                }
                return views.iterator();
            }
        };
    }
    /** Extract this cargo identity, never a neighbouring stack of the same variant. */
    public int extractItem(UUID id,int amount,TransactionContext tx) {
        var item=byId(id);if(item==null||amount<=0)return 0;
        int taken=Math.min(amount,item.stack.getCount());updateSnapshots(tx);
        item.stack.shrink(taken);item.clearFanProcessingData();if(item.stack.isEmpty())items.remove(item);return taken;
    }
    public boolean applyTransportedResult(UUID id,com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult result,float centre,TransactionContext tx){
        var current=byId(id);if(current==null||result.doesNothing())return false;
        updateSnapshots(tx);items.remove(current);var identities=new HashSet<UUID>();items.forEach(stack->identities.add(stack.id));
        var outputs=new ArrayList<>(result.getOutputs());if(result.hasHeldOutput())outputs.add(result.getHeldOutput());
        for(var output:outputs){if(output.stack.isEmpty())continue;var copy=output.copy();if(result.hasHeldOutput()&&output==result.getHeldOutput())copy.beltPosition=copy.prevBeltPosition=centre+(owner.getDirectionAwareBeltMovementSpeed()>0?-1f/512:1f/512);
            if(!identities.add(copy.id)){var fresh=new TransportedItemStack(copy.stack.copy(),copy.beltPosition);fresh.insertedAt=copy.insertedAt;fresh.insertedFrom=copy.insertedFrom;fresh.heldBy=copy.heldBy;copy=fresh;identities.add(copy.id);}
            items.add(copy);
        }return true;
    }
    public boolean replace(TransportedItemStack input, int consumed, List<ItemStack> outputs, boolean fan, TransactionContext tx) {
        return replace(input,consumed,outputs,fan,tx,true);
    }
    public boolean replace(TransportedItemStack input, int consumed, List<ItemStack> outputs, boolean fan, TransactionContext tx,boolean holdOutputs) {
        var actual = byId(input.id);
        if (actual == null || actual.stack.getCount() < consumed) return false;
        updateSnapshots(tx);
        float position = actual.beltPosition;
        float direction = Math.signum(owner.getDirectionAwareBeltMovementSpeed());
        if (direction == 0) direction = 1;
        actual.stack.shrink(consumed);
        if (actual.stack.isEmpty()) items.remove(actual);
        else if (!fan) {
            // Leave the remaining input just before the processing centre, as upstream does.
            // It crosses the centre again after the head returns and starts the next cycle.
            actual.beltPosition = (float) Math.floor(position) + 0.5f - direction / 512f;
            actual.clearFanProcessingData();
        }
        for (var output : outputs) {
            if (output.isEmpty()) continue;
            var result = new TransportedItemStack(output.copy(), fan ? position : position + direction / 128f);
            result.processedBy = fan ? input.processedBy : null; result.processingTime = fan ? -1 : 0;
            result.unresolvedFanProcessingType=fan?input.unresolvedFanProcessingType:null;
            result.heldBy = holdOutputs?input.heldBy:null;
            items.add(result);
        }
        return true;
    }
    public void tick() {
        Level level = owner.getLevel();
        if (level == null || !owner.chainLoaded()) return;
        float movement = owner.getDirectionAwareBeltMovementSpeed();
        if(level.isClientSide()&&!owner.isVirtual())movement*=com.simibubi.create.foundation.utility.ServerSpeedProvider.get();
        for (var item : items) item.prevBeltPosition = item.beltPosition;
        if (movement == 0) return;
        var ordered = new ArrayList<>(items);
        ordered.sort(Comparator.comparingDouble((TransportedItemStack s) -> s.beltPosition).thenComparing(s -> s.id));
        if (movement > 0) Collections.reverse(ordered);
        TransportedItemStack ahead = null;
        boolean changed = false;
        for (var snapshot : ordered) {
            var item=byId(snapshot.id);
            if (item==null || item.stack.isEmpty()) continue;
            // Source client/virtual scenes can force-stall an actual transported stack.
            if(level.isClientSide()&&item.locked){ahead=item;continue;}
            if(item.lockedExternally){item.lockedExternally=false;ahead=item;continue;}
            float next = item.beltPosition + movement;
            if (ahead != null) next = movement > 0 ? Math.max(item.beltPosition, Math.min(next, ahead.beltPosition - 1))
                    : Math.min(item.beltPosition, Math.max(next, ahead.beltPosition + 1));
            if(item.heldBy==null){
                next=BeltFunnelInteractionHandler.checkForFunnels(owner,this,item,next);
                var current=byId(item.id);if(current==null)continue;item=current;
                next=BeltTunnelInteractionHandler.checkForTunnels(owner,this,item,next);
                current=byId(item.id);if(current==null)continue;item=current;
            }
            if (!level.isClientSide()) {
                if (item.heldBy != null) {
                    if (LoadedLevelAccess.blockEntity(level,item.heldBy) instanceof BeltItemProcessor processor
                            && processor.tryHoldBeltItem(owner, item)) { ahead = item; continue; }
                    item.heldBy = null; changed = true;
                }
                if (owner.getBlockState().getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL) {
                    int first = (int) Math.floor(item.beltPosition + (movement > 0 ? 0.5f : -0.5f));
                    for (int segment = first; segment >= 0 && segment < owner.beltLength; segment += movement > 0 ? 1 : -1) {
                        float center = segment + 0.5f;
                        if (movement > 0 ? center > next : center < next) break;
                        var location = BeltHelper.getPositionForOffset(owner, segment);
                        if (!level.getBlockState(location.above()).getCollisionShape(level, location.above()).isEmpty()) continue;
                        if (LoadedLevelAccess.blockEntity(level,location.above(2)) instanceof BeltItemProcessor processor && processor.onBeltItemReceived(item)) {
                            item.beltPosition = center + Math.signum(movement) / 512f; item.heldBy = location.above(2); next = item.beltPosition; changed = true; break;
                        }
                    }
                    if (item.heldBy != null) { ahead = item; continue; }
                }
            } else if (item.heldBy != null) { ahead = item; continue; }
            boolean end = movement > 0 ? next >= owner.beltLength - 0.45f : next <= 0.45f;
            if (end && (!level.isClientSide() || owner.isVirtual())) {
                int outside = movement > 0 ? owner.beltLength : -1;
                var target = BeltHelper.getPositionForOffset(owner, outside);
                Direction motion = movement > 0 ? owner.getBeltFacing() : owner.getBeltFacing().getOpposite();
                var storage = com.simibubi.create.foundation.item.LoadedItemStorage.find(level, target, motion.getOpposite());
                if (storage != null) {
                    try (Transaction tx = Transaction.openOuter()) {
                        long inserted = storage.insert(ItemVariant.of(item.stack), item.stack.getCount(), tx);
                        if (inserted != 0) { updateSnapshots(tx); item.stack.shrink((int) inserted); if (item.stack.isEmpty()) items.remove(item); tx.commit(); changed = true; }
                    }
                    if (!items.contains(item)) continue;
                    next = movement > 0 ? Math.min(next, owner.beltLength - 0.45f) : Math.max(next, 0.45f);
                } else if (!level.isLoaded(target) || !level.getBlockState(target).getCollisionShape(level, target).isEmpty()) {
                    next = movement > 0 ? Math.min(next, owner.beltLength - 0.45f) : Math.max(next, 0.45f);
                } else if (movement > 0 ? next >= owner.beltLength : next <= 0) {
                    item.beltPosition = next; eject(item); items.remove(item); changed = true; continue;
                }
            }
            item.beltPosition = next; ahead = item;
        }
        if (!level.isClientSide()) {
            owner.setChanged();
            if (changed || level.getGameTime() % 5 == 0 && !items.isEmpty()) changed();
        }
    }
    public void eject(TransportedItemStack stack) {
        if (owner.getLevel() == null || owner.getLevel().isClientSide() && !owner.isVirtual() || stack.stack.isEmpty()) return;
        var position = BeltHelper.getVectorForOffset(owner, stack.beltPosition).add(0, 6f / 16, 0);
        var facing = owner.getBeltFacing(); float speed = owner.getDirectionAwareBeltMovementSpeed();
        var entity = new ItemEntity(owner.getLevel(), position.x, position.y, position.z, stack.stack.copy(),
                facing.getStepX() * Math.copySign(Math.max(Math.abs(speed), 0.125), speed), 0.125,
                facing.getStepZ() * Math.copySign(Math.max(Math.abs(speed), 0.125), speed));
        entity.setDefaultPickUpDelay(); owner.getLevel().addFreshEntity(entity);
    }
    public void ejectAll() { items.forEach(this::eject); items.clear(); owner.setChanged(); }
    public void save(ValueOutput output) { output.store("Items", TransportedItemStack.CODEC.listOf(), items); }
    public void load(ValueInput input) {
        boolean present = input.read("Items", com.mojang.serialization.Codec.PASSTHROUGH).isPresent();
        var raw = input.read("Items", net.minecraft.nbt.CompoundTag.CODEC.listOf()).orElseGet(() -> {
            if (present) throw new IllegalArgumentException("Malformed persisted belt cargo rows");
            return List.of();
        });
        for (var row : raw) for (String key : List.of("Pos", "PrevPos", "Offset", "PrevOffset"))
            if ((key.equals("Pos") || row.contains(key)) && !Float.isFinite(row.getFloatOr(key, Float.NaN)))
                throw new IllegalArgumentException("Nonfinite persisted belt cargo " + key);
        var loaded = input.read("Items", TransportedItemStack.CODEC.listOf()).orElseGet(() -> {
            if (present) throw new IllegalArgumentException("Malformed persisted native belt cargo");
            return List.of();
        });
        var seen = new HashSet<UUID>();
        for (var stack : loaded) {
            if (stack.stack.isEmpty() || !seen.add(stack.id)) throw new IllegalArgumentException("Empty or duplicate persisted belt cargo identity");
            if (owner.beltLength < 2 || stack.beltPosition < 0 || stack.beltPosition > owner.beltLength
                    || stack.prevBeltPosition < 0 || stack.prevBeltPosition > owner.beltLength
                    || stack.insertedAt < 0 || stack.insertedAt >= owner.beltLength)
                throw new IllegalArgumentException("Persisted cargo is outside its containing belt geometry");
        }
        items.clear(); items.addAll(loaded);
    }
}
