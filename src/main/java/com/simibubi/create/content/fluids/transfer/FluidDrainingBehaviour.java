package com.simibubi.create.content.fluids.transfer;

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/** Upstream top-first, far-first draining, incremental search and connectivity revalidation. */
public final class FluidDrainingBehaviour extends FluidManipulationBehaviour {
    private Fluid fluid;
    private final PriorityQueue<BlockPosEntry> queue=new PriorityQueue<>(this::comparePositions);
    private Set<BlockPos> validationSet=new HashSet<>(),newValidationSet=new HashSet<>();
    private final List<BlockPosEntry> validationFrontier=new ArrayList<>();
    private final Set<BlockPos> validationVisited=new HashSet<>();
    private boolean valid;
    public FluidDrainingBehaviour(HosePulleyBlockEntity owner){super(owner);}
    public void tick(BlockPos root){
        valid=checkValid(root);
        if(!Objects.equals(root,rootPos)){rebuildContext(root);return;}
        if(counterpartActed){counterpartActed=false;softReset(root);return;}
        if(!valid)return;
        if(frontier.isEmpty())for(int count=0;count<searchedPerTick&&!queue.isEmpty();count++){
            var pos=queue.element().pos();if(!getWorld().isLoaded(pos))break;
            var at=state(pos).getFluidState();if(!at.isEmpty()&&fluid!=null&&still(at.getType()).isSame(fluid))break;
            // A failed transfer must not be the only way to discard stale cached candidates.
            queue.remove();
        }
        if(queue.isEmpty()&&frontier.isEmpty())rebuildContext(root);
        if(!frontier.isEmpty()){continueSearch();return;}
        if(!validationFrontier.isEmpty()){continueValidation();return;}
        if(revalidateIn>0)revalidateIn--;
        if(revalidateIn==0&&!queue.isEmpty())revalidate(root);
    }
    private boolean checkValid(BlockPos root){
        var current=root;
        for(int timeout=1000;timeout>0&&!current.equals(blockEntity.getBlockPos());timeout--){
            if(!getWorld().isLoaded(current)||getWorld().isOutsideBuildHeight(current))return false;
            var type=typeAt(state(current),current);
            if(type==FluidBlockType.SOURCE)return true;
            if(type!=FluidBlockType.FLOWING)return false;
            for(var side:Direction.values()){
                var next=current.relative(side);
                if(getWorld().isLoaded(next)&&typeAt(state(next),next)==FluidBlockType.SOURCE)return true;
            }current=current.above();
        }return false;
    }
    private enum FluidBlockType {NONE,SOURCE,FLOWING}
    private FluidBlockType typeAt(BlockState state,BlockPos pos){
        if(state.hasProperty(BlockStateProperties.WATERLOGGED)&&state.getValue(BlockStateProperties.WATERLOGGED))return FluidBlockType.SOURCE;
        if(state.getBlock() instanceof LiquidBlock)return state.getValue(LiquidBlock.LEVEL)==0?FluidBlockType.SOURCE:FluidBlockType.FLOWING;
        if(!state.getFluidState().isEmpty()&&state.getCollisionShape(getWorld(),pos,CollisionContext.empty()).isEmpty())return FluidBlockType.SOURCE;
        return FluidBlockType.NONE;
    }
    public FluidStack getDrainableFluid(BlockPos root){
        if(!valid||fluid==null||!Objects.equals(root,rootPos)||!frontier.isEmpty()||queue.isEmpty()||!getWorld().isLoaded(queue.element().pos())||!checkValid(root))return FluidStack.EMPTY;
        return new FluidStack(FluidVariant.of(still(fluid)),FluidConstants.BUCKET);
    }
    public boolean pullNext(BlockPos root,TransactionContext tx){
        if(getDrainableFluid(root).isEmpty()||counterpartActed)return false;
        enlist(tx);include(root);
        if(infinite){edit(root,state(root),fluid,true,false,false,tx);boolean lava=still(fluid).isSame(Fluids.LAVA);com.simibubi.create.content.fluids.WorldFluidEdit.onAcceptedApply(getWorld(),root,()->{pulley().sourceAdvancements.award("hose_pulley");if(lava)pulley().sourceAdvancements.award("hose_pulley_lava");},true,tx);return true;}
        while(!queue.isEmpty()){
            var pos=queue.element().pos();if(!getWorld().isLoaded(pos))return false;
            var old=state(pos);var replacement=old;Fluid found=Fluids.EMPTY;
            if(old.hasProperty(BlockStateProperties.WATERLOGGED)&&old.getValue(BlockStateProperties.WATERLOGGED)){
                found=Fluids.WATER;replacement=old.setValue(BlockStateProperties.WATERLOGGED,false);
            }else if(old.getBlock() instanceof LiquidBlock){
                replacement=Blocks.AIR.defaultBlockState();
                if(old.getValue(LiquidBlock.LEVEL)==0)found=still(old.getFluidState().getType());
                else{
                    include(pos);edit(pos,replacement,null,true,false,false,tx);queue.remove();
                    if(queue.isEmpty()){valid=checkValid(root);reset();}continue;
                }
            }else if(!old.getFluidState().isEmpty()&&old.getCollisionShape(getWorld(),pos,CollisionContext.empty()).isEmpty()){
                found=still(old.getFluidState().getType());replacement=Blocks.AIR.defaultBlockState();
            }
            if(!fluid.isSame(found)){
                queue.remove();if(queue.isEmpty()){valid=checkValid(root);reset();}continue;
            }
            edit(pos,replacement,fluid,true,false,false,tx);com.simibubi.create.content.fluids.WorldFluidEdit.onAcceptedApply(getWorld(),pos,()->pulley().sourceAdvancements.award("hose_pulley"),true,tx);include(pos);
            var above=pos.above();var aboveState=state(above);
            if(aboveState.getFluidState().isEmpty()&&!aboveState.canSurvive(getWorld(),above))edit(above,Blocks.AIR.defaultBlockState(),null,true,false,false,tx);
            queue.remove();
            if(queue.isEmpty()){valid=checkValid(root);reset();}else if(!validationSet.contains(pos))reset();
            return true;
        }return false;
    }
    private void rebuildContext(BlockPos root){reset();rootPos=root.immutable();include(root);if(valid)frontier.add(new BlockPosEntry(root,0));}
    private void softReset(BlockPos root){
        queue.clear();validationSet.clear();newValidationSet.clear();validationFrontier.clear();validationVisited.clear();visited.clear();infinite=false;setValidationTimer();frontier.clear();frontier.add(new BlockPosEntry(root,0));pulley().fluidChanged();
    }
    private void revalidate(BlockPos root){validationFrontier.clear();validationVisited.clear();newValidationSet.clear();validationFrontier.add(new BlockPosEntry(root,0));setValidationTimer();}
    private void continueSearch(){
        try{fluid=search(fluid,frontier,visited,(pos,distance)->{queue.add(new BlockPosEntry(pos,distance));validationSet.add(pos);},false);}
        catch(ChunkNotLoadedException missing){frontier.clear();visited.clear();}
        if(visited.size()>=maxBlocks()&&canDrainInfinitely(fluid)&&!queue.isEmpty()){
            infinite=true;var first=queue.element();frontier.clear();visited.clear();queue.clear();queue.add(new BlockPosEntry(first.pos(),0));pulley().fluidChanged();return;
        }
        if(frontier.isEmpty()){visited.clear();pulley().fluidChanged();}
    }
    private void continueValidation(){
        try{search(fluid,validationFrontier,validationVisited,(pos,distance)->newValidationSet.add(pos),false);}
        catch(ChunkNotLoadedException missing){validationFrontier.clear();validationVisited.clear();setLongValidationTimer();return;}
        if(validationVisited.size()>=maxBlocks()&&canDrainInfinitely(fluid)){
            if(!infinite)reset();validationFrontier.clear();setLongValidationTimer();return;
        }
        if(!validationFrontier.isEmpty())return;
        if(infinite){reset();return;}
        validationSet=newValidationSet;newValidationSet=new HashSet<>();validationVisited.clear();
    }
    @Override public void reset(){super.reset();fluid=null;rootPos=null;queue.clear();validationSet.clear();newValidationSet.clear();validationFrontier.clear();validationVisited.clear();}
    private record Extra(Fluid fluid,List<BlockPosEntry> queue,Set<BlockPos> validation,Set<BlockPos> newValidation,List<BlockPosEntry> frontier,Set<BlockPos> visited,boolean valid){}
    @Override protected Object snapshotExtra(){return new Extra(fluid,List.copyOf(queue),Set.copyOf(validationSet),Set.copyOf(newValidationSet),List.copyOf(validationFrontier),Set.copyOf(validationVisited),valid);}
    @Override protected void restoreExtra(Object snapshot){var s=(Extra)snapshot;fluid=s.fluid;queue.clear();queue.addAll(s.queue);validationSet=new HashSet<>(s.validation);newValidationSet=new HashSet<>(s.newValidation);validationFrontier.clear();validationFrontier.addAll(s.frontier);validationVisited.clear();validationVisited.addAll(s.visited);valid=s.valid;}
    @Override public void load(ValueInput in){super.load(in);fluid=null;queue.clear();validationSet.clear();newValidationSet.clear();validationFrontier.clear();validationVisited.clear();if(rootPos!=null)frontier.add(new BlockPosEntry(rootPos,0));}
    public static final com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<FluidDrainingBehaviour> TYPE=new com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<>();
    @Override public com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<?> getType(){return TYPE;}
    @Override public void tick(){super.tick();if(getWorld()!=null&&!getWorld().isClientSide()&&!pulley().isMoving())tick(pulley().rootPos());}
    @Override public void write(ValueOutput output){save(output.child("Draining"));}
    @Override public void read(ValueInput input){load(input.childOrEmpty("Draining"));}

}
