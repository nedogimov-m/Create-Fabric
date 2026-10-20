package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.contraptions.ContraptionSettings;
import com.simibubi.create.content.kinetics.KineticNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** A self-starting source generator, using the shared mechanical owner/retry state. */
public class WindmillBearingBlockEntity extends MechanicalBearingBlockEntity {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"windmill","windmill_maxed");
    public enum RotationDirection { CLOCKWISE, COUNTER_CLOCKWISE }
    private RotationDirection movementDirection = RotationDirection.CLOCKWISE;
    private float lastGeneratedSpeed;
    private boolean queuedReassembly;

    public WindmillBearingBlockEntity(BlockPos pos, BlockState state) { this(AllBlockEntityTypes.WINDMILL_BEARING,pos,state); }
    public WindmillBearingBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override protected boolean isWindmill() { return true; }
    @Override protected boolean canAssembleWhenStopped() { return true; }
    @Override protected boolean shouldQueueAssemblyOnSpeedChange() { return false; }
    @Override public boolean isWoodenTop() { return true; }
    public RotationDirection getMovementDirection() { return movementDirection; }
    public void setMovementDirection(RotationDirection direction) {
        if (movementDirection == direction) return;
        movementDirection = java.util.Objects.requireNonNull(direction);
        if (isRunning()) updateGeneratedRotation();
        setChanged(); sync();
    }
    public static int speedForSails(int sails, int sailsPerRPM, RotationDirection direction) {
        if (sails < 0 || sailsPerRPM < 1) throw new IllegalArgumentException("Invalid windmill sail configuration");
        return Math.clamp(sails / sailsPerRPM, 1, 16) * (direction == RotationDirection.CLOCKWISE ? 1 : -1);
    }
    @Override public float getGeneratedSpeed() {
        if (!isRunning()) return 0;
        var owner = getMovedContraption();
        if (!isRunning()) return 0;
        if (owner == null) return lastGeneratedSpeed;
        if(!owner.hasOwnedStructure()||owner.getContraption().getStabilizedSubContraptions().isRecovering())return 0;
        return speedForSails(((BearingContraption) owner.getContraption()).getSailBlocks(), ContraptionSettings.windmillSailsPerRPM, movementDirection);
    }
    /** Pinned GeneratingKineticBlockEntity propagates its signed generated RPM directly. */
    @Override public double getDirectionalGeneratedSpeed() { return getGeneratedSpeed(); }
    @Override public void updateGeneratedRotation() {
        super.updateGeneratedRotation();
        lastGeneratedSpeed = getGeneratedSpeed(); queuedReassembly = false;
    }
    @Override protected void onAssemblyComplete() { updateGeneratedRotation();var rotor=getMovedContraption();if(rotor!=null&&rotor.hasOwnedStructure()&&rotor.getContraption() instanceof BearingContraption source&&!source.getStabilizedSubContraptions().isRecovering()){sourceAdvancements.award("windmill");if(source.getSailBlocks()>=128)sourceAdvancements.award("windmill_maxed");} super.onAssemblyComplete(); }
    @Override protected void onDisassemblyComplete() { updateGeneratedRotation(); }
    public boolean disassembleForMovement() {
        if (!isRunning()) return super.disassembleForMovement();
        if (!disassemble()) return false;
        queuedReassembly = true; setChanged(); return true;
    }
    public boolean hasQueuedReassembly() { return queuedReassembly; }
    @Override public void tick(){if(level==null){super.tick();return;}tick(level,worldPosition,getBlockState(),this);}
    public static void tick(Level level, BlockPos pos, BlockState state, WindmillBearingBlockEntity bearing) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, bearing)) {
            if (!createTick.shouldTick()) return;

        MechanicalBearingBlockEntity.tick(level, pos, state, bearing);
        if (!level.isClientSide() && bearing.queuedReassembly) {
            bearing.queuedReassembly = false;
            if (!bearing.isRunning()) bearing.requestAssembly();
            bearing.setChanged();
        }
    
        }
    }
    @Override protected void write(ValueOutput out, boolean clientPacket) {
        super.write(out, clientPacket); out.putInt("WindmillDirection", movementDirection.ordinal());
        out.putFloat("LastGenerated", lastGeneratedSpeed); out.putBoolean("QueueAssembly", queuedReassembly);
    }
    @Override protected void read(ValueInput in, boolean clientPacket) {
        
        // ScrollValue is the pinned scroll-option legacy key; explicit new key wins.
        movementDirection = RotationDirection.values()[Math.clamp(in.getIntOr("WindmillDirection", in.getIntOr("ScrollValue", 0)), 0, 1)];
        float saved = in.getFloatOr("LastGenerated", 0); lastGeneratedSpeed = Float.isFinite(saved) ? saved : 0;
        queuedReassembly = in.getBooleanOr("QueueAssembly", false);
    
        super.read(in, clientPacket);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("windmill");
        ids.add("windmill_maxed");
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
