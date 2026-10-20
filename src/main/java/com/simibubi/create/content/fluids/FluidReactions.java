package com.simibubi.create.content.fluids;

import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.*;

public final class FluidReactions {
    public static void handlePipeFlowCollision(Level world,BlockPos pos,FluidStack first,FluidStack second){
        if(!(world instanceof ServerLevel server))return;
        var a=first.variant().getFluid();var b=second.variant().getFluid();
        com.simibubi.create.foundation.advancement.GameplayAdvancements.awardAt(world,pos,"cross_streams");
        BlockHelper.destroyBlock(server,pos,1,stack->net.minecraft.world.level.block.Block.popResource(server,pos,stack));
        var event=new com.simibubi.create.api.event.PipeCollisionEvent.Flow(world,pos,a,b,a==Fluids.WATER&&b==Fluids.LAVA||a==Fluids.LAVA&&b==Fluids.WATER?Blocks.COBBLESTONE.defaultBlockState():null);
        if(event.getState()==null){var custom=a==Fluids.LAVA?com.simibubi.create.AllFluids.getLavaInteraction(b.defaultFluidState()):b==Fluids.LAVA?com.simibubi.create.AllFluids.getLavaInteraction(a.defaultFluidState()):null;if(custom!=null)event.setState(custom);}
        com.simibubi.create.api.event.PipeCollisionEvent.FLOW.invoker().collide(event);if(event.getState()!=null)world.setBlockAndUpdate(pos,event.getState());
    }
    public static void handlePipeSpillCollision(Level world,BlockPos pos,Fluid fluid,FluidState other){
        var target=fluid==Fluids.WATER&&other.is(net.minecraft.tags.FluidTags.LAVA)?other.isSource()?Blocks.OBSIDIAN:Blocks.COBBLESTONE
                :fluid==Fluids.LAVA&&other.is(net.minecraft.tags.FluidTags.WATER)?other.isSource()?Blocks.STONE:Blocks.COBBLESTONE:null;
        var custom=fluid==Fluids.LAVA?com.simibubi.create.AllFluids.getLavaInteraction(other):other.getType()==Fluids.FLOWING_LAVA?com.simibubi.create.AllFluids.getLavaInteraction(fluid.defaultFluidState()):null;
        var event=new com.simibubi.create.api.event.PipeCollisionEvent.Spill(world,pos,other.getType(),fluid,custom!=null?custom:target==null?null:target.defaultBlockState());com.simibubi.create.api.event.PipeCollisionEvent.SPILL.invoker().collide(event);if(event.getState()!=null)world.setBlockAndUpdate(pos,event.getState());
    }
    public static void reactWorldLava(Level level,BlockPos pos){
        if(level.isClientSide()||!level.isLoaded(pos))return;
        var state=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,pos);if(!state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))return;
        for(var side:net.minecraft.core.Direction.values()){
            if(side==net.minecraft.core.Direction.DOWN)continue;
            var neighbour=pos.relative(side);if(!level.isLoaded(neighbour))continue;
            var custom=com.simibubi.create.AllFluids.getLavaInteraction(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,neighbour).getFluidState());if(custom==null)continue;
            level.setBlockAndUpdate(pos,state.getFluidState().isSource()?Blocks.OBSIDIAN.defaultBlockState():custom);
            level.playSound(null,pos,net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH,net.minecraft.sounds.SoundSource.BLOCKS,0.5f,2.6f);return;
        }
    }
}
