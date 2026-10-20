package com.simibubi.create.content.contraptions.gantry;

import com.simibubi.create.*;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gantry.*;
import com.simibubi.create.foundation.utility.*;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** The actual BE joins its owned carrier after accepted graph reservation and complete source preflight. */
public class GantryCarriageBlockEntity extends KineticBlockEntity {
    private boolean assembleNextTick,initialized,assembling;
    private int retryTicks;
    private AssemblyException lastException;
    private CompoundTag pendingError;
    public GantryCarriageBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.GANTRY_PINION,pos,state);}
    public void queueAssembly(){assembleNextTick=true;retryTicks=0;setChanged();}
    public AssemblyException getLastAssemblyException(){return lastException;}
    public static float getGantryPinionModifier(Direction shaft,Direction pinion){int sign=shaft.getAxisDirection().getStep();return switch(shaft.getAxis()){case Y->pinion==Direction.NORTH||pinion==Direction.EAST?-sign:sign;case X->pinion==Direction.DOWN||pinion==Direction.SOUTH?-sign:sign;case Z->pinion==Direction.UP||pinion==Direction.WEST?-sign:sign;};}
    private GantryShaftBlockEntity shaft(){
        if(level==null||!(getBlockState().getBlock() instanceof GantryCarriageBlock))return null;var pos=worldPosition.relative(getBlockState().getValue(GantryCarriageBlock.FACING).getOpposite());if(!LoadedLevelAccess.isLoaded(level,pos)||!LoadedEntityAccess.isReady(level,pos))return null;
        return LoadedLevelAccess.blockEntity(level,pos) instanceof GantryShaftBlockEntity shaft&&GantryCarriageBlock.getValidGantryShaftAxis(getBlockState())==shaft.getBlockState().getValue(GantryShaftBlock.FACING).getAxis()?shaft:null;
    }
    public void checkValidGantryShaft(){if(level==null||level.isClientSide())return;var support=worldPosition.relative(getBlockState().getValue(GantryCarriageBlock.FACING).getOpposite());if(!LoadedLevelAccess.isLoaded(level,support)||!LoadedEntityAccess.isReady(level,support)){queueAssembly();return;}var shaft=shaft();if(shaft!=null&&shaft.canAssembleOn())queueAssembly();}
    protected GantryContraptionEntity createReservation(ServerLevel level){return new GantryContraptionEntity(AllEntityTypes.GANTRY_CONTRAPTION,level);}
    public boolean assemble()throws AssemblyException{
        if(!(level instanceof ServerLevel server)||assembling||isRemoved())return false;var support=worldPosition.relative(getBlockState().getValue(GantryCarriageBlock.FACING).getOpposite());if(!LoadedLevelAccess.isLoaded(server,support)||!LoadedEntityAccess.isReady(server,support))throw AssemblyException.entitiesNotReady(support);var shaft=shaft();if(shaft==null||!shaft.canAssembleOn())return false;assembling=true;GantryContraptionEntity reserved=null;
        try{
            var structure=new GantryContraption(getBlockState().getValue(GantryCarriageBlock.FACING));if(!structure.assemble(server,worldPosition))return false;
            var movement=shaft.getBlockState().getValue(GantryShaftBlock.FACING);if(shaft.getPinionMovementSpeed()<0)movement=movement.getOpposite();
            if(ContraptionTerrainCollision.isCollidingWithWorld(server,structure,worldPosition.relative(movement),movement))return false;
            structure.validateSourceBlocks(server,BlockPos.ZERO);ContraptionDataPacket.split(0,java.util.UUID.randomUUID(),true,structure.writeNBT(server.registryAccess(),true));
            reserved=createReservation(server);reserved.initializeDrive(shaft);reserved.setPos(Vec3.atLowerCornerOf(worldPosition));
            if(!server.addFreshEntity(reserved)||reserved.isRemoved()||server.getEntity(reserved.getUUID())!=reserved)throw AssemblyException.structureChanged(worldPosition);
            var accepted=reserved;var result=ContraptionAssemblyGraph.assemble(server,List.of(new ContraptionAssemblyGraph.Root(accepted,structure)),complete->{ContraptionHandler.addSpawnedContraptionsToCollisionList(accepted);if(!complete)accepted.beginAssemblyRecovery();accepted.sendMotion();});
            if(!result.complete())throw result.failure();
            if(structure.containsBlockBreakers())award(AllAdvancements.CONTRAPTION_ACTORS);
            lastException=null;retryTicks=0;server.playSound(null,worldPosition,AllSoundEvents.CONTRAPTION_ASSEMBLE,SoundSource.BLOCKS,1,1);return true;
        }finally{if(reserved!=null&&reserved.getContraption()==null)reserved.discard();assembling=false;}
    }
    public static void tick(Level level,BlockPos pos,BlockState state,GantryCarriageBlockEntity carriage){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, carriage)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,carriage);if(level.isClientSide())return;
        // Source initialize checks survival only. Placement/indirect-neighbor, actual rack
        // speed changes and power release carry the real assembly intent; loading a restored
        // carriage must not manufacture that intent after an explicit graph stop.
        if(!carriage.initialized){carriage.initialized=true;if(!state.canSurvive(level,pos)){level.destroyBlock(pos,true);return;}}
        if(carriage.retryTicks>0&&--carriage.retryTicks==0)carriage.assembleNextTick=true;
        if(!carriage.assembleNextTick)return;carriage.assembleNextTick=false;
        try{carriage.assemble();}catch(AssemblyException failure){carriage.lastException=failure;if(failure.canRetry())carriage.retryTicks=5;carriage.setChanged();carriage.sync();}
    
        }
    }
    @Override public void setLevel(Level level){super.setLevel(level);if(pendingError!=null){lastException=AssemblyException.read(pendingError,level.registryAccess());pendingError=null;}}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);var tag=pendingError==null?new CompoundTag():pendingError.copy();if(level!=null)AssemblyException.write(tag,level.registryAccess(),lastException);out.store("AssemblyError",CompoundTag.CODEC,tag);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);pendingError=in.read("AssemblyError",CompoundTag.CODEC).orElse(new CompoundTag()).copy();initialized=assembleNextTick=false;retryTicks=0;if(level!=null){lastException=AssemblyException.read(pendingError,level.registryAccess());pendingError=null;}}
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("contraption_actors");
    }
}
