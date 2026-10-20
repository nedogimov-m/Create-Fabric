package com.simibubi.create.content.kinetics.base;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.KineticGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.EnumMap;
import java.util.Map;

public class KineticBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation,com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation, com.simibubi.create.api.contraption.transformable.TransformableBlockEntity {
    /** Installed by the actual client sound handler; dedicated servers never resolve client classes. */
    public static java.util.function.BiConsumer<KineticBlockEntity, Boolean> CLIENT_AUDIO = (entity, noisy) -> {};
    public void tickAudio() { CLIENT_AUDIO.accept(this, isNoisy()); }
    protected boolean isNoisy() {
        // These native aliases currently share this class instead of source SimpleKinetic/Gearbox classes.
        var block = getBlockState().getBlock();
        return block != com.simibubi.create.AllBlocks.SHAFT && block != com.simibubi.create.AllBlocks.COGWHEEL
            && block != com.simibubi.create.AllBlocks.LARGE_COGWHEEL
            && !(block instanceof com.simibubi.create.content.kinetics.gearbox.GearboxBlock);
    }
    private final com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour bracket =
        new com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour(this,com.simibubi.create.content.decoration.bracket.BracketBlock::isSimpleShaft);
    public com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour bracketBehaviour(){return bracket;}
    @Override public void transform(BlockEntity blockEntity,com.simibubi.create.content.contraptions.StructureTransform transform){bracket.transformBracket(transform);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){bracket.dropBracket();super.preRemoveSideEffects(pos,state);}
    @Override public void setBlockState(BlockState next){
        var previous=getBlockState();
        if(previous!=next&&bracket!=null&&bracket.isBracketPresent()){
            boolean onlyWater=previous.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                &&next.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                &&previous.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,next.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))==next;
            if(!onlyWater)bracket.dropBracket();
        }
        super.setBlockState(next);
    }
    public Long network;
    public BlockPos source;
    public boolean networkDirty;
    public boolean updateSpeed = true;
    public int preventSpeedUpdate;
    private BlockPos generatingSource;
    private Direction kineticSourceFace;
    public com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext sequenceContext;
    private com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase sequencePhase;
    protected float speed;
    protected float lastCapacityProvided,lastStressApplied;
    protected float capacity;
    protected float stress;
    protected boolean overStressed;
    protected boolean wasMoved;
    private int networkSize;
    private int flickerTally;
    private int networkUpdateDepth;
    private float speedBeforeNetworkUpdate;
    private boolean networkSnapshotChanged;
    private boolean forcedSpeedCallback;
    private final EnumMap<Direction, Float> portSpeeds = new EnumMap<>(Direction.class);
    private boolean conflicted;
    private boolean conflictDestructionPending;
    private int crankInUse;
    private boolean crankBackwards;
    private double independentAngle;
    private int validationCountdown;

    public KineticBlockEntity(BlockPos pos, BlockState state) {
        this(AllBlockEntityTypes.simpleType(state), pos, state);
    }

    public KineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void initialize() {
        if (level instanceof ServerLevel && !isVirtual() && hasNetwork()) {
            var group = getOrCreateNetwork();
            if (!group.initialized) group.initFromTE(capacity, stress, networkSize);
            group.addSilently(this, lastCapacityProvided, lastStressApplied);
        }
        super.initialize();
    }

    @Override public void tick() {
        if (level == null) { super.tick(); return; }
        tick(level, worldPosition, getBlockState(), this);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, KineticBlockEntity entity) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;

        if (level.isClientSide()) entity.tickAudio();
        if (level instanceof ServerLevel serverLevel) {
            if (entity.isVirtual()) return;
            KineticNetworks.track(serverLevel, entity);
            if (entity.needsSpeedUpdate()) entity.attachKinetics();
            entity.preventSpeedUpdate = 0;
            if (entity.flickerTally > 0) entity.flickerTally--;
            if (entity.networkDirty) {
                if (entity.hasNetwork()) entity.getOrCreateNetwork().updateNetwork();
                entity.networkDirty = false;
            }
            if (entity instanceof GeneratingKineticBlockEntity generator) generator.tickGenerator();
            if(entity.validationCountdown--<=0){entity.validationCountdown=com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.kineticValidationFrequency.get();KineticNetworks.markDirty(serverLevel);}
            // Actual source crank owners own InUse/Backwards themselves, including after reload.
            if (!(entity instanceof com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity)) {
                if (entity.crankInUse > 0 && --entity.crankInUse == 0) KineticNetworks.markDirty(serverLevel);
                if (entity.crankInUse > 0 && level.getGameTime() % 10 == 0) {
                    level.playSound(null, pos, AllSoundEvents.CRANKING, net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 0.65f);
                    level.playSound(null, pos, AllSoundEvents.CRANKING_BUTTON, net.minecraft.sounds.SoundSource.BLOCKS, 0.1f, 0.65f);
                }
            }
        }
        entity.independentAngle = (entity.independentAngle + entity.getSpeed() * 0.3) % 360;
    
        }
    }

    /** Native state replacement owns BE lifecycle; no NBT cloning or inventory duplication. */
    public static void switchToBlockState(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server) || !com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level,pos) || level.getBlockState(pos) == state) return;
        level.setBlock(pos, state, Block.UPDATE_ALL);
        KineticNetworks.markDirty(server);
    }

    public static float convertToAngular(float rpm){return rpm*.3f;}
    public static float convertToDirection(float speed,Direction direction){return speed*direction.getAxisDirection().getStep();}
    public static float convertToLinear(float rpm){return rpm/512;}
    public boolean hasSource(){return source!=null;}
    public BlockPos getSource(){return source;}
    public BlockPos getGeneratingSource(){return generatingSource;}
    public Direction getKineticSourceFace(){return kineticSourceFace;}
    public com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase getSequencePhase(){return sequencePhase;}
    public void setKineticProvenance(BlockPos generator,BlockPos upstream,Direction face){
        if(java.util.Objects.equals(generatingSource,generator)&&java.util.Objects.equals(source,upstream)&&kineticSourceFace==face)return;
        generatingSource=generator==null?null:generator.immutable();source=upstream==null?null:upstream.immutable();kineticSourceFace=face;setChanged();sync();
    }
    public void applySequenceContext(com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext context,
            com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase phase){
        if(context==null)phase=null;
        if(java.util.Objects.equals(sequenceContext,context)&&java.util.Objects.equals(sequencePhase,phase))return;
        var previous=sequenceContext;var previousPhase=sequencePhase;sequenceContext=context;sequencePhase=phase;
        onSequenceContextChanged(previous,previousPhase);setChanged();sync();
    }
    /** Persistence/migration only: restore an already-consumed phase without replaying consumer hooks. */
    protected final void restoreSequenceContext(com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext context,
            com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase phase){sequenceContext=context;sequencePhase=context==null?null:phase;}
    protected void onSequenceContextChanged(com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext previous,
            com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase previousPhase){}
    protected void copySequenceContextFrom(KineticBlockEntity upstream){applySequenceContext(upstream.sequenceContext,upstream.sequencePhase);}
    public void onSpeedChanged(float previousSpeed) {
        boolean fromOrToZero = (previousSpeed == 0) != (getSpeed() == 0);
        boolean directionSwap = !fromOrToZero && Math.signum(previousSpeed) != Math.signum(getSpeed());
        if (fromOrToZero || directionSwap) flickerTally = getFlickerScore() + 5;
        setChanged();
    }

    public float getSpeed() {
        return appliedSpeedFor(speed,conflicted,overStressed);
    }

    /** Effective RPM is a projection of genuine source state, not another independent speed field. */
    public final float appliedSpeedFor(float theoretical,boolean conflict,boolean overStress) {
        return overStress || conflict || conflictDestructionPending || level != null && level.tickRateManager().isFrozen()
            ? 0 : theoretical;
    }

    public float getTheoreticalSpeed() {
        return speed;
    }

    public double getStressCapacity() {
        return capacity;
    }

    public double getStressImpact() {
        return stress;
    }

    public boolean isOverstressed() {
        return overStressed;
    }

    public float getPortSpeed(Direction port) {
        return getSpeed() == 0 ? 0 : portSpeeds.getOrDefault(port, speed);
    }

    public Map<Direction,Float> getTheoreticalPortSpeeds() { return Map.copyOf(portSpeeds); }

    public boolean isConflictDestructionPending() { return conflictDestructionPending; }
    public void setConflictDestructionPending(boolean pending) { conflictDestructionPending=pending; setChanged(); }

    public boolean isConflicted() {
        return conflicted || conflictDestructionPending;
    }

    public float getAngle(float partialTick) {
        // Use one world clock so newly placed/reloaded gears remain in phase.
        // RPM * 360 degrees / (60 seconds * 20 ticks).
        double ticks = level == null ? 0 : level.getGameTime() + partialTick;
        var state=getBlockState();
        int offset=state.getBlock() instanceof IRotate block?getRotationAngleOffset(block.getRotationAxis(state)):0;
        return (float) ((ticks * getSpeed() * 0.3) % 360) + offset;
    }

    /** Source generators override this float API; ordinary kinetic members generate no power. */
    public int getRotationAngleOffset(Direction.Axis axis) { return 0; }
    protected boolean syncSequenceContext() { return false; }
    public float getGeneratedSpeed() { return 0; }
    /** The original one persisted motor setting; UI editing must not narrow generated RPM. */
    public int getGeneratedSpeedSetting() {
        return this instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor
            ? motor.generatedSpeed.getValue():0;
    }
    public boolean isSource() { return getGeneratedSpeed() != 0; }
    public float calculateAddedStressCapacity() { return lastCapacityProvided=(float)getAddedStressCapacity(); }
    public float calculateStressApplied() { return lastStressApplied=(float)com.simibubi.create.api.stress.BlockStressValues.getImpact(getStressConfigKey()); }
    protected Block getStressConfigKey(){return getBlockState().getBlock();}
    public void setSpeed(float speed) {
        this.speed = speed;
        if (networkUpdateDepth == 0 && level instanceof ServerLevel server && !isVirtual()) KineticNetworks.markDirty(server);
    }
    public void setSource(BlockPos source) {
        this.source = source;
        if (level == null || level.isClientSide() || isVirtual()) return;
        var upstream = com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level, source);
        if (!(upstream instanceof KineticBlockEntity kinetic)) { removeSource(); return; }
        setNetwork(kinetic.network);
        setKineticProvenance(kinetic.getGeneratingSource(), source,
            Direction.getNearest(source.subtract(worldPosition), Direction.NORTH));
        copySequenceContextFrom(kinetic);
        KineticNetworks.track((ServerLevel) level, this);
    }
    public void removeSource() {
        float previous = getSpeed();
        speed = 0;
        setKineticProvenance(null, null, null);
        setNetwork(null);
        applySequenceContext(null, null);
        kineticSnapshotChanged(previous);
        if (level instanceof ServerLevel server && !isVirtual()) KineticNetworks.markDirty(server);
    }
    public void setNetwork(Long networkIn) {
        if (java.util.Objects.equals(network, networkIn)) return;
        if (level instanceof ServerLevel && !isVirtual() && network != null) getOrCreateNetwork().remove(this);
        network = networkIn;
        setChanged();
        if (networkIn == null || !(level instanceof ServerLevel server) || isVirtual()) return;
        var group = getOrCreateNetwork();
        group.initialized = true;
        group.add(this);
        KineticNetworks.track(server, this);
    }
    public com.simibubi.create.content.kinetics.KineticNetwork getOrCreateNetwork() {
        return com.simibubi.create.Create.TORQUE_PROPAGATOR.getOrCreateNetworkFor(this);
    }
    public boolean hasNetwork() { return network != null; }
    public void attachKinetics() {
        updateSpeed = false;
        com.simibubi.create.content.kinetics.RotationPropagator.handleAdded(level, worldPosition, this);
    }
    public void detachKinetics() {
        com.simibubi.create.content.kinetics.RotationPropagator.handleRemoved(level, worldPosition, this);
    }
    public boolean needsSpeedUpdate() { return updateSpeed; }
    public boolean isSpeedRequirementFulfilled() { return Math.abs(getSpeed()) >= getMinimumRequiredSpeed(); }
    public void clearKineticInformation() {
        if(level instanceof ServerLevel server) KineticNetworks.kineticInformationCleared(server,this);
        speed = capacity = stress = lastStressApplied = lastCapacityProvided = 0;
        source = generatingSource = null; kineticSourceFace = null; network = null;
        overStressed = conflicted = false; portSpeeds.clear(); networkSize = 0;
    }
    public void warnOfMovement() { wasMoved = true; }
    public int getFlickerScore() { return flickerTally; }
    public boolean isOverStressed() { return overStressed; }
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
            boolean connectedViaAxes, boolean connectedViaCogs) { return 0; }
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) { return false; }
    protected boolean canPropagateDiagonally(IRotate block, BlockState state) {
        return state.getBlock() instanceof com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
    }
    public java.util.List<BlockPos> addPropagationLocations(IRotate block, BlockState state, java.util.List<BlockPos> neighbours) {
        if (canPropagateDiagonally(block, state)) {
            boolean large = block instanceof com.simibubi.create.content.kinetics.simpleRelays.ICogWheel cog && cog.isLargeCog();
            var axis = block.getRotationAxis(state);
            for (int dx=-1; dx<=1; dx++) for (int dy=-1; dy<=1; dy++) for (int dz=-1; dz<=1; dz++) {
                if (dx*dx+dy*dy+dz*dz != 2 || !large && axis.choose(dx,dy,dz) != 0) continue;
                neighbours.add(worldPosition.offset(dx,dy,dz));
            }
        }
        if (this instanceof com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity conveyor)
            for (var offset : conveyor.getConnections()) neighbours.add(worldPosition.offset(offset));
        return neighbours;
    }

    public void setGeneratedSpeed(int speed) {
        if (!(this instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor))
            throw new IllegalStateException("This kinetic owner has no creative motor speed setting");
        motor.generatedSpeed.setValue(speed);
    }

    public float getIndependentAngle(float partialTick) {
        return (float) ((independentAngle + getSpeed() * 0.3 * partialTick) % 360);
    }

    public double getDirectionalGeneratedSpeed() { return getGeneratedSpeed(); }

    public double getAddedStressCapacity() { return com.simibubi.create.api.stress.BlockStressValues.getCapacity(getStressConfigKey()); }
    public float getMinimumRequiredSpeed(){return 0;}
    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){
        if(com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.disableStress.get())return false;
        double impact=com.simibubi.create.api.stress.BlockStressValues.getImpact(getBlockState().getBlock()),capacity=calculateAddedStressCapacity();
        if(impact==0&&capacity==0)return false;
        var title=com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate(capacity>0?"gui.goggles.generator_stats":"gui.goggles.kinetic_stats");tooltip.add(title);
        if(impact>0){tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate("tooltip.stressImpact").withStyle(net.minecraft.ChatFormatting.GRAY));addStressLine(tooltip,impact*Math.abs(getTheoreticalSpeed()));}
        if(capacity>0){tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate("tooltip.capacityProvided").withStyle(net.minecraft.ChatFormatting.GRAY));addStressLine(tooltip,getTheoreticalSpeed()==0?0:capacity*Math.abs(getDirectionalGeneratedSpeed()));}
        return true;
    }
    private void addStressLine(java.util.List<net.minecraft.network.chat.Component> tooltip,double amount){
        com.simibubi.create.content.equipment.goggles.GoggleTooltip.line(tooltip,com.simibubi.create.content.equipment.goggles.GoggleTooltip.units(amount,"stress").withStyle(net.minecraft.ChatFormatting.AQUA).append(" ").append(com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate("gui.goggles.at_current_speed").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)),1);
    }
    @Override public boolean addToTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){
        if(overStressed){tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate("gui.stressometer.overstressed").withStyle(net.minecraft.ChatFormatting.GOLD));tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.hint("gui.contraptions.network_overstressed"));return true;}
        if(speed!=0&&Math.abs(speed)<getMinimumRequiredSpeed()){tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate("tooltip.speedRequirement").withStyle(net.minecraft.ChatFormatting.GOLD));tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.hint("gui.contraptions.not_fast_enough",getBlockState().getBlock().getName()));return true;}
        return false;
    }

    public void turnCrank(boolean backwards) {
        crankInUse = 10;
        crankBackwards = backwards;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            KineticNetworks.markDirty(serverLevel);
        }
    }

    public void adjustGeneratedSpeed(boolean reverse) {
        int current=getGeneratedSpeedSetting();
        int next;
        if(reverse) next=(int)Math.clamp(-(long)current,-(long)KineticNetworks.maxRotationSpeed(),(long)KineticNetworks.maxRotationSpeed());
        else {
            int sign=current<0?-1:1;
            next=sign*(Math.abs((long)current)>=KineticNetworks.maxRotationSpeed()?Math.min(16,KineticNetworks.maxRotationSpeed())
                :(int)Math.min(KineticNetworks.maxRotationSpeed(),Math.max(16L,Math.abs((long)current)*2)));
        }
        setGeneratedSpeed(next);
    }

    /** A native topology update stages every owner before publishing speed callbacks. */
    public final void beginNetworkUpdate() {
        if (networkUpdateDepth++ == 0) { speedBeforeNetworkUpdate = getSpeed(); networkSnapshotChanged = false; forcedSpeedCallback = false; }
    }
    public final void endNetworkUpdate() {
        if (--networkUpdateDepth != 0) return;
        if (networkSnapshotChanged || forcedSpeedCallback) {
            if (forcedSpeedCallback || Float.compare(speedBeforeNetworkUpdate, getSpeed()) != 0) onSpeedChanged(speedBeforeNetworkUpdate);
            setChanged(); sync();
        }
    }
    public final void notifyKineticSpeedChange(float previousSpeed) { kineticSnapshotChanged(previousSpeed); }
    protected final void forceSpeedCallback() { forcedSpeedCallback = true; }
    private void kineticSnapshotChanged(float previousSpeed) { kineticSnapshotChanged(previousSpeed,false); }
    private void kineticSnapshotChanged(float previousSpeed,boolean forceCallback) {
        if (networkUpdateDepth > 0) { networkSnapshotChanged = true; forcedSpeedCallback |= forceCallback; return; }
        if (forceCallback || Float.compare(previousSpeed, getSpeed()) != 0) onSpeedChanged(previousSpeed);
        setChanged(); sync();
    }
    public void updateFromNetwork(float maxStress, float currentStress, int size) {
        float previous = getSpeed();
        networkDirty = false;
        boolean previousOverStress=overStressed;
        capacity = maxStress; stress = currentStress; networkSize = size;
        overStressed = maxStress < currentStress && !com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.disableStress.get();
        kineticSnapshotChanged(previous,previousOverStress!=overStressed);
    }
    protected final void validateAppliedSpeed(float appliedSpeed,float theoreticalSpeed,boolean conflicted,boolean overstressed) {
        float expectedApplied=appliedSpeedFor(theoreticalSpeed,conflicted,overstressed);
        if (appliedSpeed != expectedApplied && !(Float.isNaN(appliedSpeed) && Float.isNaN(expectedApplied)))
            throw new IllegalArgumentException("Applied RPM must match theoretical RPM and the source stop flags");
    }
    public void setNetworkState(float appliedSpeed, float theoreticalSpeed, boolean conflicted,
                               KineticGraph.Stress totals, Map<Direction, Float> ports) {
        validateAppliedSpeed(appliedSpeed,theoreticalSpeed,conflicted,totals.overstressed());
        if (speed == theoreticalSpeed && this.conflicted == conflicted
                && capacity == (float)totals.capacity() && stress == (float)totals.impact()
                && overStressed == totals.overstressed() && portSpeeds.equals(ports)) return;
        float previous = getSpeed();
        boolean previousConflict=isConflicted(),previousOverStress=overStressed;
        speed = theoreticalSpeed;
        this.conflicted = conflicted;
        capacity = (float) totals.capacity(); stress = (float) totals.impact();
        overStressed = totals.overstressed();
        portSpeeds.clear(); portSpeeds.putAll(ports);
        kineticSnapshotChanged(previous,previousConflict!=isConflicted() || previousOverStress!=overStressed);
    }

    protected void sync() {
        if (networkUpdateDepth > 0) { networkSnapshotChanged = true; return; }
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override public void remove() {
        if (level instanceof ServerLevel && !isVirtual()) {
            if (hasNetwork()) getOrCreateNetwork().remove(this);
            detachKinetics();
        }
        super.remove();
    }

    @Override
    public void invalidate() {
        if (level instanceof ServerLevel serverLevel) {
            KineticNetworks.remove(serverLevel, this);
        }
        super.invalidate();
    }

    @Override
    protected void write(ValueOutput output, boolean clientPacket) {
        super.write(output, clientPacket);
        
        if(source!=null)output.store("Source",BlockPos.CODEC,source);
        if(generatingSource!=null)output.store("Generator",BlockPos.CODEC,generatingSource);
        if(kineticSourceFace!=null)output.putInt("KineticSourceFace",kineticSourceFace.ordinal());
        if(sequenceContext!=null && (!clientPacket || syncSequenceContext()))output.store("Sequence",com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext.CODEC,sequenceContext);
        if(sequencePhase!=null && (!clientPacket || syncSequenceContext()))output.store("SequencePhase",com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase.CODEC,sequencePhase);
        output.putFloat("Speed", speed);
        if (needsSpeedUpdate()) output.putBoolean("NeedsSpeedUpdate", true);
        if (hasNetwork()) {
            var group = output.child("Network");
            group.putLong("Id", network); group.putFloat("Stress", stress); group.putFloat("Capacity", capacity);
            group.putInt("Size", networkSize); group.putFloat("AddedStress", lastStressApplied);
            group.putFloat("AddedCapacity", lastCapacityProvided);
        }
        output.putFloat("TheoreticalSpeed", speed);
        output.putDouble("StressCapacity", capacity);
        output.putDouble("StressImpact", stress);
        output.putBoolean("Overstressed", overStressed);
        ValueOutput ports = output.child("PortSpeeds");
        portSpeeds.forEach((port, rpm) -> ports.putFloat(port.getSerializedName(), rpm));
        if(this instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)
            output.putInt("GeneratedSpeed",getGeneratedSpeedSetting());
        output.putBoolean("Conflicted", conflicted);
        if(conflictDestructionPending) output.putBoolean("KineticConflictPending",true);
        if (!(this instanceof com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity)) {
            output.putInt("InUse", crankInUse);
            output.putBoolean("Backwards", crankBackwards);
        }
        output.putDouble("IndependentAngle", independentAngle);
    }

    @Override
    protected void read(ValueInput input, boolean clientPacket) {
        
        
        source=input.read("Source",BlockPos.CODEC).orElse(null);generatingSource=input.read("Generator",BlockPos.CODEC).orElse(null);
        int sourceFace=input.getIntOr("KineticSourceFace",-1);kineticSourceFace=sourceFace>=0&&sourceFace<Direction.values().length?Direction.values()[sourceFace]:null;
        sequenceContext=input.read("Sequence",com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext.CODEC).orElse(null);
        sequencePhase=input.read("SequencePhase",com.simibubi.create.content.kinetics.transmission.sequencer.SequencePhase.CODEC).orElse(null);
        if (wasMoved) {
            clearKineticInformation();
            super.read(input, clientPacket);
            return;
        }
        boolean sourceFormat = input.child("Network").isPresent();
        // Real Ponder virtual BE instructions edit Speed; a stale native alias cannot win.
        speed = isVirtual() || clientPacket || sourceFormat ? input.getFloatOr("Speed", 0)
            : input.getFloatOr("TheoreticalSpeed", input.getFloatOr("Speed", 0));
        var group = input.childOrEmpty("Network");
        network = sourceFormat ? group.getLong("Id").orElse(null) : null;
        stress = sourceFormat ? group.getFloatOr("Stress", 0) : (float) input.getDoubleOr("StressImpact", 0);
        capacity = sourceFormat ? group.getFloatOr("Capacity", 0) : (float) input.getDoubleOr("StressCapacity", 0);
        networkSize = group.getIntOr("Size", 0);
        lastStressApplied = group.getFloatOr("AddedStress", 0);
        lastCapacityProvided = group.getFloatOr("AddedCapacity", 0);
        overStressed = capacity < stress && !com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.disableStress.get();
        updateSpeed = input.getBooleanOr("NeedsSpeedUpdate", !sourceFormat);
        portSpeeds.clear();
        ValueInput ports = input.childOrEmpty("PortSpeeds");
        for (Direction face : Direction.values()) {
            float rpm = ports.getFloatOr(face.getSerializedName(), speed);
            portSpeeds.put(face, rpm);
        }
        conflictDestructionPending = input.getBooleanOr("KineticConflictPending",false);
        conflicted = input.getBooleanOr("Conflicted", false) || conflictDestructionPending;
        boolean sourceCrank=this instanceof com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
        crankInUse = sourceCrank?0:Math.clamp(input.getIntOr("InUse", 0), 0, 10);
        crankBackwards = !sourceCrank&&input.getBooleanOr("Backwards", false);
        double loadedAngle = input.getDoubleOr("IndependentAngle", 0);
        independentAngle = Double.isFinite(loadedAngle) ? loadedAngle % 360 : 0;
        if (level instanceof ServerLevel serverLevel) {
            KineticNetworks.markDirty(serverLevel);
        }
    
        
    
        super.read(input, clientPacket);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return super.getUpdateTag(registries);
    }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (bracket != null) behaviours.add(bracket);
    }
}
