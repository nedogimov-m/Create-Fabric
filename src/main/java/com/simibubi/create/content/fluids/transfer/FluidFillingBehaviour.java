package com.simibubi.create.content.fluids.transfer;

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.*;
import net.minecraft.tags.*;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/** Upstream bottom-first placement queue, downward search and periodic infinite-pool check. */
public final class FluidFillingBehaviour extends FluidManipulationBehaviour {
    private final PriorityQueue<BlockPosEntry> queue=new PriorityQueue<>((first,second)->-comparePositions(first,second));
    private final List<BlockPosEntry> infinityFrontier=new ArrayList<>();
    private final Set<BlockPos> infinityVisited=new HashSet<>();
    public FluidFillingBehaviour(HosePulleyBlockEntity owner){super(owner);revalidateIn=1;}
    public void tick(BlockPos root,Fluid buffered){
        if(!Objects.equals(rootPos,root)){reset();rootPos=root.immutable();queue.add(new BlockPosEntry(root,0));include(root);}
        if(counterpartActed){counterpartActed=false;softReset(root);}
        if(!infinityFrontier.isEmpty()){
            var fluid=state(root).getFluidState().getType();if(fluid!=Fluids.EMPTY)continueValidation(fluid);else infinityFrontier.clear();
        }
        if(revalidateIn>0)revalidateIn--;
        if(revalidateIn==0){visited.clear();infinityFrontier.clear();infinityVisited.clear();infinityFrontier.add(new BlockPosEntry(root,0));softReset(root);}
        // Exploration is a cache, so it must make bounded progress even when a full buffer accepts no transfer.
        // World changes and the removal of a fillable candidate remain inside the caller's transaction.
        if(buffered!=Fluids.EMPTY&&(!infinite||fillInfinite()))advanceFilledQueue(buffered);
    }
    private void advanceFilledQueue(Fluid fluid){
        for(int count=0;count<searchedPerTick&&!queue.isEmpty();count++){
            var entry=queue.element();var pos=entry.pos();
            if(visited.contains(pos)){queue.remove();continue;}
            if(!getWorld().isLoaded(pos))return;
            var space=spaceAt(pos,fluid);if(space==SpaceType.FILLABLE)return;
            visited.add(pos);queue.remove();
            if(visited.size()>=maxBlocks()&&maxBlocks()!=-1&&!fillInfinite()){infinite=true;visited.clear();queue.clear();pulley().fluidChanged();return;}
            if(space!=SpaceType.BLOCKING)addNeighbours(entry,fluid);
        }
    }
    private void addNeighbours(BlockPosEntry entry,Fluid fluid){
        long rangeSq=(long)maxRange()*maxRange();
        for(var side:Direction.values()){
            if(side==Direction.UP)continue;var next=entry.pos().relative(side);
            if(visited.contains(next)||next.distSqr(rootPos)>rangeSq||getWorld().isOutsideBuildHeight(next)||!getWorld().isLoaded(next))continue;
            if(spaceAt(next,fluid)!=SpaceType.BLOCKING)queue.add(new BlockPosEntry(next,entry.distance()+1));
        }
    }
    private void continueValidation(Fluid fluid){
        try{search(still(fluid),infinityFrontier,infinityVisited,(pos,distance)->infinityFrontier.add(new BlockPosEntry(pos,distance)),true);}
        catch(ChunkNotLoadedException missing){infinityFrontier.clear();infinityVisited.clear();setLongValidationTimer();return;}
        if(infinityVisited.size()>=maxBlocks()&&maxBlocks()!=-1&&!fillInfinite()){
            if(!infinite){reset();infinite=true;pulley().fluidChanged();}infinityFrontier.clear();setLongValidationTimer();return;
        }
        if(!infinityFrontier.isEmpty())return;
        if(infinite){reset();return;}infinityVisited.clear();
    }
    public boolean tryDeposit(Fluid fluid,BlockPos root,TransactionContext tx){
        if(!Objects.equals(root,rootPos)||counterpartActed||!getWorld().isLoaded(root))return false;
        enlist(tx);include(root);
        boolean evaporate=fluid.is(FluidTags.WATER)&&getWorld().environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES,root);
        boolean place=pulley().settings().placeSources();
        if((!fillInfinite()&&infinite)||evaporate||!place){
            if(!state(root).getFluidState().getType().isSame(fluid)&&!evaporate&&place)return false;
            edit(root,state(root),fluid,false,false,evaporate,tx);
            if(!evaporate&&!place)com.simibubi.create.content.fluids.WorldFluidEdit.onAcceptedApply(getWorld(),root,()->pulley().sourceAdvancements.award("hose_pulley"),false,tx);return true;
        }
        boolean success=false;
        for(int count=0;!success&&!queue.isEmpty()&&count<searchedPerTick;count++){
            var entry=queue.element();var pos=entry.pos();
            if(visited.contains(pos)){queue.remove();continue;}
            if(!getWorld().isLoaded(pos))return false;
            visited.add(pos);
            if(visited.size()>=maxBlocks()&&maxBlocks()!=-1){
                infinite=true;if(!fillInfinite()){visited.clear();queue.clear();return false;}
            }
            var space=spaceAt(pos,fluid);
            // A block can change after it was queued; discard it rather than repeatedly checking it forever.
            if(space==SpaceType.BLOCKING){queue.remove();continue;}
            if(space==SpaceType.FILLABLE){
                var old=state(pos);var replacement=still(fluid).defaultFluidState().createLegacyBlock();boolean waterlog=old.hasProperty(BlockStateProperties.WATERLOGGED)&&fluid.isSame(Fluids.WATER);
                if(waterlog){replacement=old.setValue(BlockStateProperties.WATERLOGGED,true);if(replacement.hasProperty(BlockStateProperties.LIT))replacement=replacement.setValue(BlockStateProperties.LIT,false);}
                edit(pos,replacement,fluid,false,!waterlog,false,tx);com.simibubi.create.content.fluids.WorldFluidEdit.onAcceptedApply(getWorld(),pos,()->pulley().sourceAdvancements.award("hose_pulley"),false,tx);include(pos);success=true;
            }
            queue.remove();
            addNeighbours(entry,fluid);
        }return success;
    }
    private void softReset(BlockPos root){visited.clear();queue.clear();queue.add(new BlockPosEntry(root,0));infinite=false;setValidationTimer();pulley().fluidChanged();}
    private enum SpaceType {FILLABLE,FILLED,BLOCKING}
    private SpaceType spaceAt(BlockPos pos,Fluid toFill){
        if(!getWorld().isLoaded(pos)||getWorld().isOutsideBuildHeight(pos))return SpaceType.BLOCKING;
        var block=state(pos);var fluid=block.getFluidState();
        if(block.hasProperty(BlockStateProperties.WATERLOGGED))return toFill.isSame(Fluids.WATER)?block.getValue(BlockStateProperties.WATERLOGGED)?SpaceType.FILLED:SpaceType.FILLABLE:SpaceType.BLOCKING;
        if(block.getBlock() instanceof LiquidBlock)return block.getValue(LiquidBlock.LEVEL)==0?toFill.isSame(fluid.getType())?SpaceType.FILLED:SpaceType.BLOCKING:SpaceType.FILLABLE;
        if(!fluid.isEmpty()&&block.getCollisionShape(getWorld(),pos,CollisionContext.empty()).isEmpty())return toFill.isSame(fluid.getType())?SpaceType.FILLED:SpaceType.BLOCKING;
        var type=block.getBlock();
        if(type instanceof DoorBlock||block.is(BlockTags.ALL_SIGNS)||block.is(Blocks.LADDER)||block.is(Blocks.SUGAR_CANE)||block.is(Blocks.BUBBLE_COLUMN)||block.is(Blocks.NETHER_PORTAL)||block.is(Blocks.END_PORTAL)||block.is(Blocks.END_GATEWAY)||block.is(Blocks.STRUCTURE_VOID))return SpaceType.BLOCKING;
        return block.isSolid()?SpaceType.BLOCKING:SpaceType.FILLABLE;
    }
    @Override public void reset(){super.reset();queue.clear();infinityFrontier.clear();infinityVisited.clear();}
    private record Extra(List<BlockPosEntry> queue,List<BlockPosEntry> frontier,Set<BlockPos> visited){}
    @Override protected Object snapshotExtra(){return new Extra(List.copyOf(queue),List.copyOf(infinityFrontier),Set.copyOf(infinityVisited));}
    @Override protected void restoreExtra(Object snapshot){var s=(Extra)snapshot;queue.clear();queue.addAll(s.queue);infinityFrontier.clear();infinityFrontier.addAll(s.frontier);infinityVisited.clear();infinityVisited.addAll(s.visited);}
    @Override public void load(ValueInput in){super.load(in);queue.clear();infinityFrontier.clear();infinityVisited.clear();if(rootPos!=null)queue.add(new BlockPosEntry(rootPos,0));}
    public static final com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<FluidFillingBehaviour> TYPE=new com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<>();
    @Override public com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<?> getType(){return TYPE;}
    @Override public void tick(){super.tick();if(getWorld()!=null&&!getWorld().isClientSide()&&!pulley().isMoving())tick(pulley().rootPos(),pulley().tank.getResource().getFluid());}
    @Override public void write(ValueOutput output){save(output.child("Filling"));}
    @Override public void read(ValueInput input){load(input.childOrEmpty("Filling"));}

}
