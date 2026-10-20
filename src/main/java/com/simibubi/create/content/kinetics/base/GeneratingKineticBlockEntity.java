package com.simibubi.create.content.kinetics.base;

import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Source generating transitions on the canonical native topology and genuine source stress network. */
public abstract class GeneratingKineticBlockEntity extends KineticBlockEntity {
    public boolean reActivateSource;
    private long generatorTick = Long.MIN_VALUE;

    public GeneratingKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected void notifyStressCapacityChange(float capacity) {
        var group = getOrCreateNetwork();
        if (group != null) group.updateCapacityFor(this, capacity);
    }

    @Override public void removeSource() {
        if (hasSource() && isSource()) reActivateSource = true;
        super.removeSource();
    }

    @Override public void setSource(BlockPos source) {
        super.setSource(source);
        checkSourceReactivation();
    }

    @Override public void setKineticProvenance(BlockPos generator, BlockPos upstream, net.minecraft.core.Direction face) {
        boolean lostSource = hasSource() && upstream == null && isSource();
        super.setKineticProvenance(generator, upstream, face);
        if (lostSource) reActivateSource = true;
        checkSourceReactivation();
    }

    private void checkSourceReactivation() {
        if (reActivateSource && level != null && hasSource()
                && LoadedLevelAccess.blockEntity(level, getSource()) instanceof KineticBlockEntity source
                && Math.abs(source.getSpeed()) >= Math.abs(getGeneratedSpeed())) reActivateSource = false;
    }

    /** Called by the real Kinetic tick bridge, including source addon instance tick(). */
    public final void tickGenerator() {
        if (level == null || level.isClientSide() || generatorTick == level.getGameTime()) return;
        generatorTick = level.getGameTime();
        if (reActivateSource) {
            reActivateSource = false;
            updateGeneratedRotation();
        }
    }

    public void updateGeneratedRotation() {
        if (!(level instanceof ServerLevel server) || isVirtual() || !KineticNetworks.isLive(server, this)) return;
        float generated = getGeneratedSpeed();
        float previous = speed;
        beginNetworkUpdate();
        try {
            if (previous != generated) applyNewSpeed(previous, generated);
            if (hasNetwork() && generated != 0) {
                var group = getOrCreateNetwork();
                notifyStressCapacityChange(calculateAddedStressCapacity());
                group.updateStressFor(this, calculateStressApplied());
                group.updateStress();
            }
            forceSpeedCallback();
            setChanged(); sendData();
            KineticNetworks.generatorChanged(server, this);
        } finally { endNetworkUpdate(); }
    }

    public void applyNewSpeed(float previousSpeed, float generatedSpeed) {
        if (!(level instanceof ServerLevel server) || isVirtual() || !KineticNetworks.isLive(server, this)) return;
        if (generatedSpeed == 0) {
            if (hasSource()) {
                notifyStressCapacityChange(0);
                if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
                return;
            }
            detachKinetics(); setSpeed(0); setNetwork(null); return;
        }
        if (previousSpeed == 0) {
            setSpeed(generatedSpeed); setNetwork(createNetworkId()); attachKinetics(); return;
        }
        if (hasSource()) {
            if (Math.abs(previousSpeed) >= Math.abs(generatedSpeed)) {
                if (Math.signum(previousSpeed) != Math.signum(generatedSpeed)) KineticNetworks.destroyForConflict(this);
                return;
            }
            detachKinetics(); setSpeed(generatedSpeed);
            setKineticProvenance(worldPosition, null, null);
            setNetwork(createNetworkId()); attachKinetics(); return;
        }
        detachKinetics(); setSpeed(generatedSpeed); attachKinetics();
    }

    /** Source network identity is the actual generating cell, matching native provenance. */
    public Long createNetworkId() { return worldPosition.asLong(); }
}
