package com.simibubi.create.content.processing.burner;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.Create;
import com.simibubi.create.api.registry.CreateDataMaps;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

public final class BlazeBurnerBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    public boolean goggles;
    public boolean trainHat;
    public void setGoggles(boolean value){goggles=value;setChanged();sync();}
    public static final int MAX_HEAT_CAPACITY = 10000;
    public static final int INSERTION_THRESHOLD = 500;
    private static final TagKey<Item> NORMAL_FUEL = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Create.asResource("blaze_burner_fuel/regular"));
    private static final TagKey<Item> SPECIAL_FUEL = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Create.asResource("blaze_burner_fuel/special"));
    private int fuelType; // 0: none, 1: normal, 2: special, persisted with upstream ordinal.
    private int remainingBurnTime;
    private boolean creative;
    public BlazeBurnerBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.HEATER, pos, state); }
    public int getRemainingBurnTime() { return remainingBurnTime; }
    public boolean isCreative() { return creative; }

    private int normalBurnTime(ItemStack stack) {
        if (!(level instanceof ServerLevel server)) return 0;
        var params = new LootParams.Builder(server).withParameter(LootContextParams.BLOCK_STATE, getBlockState())
                .withParameter(LootContextParams.BLOCK_ENTITY, this).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .withParameter(LootContextParams.CONTAINER, new SimpleContainer(stack.copyWithCount(1))).create(LootContextParamSets.CONTAINER_PROCESS);
        var context = new LootContext.Builder(params).create(Optional.empty());
        int duration = ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
        return duration > 0 ? duration : stack.is(NORMAL_FUEL) ? 1600 : 0;
    }
    private record FuelState(int type, int remaining) {}
    private final net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant<FuelState> fuelTransaction = new net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant<>() {
        @Override protected FuelState createSnapshot() { return new FuelState(fuelType,remainingBurnTime); }
        @Override protected void readSnapshot(FuelState state) { fuelType=state.type();remainingBurnTime=state.remaining(); }
        @Override protected void onFinalCommit() {
            updateHeat();setChanged();sync();
            level.playSound(null,worldPosition,SoundEvents.BLAZE_SHOOT,SoundSource.BLOCKS,0.25f,0.75f);
        }
    };
    public boolean tryFuel(ItemStack stack, boolean forceOverflow, boolean simulate) {
        try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            boolean accepted=tryFuel(stack,forceOverflow,transaction);
            if(accepted&&!simulate)transaction.commit();
            return accepted;
        }
    }
    /** Reserve the actual fuel fields; heat, sound and network updates run only on final commit. */
    public boolean tryFuel(ItemStack stack,boolean forceOverflow,net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        if (creative || stack.isEmpty() || !(level instanceof ServerLevel server)
            || getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.NONE) return false;
        var superheated = CreateDataMaps.get(server.getServer(), CreateDataMaps.SUPERHEATED_BLAZE_BURNER_FUELS, stack.getItem());
        var regular = CreateDataMaps.get(server.getServer(), CreateDataMaps.REGULAR_BLAZE_BURNER_FUELS, stack.getItem());
        int newType, duration;
        if (superheated != null) {
            newType = 2; duration = superheated.burnTime();
        } else if (regular != null) {
            newType = 1; duration = regular.burnTime();
        } else if (stack.is(SPECIAL_FUEL)) {
            newType = 2; duration = 3200;
        } else {
            newType = 1; duration = normalBurnTime(stack);
        }
        if (duration <= 0 || newType < fuelType) return false;
        if (newType == fuelType) {
            if (remainingBurnTime <= INSERTION_THRESHOLD) duration += remainingBurnTime;
            else if (forceOverflow && newType == 1) duration = remainingBurnTime < MAX_HEAT_CAPACITY
                ? Math.min(MAX_HEAT_CAPACITY, remainingBurnTime + duration) : remainingBurnTime;
            else return false;
        }
        fuelTransaction.updateSnapshots(transaction);
        fuelType=newType;remainingBurnTime=duration;
        return true;
    }
    public void applyCreativeFuel() {
        creative = true; fuelType = 0; remainingBurnTime = 0;
        var heat = getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL);
        heat = switch (heat) {
            case NONE, SMOULDERING, FADING -> BlazeBurnerBlock.HeatLevel.KINDLED;
            case KINDLED -> BlazeBurnerBlock.HeatLevel.SEETHING;
            case SEETHING -> BlazeBurnerBlock.HeatLevel.SMOULDERING;
        };
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
        setChanged(); sync();
    }
    private void updateHeat() {
        if (getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.NONE) return;
        var heat = fuelType == 2 ? BlazeBurnerBlock.HeatLevel.SEETHING : fuelType == 1
                ? remainingBurnTime < 125 ? BlazeBurnerBlock.HeatLevel.FADING : BlazeBurnerBlock.HeatLevel.KINDLED
                : BlazeBurnerBlock.HeatLevel.SMOULDERING;
        if (heat != getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL)) {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat)); sync();
        }
    }
    public static void tick(Level level, BlockPos pos, BlockState state, BlazeBurnerBlockEntity burner) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, burner)) {
            if (!createTick.shouldTick()) return;

        if (level.isClientSide() || burner.creative || state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.NONE) return;
        if (burner.remainingBurnTime > 0) { burner.remainingBurnTime--; burner.setChanged(); }
        if (burner.remainingBurnTime == 0 && burner.fuelType != 0) {
            if (burner.fuelType == 2) { burner.fuelType = 1; burner.remainingBurnTime = MAX_HEAT_CAPACITY / 2; }
            else burner.fuelType = 0;
        }
        burner.updateHeat();
    
        }
    }
    private void sync() { if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); }
    @Override protected void write(ValueOutput output, boolean clientPacket) {
        if(goggles)output.putBoolean("Goggles",true);
        if(trainHat)output.putBoolean("TrainHat",true);
        super.write(output, clientPacket); output.putInt("fuelLevel", fuelType); output.putInt("burnTimeRemaining", remainingBurnTime); output.putBoolean("isCreative", creative);
    }
    @Override protected void read(ValueInput input, boolean clientPacket) {
        goggles=input.getBooleanOr("Goggles",false);
        trainHat=input.getBooleanOr("TrainHat",false);
         fuelType = Math.clamp(input.getIntOr("fuelLevel", 0), 0, 2);
        remainingBurnTime = input.getIntOr("burnTimeRemaining", 0); creative = input.getBooleanOr("isCreative", false);
    
        super.read(input, clientPacket);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return super.getUpdateTag(registries); }
}
