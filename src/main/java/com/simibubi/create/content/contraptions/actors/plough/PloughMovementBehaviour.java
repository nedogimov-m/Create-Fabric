package com.simibubi.create.content.contraptions.actors.plough;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public class PloughMovementBehaviour extends BlockBreakingMovementBehaviour {
    public static final TagKey<Block> BLACKLIST=TagKey.create(Registries.BLOCK,Create.asResource("plough_blacklist")),WHITELIST=TagKey.create(Registries.BLOCK,Create.asResource("plough_whitelist"));
    @Override public boolean isActive(MovementContext context){return super.isActive(context)&&!DrillMovementBehaviour.backwards(context,context.state.getValue(PloughBlock.FACING));}
    @Override public Vec3 getActiveAreaOffset(MovementContext context){return context.state.getValue(PloughBlock.FACING).getUnitVec3().scale(.45);}
    @Override public void visitNewPosition(MovementContext context,BlockPos pos){
        super.visitNewPosition(context,pos);if(!(context.world instanceof ServerLevel level)||!LoadedLevelAccess.isLoaded(level,pos)||!LoadedLevelAccess.isLoaded(level,pos.below()))return;
        var player=getPlayer(context);player.setPos(Vec3.atCenterOf(pos));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_HOE));var center=Vec3.atCenterOf(pos);
        var hit=level.clip(new ClipContext(center,center.add(0,-1,0),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));if(hit.getType()!=HitResult.Type.BLOCK||!level.mayInteract(player,hit.getBlockPos()))return;
        var permission=UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,hit);if(permission==InteractionResult.FAIL||permission.consumesAction())return;player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
    }
    @Override protected boolean throwsEntities(Level level){return true;}
    @Override protected void throwEntity(MovementContext context,net.minecraft.world.entity.Entity entity){
        super.throwEntity(context,entity);
        if(!(entity instanceof net.minecraft.world.entity.item.FallingBlockEntity falling)||!(falling.getBlockState().getBlock() instanceof AnvilBlock)||entity.getDeltaMovement().length()<0.25f)return;
        for(var player:entity.level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,new AABB(entity.blockPosition()).inflate(32)))com.simibubi.create.foundation.advancement.GameplayAdvancements.award(player,"anvil_plough");
    }
    @Override public boolean canBreak(Level level,BlockPos pos,BlockState state){
        if(!LoadedLevelAccess.isLoaded(level,pos)||!LoadedLevelAccess.isLoaded(level,pos.below())||state.isAir()||LoadedLevelAccess.blockState(level,pos.below()).getBlock() instanceof FarmlandBlock||state.getBlock() instanceof LiquidBlock||state.getBlock() instanceof BubbleColumnBlock||state.is(BLACKLIST))return false;
        return state.is(WHITELIST)||state.getCollisionShape(level,pos).isEmpty();
    }
    @Override protected void onBlockBroken(MovementContext context,BlockPos pos,BlockState state){super.onBlockBroken(context,pos,state);if(state.is(Blocks.SNOW)&&context.world instanceof ServerLevel level&&level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS))Block.getDrops(state,level,pos,null,getPlayer(context),new ItemStack(Items.IRON_SHOVEL)).forEach(stack->collectOrDropItem(context,stack));}
    private PloughBlock.PloughFakePlayer getPlayer(MovementContext context){if(context.temporaryData instanceof PloughBlock.PloughFakePlayer player)return player;var player=new PloughBlock.PloughFakePlayer((ServerLevel)context.world);context.temporaryData=player;return player;}
    @Override public void stopMoving(MovementContext context){super.stopMoving(context);if(context.temporaryData instanceof PloughBlock.PloughFakePlayer player)player.discard();}
}
