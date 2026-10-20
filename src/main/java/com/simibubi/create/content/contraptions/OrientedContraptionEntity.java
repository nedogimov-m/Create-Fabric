package com.simibubi.create.content.contraptions;

import com.simibubi.create.content.contraptions.bearing.StabilizedContraption;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/** A genuine oriented owner, with independent source yaw/pitch and actual native passenger identity. */
public class OrientedContraptionEntity extends AbstractContraptionEntity {
    private static final EntityDataAccessor<Direction> INITIAL = SynchedEntityData.defineId(OrientedContraptionEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(OrientedContraptionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(OrientedContraptionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Quaternionfc> ORIENTATION = SynchedEntityData.defineId(OrientedContraptionEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<String> PARENT_ID = SynchedEntityData.defineId(OrientedContraptionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<BlockPos> PARENT_BEARING = SynchedEntityData.defineId(OrientedContraptionEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Direction> PARENT_FACING = SynchedEntityData.defineId(OrientedContraptionEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<String> MOUNTED_CART = SynchedEntityData.defineId(OrientedContraptionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> COUPLING = SynchedEntityData.defineId(OrientedContraptionEntity.class, EntityDataSerializers.STRING);
    private float previousYaw, previousPitch;
    public int nonDamageTicks = 10;
    // Server ownership topology is authoritative before any synchronized-data callback.
    private UUID serverCouplingId,serverMountedCartId;
    private boolean nativeCartRetirementComplete,nativeCartRetirementPending;
    public OrientedContraptionEntity(EntityType<? extends OrientedContraptionEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(INITIAL, Direction.UP); data.define(YAW, 0f); data.define(PITCH, 0f); data.define(ORIENTATION, new Quaternionf());
        data.define(MOUNTED_CART, "");data.define(COUPLING, "");
        data.define(PARENT_ID, ""); data.define(PARENT_BEARING, BlockPos.ZERO); data.define(PARENT_FACING, Direction.UP);
    }
    public UUID getMountedCartId(){if(!level().isClientSide())return serverMountedCartId;var value=entityData.get(MOUNTED_CART);return value.isEmpty()?null:UUID.fromString(value);}
    public void setMountedCartId(UUID id){serverMountedCartId=id;entityData.set(MOUNTED_CART,id==null?"":id.toString());}
    public UUID getCouplingId(){if(!level().isClientSide())return serverCouplingId;var value=entityData.get(COUPLING);return value.isEmpty()?null:UUID.fromString(value);}
    public void setCouplingId(UUID id){serverCouplingId=id;entityData.set(COUPLING,id==null?"":id.toString());}
    /** Callback-free counterpart of the native coupling graph's fully preflighted reversal. */
    public void replaceCouplingLeadingCartPure(UUID expected,UUID actualLeading){
        if(!(level() instanceof ServerLevel server)||server.getEntity(getUUID())!=this||!hasOwnedStructure()||!Objects.equals(serverCouplingId,expected))throw new IllegalStateException("Actual structural coupling direction changed");
        serverCouplingId=Objects.requireNonNull(actualLeading);
    }
    /** Synchronization follows the complete native topology and durable root metadata commit. */
    public void syncCouplingLeadingCart(){var id=getCouplingId();entityData.set(COUPLING,id==null?"":id.toString());}
    public net.createmod.catnip.data.Couple<com.simibubi.create.content.contraptions.minecart.capability.MinecartController> getCoupledCartsIfPresent(){
        var id=getCouplingId();if(id==null)return null;
        var main=com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController.getIfPresent(level(),id);
        if(main==null||!main.hasContraptionCoupling(true)||main.ownsPackedReceipt())return null;
        var other=com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController.getIfPresent(level(),main.getCoupledCart(true));
        if(other==null||!other.hasContraptionCoupling(false)||!Objects.equals(other.getCoupledCart(false),main.cart().getUUID())||other.ownsPackedReceipt())return null;
        return net.createmod.catnip.data.Couple.create(main,other);
    }
    public UUID getParentId() { var value = entityData.get(PARENT_ID); return value.isEmpty() ? null : UUID.fromString(value); }
    public StabilizedSubContraptions.Location getParentLocation() { return getParentId() == null ? null : new StabilizedSubContraptions.Location(entityData.get(PARENT_BEARING), entityData.get(PARENT_FACING)); }
    public void setParent(UUID parent, StabilizedSubContraptions.Location location) { entityData.set(PARENT_BEARING, location.bearing()); entityData.set(PARENT_FACING, location.facing()); entityData.set(PARENT_ID, Objects.requireNonNull(parent).toString()); }
    public Direction getInitialOrientation() { return entityData.get(INITIAL); }
    public boolean isInitialOrientationPresent() { return getInitialOrientation().getAxis().isHorizontal(); }
    public float getInitialYaw() { return (isInitialOrientationPresent() ? getInitialOrientation() : Direction.SOUTH).toYRot(); }
    public void setInitialOrientation(Direction direction) { entityData.set(INITIAL, Objects.requireNonNull(direction)); setAngles(getInitialYaw(), 0); previousYaw = entityData.get(YAW); previousPitch = 0; }
    public void setAngles(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Nonfinite oriented pose");
        entityData.set(YAW, yaw); entityData.set(PITCH, pitch); entityData.set(ORIENTATION, ContraptionOrientation.source(yaw, pitch, getInitialYaw()));
        setYRot(yaw); setXRot(pitch); setBoundingBox(makeBoundingBox(position()));
    }
    /** Exact synchronized current pose; visual interpolation may differ by a whole turn. */
    boolean hasCurrentAngles(float yaw,float pitch){return Float.compare(entityData.get(YAW),yaw)==0&&Float.compare(entityData.get(PITCH),pitch)==0;}
    public float getViewYRot(float partial) { return -ContraptionOrientation.lerp(previousYaw, entityData.get(YAW), partial); }
    public float getViewXRot(float partial) { return ContraptionOrientation.lerp(previousPitch, entityData.get(PITCH), partial); }
    @Override public Quaternionf getOrientationQuaternion(float partial) {
        if (partial == 1) return ContraptionOrientation.validated(entityData.get(ORIENTATION));
        return ContraptionOrientation.source(-getViewYRot(partial), getViewXRot(partial), getInitialYaw());
    }
    @Override public Vec3 applyRotation(Vec3 local, float partial) { return ContraptionOrientation.apply(getOrientationQuaternion(partial), local); }
    @Override public Vec3 reverseRotation(Vec3 local, float partial) { return ContraptionOrientation.inverse(getOrientationQuaternion(partial), local); }
    @Override public Vec3 getAnchorVec() { return super.getAnchorVec().subtract(.5, 0, .5); }
    @Override public Vec3 getPrevAnchorVec() { return super.getPrevAnchorVec().subtract(.5, 0, .5); }
    @Override public Vec3 getVehicleAttachmentPoint(Entity vehicle) { return vehicle instanceof AbstractContraptionEntity ? Vec3.ZERO : new Vec3(0, .19, 0); }
    @Override public ContraptionRotationState getRotationState() {
        var state = new ContraptionRotationState(); state.orientation = getOrientationQuaternion(1); return state;
    }
    public StructureTransform restorationAt(BlockPos anchor) { return new StructureTransform(anchor, 0, -entityData.get(YAW) + getInitialYaw(), 0); }
    @Override protected StructureTransform disassemblyTransform() { return restorationAt(BlockPos.containing(getAnchorVec().add(.5, .5, .5))); }
    @Override public void setContraption(Contraption structure) { super.setContraption(structure); setBoundingBox(makeBoundingBox(position())); }
    @Override public void setPos(double x, double y, double z) { super.setPos(x, y, z); if (contraption != null) setBoundingBox(makeBoundingBox(position())); }
    @Override protected AABB makeBoundingBox(Vec3 nativePosition) {
        if (contraption == null || contraption.bounds == null) return super.makeBoundingBox(nativePosition);
        var bounds = contraption.bounds; AABB result = null; var anchor = nativePosition.subtract(.5, 0, .5);
        for (double x : new double[]{bounds.minX, bounds.maxX}) for (double y : new double[]{bounds.minY, bounds.maxY}) for (double z : new double[]{bounds.minZ, bounds.maxZ}) {
            var point = applyRotation(new Vec3(x - .5, y - .5, z - .5), 1).add(.5, .5, .5).add(anchor);
            var corner = new AABB(point, point); result = result == null ? corner : result.minmax(corner);
        }
        return result;
    }
    public boolean isNativeCartRetirementComplete(){return nativeCartRetirementComplete;}
    public boolean isNativeCartRetirementPending(){return nativeCartRetirementPending;}
    private boolean hasActuallyRetiredMountedStructure(){return contraption instanceof com.simibubi.create.content.contraptions.mounted.MountedContraption&&contraption.disassembled&&!contraption.getStorage().ownsContents();}
    public void markNativeCartRetirementCompletePure(){
        if(!nativeCartRetirementPending&&!hasActuallyRetiredMountedStructure())throw new IllegalStateException("Actual world-retired mounted source or its real durable completion journal required");
        nativeCartRetirementComplete=true;
    }
    @Override public void tick(){
        if(!level().isClientSide()&&hasOwnedStructure()&&contraption instanceof com.simibubi.create.content.contraptions.mounted.MountedContraption)com.simibubi.create.content.contraptions.mounted.NativeCartBinding.recover(this);
        if(!level().isClientSide()&&(nativeCartRetirementPending||hasActuallyRetiredMountedStructure())){
            nativeCartRetirementPending=true;if(com.simibubi.create.content.contraptions.mounted.NativeCartRetirement.tryFinish(this))discard();return;
        }
        super.tick();
    }
    @Override public void remove(RemovalReason reason){
        if(!level().isClientSide()&&!isRemoved()&&reason.shouldDestroy()&&(nativeCartRetirementPending||hasActuallyRetiredMountedStructure())){
            nativeCartRetirementPending=true;if(!com.simibubi.create.content.contraptions.mounted.NativeCartRetirement.tryFinish(this))return;
        }
        super.remove(reason);
    }
    @Override protected void tickContraption() {
        if(nonDamageTicks>0)nonDamageTicks--;
        previousYaw = entityData.get(YAW); previousPitch = entityData.get(PITCH);
        if(contraption.getStabilizedSubContraptions().isRecovering()){if(!level().isClientSide())ContraptionAssemblyGraph.restore(this);return;}
        if (contraption.disassembled) return;
        boolean stabilizedWasStalled=isStalled();
        AbstractContraptionEntity parent = getVehicle() instanceof AbstractContraptionEntity riding ? riding : null;
        var parentId = getParentId();
        if (!level().isClientSide() && parentId != null && parent == null && level() instanceof ServerLevel server && server.getEntity(parentId) instanceof AbstractContraptionEntity candidate && candidate.getContraption() != null && candidate.getContraption().getStabilizedSubContraptions().accepts(this)) {
            if (startRiding(candidate, true, true)) parent = candidate;
        }
        if (parent != null && contraption instanceof StabilizedContraption stabilized) {
            // Controlled parents move this anchor, not this independent world orientation.
            if (parent instanceof OrientedContraptionEntity oriented && stabilized.getFacing().getAxis().isHorizontal()) {
                float yaw = Mth.wrapDegrees(getInitialYaw() - oriented.getInitialYaw()) - oriented.getViewYRot(1); setAngles(yaw, entityData.get(PITCH));
            }
        }
        if(contraption instanceof com.simibubi.create.content.contraptions.mounted.MountedContraption){
            if(!ContraptionAssemblyGraph.isRecovering(this)&&com.simibubi.create.content.contraptions.mounted.MountedCartMotion.updateOrientation(this))tickActors();
            com.simibubi.create.content.contraptions.mounted.MountedCartMotion.afterActors(this);return;
        }
        // A saved unresolved parent identity keeps the real child/cargo durable and motionless.
        if ((parentId == null || parent != null)&&!ContraptionAssemblyGraph.isRecovering(this)) {
            tickActors();continueStabilizedParentMotion(parent,stabilizedWasStalled);
        }
    }
    /** The original stabilized fallback changes only its real immediate parent's delta movement. */
    private void continueStabilizedParentMotion(AbstractContraptionEntity actualParent,boolean wasStalled){
        if(level().isClientSide()||!(contraption instanceof StabilizedContraption)||actualParent==null||!hasOwnedStructure()||!actualParent.hasOwnedStructure()
                ||!(level() instanceof ServerLevel server)||server.getEntity(getUUID())!=this||server.getEntity(actualParent.getUUID())!=actualParent
                ||getVehicle()!=actualParent||!actualParent.getUUID().equals(getParentId())||!actualParent.getContraption().getStabilizedSubContraptions().accepts(this)
                ||!com.simibubi.create.foundation.utility.LoadedEntityAccess.isReady(server,actualParent.blockPosition()))return;
        if(stabilizedParentMotion!=null&&!stabilizedParentMotion.parent().equals(actualParent.getUUID()))throw new IllegalStateException("Current stabilized motion belongs to another actual parent");
        if(isStalled()){
            if(!wasStalled&&stabilizedParentMotion==null)stabilizedParentMotion=new com.simibubi.create.foundation.utility.StabilizedParentMotion(actualParent.getUUID(),actualParent.getDeltaMovement());
            if(stabilizedParentMotion==null)throw new IllegalStateException("Frozen current stabilized child lost its actual parent-motion continuation");
            actualParent.setDeltaMovement(Vec3.ZERO);
        }else if(stabilizedParentMotion!=null){
            var consumed=stabilizedParentMotion;stabilizedParentMotion=null;actualParent.setDeltaMovement(consumed.motion());
        }
    }
    @Override public boolean disassemble() { return ContraptionAssemblyGraph.restore(this); }
    @Override public void stopRiding() {
        // Native setRemoved invokes stopRiding on passengers during chunk unload as well.
        // The actual passenger graph was saved already; unloading must never restore its blocks.
        var vehicle = getVehicle();
        if (vehicle != null && vehicle.getRemovalReason() != null && !vehicle.getRemovalReason().shouldDestroy()) { super.stopRiding(); return; }
        if (!level().isClientSide() && isAlive() && contraption != null && !contraption.disassembled && !ContraptionAssemblyGraph.restore(this)) return;
        super.stopRiding();
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        if(hasActuallyRetiredMountedStructure())nativeCartRetirementPending=true;
        // A pending post-restoration record owns topology cleanup only. The actual world already
        // owns its former blocks/actors, so saving/loading a second actor payload would be wrong.
        if(!nativeCartRetirementPending)super.addAdditionalSaveData(out);
        if(originalOrientedSource!=null)out.store(com.simibubi.create.foundation.utility.NeoForgeOrientedSource.KEY,com.simibubi.create.foundation.utility.NeoForgeOrientedSource.CODEC,originalOrientedSource);
        if(originalStabilizedSource!=null)out.store(com.simibubi.create.foundation.utility.NeoForgeStabilizedSource.KEY,com.simibubi.create.foundation.utility.NeoForgeStabilizedSource.CODEC,originalStabilizedSource);
        if(stabilizedParentMotion!=null)out.store(com.simibubi.create.foundation.utility.StabilizedParentMotion.KEY,com.simibubi.create.foundation.utility.StabilizedParentMotion.CODEC,stabilizedParentMotion);
        out.putBoolean("NativeCartRetirementPending",nativeCartRetirementPending);out.putBoolean("NativeCartRetirementComplete",nativeCartRetirementComplete); out.putString("InitialOrientation", getInitialOrientation().name()); out.putFloat("Yaw", entityData.get(YAW)); out.putFloat("Pitch", entityData.get(PITCH));
        if(getMountedCartId()!=null)out.store("MountedCart",UUIDUtil.CODEC,getMountedCartId());if(getCouplingId()!=null)out.store("OnCoupling",UUIDUtil.CODEC,getCouplingId());
        if (getParentId() != null) out.store("ParentContraption", UUIDUtil.CODEC, getParentId());
        if (getParentLocation() != null) out.store("ParentBearing", net.minecraft.nbt.CompoundTag.CODEC, getParentLocation().write());
    }
    private com.simibubi.create.foundation.utility.NeoForgeOrientedSource originalOrientedSource;
    private com.simibubi.create.foundation.utility.NeoForgeStabilizedSource originalStabilizedSource;
    private com.simibubi.create.foundation.utility.StabilizedParentMotion stabilizedParentMotion;
    @Override protected void readAdditionalSaveData(ValueInput in) {
        originalOrientedSource=in.read(com.simibubi.create.foundation.utility.NeoForgeOrientedSource.KEY,com.simibubi.create.foundation.utility.NeoForgeOrientedSource.CODEC).orElse(null);
        originalStabilizedSource=in.read(com.simibubi.create.foundation.utility.NeoForgeStabilizedSource.KEY,com.simibubi.create.foundation.utility.NeoForgeStabilizedSource.CODEC).orElse(null);
        stabilizedParentMotion=in.read(com.simibubi.create.foundation.utility.StabilizedParentMotion.KEY,com.simibubi.create.foundation.utility.StabilizedParentMotion.CODEC).orElse(null);
        nativeCartRetirementPending=in.getBooleanOr("NativeCartRetirementPending",false);nativeCartRetirementComplete=in.getBooleanOr("NativeCartRetirementComplete",false);
        var initial = Direction.valueOf(in.getStringOr("InitialOrientation", "UP").toUpperCase(Locale.ROOT)); entityData.set(INITIAL, initial);
        setAngles(in.getFloatOr("Yaw", 0), in.getFloatOr("Pitch", 0)); previousYaw = entityData.get(YAW); previousPitch = entityData.get(PITCH);
        setMountedCartId(in.read("MountedCart",UUIDUtil.CODEC).orElse(null));setCouplingId(in.read("OnCoupling",UUIDUtil.CODEC).orElse(null));
        var parentId = in.read("ParentContraption", UUIDUtil.CODEC).orElse(null); var parentLocation = in.read("ParentBearing", net.minecraft.nbt.CompoundTag.CODEC).map(StabilizedSubContraptions.Location::read).orElse(null);
        if ((parentId == null) != (parentLocation == null)) throw new IllegalArgumentException("Incomplete stabilized parent identity");
        if(stabilizedParentMotion!=null&&(parentId==null||!stabilizedParentMotion.parent().equals(parentId)))throw new IllegalArgumentException("Current stabilized motion lost its genuine actual parent identity");
        if (parentId != null) setParent(parentId, parentLocation); else entityData.set(PARENT_ID, "");
        if(!nativeCartRetirementPending)super.readAdditionalSaveData(in);
    }
}
