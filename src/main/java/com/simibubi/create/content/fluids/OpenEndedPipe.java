package com.simibubi.create.content.fluids;

import java.util.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.content.fluids.pipes.VanillaFluidTargets;
import net.createmod.catnip.math.BlockFace;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.*;

/** One-bucket open-end buffer. World edits occur only after a successful transfer commit. */
public final class OpenEndedPipe extends FlowSource {
    private final EndStorage storage=new EndStorage();
    private net.minecraft.world.level.block.entity.BlockEntity owner;
    @Override public void manageSource(net.minecraft.world.level.Level world,net.minecraft.world.level.block.entity.BlockEntity owner){super.manageSource(world,owner);if(owner!=null)this.owner=owner;}
    @Override public void whileFlowPresent(net.minecraft.world.level.Level world,boolean pulling){
        if(pulling||!loaded())return;var transport=FluidPropagator.getPipe(world,location.getPos());var flow=transport==null?null:transport.getFlow(location.getFace());
        if(flow==null||flow.fluid.isEmpty()||flow.fluid.variant().getFluid().defaultFluidState().createLegacyBlock().isAir())return;var other=WorldFluidEdit.state(world,location.getConnectedPos()).getFluidState();
        if(!other.isEmpty()&&other.getType() instanceof FlowingFluid fluid&&fluid.getSource()!=flow.fluid.variant().getFluid())FluidReactions.handlePipeSpillCollision(world,location.getConnectedPos(),flow.fluid.variant().getFluid(),other);
    }
    public OpenEndedPipe(BlockFace location){super(location);}
    public net.minecraft.world.level.Level getWorld(){return world;}
    public BlockPos getPos(){return location.getPos();}
    public BlockPos getOutputPos(){return location.getConnectedPos();}
    public net.minecraft.world.phys.AABB getAOE(){return new net.minecraft.world.phys.AABB(getOutputPos()).expandTowards(0,location.getFace()==Direction.DOWN?-2:-1,0);}
    @Override public boolean isEndpoint(){return true;}
    @Override public Storage<FluidVariant> provideHandler(){return storage;}
    public void write(ValueOutput out){if(!storage.variant.isBlank())out.store("Fluid",FluidVariant.CODEC,storage.variant);out.putLong("Amount",storage.amount);}
    public void read(ValueInput in){storage.variant=in.read("Fluid",FluidVariant.CODEC).orElse(FluidVariant.blank());storage.amount=storage.variant.isBlank()?0:Math.max(0,in.getLongOr("Amount",0));}
    private boolean loaded(){return world!=null&&world.isLoaded(location.getConnectedPos());}
    private FluidStack worldFluid(){
        if(!loaded())return FluidStack.EMPTY;
        var state=WorldFluidEdit.state(world,location.getConnectedPos());var special=VanillaFluidTargets.fluid(state);if(!special.isEmpty())return special;
        var fluid=state.getFluidState();return (state.canBeReplaced()||state.hasProperty(BlockStateProperties.WATERLOGGED))&&fluid.isSource()?new FluidStack(FluidVariant.of(fluid.getType()),FluidConstants.BUCKET):FluidStack.EMPTY;
    }
    private BlockState drainedState(BlockState state){
        if(state.hasProperty(BlockStateProperties.LEVEL_HONEY))return state.setValue(BlockStateProperties.LEVEL_HONEY,0);
        if(VanillaFluidTargets.canProvideFluidWithoutCapability(state))return Blocks.CAULDRON.defaultBlockState();
        if(state.hasProperty(BlockStateProperties.WATERLOGGED))return state.setValue(BlockStateProperties.WATERLOGGED,false);
        return state.getFluidState().createLegacyBlock().setValue(LiquidBlock.LEVEL,14);
    }
    private boolean canPlace(FluidVariant fluid){
        if(!loaded())return false;var pos=location.getConnectedPos();var state=WorldFluidEdit.state(world,pos);var old=state.getFluidState();
        if(!state.canBeReplaced()&&!state.hasProperty(BlockStateProperties.WATERLOGGED))return false;
        if(!(fluid.getFluid() instanceof FlowingFluid))return false;
        if(fluid.getFluid().defaultFluidState().createLegacyBlock().isAir())return true;
        if(!old.isEmpty()&&(!(old.getType() instanceof FlowingFluid flowing)||flowing.getSource()!=fluid.getFluid()))return false;
        return !old.isSource()&&(!state.hasProperty(BlockStateProperties.WATERLOGGED)||fluid.isOf(Fluids.WATER));
    }
    private final class EndStorage extends SnapshotParticipant<Snapshot> implements Storage<FluidVariant>{
        FluidVariant variant=FluidVariant.blank();long amount;
        @Override protected Snapshot createSnapshot(){return new Snapshot(variant,amount);}
        @Override protected void readSnapshot(Snapshot s){variant=s.variant;amount=s.amount;}
        @Override protected void onFinalCommit(){if(owner!=null)owner.setChanged();}
        @Override public long insert(FluidVariant fluid,long max,TransactionContext tx){
            StoragePreconditions.notBlankNotNegative(fluid,max);if(max==0||!canPlace(fluid))return 0;
            if(!variant.isBlank()&&!variant.equals(fluid))return 0;
            var handler=com.simibubi.create.api.effect.OpenPipeEffectHandler.REGISTRY.get(fluid.getFluid());
            boolean virtual=fluid.getFluid().defaultFluidState().createLegacyBlock().isAir();
            if(virtual){
                long moved=Math.min(max,handler==null?FluidConstants.BUCKET:81);
                if(handler!=null)WorldFluidEdit.afterCommit(world,getOutputPos(),()->handler.apply(world,getAOE(),new FluidStack(fluid,moved)),tx);
                return moved;
            }
            long moved=Math.min(max,Math.max(0,FluidConstants.BUCKET-amount));if(moved==0)return 0;updateSnapshots(tx);variant=fluid;amount+=moved;
            if(amount==FluidConstants.BUCKET){
                var pos=location.getConnectedPos();var expected=WorldFluidEdit.state(world,pos);
                boolean evaporate=fluid.getFluid().is(net.minecraft.tags.FluidTags.WATER)&&world.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES,pos);
                var replacement=evaporate?expected:expected.hasProperty(BlockStateProperties.WATERLOGGED)?expected.setValue(BlockStateProperties.WATERLOGGED,true):fluid.getFluid().defaultFluidState().createLegacyBlock();
                if(com.simibubi.create.infrastructure.config.AllConfigs.server().fluids.pipesPlaceFluidSourceBlocks.get())WorldFluidEdit.stage(world,pos,replacement,evaporate,tx);
                variant=FluidVariant.blank();amount=0;
            }
            if(handler!=null)WorldFluidEdit.afterCommit(world,getOutputPos(),()->handler.apply(world,getAOE(),new FluidStack(fluid,moved)),tx);
            return moved;
        }
        @Override public long extract(FluidVariant fluid,long max,TransactionContext tx){
            StoragePreconditions.notBlankNotNegative(fluid,max);if(max==0||!loaded())return 0;
            if(amount==0){var source=worldFluid();if(source.isEmpty()||!source.variant().equals(fluid))return 0;updateSnapshots(tx);variant=fluid;amount=source.getAmount();var expected=WorldFluidEdit.state(world,location.getConnectedPos());WorldFluidEdit.stage(world,location.getConnectedPos(),drainedState(expected),false,tx);
                String award=expected.hasProperty(BlockStateProperties.LEVEL_HONEY)&&fluid.getFluid().isSame(com.simibubi.create.AllFluids.HONEY)?"honey_drain":expected.getFluidState().isSource()&&fluid.getFluid().isSame(Fluids.WATER)?"water_supply":null;
                if(award!=null)WorldFluidEdit.onAcceptedApply(world,location.getConnectedPos(),()->com.simibubi.create.foundation.advancement.GameplayAdvancements.awardAt(world,location.getPos(),award),award.equals("water_supply"),tx);}
            if(!variant.equals(fluid))return 0;updateSnapshots(tx);long moved=Math.min(max,amount);amount-=moved;if(amount==0)variant=FluidVariant.blank();return moved;
        }
        @Override public Iterator<StorageView<FluidVariant>> iterator(){return Collections.<StorageView<FluidVariant>>singleton(new StorageView<>(){
            @Override public boolean isResourceBlank(){return getResource().isBlank();}
            @Override public FluidVariant getResource(){return amount>0?variant:worldFluid().variant();}
            @Override public long getAmount(){return amount>0?amount:worldFluid().getAmount();}
            @Override public long getCapacity(){return FluidConstants.BUCKET;}
            @Override public long extract(FluidVariant resource,long max,TransactionContext tx){return EndStorage.this.extract(resource,max,tx);}
        }).iterator();}
    }
    private record Snapshot(FluidVariant variant,long amount){}
}
