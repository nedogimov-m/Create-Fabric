package com.simibubi.create.content.contraptions.actors.psi;

import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.foundation.item.MountedItemInventory;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;

/** Source endpoint timer/keep-alive protocol; connections themselves are transient. */
public abstract class PortableStorageInterfaceBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    public static final int ANIMATION = 4;
    private java.util.UUID advancementOwner;
    public java.util.UUID advancementOwner(){return advancementOwner;}
    public void placedBy(net.minecraft.server.level.ServerPlayer player){if(!com.simibubi.create.foundation.advancement.GaugeAdvancements.actualPlacer(this,player))return;advancementOwner=player.getUUID();removeCompletedOwner();setChanged();}
    private void removeCompletedOwner(){if(!com.simibubi.create.foundation.advancement.AdvancementBehaviour.hasPendingFor(this,advancementOwner)&&com.simibubi.create.foundation.advancement.GaugeAdvancements.complete(this,advancementOwner,"portable_storage_interface")){advancementOwner=null;setChanged();}}
    protected int transferTimer;
    protected float distance;
    protected boolean powered;
    protected AbstractContraptionEntity connectedEntity;
    private boolean initialized;
    private float previousAnimation, animation;
    private long connectionGeneration;
    private Direction connectedFacing;
    public int keepAlive;

    protected PortableStorageInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    protected abstract void connectStorage(Contraption contraption, long generation);
    protected abstract void clearStorage();

    public void startTransferringTo(Contraption contraption, float distance) {
        if (connectedEntity == contraption.entity) return;
        MountedItemInventory.requireNoTransaction();
        if (contraption.entity == null || !contraption.entity.isAlive() || !contraption.getStorage().ownsContents()) return;
        if (!Float.isFinite(distance)) throw new IllegalArgumentException("Non-finite interface connection distance");
        this.distance = Math.clamp(distance, 0, 2);
        connectedEntity = contraption.entity;
        connectedFacing = getBlockState().getValue(PortableStorageInterfaceBlock.FACING);
        connectStorage(contraption, ++connectionGeneration);
        startConnecting();
        notifyUpdate();
        notifyNeighbors();
    }

    protected void stopTransferring() {
        if (connectedEntity == null) return;
        connectedEntity = null;
        connectionGeneration++;
        clearStorage();
        notifyNeighbors();
    }

    public boolean canTransfer() {
        return !powered && isConnected() && connectedEntity != null && connectedEntity.isAlive()
            && getBlockState().getValue(PortableStorageInterfaceBlock.FACING) == connectedFacing
            && connectedEntity.getContraption() != null && connectedEntity.getContraption().getStorage().ownsContents()
            && level != null && !level.isClientSide() && !isRemoved()
            && LoadedLevelAccess.blockEntity(level, worldPosition) == this;
    }

    boolean ownsConnection(AbstractContraptionEntity entity, long generation) {
        return connectedEntity == entity && connectionGeneration == generation && canTransfer();
    }
    boolean connectedToAnother(Contraption contraption) { return connectedEntity != null && connectedEntity.isAlive() && connectedEntity != contraption.entity; }
    public AbstractContraptionEntity getConnectedEntity() { return connectedEntity; }
    public int getTransferTimer() { return transferTimer; }
    public boolean isPowered() { return powered; }
    public boolean isTransferring() { return transferTimer > ANIMATION; }
    public boolean isConnected() { return transferTimer >= ANIMATION && transferTimer <= getTransferTimeout() + ANIMATION; }
    public int getTransferTimeout() { return Math.max(1, ContraptionSettings.psiTimeout); }
    public float getConnectionDistance() { return distance; }
    public float getExtensionDistance(float partial) {
        float value = previousAnimation + (animation - previousAnimation) * partial;
        return value * value * distance / 2;
    }
    public void startConnecting() { transferTimer = 6 + ANIMATION * 2; }
    public void onContentTransferred() { transferTimer = getTransferTimeout() + ANIMATION;if(canTransfer())com.simibubi.create.foundation.advancement.GaugeAdvancements.award(this,advancementOwner,"portable_storage_interface");removeCompletedOwner();notifyUpdate(); }

    public static void tick(Level level, BlockPos pos, BlockState state, PortableStorageInterfaceBlockEntity endpoint) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, endpoint)) {
            if (!createTick.shouldTick()) return;
 endpoint.tick(); 
        }
    }
    public void tick() {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, worldPosition, getBlockState(), this)) {
            if (!createTick.shouldTick()) return;

        if (!initialized) {
            initialized = true;if(!level.isClientSide())removeCompletedOwner();
            if (!level.isClientSide()) { powered = level.hasNeighborSignal(worldPosition); if (!powered) notifyContraptions(); }
        }
        previousAnimation = animation;
        boolean wasConnected = isConnected();
        if (!level.isClientSide() && connectedEntity != null && (!connectedEntity.isAlive()
            || connectedEntity.getContraption() == null || !connectedEntity.getContraption().getStorage().ownsContents()
            || getBlockState().getValue(PortableStorageInterfaceBlock.FACING) != connectedFacing)) stopTransferring();
        if (keepAlive > 0 && --keepAlive == 0 && !level.isClientSide()) {
            stopTransferring(); transferTimer = ANIMATION - 1; notifyUpdate(); return;
        }
        int timeout = getTransferTimeout();
        transferTimer = Math.min(transferTimer, ANIMATION * 2 + timeout);
        boolean decrement = transferTimer > ANIMATION || transferTimer > 0 && keepAlive == 0
            && (!level.isClientSide() || transferTimer != ANIMATION);
        if (decrement) {
            transferTimer--;
            if (transferTimer == ANIMATION - 1) notifyUpdate();
            if (transferTimer <= 0 || powered) stopTransferring();
        }
        if (!level.isClientSide() && wasConnected != isConnected()) { setChanged(); notifyNeighbors(); }
        if (isConnected()) animation = 1;
        else if (transferTimer >= timeout + ANIMATION) animation = 1 - (transferTimer - timeout - ANIMATION) / (float) ANIMATION;
        else if (transferTimer < ANIMATION) animation = transferTimer / (float) ANIMATION;
        else animation = 0;
        animation = Math.clamp(animation, 0, 1);
    
        }
    }

    public void neighbourChanged() {
        if (level == null) return;
        boolean next = level.hasNeighborSignal(worldPosition);
        if (next == powered) return;
        powered = next;
        if (powered) stopTransferring(); else notifyContraptions();
        notifyUpdate();
    }
    private void notifyContraptions() {
        if (level != null) level.getEntitiesOfClass(AbstractContraptionEntity.class, new AABB(worldPosition).inflate(3))
            .forEach(AbstractContraptionEntity::refreshPSIs);
    }
    private void notifyNeighbors() {
        if (level != null && !level.isClientSide() && !isRemoved() && LoadedLevelAccess.isLoaded(level, worldPosition)) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    public void notifyUpdate() {
        setChanged();
        if (level != null && !level.isClientSide() && !isRemoved()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override public void invalidate() {
        connectedEntity = null; connectionGeneration++; clearStorage(); initialized = false;
        super.invalidate();
    }
    @Override protected void write(ValueOutput out, boolean clientPacket) {
        super.write(out, clientPacket); out.putInt("Timer", transferTimer); out.putFloat("Distance", distance); out.putBoolean("Powered", powered);
    }
    @Override protected void read(ValueInput in, boolean clientPacket) {
        super.read(in, clientPacket);
        transferTimer = in.getIntOr("Timer", 0);
        float incoming = in.getFloatOr("Distance", 0); distance = Float.isFinite(incoming) ? incoming : 0;
        boolean oldPowered = powered; powered = in.getBooleanOr("Powered", false);
        if (level != null && level.isClientSide() && oldPowered && !powered) notifyContraptions();
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var out = TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, registries); write(out, true); return out.buildResult();
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("portable_storage_interface");
    }
    private final com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage canonicalAdvancementOwner=new com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage() {
        @Override public java.util.UUID getPlayerId(){return advancementOwner;}
        @Override public void setPlayerId(java.util.UUID id){advancementOwner=id;}
    };
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return canonicalAdvancementOwner;}
}
