package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class DeployerBlock extends DirectionalAxisKineticBlock {
    public DeployerBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.DOWN).setValue(AXIS_ALONG_FIRST_COORDINATE,false));}
    private static final class AlignedPlacementContext extends BlockPlaceContext {
        final BlockPos target;final BlockState template;
        AlignedPlacementContext(Player player,InteractionHand hand,BlockHitResult hit,BlockPos target,BlockState template){super(new UseOnContext(player,hand,hit));this.target=target;this.template=template;}
        @Override public BlockPos getClickedPos(){return target;}
        @Override public boolean replacingClickedOnBlock(){return true;}
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return context instanceof AlignedPlacementContext aligned?aligned.template:super.getStateForPlacement(context);}
    @Override protected Direction getFacingForPlacement(BlockPlaceContext context){return context instanceof com.simibubi.create.content.processing.AssemblyOperatorUseContext?Direction.DOWN:super.getFacingForPlacement(context);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new DeployerBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.DEPLOYER,DeployerBlockEntity::tick);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return switch(state.getValue(FACING)){case UP->box(0,0,0,16,12,16);case DOWN->box(0,4,0,16,16,16);case NORTH->box(0,0,4,16,16,16);case SOUTH->box(0,0,0,16,16,12);case EAST->box(0,0,0,12,16,16);case WEST->box(4,0,0,16,16,16);};}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var pole=switch(state.getValue(FACING).getAxis()){case X->box(0,5,5,16,11,11);case Y->box(5,0,5,11,16,11);case Z->box(5,5,0,11,11,16);};return Shapes.or(getCollisionShape(state,level,pos,context),pole);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack){super.setPlacedBy(level,pos,state,entity,stack);if(entity instanceof net.minecraft.server.level.ServerPlayer&&level.getBlockEntity(pos) instanceof DeployerBlockEntity deployer){deployer.owner=entity.getUUID();deployer.setChanged();}}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){super.neighborChanged(state,level,pos,neighbor,orientation,moved);if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof DeployerBlockEntity deployer)deployer.redstoneUpdate();}
    public static boolean onHand(BlockState state,BlockPos pos,Vec3 hit){var face=state.getValue(FACING);var local=hit.subtract(Vec3.atCenterOf(pos));double along=face.getStepX()*local.x+face.getStepY()*local.y+face.getStepZ()*local.z;return Math.abs(along+0.5)>=0.75;}
    public boolean filterSide(BlockState state,Direction face){return face.getAxis()!=state.getValue(FACING).getAxis()&&face.getAxis()!=getRotationAxis(state);}
    public static Vec3 filterPosition(BlockState state,Direction face){var facing=state.getValue(FACING);return new Vec3(0.5+face.getStepX()*15d/32-facing.getStepX()/8d,0.5+face.getStepY()*15d/32-facing.getStepY()/8d,0.5+face.getStepZ()*15d/32-facing.getStepZ()/8d);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.mayBuild()||!level.mayInteract(player,pos)||stack.is(AllItems.WRENCH))return InteractionResult.PASS;
        if(!player.isShiftKeyDown()&&stack.is(asItem())&&stack.getItem() instanceof BlockItem item){
            var direction=java.util.Arrays.stream(Direction.values()).filter(side->side.getAxis()!=state.getValue(FACING).getAxis()&&level.isLoaded(pos.relative(side))&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,pos.relative(side)).canBeReplaced()).min(java.util.Comparator.comparingDouble(side->Vec3.atCenterOf(pos).add(side.getUnitVec3().scale(0.5)).distanceToSqr(hit.getLocation())));
            if(direction.isPresent()){var placed=item.place(new AlignedPlacementContext(player,hand,hit,pos.relative(direction.get()),state));if(placed.consumesAction())return placed;}
        }
        if(player.isShiftKeyDown()&&filterSide(state,hit.getDirection())&&hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)).distanceTo(filterPosition(state,hit.getDirection()))<0.25){if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof DeployerBlockEntity deployer)deployer.filtering.applyPlayerSample(player,hand);return InteractionResult.SUCCESS;}
        if(!onHand(state,pos,hit.getLocation()))return InteractionResult.PASS;
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof DeployerBlockEntity deployer){var old=deployer.getHeldItem().copy();deployer.setHeldItem(stack.copy());player.setItemInHand(hand,old);}return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
}
