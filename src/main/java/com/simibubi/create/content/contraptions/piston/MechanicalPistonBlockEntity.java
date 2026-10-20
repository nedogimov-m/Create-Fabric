package com.simibubi.create.content.contraptions.piston;

import com.simibubi.create.*;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import static com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.*;

/** A reservation is accepted by the native level before the first source pole or cargo is removed. */
public class MechanicalPistonBlockEntity extends LinearActuatorBlockEntity {
    protected int extensionLength;
    public MechanicalPistonBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.MECHANICAL_PISTON,pos,state);}
    protected LinearContraptionEntity createReservation(ServerLevel level){return new LinearContraptionEntity(AllEntityTypes.LINEAR_CONTRAPTION,level);}
    @Override protected boolean matches(LinearContraptionEntity owner){return owner.getContraption() instanceof PistonContraption piston&&piston.getControllerPos().equals(worldPosition)&&worldPosition.equals(owner.getControllerPos())&&piston.getOrientation()==getBlockState().getValue(FACING);}
    private float rawSpeed(){var face=getBlockState().getValue(FACING);int sign=face.getAxisDirection().getStep()*(face.getAxis()==Direction.Axis.Z?-1:1);return Math.clamp(getSpeed()/512f,-.49f,.49f)*-sign;}
    @Override public float getMovementSpeed(){float value=rawSpeed();if(level!=null&&level.isClientSide())value*=com.simibubi.create.foundation.utility.ServerSpeedProvider.get();if(running)value=Math.clamp(value,-offset,extensionLength-offset);return sequencedOffsetLimit<0?value:(float)Math.clamp(value,-sequencedOffsetLimit,sequencedOffsetLimit);}
    @Override protected int getExtensionRange(){return extensionLength;}
    @Override protected int getInitialOffset(){return getMovedContraption()!=null&&getMovedContraption().getContraption() instanceof PistonContraption piston?piston.initialExtensionProgress:0;}
    @Override protected Vec3 toMotionVector(float amount){return getBlockState().getValue(FACING).getUnitVec3().scale(amount);}
    @Override protected Vec3 toPosition(float amount){return Vec3.atLowerCornerOf(worldPosition.relative(getBlockState().getValue(FACING))).add(toMotionVector(amount));}
    private void bind(LinearContraptionEntity owner,PistonContraption structure){
        if(owner.getContraption()!=structure)owner.setContraption(structure);movedContraption=owner;movedUuid=owner.getUUID();running=true;owner.setProgress(offset);
        ContraptionHandler.addSpawnedContraptionsToCollisionList(owner);
    }
    @Override public boolean assemble()throws AssemblyException{
        if(!(level instanceof ServerLevel server)||running||movedUuid!=null||transferring)return false;
        transferring=true;LinearContraptionEntity reserved=null;PistonContraption structure=null;
        try{
            structure=new PistonContraption(getBlockState().getValue(FACING),rawSpeed()<0);if(!structure.assemble(server,worldPosition))return false;
            float initial=structure.initialExtensionProgress;float trial=initial+Math.signum(rawSpeed())*.5f;
            if(rawSpeed()==0||trial<=0||trial>=structure.extensionLength)return false;
            var direction=Direction.getApproximateNearest(toMotionVector(rawSpeed()));var start=structure.anchor.relative(structure.getOrientation(),structure.initialExtensionProgress);
            if(ContraptionTerrainCollision.isCollidingWithWorld(server,structure,start.relative(direction),direction))return false;
            structure.validateSourceBlocks(server,structure.removalOffset());ContraptionDataPacket.split(0,java.util.UUID.randomUUID(),true,structure.writeNBT(server.registryAccess(),true));
            reserved=createReservation(server);reserved.setControllerPos(worldPosition);reserved.setProgress(initial);reserved.setPos(Vec3.atLowerCornerOf(start));
            if(!server.addFreshEntity(reserved)||reserved.isRemoved()||server.getEntity(reserved.getUUID())!=reserved)throw AssemblyException.structureChanged(worldPosition);
            var accepted=reserved;var captured=structure;
            var result=ContraptionAssemblyGraph.assemble(server,java.util.List.of(new ContraptionAssemblyGraph.Root(accepted,captured,captured.removalOffset())),complete->{
                extensionLength=captured.extensionLength;offset=initial;bind(accepted,captured);lastException=null;retryTicks=0;recoveringAssembly=!complete;pendingDisassembly=false;
            });
            if(!result.complete())throw result.failure();
            if(captured.containsBlockBreakers())award(AllAdvancements.CONTRAPTION_ACTORS);
            server.playSound(null,worldPosition,AllSoundEvents.CONTRAPTION_ASSEMBLE,SoundSource.BLOCKS,1,1);setChanged();sync();return true;
        }catch(AssemblyException failure){
            if(structure!=null&&structure.phase()==Contraption.Phase.REMOVED&&reserved!=null){
                extensionLength=structure.extensionLength;offset=structure.initialExtensionProgress;
                bind(reserved,structure);recoveringAssembly=true;failure(failure,false);
            }throw failure;
        }finally{if(reserved!=null&&reserved.getContraption()==null)reserved.discard();transferring=false;}
    }
    @Override public boolean canDisassembleNow(){
        if(transferring)return false;var owner=getMovedContraption();if(owner==null)return movedUuid==null;
        try{ContraptionAssemblyGraph.preflightTree(owner);return true;}
        catch(AssemblyException unavailable){return false;}
    }
    @Override public boolean disassemble(){
        if(!(level instanceof ServerLevel server)||transferring)return false;
        var owner=getMovedContraption();if(owner==null){pendingDisassembly=movedUuid!=null;setChanged();return movedUuid==null&&!running;}
        pendingDisassembly=true;setChanged();
        if(!canDisassembleNow())return false;
        transferring=true;
        try{
            offset=getGridOffset(offset);owner.setProgress(offset);owner.setDeltaMovement(Vec3.ZERO);
            var state=LoadedLevelAccess.blockState(server,worldPosition);
            if(isPiston(state))LoadedLevelAccess.setBlockQuiet(server,worldPosition,state.setValue(STATE,PistonState.EXTENDED));
            if(!owner.disassemble()){
                if(isPiston(LoadedLevelAccess.blockState(server,worldPosition)))LoadedLevelAccess.setBlockQuiet(server,worldPosition,state.setValue(STATE,PistonState.MOVING));
                return false;
            }
            movedContraption=null;movedUuid=null;running=waitingForSpeedChange=assembleNextTick=pendingDisassembly=false;retryTicks=0;clearSequenceLimit();
            server.playSound(null,worldPosition,AllSoundEvents.CONTRAPTION_DISASSEMBLE,SoundSource.BLOCKS,1,1);setChanged();sync();return true;
        }finally{transferring=false;}
    }
    public void onLengthBroken(){offset=0;setChanged();sync();}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("ExtensionLength",extensionLength);}
    @Override protected void read(ValueInput in, boolean clientPacket){extensionLength=Math.max(0,in.getIntOr("ExtensionLength",0));super.read(in,clientPacket);offset=Math.clamp(offset,0,extensionLength);
        
    }
}
