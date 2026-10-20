package com.simibubi.create.content.contraptions.pulley;

import com.simibubi.create.*;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** A real linear body owner, or an empty virtual rope owned only by this native controller. */
public class PulleyBlockEntity extends LinearActuatorBlockEntity implements com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchObservable {
    private float prevAnimatedOffset;
    public void animateOffset(float forcedOffset) { offset = forcedOffset; }

    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements;
    protected int initialOffset,travelRange;
    protected BlockPos mirrorParent;
    protected UUID mirrorOwner;
    private UUID mirrorBodyId;
    // Genuine original positional evidence remains pending until actual loaded owners reconcile.
    private BlockPos originalMirrorParent;
    private final LinkedHashSet<BlockPos> originalMirrorChildren=new LinkedHashSet<>();
    protected final LinkedHashSet<BlockPos> mirrorChildren=new LinkedHashSet<>();
    protected boolean ropesRemoved;
    private boolean queuedReassembly,removing;
    public PulleyBlockEntity(BlockPos pos,BlockState state){this(AllBlockEntityTypes.ROPE_PULLEY,pos,state);}
    protected PulleyBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,type==AllBlockEntityTypes.ELEVATOR_PULLEY?new String[]{"contraption_actors"}:new String[]{"contraption_actors","pulley_maxed"});}
    public BlockPos getMirrorParent(){return mirrorParent!=null?mirrorParent:originalMirrorParent;}
    public UUID getMirrorOwner(){return mirrorOwner;}
    public boolean hasQueuedReassembly(){return queuedReassembly;}
    @Override public int getCurrentValue(){return worldPosition.getY()-(int)getInterpolatedOffset(.5f);}
    @Override public int getMinValue(){return level.getMinY();}
    @Override public int getMaxValue(){return worldPosition.getY();}
    @Override public net.minecraft.network.chat.MutableComponent format(int value){return net.minecraft.network.chat.Component.translatable("create.gui.threshold_switch.pulley_y_level",value);}
    public int getInitialRopeOffset(){return initialOffset;}
    public boolean hasVirtualRopes(){return ropesRemoved||mirrorParent!=null||originalMirrorParent!=null;}
    @Override protected boolean isPassive(){return mirrorParent!=null||originalMirrorParent!=null;}
    protected int newAssemblyRange(){return Math.max(0,Math.min(PulleySettings.maxRopeLength,worldPosition.getY()-1-level.getMinY()));}
    @Override protected int getExtensionRange(){return running||movedUuid!=null||ropesRemoved?travelRange:newAssemblyRange();}
    @Override protected int getInitialOffset(){return initialOffset;}
    @Override protected Vec3 toMotionVector(float amount){return new Vec3(0,-amount,0);}
    @Override protected Vec3 toPosition(float amount){var owner=getMovedContraption();return owner!=null&&owner.getContraption() instanceof PulleyContraption pulley?pulley.positionAtProgress(amount):Vec3.atLowerCornerOf(worldPosition).add(0,-amount-1,0);}
    @Override protected boolean canAttachWithoutSavedOwner(LinearContraptionEntity owner){return running&&originalMirrorParent==null;}
    @Override protected boolean matches(LinearContraptionEntity owner){return owner.getContraption() instanceof PulleyContraption pulley&&worldPosition.equals(owner.getControllerPos())&&pulley.anchor.above(pulley.getInitialOffset()+1).equals(worldPosition);}
    protected boolean isControllerBlock(BlockState state){return state.getBlock() instanceof PulleyBlock;}
    protected PulleyContraption createContraption(int offset){return new PulleyContraption(offset);}
    protected boolean mayStartAt(int offset,int range){return !(offset>=range&&getSpeed()>0||offset<=0&&getSpeed()<0);}
    protected boolean requiresBody(){return false;}
    protected boolean shouldCheckInitialCollision(){return true;}
    protected void beforeBodyTransfer(LinearContraptionEntity owner,PulleyContraption structure)throws AssemblyException{}
    protected void onBodyAssembled(LinearContraptionEntity owner,PulleyContraption structure){}
    protected void releaseUnusedReservation(LinearContraptionEntity owner){}
    protected LinearContraptionEntity createReservation(ServerLevel server){return new LinearContraptionEntity(AllEntityTypes.LINEAR_CONTRAPTION,server);}
    @Override public LinearContraptionEntity getMovedContraption(){var previous=movedUuid;var owner=super.getMovedContraption();if(previous!=null&&movedUuid==null&&ropesRemoved&&mirrorParent==null&&!transferring){running=true;pendingDisassembly=true;setChanged();}return owner;}
    public void startMirroringOther(BlockPos parentPos){if(level instanceof ServerLevel server&&LoadedLevelAccess.isLoaded(server,parentPos)&&LoadedLevelAccess.blockEntity(server,parentPos) instanceof PulleyBlockEntity parent&&parent.getType()==getType()){var owner=parent.getMovedContraption();if(owner!=null)startMirroringOther(parent,owner);}}
    public void notifyMirrorsOfDisassembly(){if(!(level instanceof ServerLevel server))return;var owner=getMovedContraption();if(owner!=null&&owner.hasOwnedStructure())return;if(mirrorBodyId!=null)finishMirrors(server,mirrorBodyId,getGridOffset(offset));}
    public AbstractContraptionEntity getAttachedContraption(){
        if(mirrorParent==null)return getMovedContraption();
        if(level==null||!LoadedLevelAccess.isLoaded(level,mirrorParent)||!(LoadedLevelAccess.blockEntity(level,mirrorParent) instanceof PulleyBlockEntity parent)||parent.getType()!=getType())return null;
        var owner=parent.getMovedContraption();return owner!=null&&Objects.equals(mirrorOwner,owner.getUUID())?owner:null;
    }
    @Override public float getInterpolatedOffset(float partial){if(isVirtual())return net.minecraft.util.Mth.lerp(partial,prevAnimatedOffset,offset);var attached=getAttachedContraption();if(attached!=null&&attached.getContraption() instanceof PulleyContraption pulley){double y=attached.yo+(attached.getY()-attached.yo)*partial;return (float)(pulley.anchor.getY()+pulley.getInitialOffset()-y);}return running&&!isPassive()?super.getInterpolatedOffset(partial):Math.clamp(offset,0,getExtensionRange());}
    protected List<BlockState> scanRopes(ServerLevel server)throws AssemblyException{
        var states=new ArrayList<BlockState>();int limit=newAssemblyRange();for(int i=1;i<=limit;i++){var state=PulleyWorldAccess.required(server,worldPosition.below(i));if(!PulleyBlock.isRope(state))break;states.add(state);}return states;
    }
    protected void removeSourceRopes(ServerLevel server,List<BlockState> expected)throws AssemblyException{
        for(int i=0;i<expected.size();i++)if(PulleyWorldAccess.required(server,worldPosition.below(i+1))!=expected.get(i))throw AssemblyException.structureChanged(worldPosition.below(i+1));
        ropesRemoved=true;setChanged();for(int i=0;i<expected.size();i++)PulleyWorldAccess.setMoving(server,worldPosition.below(i+1),expected.get(i).getFluidState().createLegacyBlock());
    }
    private void bind(LinearContraptionEntity owner,PulleyContraption structure,int initial){
        if(owner.getContraption()!=structure)owner.setContraption(structure);movedContraption=owner;movedUuid=owner.getUUID();mirrorBodyId=owner.getUUID();offset=initialOffset=initial;running=true;owner.setProgress(offset);
        ContraptionHandler.addSpawnedContraptionsToCollisionList(owner);setChanged();sync();
    }
    @Override public boolean assemble()throws AssemblyException{
        if(!(level instanceof ServerLevel server)||running||movedUuid!=null||transferring||mirrorParent!=null||originalMirrorParent!=null||!originalMirrorChildren.isEmpty())return false;
        if(!isControllerBlock(LoadedLevelAccess.blockState(server,worldPosition))||getSpeed()==0)return false;
        PulleyWorldAccess.requireTransfer();transferring=true;LinearContraptionEntity reserved=null;
        try{
            var ropes=scanRopes(server);int initial=ropes.size(),range=newAssemblyRange();if(!mayStartAt(initial,range))return false;
            var anchor=worldPosition.below(initial+1);var structure=createContraption(initial);boolean captured=structure.assemble(server,anchor);boolean body=!structure.getBlocks().isEmpty();
            var direction=getSpeed()>0?Direction.DOWN:Direction.UP;
            if(captured&&body&&shouldCheckInitialCollision()&&ContraptionTerrainCollision.isCollidingWithWorld(server,structure,anchor.relative(direction),direction))captured=false;
            if(requiresBody()&&(!captured||!body)||!captured&&getSpeed()>0)return false;
            travelRange=range;offset=initialOffset=initial;
            if(captured&&body){
                reserved=createReservation(server);reserved.setControllerPos(worldPosition);reserved.setProgress(initial);reserved.setPos(Vec3.atLowerCornerOf(anchor));
                if(!server.addFreshEntity(reserved)||reserved.isRemoved()||server.getEntity(reserved.getUUID())!=reserved)throw AssemblyException.structureChanged(worldPosition);
                beforeBodyTransfer(reserved,structure);var accepted=reserved;
                var result=ContraptionAssemblyGraph.assemble(server,List.of(new ContraptionAssemblyGraph.Root(accepted,structure)),complete->{bind(accepted,structure,initial);recoveringAssembly=!complete;pendingDisassembly=false;});
                if(!result.complete())throw result.failure()==null?AssemblyException.entitiesNotReady(worldPosition):result.failure();
            }else{running=true;setChanged();}
            try{removeSourceRopes(server,ropes);}catch(AssemblyException changed){pendingDisassembly=true;recoveringAssembly=true;setChanged();throw changed;}
            retryTicks=0;lastException=null;waitingForSpeedChange=false;queuedReassembly=false;setChanged();sync();
            if(movedContraption!=null){
                findMirrors(server,movedContraption,structure);onBodyAssembled(movedContraption,structure);
                if(movedContraption!=null&&movedContraption.hasOwnedStructure()&&movedContraption.getContraption()==structure
                    &&structure.getActors().stream().anyMatch(actor->{var behaviour=com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(actor.left.state());return behaviour instanceof com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour||behaviour instanceof com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;}))sourceAdvancements.award("contraption_actors");
            }server.playSound(null,worldPosition,AllSoundEvents.CONTRAPTION_ASSEMBLE,SoundSource.BLOCKS,1,1);return true;
        }finally{if(reserved!=null&&reserved.getContraption()==null){releaseUnusedReservation(reserved);reserved.discard();}transferring=false;}
    }
    private void findMirrors(ServerLevel server,LinearContraptionEntity owner,PulleyContraption structure){
        for(var local:structure.createColliders(server,Direction.UP))if(local.getY()==0){var candidate=structure.anchor.offset(local);var pos=new BlockPos(candidate.getX(),worldPosition.getY(),candidate.getZ());if(pos.equals(worldPosition)||!LoadedLevelAccess.isLoaded(server,pos))continue;if(LoadedLevelAccess.blockEntity(server,pos) instanceof PulleyBlockEntity mirror&&mirror.getType()==getType())mirror.startMirroringOther(this,owner);}
    }
    private void startMirroringOther(PulleyBlockEntity parent,LinearContraptionEntity owner){
        if(!(level instanceof ServerLevel server)||running||movedUuid!=null||mirrorParent!=null||transferring)return;
        var expected=new ArrayList<BlockState>();int length=parent.getGridOffset(parent.offset);
        try{for(int i=1;i<=length;i++){var state=PulleyWorldAccess.required(server,worldPosition.below(i));if(!PulleyBlock.isRope(state)&&!state.canBeReplaced())return;expected.add(state);}}
        catch(AssemblyException unavailable){return;}
        mirrorParent=parent.worldPosition.immutable();mirrorOwner=owner.getUUID();offset=initialOffset=length;travelRange=parent.travelRange;running=true;ropesRemoved=true;setChanged();
        parent.mirrorChildren.add(worldPosition.immutable());parent.setChanged();parent.sync();
        for(int i=0;i<expected.size();i++)PulleyWorldAccess.setMoving(server,worldPosition.below(i+1),expected.get(i).getFluidState().createLegacyBlock());sync();
    }
    protected void preflightRopeTargets(ServerLevel server,int grid)throws AssemblyException{if(!shouldCreateRopes()||!ropesRemoved)return;for(int i=1;i<=grid;i++)PulleyWorldAccess.requirePerimeter(server,worldPosition.below(i));}
    protected boolean shouldCreateRopes(){return !removing;}
    private void restoreRopes(ServerLevel server,int grid)throws AssemblyException{
        if(!ropesRemoved||!shouldCreateRopes())return;preflightRopeTargets(server,grid);var positions=new ArrayList<BlockPos>();
        // Snapshot fluids, then clear targets before creating any new adjacent rope segments.
        var replacements=new LinkedHashMap<BlockPos,BlockState>();
        for(int i=1;i<=grid;i++){
            var pos=worldPosition.below(i);var old=PulleyWorldAccess.required(server,pos);if(old.getDestroySpeed(server,pos)==-1)continue;
            boolean water=old.getFluidState().getType()==Fluids.WATER;replacements.put(pos.immutable(),(i==grid?AllBlocks.PULLEY_MAGNET.defaultBlockState():AllBlocks.ROPE.defaultBlockState()).setValue(BlockStateProperties.WATERLOGGED,water));
        }
        for(var pos:replacements.keySet()){
            var old=PulleyWorldAccess.required(server,pos);if(!old.isAir()&&!PulleyBlock.isRope(old)&&old.getFluidState().isEmpty())server.destroyBlock(pos,old.getCollisionShape(server,pos).isEmpty());
        }
        for(var entry:replacements.entrySet()){PulleyWorldAccess.required(server,entry.getKey());PulleyWorldAccess.setMoving(server,entry.getKey(),entry.getValue());positions.add(entry.getKey());}
        DeferredContraptionRestoration.finishOrQueue(server,positions);ropesRemoved=false;setChanged();
    }
    @Override public boolean canDisassembleNow(){
        if(!(level instanceof ServerLevel server)||transferring||!PulleyWorldAccess.transferAllowed()||!reconcileOriginalMirrors(server))return false;
        if(mirrorParent!=null){var parent=LoadedLevelAccess.blockEntity(server,mirrorParent);return parent instanceof PulleyBlockEntity pulley&&pulley.getType()==getType()&&Objects.equals(pulley.movedUuid,mirrorOwner)&&pulley.canDisassembleNow();}
        var owner=getMovedContraption();if(owner==null&&movedUuid!=null)return false;
        try{preflightRopeTargets(server,getGridOffset(offset));if(owner!=null)ContraptionAssemblyGraph.preflightTree(owner);return true;}catch(AssemblyException unavailable){return false;}
    }
    @Override public boolean disassemble(){
        if(!(level instanceof ServerLevel server)||transferring||!PulleyWorldAccess.transferAllowed()||!reconcileOriginalMirrors(server))return false;
        if(mirrorParent!=null){if(PulleyMirrorRestoration.tryFinish(server,worldPosition))return true;var be=LoadedLevelAccess.blockEntity(server,mirrorParent);return be instanceof PulleyBlockEntity parent&&parent.getType()==getType()&&Objects.equals(parent.movedUuid,mirrorOwner)&&parent.disassemble();}
        var owner=getMovedContraption();if(owner==null&&movedUuid!=null){pendingDisassembly=true;setChanged();return false;}
        if(!running&&!ropesRemoved&&owner==null)return true;
        pendingDisassembly=true;setChanged();if(!canDisassembleNow())return false;transferring=true;
        try{
            int grid=getGridOffset(offset);var completedId=movedUuid;if(owner!=null){owner.setProgress(grid);owner.setDeltaMovement(Vec3.ZERO);if(!owner.disassemble())return false;}
            if(completedId==null)completedId=mirrorBodyId;if(completedId!=null)finishMirrors(server,completedId,grid);offset=grid;restoreRopes(server,grid);
            movedContraption=null;movedUuid=null;running=waitingForSpeedChange=assembleNextTick=pendingDisassembly=recoveringAssembly=false;retryTicks=0;initialOffset=0;queuedReassembly=false;clearSequenceLimit();setChanged();sync();server.playSound(null,worldPosition,AllSoundEvents.CONTRAPTION_DISASSEMBLE,SoundSource.BLOCKS,1,1);return true;
        }catch(AssemblyException unavailable){failure(unavailable,false);return false;}finally{transferring=false;}
    }
    private void finishMirrors(ServerLevel server,UUID owner,int grid){finishMirrors(server,owner,grid,true);}
    private void finishMirrors(ServerLevel server,UUID owner,int grid,boolean completed){for(var pos:List.copyOf(mirrorChildren))PulleyMirrorRestoration.finishOrQueue(server,worldPosition,pos,owner,grid,completed);mirrorChildren.clear();mirrorBodyId=null;setChanged();}
    protected boolean finishMirroring(ServerLevel server,BlockPos parent,UUID owner,int grid)throws AssemblyException{
        if(!Objects.equals(mirrorParent,parent)||!Objects.equals(mirrorOwner,owner))return true;
        preflightRopeTargets(server,grid);offset=grid;restoreRopes(server,grid);mirrorParent=null;mirrorOwner=null;running=false;initialOffset=0;setChanged();sync();return true;
    }
    @Override protected void tickWithoutContraption(){
        if(!(level instanceof ServerLevel server))return;
        if(mirrorParent!=null){if(PulleyMirrorRestoration.tryFinish(server,worldPosition))return;var actual=getAttachedContraption();if(actual!=null&&LoadedLevelAccess.blockEntity(server,mirrorParent) instanceof PulleyBlockEntity parent){offset=parent.offset;travelRange=parent.travelRange;setChanged();if(server.getGameTime()%3==0)sync();}return;}
        if(movedUuid!=null||!running)return;
        float speed=getMovementSpeed(),next=Math.clamp(offset+speed,0,getExtensionRange());
        try{
            var below=worldPosition.below((int)next+1);var sampled=PulleyWorldAccess.required(server,below);
            if(speed>0&&(int)next!=(int)offset&&BlockMovementChecks.isMovementNecessary(sampled,server,below)&&!BlockMovementChecks.isBrittle(sampled)){if(disassemble())requestAssembly();return;}
            float travelled=next-offset;offset=next;consumeSequencedTravel(Math.abs(travelled));setChanged();if(server.getGameTime()%3==0)sync();if(next<=0||next>=getExtensionRange())tryDisassemble();
        }catch(AssemblyException unavailable){failure(unavailable,false);}
    }
    /** Resolve only reciprocal source rows against the real loaded controller and its actual body. */
    private boolean reconcileOriginalMirrors(ServerLevel server){
        if(level!=server||isRemoved()||LoadedLevelAccess.blockEntity(server,worldPosition)!=this)return false;
        if(originalMirrorParent!=null){
            if(!LoadedLevelAccess.isLoaded(server,originalMirrorParent)
                ||!(LoadedLevelAccess.blockEntity(server,originalMirrorParent) instanceof PulleyBlockEntity parent)
                ||!bindOriginalMirror(server,parent))return false;
        }
        if(!originalMirrorChildren.isEmpty()){
            var owner=getMovedContraption();
            if(!actualOriginalBody(server,owner))return false;
            owner.setDeltaMovement(Vec3.ZERO);
            for(var childPos:List.copyOf(originalMirrorChildren)){
                if(!LoadedLevelAccess.isLoaded(server,childPos)
                    ||!(LoadedLevelAccess.blockEntity(server,childPos) instanceof PulleyBlockEntity child)
                    ||!child.bindOriginalMirror(server,this))return false;
            }
        }
        return true;
    }
    private boolean actualOriginalBody(ServerLevel server,LinearContraptionEntity owner){
        return running&&owner!=null&&!owner.isRemoved()&&owner.level()==server&&server.getEntity(owner.getUUID())==owner
            &&owner.hasOwnedStructure()&&owner.getContraption()!=null&&!owner.getContraption().disassembled&&matches(owner);
    }
    private boolean bindOriginalMirror(ServerLevel server,PulleyBlockEntity parent){
        if(originalMirrorParent==null||!originalMirrorParent.equals(parent.worldPosition)||parent==this
            ||parent.getType()!=getType()||parent.level!=server||parent.isRemoved()
            ||LoadedLevelAccess.blockEntity(server,parent.worldPosition)!=parent||worldPosition.getY()!=parent.worldPosition.getY()
            ||!parent.originalMirrorChildren.contains(worldPosition)||mirrorParent!=null||movedUuid!=null)return false;
        var owner=parent.getMovedContraption();if(!parent.actualOriginalBody(server,owner))return false;
        var body=(PulleyContraption)owner.getContraption();BlockPos local;
        try{local=new BlockPos(Math.subtractExact(worldPosition.getX(),body.anchor.getX()),0,Math.subtractExact(worldPosition.getZ(),body.anchor.getZ()));}
        catch(ArithmeticException overflow){return false;}
        if(!body.createColliders(server,Direction.UP).contains(local))return false;
        mirrorParent=parent.worldPosition.immutable();mirrorOwner=owner.getUUID();originalMirrorParent=null;
        offset=parent.offset;travelRange=parent.travelRange;running=true;
        parent.mirrorChildren.add(worldPosition.immutable());parent.originalMirrorChildren.remove(worldPosition);parent.mirrorBodyId=owner.getUUID();
        setChanged();parent.setChanged();sync();parent.sync();return true;
    }
    public boolean disassembleForMovement(){if(!running&&movedUuid==null&&mirrorParent==null&&originalMirrorParent==null&&originalMirrorChildren.isEmpty())return true;if(!disassemble())return false;queuedReassembly=true;setChanged();return true;}
    public void onLengthBroken(){if(!running){initialOffset=0;offset=0;}else{pendingDisassembly=true;if(getMovedContraption()!=null)getMovedContraption().setDeltaMovement(Vec3.ZERO);}setChanged();sync();}
    public static void tick(Level level,BlockPos pos,BlockState state,PulleyBlockEntity pulley){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, pulley)) {
            if (!createTick.shouldTick()) return;

        if(pulley.isVirtual()) {
            com.simibubi.create.content.kinetics.base.KineticBlockEntity.tick(level,pos,state,pulley);
            pulley.prevAnimatedOffset=pulley.offset;
            return;
        }
        if(level instanceof ServerLevel server&&!pulley.reconcileOriginalMirrors(server)){
            com.simibubi.create.content.kinetics.base.KineticBlockEntity.tick(level,pos,state,pulley);return;
        }
        float previousOffset=pulley.offset;boolean restart=pulley.queuedReassembly;pulley.queuedReassembly=false;
        if(!level.isClientSide())
        LinearActuatorBlockEntity.tick(level,pos,state,pulley);
        if(!level.isClientSide()){if(previousOffset<200&&pulley.offset>=200)pulley.sourceAdvancements.award("pulley_maxed");if(restart&&!pulley.running)pulley.requestAssembly();}
    
        }
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){removing=true;if(level instanceof ServerLevel server&&movedUuid!=null)finishMirrors(server,movedUuid,getGridOffset(offset),false);super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput out, boolean clientPacket){
        super.write(out,clientPacket);out.putInt("InitialOffset",initialOffset);out.putInt("RopeTravelRange",travelRange);out.putBoolean("RopesRemoved",ropesRemoved);out.putBoolean("QueuePulleyReassembly",queuedReassembly);
        if(mirrorBodyId!=null)out.store("MirrorBodyOwner",UUIDUtil.CODEC,mirrorBodyId);
        if(mirrorParent!=null)out.store("MirrorParent",BlockPos.CODEC,mirrorParent);
        if(mirrorOwner!=null)out.store("MirrorOwner",UUIDUtil.CODEC,mirrorOwner);
        if(!mirrorChildren.isEmpty())out.store("MirrorChildren",BlockPos.CODEC.listOf(),List.copyOf(mirrorChildren));
        if(originalMirrorParent!=null)out.store("OriginalMirrorParent",BlockPos.CODEC,originalMirrorParent);
        if(!originalMirrorChildren.isEmpty())out.store("OriginalMirrorChildren",BlockPos.CODEC.listOf(),List.copyOf(originalMirrorChildren));
    }
    @Override protected void read(ValueInput in, boolean clientPacket){
        initialOffset=Math.max(0,in.getIntOr("InitialOffset",0));super.read(in,clientPacket);
        if((offset<0&&!(this instanceof com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity&&offset==-1&&!running&&movedUuid==null))||offset>Integer.MAX_VALUE-1.0)throw new IllegalArgumentException("Unrepresentable saved pulley progress");
        int actualMinimumRange=Math.max(initialOffset,(int)Math.ceil(offset));
        travelRange=Math.max(actualMinimumRange,in.getIntOr("RopeTravelRange",Math.max(actualMinimumRange,PulleySettings.maxRopeLength)));
        ropesRemoved=in.getBooleanOr("RopesRemoved",running);queuedReassembly=in.getBooleanOr("QueuePulleyReassembly",false);
        mirrorParent=in.read("MirrorParent",BlockPos.CODEC).orElse(null);mirrorOwner=in.read("MirrorOwner",UUIDUtil.CODEC).orElse(null);mirrorBodyId=in.read("MirrorBodyOwner",UUIDUtil.CODEC).orElse(movedUuid);
        if((mirrorParent==null)!=(mirrorOwner==null))throw new IllegalArgumentException("Incomplete saved pulley mirror identity");
        mirrorChildren.clear();mirrorChildren.addAll(in.read("MirrorChildren",BlockPos.CODEC.listOf()).orElse(List.of()));
        originalMirrorParent=in.read("OriginalMirrorParent",BlockPos.CODEC).orElse(null);
        originalMirrorChildren.clear();originalMirrorChildren.addAll(in.read("OriginalMirrorChildren",BlockPos.CODEC.listOf()).orElse(List.of()));
        if(originalMirrorParent!=null&&(mirrorParent!=null||movedUuid!=null||originalMirrorParent.equals(worldPosition))
            ||originalMirrorChildren.contains(worldPosition))throw new IllegalArgumentException("Contradictory saved original pulley relation");
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("pulley_maxed");
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
