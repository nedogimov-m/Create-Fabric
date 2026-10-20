package com.simibubi.create.content.logistics.chute;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public abstract class AbstractChuteBlock extends BaseEntityBlock {
    protected AbstractChuteBlock(Properties properties){super(properties);}
    public static boolean isChute(BlockState state){return state.getBlock() instanceof AbstractChuteBlock;}
    public static boolean isOpenChute(BlockState state){return state.getBlock() instanceof AbstractChuteBlock chute&&chute.isOpen(state);}
    public static boolean isTransparentChute(BlockState state){return state.getBlock() instanceof AbstractChuteBlock chute&&chute.isTransparent(state);}
    public static Direction getChuteFacing(BlockState state){return state.getBlock() instanceof AbstractChuteBlock chute?chute.getFacing(state):null;}
    public Direction getFacing(BlockState state){return Direction.DOWN;}
    public boolean isOpen(BlockState state){return true;}
    public boolean isTransparent(BlockState state){return false;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return this instanceof SmartChuteBlock?new SmartChuteBlockEntity(pos,state):new ChuteBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return this instanceof SmartChuteBlock?createTickerHelper(type,AllBlockEntityTypes.SMART_CHUTE,ChuteBlockEntity::tick):createTickerHelper(type,AllBlockEntityTypes.CHUTE,ChuteBlockEntity::tick);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighborPos,BlockState neighbor,RandomSource random){ticks.scheduleTick(pos,this,1);return side==Direction.UP?updateChuteState(state,neighbor,level,pos):state;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(!level.isClientSide()){level.scheduleTick(pos,this,1);if(LoadedLevelAccess.blockEntity(level,pos) instanceof ChuteBlockEntity chute)chute.requestAirflowUpdate();}}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){var next=updateChuteState(state,LoadedLevelAccess.blockState(level,pos.above()),level,pos);if(next!=state)level.setBlockAndUpdate(pos,next);if(LoadedLevelAccess.blockEntity(level,pos) instanceof ChuteBlockEntity chute){chute.requestAirflowUpdate();chute.refreshAirflow();}}
    public abstract BlockState updateChuteState(BlockState state,BlockState above,BlockGetter level,BlockPos pos);
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){if(!level.isClientSide()){level.scheduleTick(pos,this,1);updateDiagonalNeighbours(state,level,pos);}}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){super.affectNeighborsAfterRemoval(state,level,pos,moved);if(!LoadedLevelAccess.blockState(level,pos).is(this))updateDiagonalNeighbours(state,level,pos);}
    private void updateDiagonalNeighbours(BlockState state,Level level,BlockPos pos){
        var facing=getFacing(state);var target=pos.below();if(facing.getAxis().isHorizontal())target=target.relative(facing.getOpposite());schedule(level,target);
        for(var side:Direction.Plane.HORIZONTAL)schedule(level,pos.above().relative(side));
    }
    private static void schedule(Level level,BlockPos pos){var state=LoadedLevelAccess.blockState(level,pos);if(isChute(state))level.scheduleTick(pos,state.getBlock(),1);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return ChuteShapes.getShape(state);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return ChuteShapes.getCollisionShape(state);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.interact(level,player,hand,hit);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){var handled=useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);if(handled.consumesAction())return handled;if(!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos)||!(LoadedLevelAccess.blockEntity(level,pos) instanceof ChuteBlockEntity chute)||chute.getItem().isEmpty())return InteractionResult.PASS;if(!level.isClientSide())player.getInventory().placeItemBackInInventory(chute.takeItem(),Prediction.SERVER_ONLY);return InteractionResult.SUCCESS;}
    @Override public void fallOn(Level level,BlockState state,BlockPos pos,Entity entity,double distance){super.fallOn(level,state,pos,entity,distance);captureLanding(level,pos,entity);}
    @Override public void stepOn(Level level,BlockPos pos,BlockState state,Entity entity){super.stepOn(level,pos,state,entity);captureLanding(level,pos,entity);}
    private static void captureLanding(Level level,BlockPos pos,Entity entity){if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos) instanceof ChuteBlockEntity chute)chute.captureItem(entity);}
    @Override public void bounceOn(Level level,BlockState state,BlockPos pos,Entity entity,double distance){super.bounceOn(level,state,pos,entity,distance);captureLanding(level,pos,entity);}

    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity,InsideBlockEffectApplier effects,boolean inside){captureLanding(level,pos,entity);}
}
