package com.simibubi.create.content.kinetics.chainConveyor;

import com.simibubi.create.content.logistics.box.PackageItem;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** An owned parcel, with a transient tick stamp preserved when ownership moves. */
public final class ChainConveyorPackage {
    private static final AtomicInteger IDS = new AtomicInteger();
    public final int netId;
    public final UUID identity;
    public final ItemStack item;
    public float chainPosition;
    public boolean reversed;
    public Vec3 worldPosition;
    public float yaw;
    long lastTick = Long.MIN_VALUE;
    ChainConveyorBlockEntity owner;

    public ChainConveyorPackage(float position, ItemStack item) { this(position, item, IDS.incrementAndGet(),UUID.randomUUID()); }
    private ChainConveyorPackage(float position, ItemStack item, int id, UUID identity) {
        this.chainPosition = position;
        this.item = item.copyWithCount(1);
        this.netId = id;
        this.identity = identity;
    }
    void write(ValueOutput out) {
        out.putFloat("Position", chainPosition);
        out.store("Item", ItemStack.CODEC, item);
        out.putInt("NetID", netId);
        out.store("Identity",UUIDUtil.CODEC,identity);
        out.putBoolean("Reversed", reversed);
    }
    static ChainConveyorPackage read(ValueInput in, boolean defaultReversed, boolean clientPacket) {
        var item = in.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        float position = in.getFloatOr("Position", 0);
        if (!PackageItem.isPackage(item) || !Float.isFinite(position)) return null;
        var suppliedId = clientPacket ? in.getInt("NetID") : java.util.Optional.<Integer>empty();
        int id = suppliedId.orElseGet(IDS::incrementAndGet);
        if (suppliedId.isPresent() && id > 0) IDS.accumulateAndGet(id, Math::max);
        var parcel = new ChainConveyorPackage(position, item, id,in.read("Identity",UUIDUtil.CODEC).orElseGet(UUID::randomUUID));
        parcel.reversed = in.getBooleanOr("Reversed", defaultReversed);
        return parcel;
    }
}
