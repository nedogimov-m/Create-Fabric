package com.simibubi.create.content.logistics.funnel;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public class FunnelBlock extends AbstractFunnelBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    public static final BooleanProperty EXTRACTING=BooleanProperty.create("extracting");
    public FunnelBlock(Properties properties,boolean brass){super(properties,brass);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(EXTRACTING,false));}
    public BeltFunnelBlock belt(){return brass?com.simibubi.create.AllBlocks.BRASS_BELT_FUNNEL:com.simibubi.create.AllBlocks.ANDESITE_BELT_FUNNEL;}
    public BlockState getEquivalentBeltFunnel(BlockGetter level,BlockPos pos,BlockState state){return belt().defaultBlockState().setValue(BeltFunnelBlock.HORIZONTAL_FACING,getFacing(state)).setValue(POWERED,state.getValue(POWERED)).setValue(WATERLOGGED,state.getValue(WATERLOGGED));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(FACING,EXTRACTING);}
    @Override protected Direction getFacing(BlockState state){return state.getValue(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=super.getStateForPlacement(context).setValue(EXTRACTING,!context.isSecondaryUseActive());for(var look:context.getNearestLookingDirections()){var candidate=state.setValue(FACING,look.getOpposite());if(candidate.canSurvive(context.getLevel(),context.getClickedPos()))return candidate;}return state;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(getFacing(state)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(getFacing(state)));}
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context){if(!context.getLevel().isClientSide())context.getLevel().setBlockAndUpdate(context.getClickedPos(),state.cycle(EXTRACTING));return InteractionResult.SUCCESS;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        var handled=super.useItemOn(stack,state,level,pos,player,hand,hit);if(handled.consumesAction())return handled;
        if(!player.mayBuild()||!level.mayInteract(player,pos)||stack.is(com.simibubi.create.AllItems.WRENCH)||!canInsertIntoFunnel(state)||hit.getDirection()!=getFacing(state))return InteractionResult.PASS;
        if(!level.isClientSide()){var remaining=tryInsert(level,pos,stack,false);if(remaining.getCount()!=stack.getCount())player.setItemInHand(hand,remaining);}return InteractionResult.SUCCESS;
    }
    public boolean canInsertIntoFunnel(BlockState state){return !state.getValue(POWERED)&&!state.getValue(EXTRACTING);}
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity,InsideBlockEffectApplier effects,boolean inside){
        if(level.isClientSide()||!canInsertIntoFunnel(state)||entity.isRemoved())return;
        var before=entity instanceof ItemEntity item?item.getItem():entity instanceof com.simibubi.create.content.logistics.box.PackageEntity box?box.getBox():ItemStack.EMPTY;if(before.isEmpty())return;
        var facing=getFacing(state);var open=Vec3.atCenterOf(pos).add(facing.getUnitVec3().scale(entity instanceof ItemEntity?-.25:-.125));var diff=entity.position().subtract(open);double projection=facing.getAxis().choose(diff.x,diff.y,diff.z);if((projection<0)==(facing.getAxisDirection()==Direction.AxisDirection.POSITIVE))return;
        var be=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos);if(be instanceof FunnelBlockEntity funnel&&funnel.filtering.test(before)&&!com.simibubi.create.content.logistics.box.PackageEntity.centerPackage(entity,open.add(0,facing==Direction.UP?.25:-.5,0)))return;
        var remaining=tryInsert(level,pos,before,false);if(remaining.isEmpty())entity.discard();else if(entity instanceof ItemEntity item&&remaining.getCount()!=before.getCount())item.setItem(remaining);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return FunnelShapes.normal(getFacing(state));}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){if(context instanceof EntityCollisionContext entity&&entity.getEntity() instanceof ItemEntity&&getFacing(state).getAxis().isHorizontal())return FunnelShapes.collision(getFacing(state));return getShape(state,level,pos,context);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighborPos,BlockState neighbor,RandomSource random){
        super.updateShape(state,level,ticks,pos,side,neighborPos,neighbor,random);if(getFacing(state).getAxis().isHorizontal()&&side==Direction.DOWN&&BeltFunnelBlock.isOnValidBelt(state,level,pos))return getEquivalentBeltFunnel(level,pos,state).setValue(BeltFunnelBlock.SHAPE,BeltFunnelBlock.getShapeForPosition(level,pos,getFacing(state),state.getValue(EXTRACTING)));return state;
    }
}
