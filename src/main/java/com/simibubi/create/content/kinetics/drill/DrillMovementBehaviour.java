package com.simibubi.create.content.kinetics.drill;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class DrillMovementBehaviour extends BlockBreakingMovementBehaviour {
    private static final TagKey<Block> TRACKS=TagKey.create(Registries.BLOCK,Create.asResource("tracks"));
    public static boolean backwards(MovementContext context,Direction facing){return context.relativeMotion.normalize().dot(facing.getOpposite().getUnitVec3())>.125;}
    @Override public boolean isActive(MovementContext context){return super.isActive(context)&&!backwards(context,context.state.getValue(DrillBlock.FACING));}
    @Override public Vec3 getActiveAreaOffset(MovementContext context){return context.state.getValue(DrillBlock.FACING).getUnitVec3().scale(.65);}
    @Override public boolean disableBlockEntityRendering(){return true;}
    @Override protected DamageSource getDamageSource(Level level){return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DrillBlock.DRILL_DAMAGE));}
    @Override public boolean canBreak(Level level,BlockPos pos,BlockState state){return super.canBreak(level,pos,state)&&!state.getCollisionShape(level,pos).isEmpty()&&!state.is(TRACKS);}
}
