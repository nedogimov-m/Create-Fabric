package com.simibubi.create.content.decoration.bracket;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import java.util.function.Predicate;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;

/** One actual source bracket state, persisted with the host and resolved to the cached component. */
public final class BracketedBlockEntityBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<BracketedBlockEntityBehaviour> TYPE=new BehaviourType<BracketedBlockEntityBehaviour>().withFallback(be ->
        be instanceof KineticBlockEntity kinetic?kinetic.bracketBehaviour():be instanceof FluidPipeBlockEntity pipe?pipe.bracketBehaviour():null);
    private final Predicate<BlockState> predicate;
    private BlockState bracket;
    public BracketedBlockEntityBehaviour(BlockEntity owner,Predicate<BlockState> predicate){super(owner);this.predicate=predicate;}
    @Override public BehaviourType<?> getType(){return TYPE;}
    public static boolean eligiblePipe(BlockState state){return (state.is(AllBlocks.FLUID_PIPE)||state.is(AllBlocks.GLASS_FLUID_PIPE))&&FluidPropagator.getStraightPipeAxis(state)!=null;}
    public boolean canHaveBracket(){return predicate.test(blockEntity.getBlockState());}
    public boolean isBracketPresent(){return bracket!=null;}
    public BlockState getBracket(){return bracket;}
    public boolean isBracketValid(BlockState state){return state!=null&&state.getBlock() instanceof BracketBlock;}
    public void applyBracket(BlockState state){if(!isBracketValid(state))throw new IllegalArgumentException("Invalid bracket state");bracket=state;changed();notifyNeighbors();}
    public BlockState removeBracket(boolean replacing){
        var previous=bracket;if(previous==null)return null;bracket=null;
        var level=getWorld();if(level!=null&&!level.isClientSide())level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK,getPos(),Block.getId(previous));
        changed();if(!replacing)notifyNeighbors();return previous;
    }
    private void notifyNeighbors(){var level=getWorld();if(level!=null&&!level.isClientSide())blockEntity.getBlockState().updateNeighbourShapes(level,getPos(),Block.UPDATE_ALL);}
    public void dropBracket(){var level=getWorld();if(level==null||level.isClientSide())return;var removed=removeBracket(true);if(removed!=null)Block.popResource(level,getPos(),new ItemStack(removed.getBlock()));}
    public void transformBracket(StructureTransform transform){if(bracket!=null){bracket=transform.apply(bracket);changed();}}
    @Override public ItemRequirement getRequiredItems(){return bracket==null?ItemRequirement.NONE:ItemRequirement.of(bracket,null);}
    @Override public boolean isSafeNBT(){return true;}
    @Override public void write(ValueOutput output){if(isBracketValid(bracket))output.store("Bracket",BlockState.CODEC,bracket);}
    @Override public void read(ValueInput input){bracket=input.read("Bracket",BlockState.CODEC).filter(this::isBracketValid).orElse(null);}
}
