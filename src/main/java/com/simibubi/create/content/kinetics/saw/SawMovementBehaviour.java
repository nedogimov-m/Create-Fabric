package com.simibubi.create.content.kinetics.saw;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SawMovementBehaviour extends BlockBreakingMovementBehaviour {
    @Override public boolean isActive(MovementContext context){return super.isActive(context)&&!DrillMovementBehaviour.backwards(context,context.state.getValue(SawBlock.FACING));}
    @Override public Vec3 getActiveAreaOffset(MovementContext context){return context.state.getValue(SawBlock.FACING).getUnitVec3().scale(.65);}
    @Override public void visitNewPosition(MovementContext context,BlockPos pos){
        super.visitNewPosition(context,pos);var facing=context.rotation.apply(context.state.getValue(SawBlock.FACING).getUnitVec3());
        if(Direction.getApproximateNearest(facing).getAxis().isVertical()&&context.data.contains("BreakingPos"))cancelStall(context);
    }
    @Override public boolean canBreak(Level level,BlockPos pos,BlockState state){return super.canBreak(level,pos,state)&&SawBlockEntity.isSawable(state);}
    @Override protected void onBlockBroken(MovementContext context,BlockPos pos,BlockState state){if(!state.is(BlockTags.LEAVES)&&context.world instanceof ServerLevel server)TreeCutter.findTree(server,pos,state).destroyBlocks(server,(dropPos,stack)->dropItemFromCutTree(context,dropPos,stack));}
    public void dropItemFromCutTree(MovementContext context,BlockPos pos,ItemStack stack){
        var remainder=MovementBehaviour.insertItem(context,stack,true);if(remainder.isEmpty())return;var p=Vec3.atCenterOf(pos);
        var item=new ItemEntity(context.world,p.x,p.y,p.z,remainder);item.setDeltaMovement(context.relativeMotion.scale(context.position==null?1:p.distanceTo(context.position)/20));context.world.addFreshEntity(item);
    }
    @Override public boolean disableBlockEntityRendering(){return true;}
    @Override protected DamageSource getDamageSource(Level level){return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SawBlock.SAW_DAMAGE));}
}
