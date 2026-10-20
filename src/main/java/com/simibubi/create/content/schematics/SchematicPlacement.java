package com.simibubi.create.content.schematics;

import com.simibubi.create.*;
import com.simibubi.create.api.schematic.state.*;
import com.simibubi.create.foundation.blockEntity.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.TagValueInput;

/** Actual native schematic placement. A refused or unloaded target remains a paid pending launch. */
public final class SchematicPlacement {
    public interface Publication {
        /** Generic callers retain their existing policy; owned actors can refuse stale callbacks. */
        default boolean mayPublish(BlockState actual){return true;}
        default void basePrepared(BlockState actual){}
        void baseAccepted(BlockState actual);
        void dataAccepted();
        default void partialDataAccepted(BlockEntity actual){}
    }
    private static final Publication UNOWNED=new Publication(){public void baseAccepted(BlockState actual){}public void dataAccepted(){}};

    /** The caller's cached receiver must survive both materialization and fresh protection callbacks. */
    private static boolean mayPublishTo(Level level,BlockPos target,BlockState actual,BlockEntity receiver,Publication publication){return com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,target)==receiver&&publication.mayPublish(actual)&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,target)==receiver;}
    public static boolean placeBlock(Level level,BlockState requested,BlockPos target,ItemStack stack,CompoundTag saved){return placeBlock(level,requested,target,stack,saved,UNOWNED);}
    public static boolean placeBlock(Level level,BlockState requested,BlockPos target,ItemStack stack,CompoundTag saved,Publication publication){
        if(!(level instanceof ServerLevel server)||!LoadedLevelAccess.isLoaded(level,target)||!level.isInValidBounds(target)||!level.getWorldBorder().isWithinBounds(target)||!SchematicWorldAccess.readyForPlacement(level,requested,target))return false;
        var previous=LoadedLevelAccess.blockState(level,target);var existing=com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,target);if(previous.getDestroySpeed(level,target)==-1&&previous!=requested)return false;
        if(existing instanceof com.simibubi.create.content.trains.track.TrackBlockEntity track&&(track.boundLocation!=null||track.getPortalBinding()!=null))return false;
        var state=requested;var filter=SchematicStateFilterRegistry.REGISTRY.get(state);if(filter!=null)state=filter.filterStates(existing,state);else if(state.getBlock() instanceof SchematicStateFilter sourceFilter)state=sourceFilter.filterStates(existing,state);
        if(state.hasProperty(BlockStateProperties.EXTENDED))state=state.setValue(BlockStateProperties.EXTENDED,false);if(state.hasProperty(BlockStateProperties.WATERLOGGED))state=state.setValue(BlockStateProperties.WATERLOGGED,false);
        if(state.is(Blocks.COMPOSTER))state=Blocks.COMPOSTER.defaultBlockState();else if(state.is(BlockTags.CAULDRONS))state=Blocks.CAULDRON.defaultBlockState();
        BlockPos other=null;if(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)&&state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER)other=target.above();if(state.hasProperty(BlockStateProperties.BED_PART)&&state.getValue(BlockStateProperties.BED_PART)==BedPart.FOOT)other=target.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if(other!=null&&(!LoadedLevelAccess.isLoaded(level,other)||!level.isInValidBounds(other)||!level.getWorldBorder().isWithinBounds(other)||LoadedLevelAccess.blockState(level,other).getDestroySpeed(level,other)==-1))return false;
        if(server.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES,target)&&state.getFluidState().is(FluidTags.WATER)){if(!publication.mayPublish(state))return false;level.playSound(null,target,net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.BLOCKS,.5f,2.6f+(level.getRandom().nextFloat()-level.getRandom().nextFloat())*.8f);server.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,target.getX()+.5,target.getY()+.5,target.getZ()+.5,8,.5,.5,.5,0);Block.dropResources(state,level,target);return true;}
        var data=saved==null?null:saved.copy();
        if(existing instanceof com.simibubi.create.content.trains.track.TrackBlockEntity track&&data!=null&&state.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock){
            var detached=new com.simibubi.create.content.trains.track.TrackBlockEntity(target,state);detached.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data));
            // Source merge keeps the actual existing endpoint state/owner and every unrelated connection.
            if(!mayPublishTo(level,target,state,existing,publication))return false;boolean merged=com.simibubi.create.content.trains.track.TrackSchematicSupport.merge(track,detached);if(merged){publication.baseAccepted(previous);publication.dataAccepted();}return merged;
        }
        if(existing instanceof IMergeableBE merge&&data!=null&&state.getBlock() instanceof EntityBlock factory){var loaded=factory.newBlockEntity(target,state);if(loaded!=null&&loaded.getType()==existing.getType()){
            loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data));
            if(!mayPublishTo(level,target,state,existing,publication))return false;
            try{merge.accept(loaded);}finally{if(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,target)==existing)publication.partialDataAccepted(existing);}
            existing.setChanged();publication.baseAccepted(previous);publication.dataAccepted();return true;
        }}
        if(previous!=state){
            if(!publication.mayPublish(state))return false;
            publication.basePrepared(state);
            if(state.getBlock() instanceof BaseRailBlock){var chunk=server.getChunkSource().getChunkNow(target.getX()>>4,target.getZ()>>4);if(chunk==null)return false;var before=chunk.setBlockState(target,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_MOVE_BY_PISTON);if(chunk.getBlockState(target)!=state)return false;if(before!=null){level.setBlocksDirty(target,before,state);level.sendBlockUpdated(target,before,state,Block.UPDATE_CLIENTS);level.updatePOIOnBlockStateChange(target,before,state);}}
            else try{if(!level.setBlock(target,state,state.is(AllBlocks.BELT)?Block.UPDATE_CLIENTS:Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)&&LoadedLevelAccess.blockState(level,target)!=state)return false;}finally{if(LoadedLevelAccess.blockState(level,target)==state)publication.baseAccepted(state);}
        }
        if(LoadedLevelAccess.blockState(level,target)!=state)return false;
        publication.baseAccepted(state);
        var entity=com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,target);
        if(data!=null&&state.hasBlockEntity()){
            if(entity==null)return false;
            if(entity instanceof com.simibubi.create.content.trains.track.TrackBlockEntity track){
                var detached=new com.simibubi.create.content.trains.track.TrackBlockEntity(target,state);detached.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data));if(!mayPublishTo(level,target,state,entity,publication))return false;var merged=com.simibubi.create.content.trains.track.TrackSchematicSupport.merge(track,detached);if(merged)publication.dataAccepted();return merged;
            }data.putInt("x",target.getX());data.putInt("y",target.getY());data.putInt("z",target.getZ());if(entity instanceof IMultiBlockEntityContainer container&&!container.isController())data.store("Controller",BlockPos.CODEC,container.getController());
            if(!mayPublishTo(level,target,state,entity,publication))return false;
            try{entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data));}finally{if(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,target)==entity)publication.partialDataAccepted(entity);}
            entity.setChanged();publication.dataAccepted();if(entity instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity)com.simibubi.create.content.kinetics.KineticNetworks.markDirty(server);level.sendBlockUpdated(target,state,state,Block.UPDATE_CLIENTS);
        }
        if(data==null||!state.hasBlockEntity())publication.dataAccepted();
        if(!publication.mayPublish(state))return false;
        try{state.getBlock().setPlacedBy(level,target,state,null,stack);}catch(RuntimeException failure){Create.LOGGER.warn("Native schematic block placement hook refused {} at {}",state,target,failure);return false;}
        return LoadedLevelAccess.blockState(level,target)==state;
    }
    private SchematicPlacement(){}
}
