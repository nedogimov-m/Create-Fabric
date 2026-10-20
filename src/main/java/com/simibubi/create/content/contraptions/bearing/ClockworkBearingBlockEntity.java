package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Two native owned hands; source input controls approach speed, while time supplies targets. */
public class ClockworkBearingBlockEntity extends KineticBlockEntity implements IBearingBlockEntity {
    public enum ClockHands { HOUR_FIRST, MINUTE_FIRST, HOUR_FIRST_24 }
    private ControlledContraptionEntity hourHand, minuteHand;
    private UUID hourId, minuteId;
    private float hourAngle, minuteAngle;
    private float clientHourAngleDiff, clientMinuteAngleDiff;
    private boolean running, assembleNextTick, recovering;
    private boolean retryAssemblyAfterRecovery;
    private boolean restoringHands,queuedReassembly;
    // These cells already belong to the world; no inventories or duplicate hand payloads are retained.
    private final LinkedHashSet<BlockPos> pendingRestorationUpdates = new LinkedHashSet<>();
    private int retryTicks;
    private ClockHands operationMode = ClockHands.HOUR_FIRST;
    private UUID advancementOwner;
    private boolean advancementInitialized;
    public UUID advancementOwner() { return advancementOwner; }
    public void placedBy(net.minecraft.server.level.ServerPlayer player) {
        if(level == null || player.level() != level || player instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
        advancementOwner=player.getUUID();com.simibubi.create.foundation.advancement.NativeDeviceAdvancements.removeCompletedClockworkOwner(this);setChanged();
    }
    public void clearAdvancementOwner(UUID owner) { if(!com.simibubi.create.foundation.advancement.AdvancementBehaviour.hasPendingFor(this,owner)&&Objects.equals(advancementOwner,owner)){advancementOwner=null;setChanged();} }
    private AssemblyException lastException;
    private CompoundTag pendingAssemblyError;

    public ClockworkBearingBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.CLOCKWORK_BEARING, pos, state); }
    public boolean isRunning() { return running; }
    public boolean isRecovering() { return recovering; }
    public ClockHands getOperationMode() { return operationMode; }
    public void setOperationMode(ClockHands mode) { operationMode = Objects.requireNonNull(mode); setChanged(); sync(); }
    public AssemblyException getLastAssemblyException() { return lastException; }
    public void requestAssembly() { assembleNextTick = true; retryTicks = 0; setChanged(); }
    public ControlledContraptionEntity getHourHand() {
        if (completed(hourHand)) { hourHand = null; hourId = null; if (minuteId == null) running = false; setChanged(); }
        hourHand = resolve(hourHand, hourId, ClockworkContraption.HandType.HOUR); return hourHand;
    }
    public ControlledContraptionEntity getMinuteHand() {
        if (completed(minuteHand)) { minuteHand = null; minuteId = null; if (hourId == null) running = false; setChanged(); }
        minuteHand = resolve(minuteHand, minuteId, ClockworkContraption.HandType.MINUTE); return minuteHand;
    }
    private static boolean completed(ControlledContraptionEntity owner) { return owner != null && owner.isRemoved() && owner.getContraption() != null && owner.getContraption().disassembled; }
    private ControlledContraptionEntity resolve(ControlledContraptionEntity cached, UUID id, ClockworkContraption.HandType type) {
        if (cached != null && !cached.isRemoved() && matches(cached, type)) return cached;
        if (id != null && level instanceof ServerLevel server && server.getEntity(id) instanceof ControlledContraptionEntity owner && matches(owner, type)) return owner;
        if (id != null && level != null && level.isClientSide()) for (var owner : level.getEntitiesOfClass(ControlledContraptionEntity.class, new AABB(worldPosition).inflate(17)))
            if (id.equals(owner.getUUID()) && matches(owner, type)) { owner.setControllerPos(worldPosition); return owner; }
        return null;
    }
    private boolean matches(ControlledContraptionEntity owner, ClockworkContraption.HandType type) {
        return owner.getContraption() instanceof ClockworkContraption hand && hand.handType == type && hand.getFacing() == getBlockState().getValue(BearingBlock.FACING) && (level != null && level.isClientSide() || worldPosition.equals(owner.getControllerPos()));
    }
    private Direction facing() { return getBlockState().getValue(BearingBlock.FACING); }
    public float getAngularSpeed() {
        float speed=-Math.abs(getSpeed()*.3f);
        return level!=null&&level.isClientSide()?speed*com.simibubi.create.foundation.utility.ServerSpeedProvider.get():speed;
    }
    public boolean controlsClientRotation(ControlledContraptionEntity entity) {
        return entity!=null&&level!=null&&level.isClientSide()&&running&&!recovering
            &&(entity==getHourHand()||entity==getMinuteHand())&&!entity.isStalled()&&!ContraptionAssemblyGraph.isFrozen(entity);
    }
    private void tickClientRotation() {
        clientHourAngleDiff/=2;clientMinuteAngleDiff/=2;
        if(!running)return;
        var hour=getHourHand();var minute=getMinuteHand();
        if(recovering){
            if(hour!=null)hourAngle=hour.getSyncedAngle();
            if(minute!=null)minuteAngle=minute.getSyncedAngle();
            clientHourAngleDiff=clientMinuteAngleDiff=0;
        }else{
            if(hour!=null&&(hour.isStalled()||ContraptionAssemblyGraph.isFrozen(hour))){hourAngle=hour.getSyncedAngle();clientHourAngleDiff=0;}
            else hourAngle=(hourAngle+getHourArmSpeed())%360;
            if(minute!=null&&(minute.isStalled()||ContraptionAssemblyGraph.isFrozen(minute))){minuteAngle=minute.getSyncedAngle();clientMinuteAngleDiff=0;}
            else minuteAngle=(minuteAngle+getMinuteArmSpeed())%360;
        }
        applyRotations();
    }
    public float getHourArmSpeed() {
        if (level == null) return 0;
        long time = ClockworkTime.time(level);
        float target = operationMode == ClockHands.MINUTE_FIRST ? ClockworkTime.minuteTarget(time, facing()) : ClockworkTime.hourTarget(time, operationMode == ClockHands.HOUR_FIRST_24, facing());
        return ClockworkTime.approachSpeed(hourAngle, target, getAngularSpeed() / 2)+(level.isClientSide()?clientHourAngleDiff/3f:0);
    }
    public float getMinuteArmSpeed() {
        if (level == null) return 0;
        long time = ClockworkTime.time(level);
        float target = operationMode == ClockHands.MINUTE_FIRST ? ClockworkTime.hourTarget(time, false, facing()) : ClockworkTime.minuteTarget(time, facing());
        return ClockworkTime.approachSpeed(minuteAngle, target, getAngularSpeed())+(level.isClientSide()?clientMinuteAngleDiff/3f:0);
    }
    @Override public boolean isWoodenTop() { return false; }
    @Override public float getInterpolatedAngle(float partial) { var hand = getHourHand(); return hand == null ? hourAngle : hand.getAngle(partial); }
    public float getMinuteAngle(float partial) { var hand = getMinuteHand(); return hand == null ? minuteAngle : hand.getAngle(partial); }
    @Override public void setAngle(float angle) { if (!Float.isFinite(angle)) throw new IllegalArgumentException("Nonfinite clockwork angle"); hourAngle = angle; }
    private void applyRotations() {
        if (hourHand != null) { hourHand.setAngle(hourAngle); hourHand.setRotationAxis(facing().getAxis()); }
        if (minuteHand != null) { minuteHand.setAngle(minuteAngle); minuteHand.setRotationAxis(facing().getAxis()); }
    }
    /** Both reservations are accepted by the native level before any world block changes owner. */
    protected ControlledContraptionEntity createReservation(ServerLevel level, ClockworkContraption hand) { return new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, level); }
    private ControlledContraptionEntity reserve(ServerLevel level, ClockworkContraption hand) {
        var owner = createReservation(level, hand);
        owner.setControllerPos(worldPosition); owner.setRotationAxis(facing().getAxis()); owner.setPos(Vec3.atLowerCornerOf(hand.anchor));
        return level.addFreshEntity(owner) && !owner.isRemoved() && level.getEntity(owner.getUUID()) == owner ? owner : null;
    }
    private void bind(ControlledContraptionEntity owner, ClockworkContraption hand) {
        if(owner.getContraption()!=hand)owner.setContraption(hand);
        if (hand.handType == ClockworkContraption.HandType.HOUR) { hourHand = owner; hourId = owner.getUUID(); }
        else { minuteHand = owner; minuteId = owner.getUUID(); }
        // Graph binding sends a full snapshot after every native owner/topology has been attached.
    }
    private void failure(AssemblyException exception) { lastException = exception; if (exception.canRetry()) retryTicks = 5; setChanged(); sync(); }
    public void assemble() {
        if (!(level instanceof ServerLevel server) || running || recovering || hourId != null || minuteId != null) return;
        ClockworkContraption hour=null,minute=null;ControlledContraptionEntity reservedHour=null,reservedMinute=null;
        try{
            var pair=ClockworkContraption.assembleClockworkAt(server,worldPosition,facing());if(pair==null)return;hour=pair.getLeft();minute=pair.getRight();
            reservedHour=reserve(server,hour);if(reservedHour==null)throw AssemblyException.structureChanged(worldPosition);
            if(minute!=null){reservedMinute=reserve(server,minute);if(reservedMinute==null)throw AssemblyException.structureChanged(worldPosition);}
            var h=hour;var m=minute;var rh=reservedHour;var rm=reservedMinute;var roots=new ArrayList<ContraptionAssemblyGraph.Root>();roots.add(new ContraptionAssemblyGraph.Root(rh,h));if(m!=null)roots.add(new ContraptionAssemblyGraph.Root(rm,m));
            var result=ContraptionAssemblyGraph.assemble(server,roots,complete->{
                if(h.phase()==Contraption.Phase.REMOVED)bind(rh,h);if(m!=null&&m.phase()==Contraption.Phase.REMOVED)bind(rm,m);
                running=true;recovering=!complete;retryAssemblyAfterRecovery=false;retryTicks=0;hourAngle=minuteAngle=0;if(complete)lastException=null;setChanged();sync();
            });
            if(!result.complete()){recovering=hourId!=null||minuteId!=null;retryAssemblyAfterRecovery=recovering&&result.failure()!=null&&result.failure().canRetry();failure(result.failure()==null?AssemblyException.entitiesNotReady(worldPosition):result.failure());return;}
            com.simibubi.create.foundation.advancement.NativeDeviceAdvancements.clockworkCompleted(this);
            server.playSound(null,worldPosition,AllSoundEvents.CONTRAPTION_ASSEMBLE,SoundSource.BLOCKS,1,1);setChanged();sync();
        }catch(AssemblyException exception){failure(exception);}
        finally{if(reservedHour!=null&&reservedHour.getContraption()==null)reservedHour.discard();if(reservedMinute!=null&&reservedMinute.getContraption()==null)reservedMinute.discard();}
    }
    public boolean disassemble() {
        if (restoringHands) return false;
        retryAssemblyAfterRecovery=false;
        restoringHands = true;
        try { return restoreOwnedHands(); }
        finally { restoringHands = false; }
    }
    private boolean restoreOwnedHands() {
        if(!(level instanceof ServerLevel server))return false;
        getHourHand();getMinuteHand();
        if(hourId!=null&&hourHand==null||minuteId!=null&&minuteHand==null){recovering=true;setChanged();return false;}
        var roots=new ArrayList<ContraptionAssemblyGraph.RestorationRoot>();if(hourHand!=null)roots.add(new ContraptionAssemblyGraph.RestorationRoot(hourHand,new StructureTransform(hourHand.blockPosition(),0,0,0)));if(minuteHand!=null)roots.add(new ContraptionAssemblyGraph.RestorationRoot(minuteHand,new StructureTransform(minuteHand.blockPosition(),0,0,0)));
        var result=ContraptionAssemblyGraph.restoreForest(roots,pendingRestorationUpdates,owner->{if(owner==hourHand){hourHand=null;hourId=null;}else if(owner==minuteHand){minuteHand=null;minuteId=null;}setChanged();},()->{hourAngle=minuteAngle=0;applyRotations();});
        if(!result.complete()){recovering=true;if(result.failure()!=null)failure(result.failure());else{setChanged();sync();}return false;}
        if(!pendingRestorationUpdates.isEmpty()){DeferredContraptionRestoration.finishOrQueue(server,pendingRestorationUpdates);pendingRestorationUpdates.clear();}
        running=recovering=assembleNextTick=retryAssemblyAfterRecovery=false;retryTicks=0;hourAngle=minuteAngle=0;
        server.playSound(null,worldPosition,AllSoundEvents.CONTRAPTION_DISASSEMBLE,SoundSource.BLOCKS,1,1);setChanged();sync();return true;
    }
    public boolean disassembleForMovement(){if(!running&&hourId==null&&minuteId==null)return true;if(!disassemble())return false;queuedReassembly=true;setChanged();return true;}
    public static void tick(Level level, BlockPos pos, BlockState state, ClockworkBearingBlockEntity bearing) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, bearing)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level, pos, state, bearing); if (level.isClientSide()) { bearing.tickClientRotation(); return; }
        if(!bearing.advancementInitialized){bearing.advancementInitialized=true;com.simibubi.create.foundation.advancement.NativeDeviceAdvancements.removeCompletedClockworkOwner(bearing);}
        boolean relocated=bearing.queuedReassembly;bearing.queuedReassembly=false;
        try{
        var boundHour=bearing.getHourHand();var boundMinute=bearing.getMinuteHand();
        if (bearing.recovering||boundHour!=null&&boundHour.getContraption().getStabilizedSubContraptions().isRecovering()||boundMinute!=null&&boundMinute.getContraption().getStabilizedSubContraptions().isRecovering()) { boolean restart=bearing.retryAssemblyAfterRecovery; if(bearing.disassemble()){if(restart)bearing.requestAssembly();}else bearing.retryAssemblyAfterRecovery=restart;return; }
        if (bearing.retryTicks > 0 && --bearing.retryTicks == 0) bearing.assembleNextTick = true;
        if (bearing.assembleNextTick) {
            bearing.assembleNextTick = false;
            if (bearing.running) { if (bearing.getSpeed() == 0) bearing.disassemble(); }
            else bearing.assemble();
            return;
        }
        if (!bearing.running) return;
        var hour = bearing.getHourHand(); var minute = bearing.getMinuteHand();
        if (hour != null && !hour.isStalled()&&!ContraptionAssemblyGraph.isFrozen(hour)) bearing.hourAngle = (bearing.hourAngle + bearing.getHourArmSpeed()) % 360;
        if (minute != null && !minute.isStalled()&&!ContraptionAssemblyGraph.isFrozen(minute)) bearing.minuteAngle = (bearing.minuteAngle + bearing.getMinuteArmSpeed()) % 360;
        bearing.applyRotations(); bearing.setChanged(); if (level.getGameTime() % 3 == 0) bearing.sync();
        }finally{if(relocated&&!bearing.running)bearing.requestAssembly();}
    
        }
    }
    @Override public void onSpeedChanged(float previous) {
        super.onSpeedChanged(previous);
        if (previous != getSpeed()) assembleNextTick = true;
    }
    @Override public void attach(ControlledContraptionEntity owner) {
        if (!(owner.getContraption() instanceof ClockworkContraption hand) || !matches(owner, hand.handType)) return;
        var expected = hand.handType == ClockworkContraption.HandType.HOUR ? hourId : minuteId;
        if (expected != null && !expected.equals(owner.getUUID())) return;
        owner.setPos(Vec3.atLowerCornerOf(worldPosition.relative(facing(), hand.offset + 1)));
        if (hand.handType == ClockworkContraption.HandType.HOUR) { hourHand = owner; hourId = owner.getUUID(); hourAngle = owner.getAngle(1); }
        else { minuteHand = owner; minuteId = owner.getUUID(); minuteAngle = owner.getAngle(1); }
        running = true; setChanged(); sync();
    }
    @Override public boolean isAttachedTo(AbstractContraptionEntity owner) { return owner == getHourHand() || owner == getMinuteHand(); }
    @Override public void onStall() { sync(); }
    @Override public boolean isValid() { return !isRemoved(); }
    @Override public BlockPos getBlockPosition() { return worldPosition; }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) { if (level != null && !level.isClientSide()) disassemble(); super.preRemoveSideEffects(pos, state); }
    @Override public void setLevel(Level level) { super.setLevel(level); if (pendingAssemblyError != null) { lastException = AssemblyException.read(pendingAssemblyError, level.registryAccess()); pendingAssemblyError = null; } }
    @Override protected void write(ValueOutput out, boolean clientPacket) {
        super.write(out, clientPacket); out.putBoolean("Running", running);out.putBoolean("QueuedClockworkReassembly",queuedReassembly); out.putBoolean("Recovering", recovering); out.putBoolean("RetryAssemblyAfterRecovery", retryAssemblyAfterRecovery); out.putFloat("HourAngle", hourAngle); out.putFloat("MinuteAngle", minuteAngle); out.putInt("ClockHands", operationMode.ordinal()); out.putInt("AssemblyRetryTicks", retryTicks);
        if (hourId != null) out.store("HourOwner", UUIDUtil.CODEC, hourId); if (minuteId != null) out.store("MinuteOwner", UUIDUtil.CODEC, minuteId);
        if (!pendingRestorationUpdates.isEmpty()) out.store("PendingRestorationUpdates", BlockPos.CODEC.listOf(), List.copyOf(pendingRestorationUpdates));
        var error = pendingAssemblyError == null ? new CompoundTag() : pendingAssemblyError.copy(); if (level != null) AssemblyException.write(error, level.registryAccess(), lastException); out.store("AssemblyError", CompoundTag.CODEC, error);
    }
    @Override protected void read(ValueInput in, boolean clientPacket) {
        float hourBefore=hourAngle,minuteBefore=minuteAngle;boolean runningBefore=running;
        UUID previousHourId=hourId,previousMinuteId=minuteId;
         advancementInitialized=false;running = in.getBooleanOr("Running", false);queuedReassembly=in.getBooleanOr("QueuedClockworkReassembly",false); recovering = in.getBooleanOr("Recovering", false); retryAssemblyAfterRecovery = in.getBooleanOr("RetryAssemblyAfterRecovery", false); hourAngle = finite(in.getFloatOr("HourAngle", 0)); minuteAngle = finite(in.getFloatOr("MinuteAngle", 0));
        operationMode = ClockHands.values()[Math.clamp(in.getIntOr("ClockHands", in.getIntOr("ScrollValue", 0)), 0, 2)]; retryTicks = Math.clamp(in.getIntOr("AssemblyRetryTicks", 0), 0, 5);
        hourId = in.read("HourOwner", UUIDUtil.CODEC).orElse(null); minuteId = in.read("MinuteOwner", UUIDUtil.CODEC).orElse(null);
        if(!clientPacket||!Objects.equals(previousHourId,hourId))hourHand=null;
        if(!clientPacket||!Objects.equals(previousMinuteId,minuteId))minuteHand=null;
        pendingRestorationUpdates.clear(); for (var pos : in.read("PendingRestorationUpdates", BlockPos.CODEC.listOf()).orElse(List.of())) pendingRestorationUpdates.add(pos.immutable());
        recovering |= !pendingRestorationUpdates.isEmpty();
        pendingAssemblyError = in.read("AssemblyError", CompoundTag.CODEC).orElse(new CompoundTag()).copy(); if (level != null) { lastException = AssemblyException.read(pendingAssemblyError, level.registryAccess()); pendingAssemblyError = null; }
    
        super.read(in, clientPacket);
        if(clientPacket){
            if(running&&runningBefore&&!recovering){
                if(Objects.equals(previousHourId,hourId)&&(hourHand==null||!hourHand.isStalled()&&!ContraptionAssemblyGraph.isFrozen(hourHand))){clientHourAngleDiff=net.minecraft.util.Mth.wrapDegrees(hourAngle-hourBefore);hourAngle=hourBefore;}else clientHourAngleDiff=0;
                if(Objects.equals(previousMinuteId,minuteId)&&(minuteHand==null||!minuteHand.isStalled()&&!ContraptionAssemblyGraph.isFrozen(minuteHand))){clientMinuteAngleDiff=net.minecraft.util.Mth.wrapDegrees(minuteAngle-minuteBefore);minuteAngle=minuteBefore;}else clientMinuteAngleDiff=0;
            }else clientHourAngleDiff=clientMinuteAngleDiff=0;
            if(!running)hourHand=minuteHand=null;
        }
    }
    private static float finite(float value) { return Float.isFinite(value) ? value % 360 : 0; }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("clockwork_bearing");
    }
    private final com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage canonicalAdvancementOwner=new com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage() {
        @Override public java.util.UUID getPlayerId(){return advancementOwner;}
        @Override public void setPlayerId(java.util.UUID id){advancementOwner=id;}
    };
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return canonicalAdvancementOwner;}
}
