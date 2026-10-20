package com.simibubi.create.content.fluids.tank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;
import java.util.*;

/** Holds fluid safely when a tank is dismantled while another part is in an unloaded chunk. */
public final class TankRecoveryData extends SavedData {
    public record Pending(BlockPos origin, List<BlockPos> members, FluidVariant fluid, long amount, boolean known, boolean windows, boolean creative, List<BlockPos> removed) {
        static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("Origin").forGetter(Pending::origin), BlockPos.CODEC.listOf().fieldOf("Members").forGetter(Pending::members),
                FluidVariant.CODEC.fieldOf("Fluid").forGetter(Pending::fluid), Codec.LONG.fieldOf("Amount").forGetter(Pending::amount),
                Codec.BOOL.fieldOf("Known").forGetter(Pending::known), Codec.BOOL.fieldOf("Window").forGetter(Pending::windows), Codec.BOOL.fieldOf("Creative").forGetter(Pending::creative), BlockPos.CODEC.listOf().optionalFieldOf("Removed", List.of()).forGetter(Pending::removed)
        ).apply(i, Pending::new));
        Pending without(BlockPos pos) { var omitted = new ArrayList<>(removed); if (!omitted.contains(pos)) omitted.add(pos); return new Pending(origin, members.stream().filter(p -> !p.equals(pos)).toList(), fluid, amount, known, windows, creative, List.copyOf(omitted)); }
    }
    public static final Codec<TankRecoveryData> CODEC = Pending.CODEC.listOf().fieldOf("Pending").codec().xmap(TankRecoveryData::new, data -> List.copyOf(data.pending));
    public static final SavedDataType<TankRecoveryData> TYPE = new SavedDataType<>(Create.asResource("fluid_tank_recovery"), TankRecoveryData::new, CODEC, null);
    private final List<Pending> pending = new ArrayList<>();
    public TankRecoveryData() {}
    private TankRecoveryData(List<Pending> values) { pending.addAll(values); }
    public static TankRecoveryData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    private Pending find(BlockPos pos) { return pending.stream().filter(p -> p.origin.equals(pos) || p.members.contains(pos)).findFirst().orElse(null); }
    public boolean contains(BlockPos pos) { return find(pos) != null; }
    /** Also public for contraption or other structure adapters which need to defer a split. */
    public void deferRemoval(FluidTankBlockEntity part) {
        var level = (ServerLevel) part.getLevel(); var removing = part.getBlockPos(); var old = find(removing);
        if (old == null) old = pending.stream().filter(entry -> entry.origin.equals(part.getController())).findFirst().orElse(null);
        if (old != null) {
            pending.remove(old);
            if (!old.known && removing.equals(old.origin)) old = capture(old, part);
            var next = old.without(removing); if (!next.members.isEmpty()) pending.add(next); setDirty(); return;
        }
        var master = part.getControllerBE(); var origin = part.getController();
        int width = master == null ? part.getWidth() : master.getWidth(), height = master == null ? part.getHeight() : master.getHeight();
        var members = new ArrayList<BlockPos>();
        for (int y=0;y<height;y++) for(int x=0;x<width;x++) for(int z=0;z<width;z++) { var pos=origin.offset(x,y,z); if(!pos.equals(removing)) members.add(pos); }
        var entry = new Pending(origin, List.copyOf(members), FluidVariant.blank(), 0, false, part.hasWindows(), part instanceof CreativeFluidTankBlockEntity, List.of(removing));
        if (master != null) entry = capture(entry, master);
        if (!members.isEmpty()) pending.add(entry); setDirty();
        for(var pos:members) if(level.isLoaded(pos) && level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) tank.sendDataImmediately();
    }
    private Pending capture(Pending entry, FluidTankBlockEntity master) {
        var stack = master.getTankInventory().getFluid();
        var members = new ArrayList<BlockPos>();
        for (int y=0;y<master.getHeight();y++) for(int x=0;x<master.getWidth();x++) for(int z=0;z<master.getWidth();z++) { var member=entry.origin.offset(x,y,z); if(!entry.removed.contains(member)) members.add(member); }
        var result = new Pending(entry.origin, List.copyOf(members), stack.variant(), stack.getAmount(), true, master.hasWindows(), master instanceof CreativeFluidTankBlockEntity, entry.removed);
        master.getTankInventory().clear(); return result;
    }
    /** Returns true while a member belongs to a deferred split; never asks Minecraft to load a chunk. */
    public boolean tryRestore(ServerLevel level, BlockPos pos) {
        var entry = find(pos); if (entry == null) return false;
        for (var member:entry.members) if(!level.isLoaded(member)) return true;
        if(!entry.known) {
            if(!(level.getBlockEntity(entry.origin) instanceof FluidTankBlockEntity master)) return true;
            pending.remove(entry); entry=capture(entry,master); pending.add(entry); setDirty();
            for (var member:entry.members) if(!level.isLoaded(member)) return true;
        }
        var survivors=new ArrayList<FluidTankBlockEntity>();
        for(var member:entry.members) if(level.getBlockEntity(member) instanceof FluidTankBlockEntity tank && (tank instanceof CreativeFluidTankBlockEntity)==entry.creative) survivors.add(tank);
        pending.remove(entry); setDirty();
        long remaining=entry.amount;
        long configurationOverflow=entry.creative?0:Math.max(0,entry.amount-FluidTankBlockEntity.capacityForBlocks(entry.members.size()+entry.removed.size()));
        for(var tank:survivors) {
            tank.resetForRecovery(entry.windows);
            long amount=entry.creative?FluidTankBlockEntity.getCapacityMultiplier():Math.min(remaining,FluidTankBlockEntity.getCapacityMultiplier());
            if(!entry.fluid.isBlank()&&amount>0)tank.getTankInventory().setFluid(new FluidStack(entry.fluid,amount));
            remaining-=amount;
        }
        if(configurationOverflow>0&&remaining>0&&!survivors.isEmpty()&&!entry.fluid.isBlank())survivors.getFirst().acceptOwnedFluid(new FluidStack(entry.fluid,Math.min(configurationOverflow,remaining)));
        return true;
    }
}
