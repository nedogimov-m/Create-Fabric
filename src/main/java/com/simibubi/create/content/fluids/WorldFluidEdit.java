package com.simibubi.create.content.fluids;

import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;

/** Shared transaction participant prevents two open pipes from consuming or placing the same source block twice. */
public final class WorldFluidEdit extends SnapshotParticipant<WorldFluidEdit.State> {
    private static final Map<Level,Map<BlockPos,WeakReference<WorldFluidEdit>>> EDITS=new WeakHashMap<>();
    private final Level level;private final BlockPos pos;
    private BlockState original,proposed;private boolean evaporate;
    private int flags=3;
    private boolean scheduleFluid=true,dropResources,clearFluidTicks;
    private SoundEvent sound;
    private final List<Runnable> effects=new ArrayList<>(),beforeApply=new ArrayList<>(),afterApply=new ArrayList<>();
    private WorldFluidEdit(Level level,BlockPos pos){this.level=level;this.pos=pos.immutable();}
    private static WorldFluidEdit get(Level level,BlockPos pos){
        var map=EDITS.computeIfAbsent(level,l->new HashMap<>());var ref=map.get(pos);var edit=ref==null?null:ref.get();
        if(edit==null){edit=new WorldFluidEdit(level,pos);map.put(pos.immutable(),new WeakReference<>(edit));}return edit;
    }
    public static BlockState state(Level level,BlockPos pos){var map=EDITS.get(level);var ref=map==null?null:map.get(pos);var edit=ref==null?null:ref.get();return edit==null||edit.proposed==null?com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,pos):edit.proposed;}
    static void stage(Level level,BlockPos pos,BlockState state,boolean evaporate,TransactionContext tx){var edit=get(level,pos);edit.updateSnapshots(tx);if(edit.original==null)edit.original=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,pos);edit.proposed=state;edit.evaporate=evaporate;edit.flags=3;edit.scheduleFluid=true;edit.dropResources=edit.clearFluidTicks=false;edit.sound=null;}
    static void afterCommit(Level level,BlockPos pos,Runnable effect,TransactionContext tx){
        var edit=get(level,pos);edit.updateSnapshots(tx);
        if(edit.original==null){edit.original=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,pos);edit.proposed=edit.original;edit.scheduleFluid=false;}
        edit.effects.add(effect);
    }
    /** Exact source events run only for an accepted world edit, with source before/after order. */
    public static void onAcceptedApply(Level level,BlockPos pos,Runnable effect,boolean before,TransactionContext tx){
        var edit=get(level,pos);edit.updateSnapshots(tx);
        if(edit.original==null){edit.original=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,pos);edit.proposed=edit.original;edit.scheduleFluid=false;}
        (before?edit.beforeApply:edit.afterApply).add(effect);
    }
    /** Reservoir edits suppress immediate neighbour updates until the searched area is released. */
    public static void stageQuiet(Level level,BlockPos pos,BlockState state,boolean dropResources,FluidVariant fluid,boolean draining,boolean evaporate,TransactionContext tx){
        stage(level,pos,state,evaporate,tx);var edit=get(level,pos);edit.flags=18;edit.scheduleFluid=false;edit.dropResources=dropResources;edit.clearFluidTicks=!draining;
        edit.sound=fluid.isBlank()?null:draining?FluidVariantAttributes.getFillSound(fluid):FluidVariantAttributes.getEmptySound(fluid);
    }
    record State(BlockState original,BlockState proposed,boolean evaporate,int flags,boolean scheduleFluid,boolean dropResources,boolean clearFluidTicks,SoundEvent sound,List<Runnable> effects,List<Runnable> beforeApply,List<Runnable> afterApply){}
    @Override protected State createSnapshot(){return new State(original,proposed,evaporate,flags,scheduleFluid,dropResources,clearFluidTicks,sound,List.copyOf(effects),List.copyOf(beforeApply),List.copyOf(afterApply));}
    @Override protected void readSnapshot(State state){original=state.original;proposed=state.proposed;evaporate=state.evaporate;flags=state.flags;scheduleFluid=state.scheduleFluid;dropResources=state.dropResources;clearFluidTicks=state.clearFluidTicks;sound=state.sound;effects.clear();effects.addAll(state.effects);beforeApply.clear();beforeApply.addAll(state.beforeApply);afterApply.clear();afterApply.addAll(state.afterApply);if(original==null){var map=EDITS.get(level);if(map!=null)map.remove(pos);}}
    @Override protected void onFinalCommit(){
        if(level.isLoaded(pos)&&original!=null&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,pos)==original){
            List.copyOf(beforeApply).forEach(Runnable::run);
            if(proposed!=original){
                if(dropResources)net.minecraft.world.level.block.Block.dropResources(original,level,pos,com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos));
                if(flags==18)com.simibubi.create.foundation.utility.LoadedLevelAccess.setBlockQuiet(level,pos,proposed);
                else level.setBlock(pos,proposed,flags);
            }
            if(clearFluidTicks&&level.getFluidTicks() instanceof net.minecraft.world.ticks.LevelTicks<?> ticks)ticks.clearArea(new net.minecraft.world.level.levelgen.structure.BoundingBox(pos));
            if(sound!=null)level.playSound(null,pos,sound,SoundSource.BLOCKS,0.3f,1);
            if(evaporate)level.playSound(null,pos,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,0.5f,2.6f);
            var fluid=proposed.getFluidState();if(scheduleFluid&&!fluid.isEmpty())level.scheduleTick(pos,fluid.getType(),1);
            List.copyOf(afterApply).forEach(Runnable::run);
        }
        var committedEffects=List.copyOf(effects);effects.clear();beforeApply.clear();afterApply.clear();original=proposed=null;evaporate=false;flags=3;scheduleFluid=true;dropResources=clearFluidTicks=false;sound=null;var map=EDITS.get(level);if(map!=null)map.remove(pos);
        committedEffects.forEach(Runnable::run);
    }
}
