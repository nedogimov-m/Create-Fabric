package com.simibubi.create.content.trains.signal;

import com.mojang.serialization.Codec;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.track.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/**
 * Real native signal owner and source state/overlay/input lifecycle. Traffic consumers remain
 * absent.
 */
public final class SignalBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity
    implements TrackEdgePoint.MigrationReceiver,
        TrackTargetingBlockItem.Target,
        TransformableBlockEntity {
  public enum OverlayState {
    RENDER,
    SKIP,
    DUAL
  }

  public enum SignalState {
    RED,
    YELLOW,
    GREEN,
    INVALID;
    public static final Codec<SignalState> CODEC =
        Codec.STRING.xmap(SignalState::valueOf, SignalState::name);

    public boolean isRedLight(float time) {
      return this == RED || this == INVALID && time % 40 < 3;
    }

    public boolean isYellowLight(float time) {
      return this == YELLOW;
    }

    public boolean isGreenLight(float time) {
      return this == GREEN;
    }
  }

  private final TrackTargetingBehaviour<SignalBoundary> targeting;
  private SignalState signalState = SignalState.INVALID;
  private OverlayState overlay = OverlayState.SKIP;
  private int switchToRedAfterTrainEntered;
  private boolean lastReportedPower;
  private long revision;

  public SignalBlockEntity(BlockPos pos, BlockState state) {
    super(AllBlockEntityTypes.TRACK_SIGNAL, pos, state);
    targeting =
        new TrackTargetingBehaviour<>(this, SignalEdgePoints.SIGNAL, this::targetingChanged);
  }

  @Override
  public TrackTargetingBehaviour<SignalBoundary> targeting() {
    return targeting;
  }

  @Override
  public boolean setTarget(TrackTargetingSelection selected) {
    boolean accepted = targeting.configure(selected);
    if (accepted) targeting.resolveTick();
    return accepted;
  }

  public SignalBoundary getSignal() {
    var point = targeting.getEdgePoint();
    return point != null && level != null && point.hasOwner(owner()) ? point : null;
  }

  public GlobalPos owner() {
    return GlobalPos.of(level.dimension(), worldPosition);
  }

  public boolean getReportedPower() {
    return lastReportedPower;
  }

  public SignalState getState() {
    return signalState;
  }

  public OverlayState getOverlay() {
    return overlay;
  }

  public boolean isPowered() {
    return signalState == SignalState.RED;
  }

  public long revision() {
    return revision;
  }

  private void targetingChanged() {
    revision = Math.incrementExact(revision);
    notifyUpdate();
  }

  public void notifyUpdate() {
    setChanged();
    if (level != null && !level.isClientSide())
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
  }

  public static void tick(Level level, BlockPos pos, BlockState state, SignalBlockEntity signal) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, signal)) {
            if (!createTick.shouldTick()) return;

    
    if (level.isClientSide()) return;
    var boundary = signal.getSignal();
    if (boundary == null) {
      signal.enterState(SignalState.INVALID);
      signal.setOverlay(OverlayState.RENDER);
      return;
    }
    boolean power = state.getValue(SignalBlock.POWERED);
    if (signal.lastReportedPower != power) {
      signal.lastReportedPower = power;
      boundary.updateBlockEntityPower(signal);
      signal.notifyUpdate();
    }
    var type = boundary.getTypeFor(signal.owner());
    if (state.getValue(SignalBlock.TYPE) != type)
      level.setBlock(pos, state.setValue(SignalBlock.TYPE, type), Block.UPDATE_ALL);
    signal.enterState(boundary.getStateFor(signal.owner()));
    signal.setOverlay(boundary.getOverlayFor(signal.owner()));
  
        }
    }

  public boolean configure(Player player, SignalBlock.SignalType type, long expectedRevision) {
    if (level == null
        || level.isClientSide()
        || player.level() != level
        || !SignalMenu.canEdit(player, worldPosition)
        || expectedRevision != revision
        || getSignal() == null
        || type == null) return false;
    getSignal().setSignalType(owner(), type);
    revision = Math.incrementExact(revision);
    notifyUpdate();
    return true;
  }

  public void cycleType(Player player) {
    if (level == null
        || level.isClientSide()
        || player.level() != level
        || !SignalMenu.canEdit(player, worldPosition)) return;
    var boundary = getSignal();
    if (boundary == null) {
      player.sendOverlayMessage(Component.translatable("create.track_signal.cannot_change_mode"));
      return;
    }
    boundary.cycleSignalType(owner());
    revision = Math.incrementExact(revision);
    notifyUpdate();
    player.sendOverlayMessage(
        Component.translatable(
            "create.track_signal.mode_change." + boundary.getTypeFor(owner()).getSerializedName()));
  }

  public void setOverlay(OverlayState next) {
    if (overlay != next) {
      overlay = next;
      notifyUpdate();
    }
  }

  public void enterState(SignalState next) {
    if (switchToRedAfterTrainEntered > 0) switchToRedAfterTrainEntered--;
    if (signalState == next || next == SignalState.RED && switchToRedAfterTrainEntered > 0) return;
    signalState = next;
    switchToRedAfterTrainEntered = next == SignalState.GREEN || next == SignalState.YELLOW ? 15 : 0;
    notifyUpdate();
    if (level != null && !level.isClientSide())
      level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
  }

  @Override
  public void transform(BlockEntity entity, StructureTransform transform) {
    targeting.transform(entity, transform);
  }

  @Override
  public void invalidateEdgePoint(CompoundTag sourceData) {
    targeting.invalidateEdgePoint(sourceData);
  }

  @Override
  protected void write(ValueOutput out, boolean clientPacket) {
    super.write(out, clientPacket);
    
    out.putString("State", signalState.name());
    out.putString("Overlay", overlay.name());
    out.putBoolean("Power", lastReportedPower);
    out.putLong("ConfigurationRevision", revision);
  }

  @Override
  protected void read(ValueInput in, boolean clientPacket) {
    super.read(in, clientPacket);
    
    signalState = SignalState.valueOf(in.getStringOr("State", "INVALID"));
    overlay = OverlayState.valueOf(in.getStringOr("Overlay", "SKIP"));
    lastReportedPower = in.getBooleanOr("Power", false);
    revision = in.getLongOr("ConfigurationRevision", 0);
    if (revision < 0)
      throw new IllegalArgumentException("Negative actual signal configuration revision");
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return super.getUpdateTag(registries);
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (targeting != null) behaviours.add(targeting);
    }
}
