package com.simibubi.create.content.contraptions;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.network.syncher.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Source controlled one-axis rotation and translation, bound to a world controller. */
public class ControlledContraptionEntity extends AbstractContraptionEntity {
    private static final EntityDataAccessor<Float> ANGLE = SynchedEntityData.defineId(ControlledContraptionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> AXIS = SynchedEntityData.defineId(ControlledContraptionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> CONTROLLER_POS = SynchedEntityData.defineId(ControlledContraptionEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    protected BlockPos controllerPos;
    protected float angle, prevAngle;
    // A bearing and its entity can tick in either order; capture the old client pose once.
    private long clientAngleTick = Long.MIN_VALUE;
    private boolean clientAngleInitialized;
    private record InteractionPose(Vec3 origin,Direction.Axis axis,float angle){}
    private final ArrayDeque<InteractionPose> interactionPoses=new ArrayDeque<>();
    public ControlledContraptionEntity(EntityType<? extends ControlledContraptionEntity> type, Level level) { super(type, level); }
    public static ControlledContraptionEntity create(Level level, IControlContraption controller, Contraption contraption) {
        var entity = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, level); entity.setControllerPos(controller.getBlockPosition()); entity.setContraption(contraption); return entity;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(ANGLE, 0f); data.define(AXIS, Direction.Axis.Y.ordinal()); data.define(CONTROLLER_POS, Optional.empty()); }
    public void setAngle(float angle) {
        if (!Float.isFinite(angle)) throw new IllegalArgumentException("Invalid contraption angle");
        if (level().isClientSide()) {
            if (!clientAngleInitialized) {
                prevAngle = angle % 360;
                clientAngleInitialized = true;
            } else if (clientAngleTick != level().getGameTime()) prevAngle = this.angle;
            clientAngleTick = level().getGameTime();
        } else prevAngle = this.angle;
        this.angle = angle % 360;
        if (!level().isClientSide()) entityData.set(ANGLE, this.angle);
        refreshPassengerPositionsForPose();
    }
    /** Native server sample remains the fallback for stalled, recovering or unbound owners. */
    public float getSyncedAngle() { return entityData.get(ANGLE); }
    private boolean hasClientBearingRotation() {
        if (controllerPos == null) return false;
        var controller = LoadedLevelAccess.blockEntity(level(), controllerPos);
        return controller instanceof com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity bearing
                && bearing.controlsClientRotation(this)
            || controller instanceof com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity clock
                && clock.controlsClientRotation(this);
    }
    public float getAngle(float partial) { return prevAngle + Mth.wrapDegrees(angle - prevAngle) * partial; }
    public void setRotationAxis(Direction.Axis axis) { entityData.set(AXIS, axis.ordinal());refreshPassengerPositionsForPose(); }
    public Direction.Axis getRotationAxis() { return Direction.Axis.values()[Math.clamp(entityData.get(AXIS), 0, 2)]; }
    public BlockPos getControllerPos() { return controllerPos; }
    public void setControllerPos(BlockPos pos) {
        controllerPos = pos == null ? null : pos.immutable();
        if (!level().isClientSide()) entityData.set(CONTROLLER_POS, Optional.ofNullable(controllerPos));
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (level().isClientSide() && CONTROLLER_POS.equals(accessor))
            controllerPos = entityData.get(CONTROLLER_POS).orElse(null);
    }
    @Override public Vec3 applyRotation(Vec3 local, float partial) { return StructureTransform.rotate(local, getAngle(partial), getRotationAxis()); }
    @Override public org.joml.Quaternionf getOrientationQuaternion(float partial){return ContraptionOrientation.controlled(getAngle(partial),getRotationAxis());}
    @Override public Vec3 reverseRotation(Vec3 local, float partial) { return StructureTransform.rotate(local, -getAngle(partial), getRotationAxis()); }
    @Override public ContraptionRotationState getRotationState(){var state=new ContraptionRotationState();switch(getRotationAxis()){case X->state.xRotation=angle;case Y->state.yRotation=angle;case Z->state.zRotation=angle;}return state;}
    @Override protected StructureTransform disassemblyTransform() {
        return new StructureTransform(blockPosition(), getRotationAxis() == Direction.Axis.X ? angle : 0, getRotationAxis() == Direction.Axis.Y ? angle : 0, getRotationAxis() == Direction.Axis.Z ? angle : 0);
    }
    @Override protected void tickContraption() {
        if (level().isClientSide()) {
            // The actual bearing predicts this same entity, including its native passenger pose.
            // Do not replace that pose every tick with the last server metadata sample.
            if (!hasClientBearingRotation()) setAngle(getSyncedAngle());
            tickActors();
            return;
        }
        interactionPoses.addFirst(new InteractionPose(position().add(.5,.5,.5),getRotationAxis(),angle));if(interactionPoses.size()>16)interactionPoses.removeLast();
        tickActors();
        if (controllerPos == null || !level().isLoaded(controllerPos)) return;
        if (LoadedLevelAccess.blockEntity(level(), controllerPos) instanceof IControlContraption controller && controller.isValid()) {
            if (!controller.isAttachedTo(this)) controller.attach(this);
        } else disassemble();
    }
    @Override protected void onContraptionStalled(){if(controllerPos!=null&&LoadedLevelAccess.blockEntity(level(),controllerPos) instanceof IControlContraption controller)controller.onStall();}
    @Override protected List<InteractionPick> interactionPicks(Vec3 start,Vec3 end){
        var result=new ArrayList<>(super.interactionPicks(start,end));
        for(var pose:interactionPoses){var localStart=StructureTransform.rotate(start.subtract(pose.origin),-pose.angle,pose.axis).add(.5,.5,.5);var localEnd=StructureTransform.rotate(end.subtract(pose.origin),-pose.angle,pose.axis).add(.5,.5,.5);var hit=rayTraceLocal(localStart,localEnd);if(hit!=null)result.add(new InteractionPick(hit,StructureTransform.rotate(hit.getLocation().subtract(.5,.5,.5),pose.angle,pose.axis).add(pose.origin)));}return result;
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putFloat("Angle", angle); output.putInt("Axis", getRotationAxis().ordinal());
        if (controllerPos != null) output.store("ControllerRelative", BlockPos.CODEC, controllerPos.subtract(blockPosition()));
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); setAngle(input.getFloatOr("Angle", 0)); prevAngle = angle;
        setRotationAxis(Direction.Axis.values()[Math.clamp(input.getIntOr("Axis", 1), 0, 2)]);
        setControllerPos(input.read("ControllerRelative", BlockPos.CODEC).map(pos -> pos.offset(blockPosition())).orElse(null));
    }
}
