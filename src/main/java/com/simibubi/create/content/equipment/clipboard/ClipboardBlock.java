package com.simibubi.create.content.equipment.clipboard;

import com.simibubi.create.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.List;

public final class ClipboardBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock,SimpleWaterloggedBlock {
    public static final BooleanProperty WRITTEN=BooleanProperty.create("written"),WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public ClipboardBlock(Properties properties){super(properties.noOcclusion());registerDefaultState(defaultBlockState().setValue(WRITTEN,false).setValue(WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(WRITTEN,FACE,FACING,WATERLOGGED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=super.getStateForPlacement(context);if(state==null)return null;if(state.getValue(FACE)!=AttachFace.WALL)state=state.setValue(FACING,state.getValue(FACING).getOpposite());return state.setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER)).setValue(WRITTEN,context.getItemInHand().has(AllDataComponents.CLIPBOARD_CONTENT));}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return !level.getBlockState(pos.relative(getConnectedDirection(state).getOpposite())).canBeReplaced();}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var facing=state.getValue(FACING);boolean swap=facing.getAxis()==Direction.Axis.X;return switch(state.getValue(FACE)){case FLOOR->swap?box(1,0,3,15,1,13):box(3,0,1,13,1,15);case CEILING->swap?box(1,15,3,15,16,13):box(3,15,1,13,16,15);case WALL->switch(facing){case SOUTH->box(3,1,0,13,15,1);case NORTH->box(3,1,15,13,15,16);case EAST->box(0,1,3,1,15,13);default->box(15,1,3,16,15,13);};};}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return super.updateShape(state,level,ticks,pos,side,otherPos,other,random);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ClipboardBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return !level.isClientSide()&&type==AllBlockEntityTypes.CLIPBOARD?(world,pos,block,entity)->ClipboardBlockEntity.tick(world,pos,block,(ClipboardBlockEntity)entity):null;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(player.isShiftKeyDown())collect(state,level,pos,player);else if(level.isClientSide()&&ClipboardBlockItem.CLIENT_OPEN!=null)ClipboardBlockItem.CLIENT_OPEN.accept(player,pos);return InteractionResult.SUCCESS;}
    @Override protected void attack(BlockState state,Level level,BlockPos pos,Player player){collect(state,level,pos,player);}
    private void collect(BlockState state,Level level,BlockPos pos,Player player){if(level.isClientSide()||player instanceof FakePlayer||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos)||!player.isWithinBlockInteractionRange(pos,1))return;var stack=getCloneItemStack(level,pos,state,false);if(!level.destroyBlock(pos,false)||level.getBlockState(pos).is(this))return;if(player.getMainHandItem().isEmpty())player.setItemInHand(InteractionHand.MAIN_HAND,stack);else player.getInventory().placeItemBackInInventory(stack,net.minecraft.util.Prediction.SERVER_ONLY);}
    private ItemStack contentStack(BlockEntity entity){var stack=new ItemStack(this);if(entity instanceof ClipboardBlockEntity clipboard)stack.applyComponents(clipboard.components());return stack;}
    @Override protected ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData){return contentStack(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos));}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return List.of(contentStack(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)));}
}
