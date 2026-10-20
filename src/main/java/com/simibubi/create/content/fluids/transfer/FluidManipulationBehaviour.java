package com.simibubi.create.content.fluids.transfer;

import com.simibubi.create.Create;
import com.simibubi.create.content.fluids.WorldFluidEdit;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.*;
import java.util.*;
import java.util.function.*;

/** Create's incremental reservoir search and ordering, with rollback for transfer-side mutations. */
public abstract class FluidManipulationBehaviour extends com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour {
    public record BlockPosEntry(BlockPos pos,int distance){public BlockPosEntry{pos=pos.immutable();}}
    public static final class ChunkNotLoadedException extends Exception {}
    protected static final int searchedPerTick=1024,validationTimerMin=160;
    protected BoundingBox affectedArea;
    protected BlockPos rootPos;
    protected boolean infinite,counterpartActed;
    protected final List<BlockPosEntry> frontier=new ArrayList<>();
    protected final Set<BlockPos> visited=new HashSet<>();
    protected int revalidateIn=validationTimerMin;
    private final List<BoundingBox> deferredUpdates=new ArrayList<>();
    protected FluidManipulationBehaviour(HosePulleyBlockEntity owner){super(owner);}
    protected final HosePulleyBlockEntity pulley(){return (HosePulleyBlockEntity)blockEntity;}
    public Level getWorld(){return blockEntity.getLevel();}
    protected int maxRange(){return pulley().settings().range();}
    protected int maxBlocks(){return pulley().settings().blockThreshold();}
    protected boolean fillInfinite(){return pulley().settings().fillInfinite();}
    protected int validationTimer(){return maxBlocks()<0?validationTimerMin:Math.max(validationTimerMin,maxBlocks()/searchedPerTick+1);}
    protected int setValidationTimer(){return revalidateIn=validationTimer();}
    protected int setLongValidationTimer(){return revalidateIn=validationTimer()*2;}
    @Override public void tick(){
        pulley().settings();
        super.tick();
    }
    public boolean isInfinite(){return infinite;}
    public void counterpartActed(){counterpartActed=true;}
    public void enlist(TransactionContext tx){snapshots.enlist(tx);}
    protected boolean canDrainInfinitely(Fluid fluid){return fluid!=null&&maxBlocks()!=-1&&pulley().settings().bottomlessMode().test(fluid);}
    protected static Fluid still(Fluid fluid){return fluid instanceof FlowingFluid flowing?flowing.getSource():fluid;}
    protected static Fluid flowing(Fluid fluid){return fluid instanceof FlowingFluid flowing?flowing.getFlowing():fluid;}
    protected BlockState state(BlockPos pos){return WorldFluidEdit.state(getWorld(),pos);}
    protected void include(BlockPos pos){affectedArea=affectedArea==null?new BoundingBox(pos):new BoundingBox(Math.min(affectedArea.minX(),pos.getX()),Math.min(affectedArea.minY(),pos.getY()),Math.min(affectedArea.minZ(),pos.getZ()),Math.max(affectedArea.maxX(),pos.getX()),Math.max(affectedArea.maxY(),pos.getY()),Math.max(affectedArea.maxZ(),pos.getZ()));}
    protected int comparePositions(BlockPosEntry first,BlockPosEntry second){
        if(first.pos.getY()!=second.pos.getY())return Integer.compare(second.pos.getY(),first.pos.getY());
        int distance=Integer.compare(second.distance,first.distance);
        return distance!=0?distance:Double.compare(second.pos.distSqr(rootPos),first.pos.distSqr(rootPos));
    }
    protected Fluid search(Fluid fluid,List<BlockPosEntry> frontier,Set<BlockPos> visited,BiConsumer<BlockPos,Integer> add,boolean downward) throws ChunkNotLoadedException {
        long rangeSq=(long)maxRange()*maxRange();
        for(int count=0;count<searchedPerTick&&!frontier.isEmpty()&&(visited.size()<=maxBlocks()||!canDrainInfinitely(fluid));count++){
            var entry=frontier.removeFirst();var current=entry.pos;
            if(visited.contains(current))continue;
            if(!getWorld().isLoaded(current))throw new ChunkNotLoadedException();
            visited.add(current);var fluidState=state(current).getFluidState();if(fluidState.isEmpty())continue;
            var found=still(fluidState.getType());if(fluid==null)fluid=found;if(!found.isSame(fluid))continue;
            add.accept(current,entry.distance);
            for(var side:Direction.values()){
                if(!downward&&side==Direction.DOWN)continue;var next=current.relative(side);
                if(visited.contains(next)||next.distSqr(rootPos)>rangeSq||getWorld().isOutsideBuildHeight(next))continue;
                if(!getWorld().isLoaded(next))throw new ChunkNotLoadedException();
                var nextFluid=state(next).getFluidState();if(nextFluid.isEmpty())continue;
                var nextType=nextFluid.getType();
                if(nextType==flowing(nextType)&&side==Direction.UP&&(rootPos.getX()!=next.getX()||rootPos.getZ()!=next.getZ()))continue;
                frontier.add(new BlockPosEntry(next,entry.distance+1));
            }
        }return fluid;
    }
    public void reset(){
        if(affectedArea!=null){if(Transaction.isOpen())deferredUpdates.add(copy(affectedArea));else scheduleUpdates(affectedArea);}
        affectedArea=null;setValidationTimer();frontier.clear();visited.clear();infinite=false;
    }
    private void scheduleUpdates(BoundingBox area){
        var world=getWorld();if(world==null)return;
        // Chunk-local access prevents a large affected area from refreshing UNKNOWN loading tickets.
        for(var pos:BlockPos.betweenClosed(area.minX()-1,area.minY()-1,area.minZ()-1,area.maxX()+1,area.maxY()+1,area.maxZ()+1)){
            if(!world.isLoaded(pos))continue;var fluid=state(pos).getFluidState();if(!fluid.isEmpty())world.scheduleTick(pos,fluid.getType(),world.getRandom().nextInt(5));
        }
    }
    protected void edit(BlockPos pos,BlockState replacement,Fluid fluid,boolean draining,boolean drops,boolean evaporate,TransactionContext tx){WorldFluidEdit.stageQuiet(getWorld(),pos,replacement,drops,fluid==null?FluidVariant.blank():FluidVariant.of(still(fluid)),draining,evaporate,tx);}
    protected abstract Object snapshotExtra();
    protected abstract void restoreExtra(Object snapshot);
    record Snapshot(BoundingBox area,BlockPos root,boolean infinite,boolean counterpart,List<BlockPosEntry> frontier,Set<BlockPos> visited,int timer,List<BoundingBox> updates,Object extra){}
    private static BoundingBox copy(BoundingBox box){return box==null?null:new BoundingBox(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());}
    protected Snapshot createSnapshot(){return new Snapshot(copy(affectedArea),rootPos,infinite,counterpartActed,List.copyOf(frontier),Set.copyOf(visited),revalidateIn,List.copyOf(deferredUpdates),snapshotExtra());}
    protected void readSnapshot(Snapshot s){affectedArea=copy(s.area);rootPos=s.root;infinite=s.infinite;counterpartActed=s.counterpart;frontier.clear();frontier.addAll(s.frontier);visited.clear();visited.addAll(s.visited);revalidateIn=s.timer;deferredUpdates.clear();deferredUpdates.addAll(s.updates);restoreExtra(s.extra);}
    protected void onFinalCommit(){deferredUpdates.forEach(this::scheduleUpdates);deferredUpdates.clear();pulley().fluidChanged();}
    public void save(ValueOutput out){out.putBoolean("Infinite",infinite);if(rootPos!=null)out.store("LastPos",BlockPos.CODEC,rootPos);if(affectedArea!=null){out.store("AffectedAreaFrom",BlockPos.CODEC,new BlockPos(affectedArea.minX(),affectedArea.minY(),affectedArea.minZ()));out.store("AffectedAreaTo",BlockPos.CODEC,new BlockPos(affectedArea.maxX(),affectedArea.maxY(),affectedArea.maxZ()));}}
    public void load(ValueInput in){rootPos=in.read("LastPos",BlockPos.CODEC).orElse(null);infinite=in.getBooleanOr("Infinite",false);var min=in.read("AffectedAreaFrom",BlockPos.CODEC);var max=in.read("AffectedAreaTo",BlockPos.CODEC);affectedArea=min.isPresent()&&max.isPresent()?BoundingBox.fromCorners(min.get(),max.get()):null;frontier.clear();visited.clear();deferredUpdates.clear();setValidationTimer();}
    public enum BottomlessFluidMode implements Predicate<Fluid> {
        ALLOW_ALL,DENY_ALL,ALLOW_BY_TAG,DENY_BY_TAG;
        private static final TagKey<Fluid> ALLOW=TagKey.create(Registries.FLUID,Create.asResource("bottomless/allow")),DENY=TagKey.create(Registries.FLUID,Create.asResource("bottomless/deny"));
        @Override public boolean test(Fluid fluid){return switch(this){case ALLOW_ALL->true;case DENY_ALL->false;case ALLOW_BY_TAG->fluid.is(ALLOW);case DENY_BY_TAG->!fluid.is(DENY);};}
    }
    private final Snapshots snapshots=new Snapshots();
    private final class Snapshots extends SnapshotParticipant<Snapshot> {
        void enlist(TransactionContext tx){updateSnapshots(tx);}
        @Override protected Snapshot createSnapshot(){return FluidManipulationBehaviour.this.createSnapshot();}
        @Override protected void readSnapshot(Snapshot state){FluidManipulationBehaviour.this.readSnapshot(state);}
        @Override protected void onFinalCommit(){FluidManipulationBehaviour.this.onFinalCommit();}
    }
    @Override public void destroy(){if(getWorld()!=null&&!getWorld().isClientSide())reset();}

}
