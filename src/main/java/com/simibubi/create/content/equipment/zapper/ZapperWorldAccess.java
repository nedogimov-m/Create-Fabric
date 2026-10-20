package com.simibubi.create.content.equipment.zapper;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Actual loaded native world access. No tickets, surrogate players, equipment flags or copied ownership. */
public final class ZapperWorldAccess {
    public static boolean loaded(LevelAccessor world,BlockPos pos){return world instanceof Level level?LoadedLevelAccess.isLoaded(level,pos)&&level.isInValidBounds(pos)&&level.getWorldBorder().isWithinBounds(pos):world.hasChunkAt(pos);}
    public static BlockState read(LevelAccessor world,BlockPos pos){return loaded(world,pos)?world.getBlockState(pos):Blocks.BARRIER.defaultBlockState();}
    public static boolean canEdit(Level level,Player player,BlockPos pos){return !level.isClientSide()&&!level.isDebug()&&loaded(level,pos)&&(player==null||!player.isSpectator()&&player.mayBuild()&&level.mayInteract(player,pos));}
    private record Change(BlockPos pos,BlockState before,net.minecraft.world.level.block.entity.BlockEntity entity,BlockState after){}
    private static boolean neighbourhoodLoaded(Level level,Player player,BlockPos pos){
        if(!canEdit(level,player,pos))return false;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!loaded(level,pos.offset(dx,0,dz)))return false;
        return true;
    }
    private static Change capture(Level level,Player player,BlockPos pos,BlockState after){
        return after!=null&&neighbourhoodLoaded(level,player,pos)?new Change(pos.immutable(),read(level,pos),LoadedLevelAccess.blockEntity(level,pos),after):null;
    }
    private static boolean unchanged(Level level,Player player,Change change){return neighbourhoodLoaded(level,player,change.pos)&&read(level,change.pos)==change.before&&LoadedLevelAccess.blockEntity(level,change.pos)==change.entity;}
    private static boolean authorize(Level level,Player player,Change change){return !(level instanceof ServerLevel server)||!(player instanceof ServerPlayer actual)||PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(server,actual,change.pos,change.before,change.entity);}
    private static boolean allow(Level level,Player player,Change change){return change.before==change.after||authorize(level,player,change);}
    /** Source Place/Replace may repaint safe configuration on an unchanged real block.
     * That explicit edit still checks actual access, native owner identity and protection vetoes. */
    public static boolean writeForPainting(Level level,Player player,BlockPos pos,BlockState state){
        var change=capture(level,player,pos,state);
        return change!=null&&authorize(level,player,change)&&unchanged(level,player,change)&&(change.before==change.after||level.setBlockAndUpdate(pos,state));
    }
    public static boolean write(Level level,Player player,BlockPos pos,BlockState state){
        var change=capture(level,player,pos,state);
        if(change==null||change.before==change.after||!allow(level,player,change)||!unchanged(level,player,change))return false;
        // Native updates own BE removal, inventory release, shapes, light and all neighbour callbacks.
        return level.setBlockAndUpdate(pos,state);
    }
    /** Preflight the two native edits of one Flatten step before either is published.
     * Native callbacks remain sequential; no synthetic rollback of their effects is attempted. */
    public static boolean writePair(Level level,Player player,BlockPos first,BlockState firstState,BlockPos second,BlockState secondState){
        var a=capture(level,player,first,firstState);var b=capture(level,player,second,secondState);
        if(a==null||b==null||!allow(level,player,a)||!allow(level,player,b)||!unchanged(level,player,a)||!unchanged(level,player,b))return false;
        if(a.before!=a.after&&!level.setBlockAndUpdate(a.pos,a.after))return false;
        // A real first-edit callback may replace the second owner: never overwrite that replacement.
        return unchanged(level,player,b)&&(b.before==b.after||level.setBlockAndUpdate(b.pos,b.after));
    }
    public static net.minecraft.world.phys.BlockHitResult clip(Player player,int range){var start=player.getEyePosition();var end=start.add(player.getLookAngle().scale(range));return new RayView(player.level()).clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));}
    private record RayView(Level level) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos){return LoadedLevelAccess.blockState(level,pos);}
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos);}
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinY(){return level.getMinY();}
    }
    private ZapperWorldAccess(){}
}
