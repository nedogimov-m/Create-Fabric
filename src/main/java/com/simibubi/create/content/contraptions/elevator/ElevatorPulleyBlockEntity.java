package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.pulley.*;
import com.simibubi.create.content.kinetics.transmission.sequencer.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Source floor target, acceleration/braking and arrival: no physical rope or empty cabin surrogate. */
public class ElevatorPulleyBlockEntity extends PulleyBlockEntity {
    private float previousSpeed;
    private boolean arrived=true;
    private int clientOffsetTarget,namesVersion=-1,lastSyncedTarget=Integer.MIN_VALUE;
    private boolean lastSyncedArrived;
    private Integer lastTargetY;
    public ElevatorPulleyBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.ELEVATOR_PULLEY,pos,state);movementMode=MovementMode.MOVE_NEVER_PLACE;offset=-1;}
    public boolean hasArrived(){return arrived;}
    public boolean retainsColumnOwner(UUID owner){getMovedContraption();return Objects.equals(movedUuid,owner)||transferring&&pendingColumn!=null;}
    @Override public MovementMode getMovementMode(){return MovementMode.MOVE_NEVER_PLACE;}
    @Override public void setMovementMode(MovementMode ignored){movementMode=MovementMode.MOVE_NEVER_PLACE;}
    @Override protected boolean isControllerBlock(BlockState state){return state.getBlock() instanceof ElevatorPulleyBlock;}
    @Override protected boolean requiresBody(){return true;}
    @Override protected boolean mayStartAt(int offset,int range){return true;}
    @Override protected boolean shouldCheckInitialCollision(){return false;}
    @Override protected boolean shouldStopOnTerrainCollision(){return false;}
    @Override protected net.minecraft.world.phys.Vec3 toMotionVector(float amount){return arrived?net.minecraft.world.phys.Vec3.ZERO:super.toMotionVector(amount);}
    @Override protected void updateContraptionPose(LinearContraptionEntity owner,float next){
        // Source stops coarse cabin motion once within half a block, while controller
        // offset keeps converging. Preserve that actual pose before quarter correction.
        double y=owner.getY();super.updateContraptionPose(owner,next);if(arrived)owner.setPoseBiasY((float)(owner.getPoseBiasY()+y-owner.getY()));
    }
    @Override protected boolean shouldCreateRopes(){return false;}
    @Override protected PulleyContraption createContraption(int offset){return new ElevatorContraption(offset);}
    @Override protected boolean matches(LinearContraptionEntity owner){return owner.getContraption() instanceof ElevatorContraption&&super.matches(owner);}
    @Override protected List<BlockState> scanRopes(ServerLevel server)throws AssemblyException{
        var states=new ArrayList<BlockState>();for(int i=1;i<=newAssemblyRange();i++){var pos=worldPosition.below(i);if(!LoadedLevelAccess.isLoaded(server,pos))throw AssemblyException.entitiesNotReady(pos);com.simibubi.create.foundation.utility.LoadedEntityAccess.requireReady(server,pos);var state=LoadedLevelAccess.blockState(server,pos);if(!state.getCollisionShape(server,pos).isEmpty()&&!state.canBeReplaced())break;states.add(state);}return states;
    }
    @Override protected void removeSourceRopes(ServerLevel server,List<BlockState> states){ropesRemoved=true;setChanged();}
    @Override protected void beforeBodyTransfer(LinearContraptionEntity owner,PulleyContraption structure)throws AssemblyException{
        var cabin=(ElevatorContraption)structure;cabin.maxContactY=worldPosition.getY()+cabin.getContactYOffset()-1;cabin.minContactY=cabin.maxContactY-travelRange;
        var column=ElevatorColumn.getOrCreate(level,cabin.getGlobalColumn());if(!column.claim(owner.getUUID(),worldPosition))throw new AssemblyException(net.minecraft.network.chat.Component.translatable("create.gui.assembly.exception.column_conflict"));pendingColumn=cabin.getGlobalColumn();
    }
    @Override protected void releaseUnusedReservation(LinearContraptionEntity owner){if(owner.getContraption()!=null)return;if(pendingColumn!=null){var column=ElevatorColumn.get(level,pendingColumn);if(column!=null)column.release(owner.getUUID());pendingColumn=null;}}
    private ElevatorColumn.ColumnCoords pendingColumn;
    @Override protected void onBodyAssembled(LinearContraptionEntity owner,PulleyContraption structure){
        var cabin=(ElevatorContraption)structure;pendingColumn=null;var column=ElevatorColumn.getOrCreate(level,cabin.getGlobalColumn());column.target(cabin.anchor.getY()+cabin.getContactYOffset());column.gatherAll();column.markDirty();lastTargetY=column.getTargetedYLevel();arrived=true;previousSpeed=0;namesVersion=-1;cabin.arrived=true;synchronize(cabin,column,owner,true);
    }
    private boolean ensureColumn(LinearContraptionEntity owner,ElevatorContraption cabin){var column=ElevatorColumn.getOrCreate(level,cabin.getGlobalColumn());column.adoptOriginalActive(owner,this);if(!column.claim(owner.getUUID(),worldPosition))return false;if(!column.isTargetAvailable())column.target(lastTargetY!=null?lastTargetY:cabin.clientYTarget);return true;}
    private int targetOffset(){
        if(level!=null&&level.isClientSide())return clientOffsetTarget;var owner=getMovedContraption();if(owner==null||!(owner.getContraption() instanceof ElevatorContraption cabin))return (int)offset;var target=cabin.getCurrentTargetY(level);if(target==null)return (int)offset;lastTargetY=target;return worldPosition.getY()-target+cabin.getContactYOffset()-1;
    }
    @Override public float getMovementSpeed(){
        int target=targetOffset();if(level!=null&&!level.isClientSide()&&target!=clientOffsetTarget){clientOffsetTarget=target;setChanged();sync();}
        float difference=target-offset;float converted=Math.clamp(getSpeed()/256f,-1.99f,1.99f),limit=Math.abs(converted),acceleration=Mth.lerp(Math.abs(converted),.0075f,.0175f),braking=(float)Math.sqrt(2*Math.abs(difference)*acceleration);
        float speed=Math.clamp(difference,-limit,limit);speed=Math.clamp(speed,previousSpeed-acceleration,previousSpeed+acceleration);speed=Math.clamp(speed,-braking,braking);arrived=Math.abs(difference)<.5f;return previousSpeed=speed;
    }
    @Override public void onSpeedChanged(float previous){setChanged();}
    @Override protected void onSequenceContextChanged(SequencedGearshiftBlockEntity.SequenceContext previous,SequencePhase previousPhase){boolean queued=assembleNextTick;super.onSequenceContextChanged(previous,previousPhase);assembleNextTick=queued;}
    public void clicked(){
        if(mirrorParent!=null){if(level!=null&&LoadedLevelAccess.blockEntity(level,mirrorParent) instanceof ElevatorPulleyBlockEntity parent&&getAttachedContraption()!=null)parent.clicked();return;}
        if(running||movedUuid!=null)disassemble();else requestAssembly();
    }
    @Override public boolean disassemble(){
        var owner=getMovedContraption();var cabin=owner!=null&&owner.getContraption() instanceof ElevatorContraption current?current:null;var id=movedUuid;
        if(owner!=null){if(!canDisassembleNow())return false;owner.setPoseBiasY(0);}
        if(!super.disassemble())return false;
        if(cabin!=null&&id!=null){var column=ElevatorColumn.get(level,cabin.getGlobalColumn());if(column!=null)column.release(id);}offset=-1;ropesRemoved=false;previousSpeed=0;arrived=true;pendingColumn=null;setChanged();sync();return true;
    }
    @Override protected boolean finishMirroring(ServerLevel server,BlockPos parent,UUID owner,int grid)throws AssemblyException{boolean complete=super.finishMirroring(server,parent,owner,grid);if(complete){offset=-1;ropesRemoved=false;setChanged();sync();}return complete;}
    @Override public void attach(ControlledContraptionEntity entity){super.attach(entity);if(entity instanceof LinearContraptionEntity owner&&owner==getMovedContraption()&&owner.getContraption() instanceof ElevatorContraption cabin){if(level!=null&&!level.isClientSide())ensureColumn(owner,cabin);else ElevatorNetworking.requestFloors(owner);}}
    private void triggerContact(ElevatorContraption cabin,int y){var column=ElevatorColumn.get(level,cabin.getGlobalColumn());if(column==null)return;var pos=column.contactAt(y);if(!LoadedLevelAccess.isLoaded(level,pos)||!(LoadedLevelAccess.blockEntity(level,pos) instanceof ElevatorContactBlockEntity contact)||!(LoadedLevelAccess.blockState(level,pos).getBlock() instanceof ElevatorContactBlock block))return;contact.activateBlock=true;contact.setChanged();block.scheduleActivation(level,pos);column.floorReached(contact.shortName);}
    private void synchronize(ElevatorContraption cabin,ElevatorColumn column,LinearContraptionEntity owner,boolean force){
        var target=cabin.getCurrentTargetY(level);int y=target==null?(int)Math.round(owner.getY())+cabin.getContactYOffset():target;cabin.clientYTarget=y;
        if(force||namesVersion!=column.namesListVersion()||lastSyncedTarget!=y||lastSyncedArrived!=cabin.arrived){namesVersion=column.namesListVersion();cabin.namesList=column.compileNamesList();lastSyncedTarget=y;lastSyncedArrived=cabin.arrived;ElevatorNetworking.sendFloors(owner);}
    }
    public static void tick(Level level,BlockPos pos,BlockState state,ElevatorPulleyBlockEntity elevator){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, elevator)) {
            if (!createTick.shouldTick()) return;

        boolean wasArrived=elevator.arrived;elevator.waitingForSpeedChange=false;var before=elevator.getMovedContraption();
        if(!level.isClientSide()&&before!=null&&before.getContraption() instanceof ElevatorContraption cabin&&!elevator.ensureColumn(before,cabin)){before.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);return;}
        if(before!=null)elevator.arrived=Math.abs(elevator.targetOffset()-elevator.offset)<.5f;PulleyBlockEntity.tick(level,pos,state,elevator);var owner=elevator.getMovedContraption();if(owner==null||!(owner.getContraption() instanceof ElevatorContraption cabin))return;
        if(level.isClientSide()){cabin.setClientYTarget(elevator.worldPosition.getY()-elevator.clientOffsetTarget+cabin.getContactYOffset()-1);cabin.arrived=wasArrived;ElevatorNetworking.requestFloors(owner);return;}
        cabin.arrived=wasArrived;
        if(owner.isStalled()||ContraptionAssemblyGraph.isFrozen(owner)||elevator.pendingDisassembly||elevator.recoveringAssembly||elevator.lastException!=null)return;
        var target=cabin.getCurrentTargetY(level);if(elevator.arrived&&target!=null){double difference=target-cabin.getContactYOffset()-owner.getY();if(Math.abs(difference)>1f/128)difference*=.25;owner.setPoseBiasY((float)(owner.getPoseBiasY()+difference));if(!wasArrived||level.getGameTime()%10==0)elevator.triggerContact(cabin,target);if(!wasArrived)level.playSound(null,owner.blockPosition(),AllSoundEvents.CONTRAPTION_DISASSEMBLE,SoundSource.BLOCKS,.75f,.8f);}
        var column=ElevatorColumn.get(level,cabin.getGlobalColumn());if(column!=null){elevator.synchronize(cabin,column,owner,false);}
    
        }
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putFloat("ElevatorPreviousSpeed",previousSpeed);out.putBoolean("ElevatorArrived",arrived);out.putInt("ClientTarget",clientOffsetTarget);if(lastTargetY!=null)out.putInt("ElevatorTargetY",lastTargetY);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);previousSpeed=in.getFloatOr("ElevatorPreviousSpeed",0);if(!Float.isFinite(previousSpeed))previousSpeed=0;arrived=in.getBooleanOr("ElevatorArrived",true);clientOffsetTarget=in.getIntOr("ClientTarget",0);lastTargetY=in.getInt("ElevatorTargetY").orElse(null);movementMode=MovementMode.MOVE_NEVER_PLACE;if(!running&&movedUuid==null&&in.read("Offset",com.mojang.serialization.Codec.FLOAT).isEmpty())offset=-1;}
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("contraption_actors");
    }
}
