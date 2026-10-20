package com.simibubi.create.content.logistics.itemHatch;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
public final class ItemHatchBlock extends HorizontalDirectionalBlock implements EntityBlock,SimpleWaterloggedBlock {
 public static final BooleanProperty OPEN=BlockStateProperties.OPEN,WATERLOGGED=BlockStateProperties.WATERLOGGED;
 private static final TagKey<Item> WRENCHES=TagKey.create(net.minecraft.core.registries.Registries.ITEM,net.minecraft.resources.Identifier.parse("c:tools/wrench"));
 private static final Map<Direction,VoxelShape> SHAPES=shapes();
 public ItemHatchBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(OPEN,false).setValue(WATERLOGGED,false));}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,OPEN,WATERLOGGED);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext c){return c.getClickedFace().getAxis().isVertical()?null:defaultBlockState().setValue(FACING,c.getClickedFace().getOpposite()).setValue(WATERLOGGED,c.getLevel().getFluidState(c.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER));}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ItemHatchBlockEntity(pos,state);}
 @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
 @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
 @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return deposit(stack,state,level,pos,player);}
 public InteractionResult deposit(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player){
  if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
  if(level.isClientSide()||player instanceof FakePlayer)return InteractionResult.SUCCESS;
  if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof ItemHatchBlockEntity hatch))return InteractionResult.FAIL;
  if(player.isShiftKeyDown()&&stack.is(WRENCHES))return InteractionResult.PASS;
  boolean heldOnly=!player.isShiftKeyDown();var result=ItemHatchTransfer.deposit(level,pos,hatch,player,heldOnly);if(result==ItemHatchTransfer.Result.NO_STORAGE)return InteractionResult.FAIL;if(result==ItemHatchTransfer.Result.NO_INSERTION)return InteractionResult.SUCCESS;
  level.playSound(null,pos,AllSoundEvents.ITEM_HATCH,SoundSource.BLOCKS,.25f,1.4f);level.playSound(null,pos,AllSoundEvents.ITEM_HATCH_COMPOUNDED_1,SoundSource.BLOCKS,.75f,1.15f);
  if(LoadedLevelAccess.blockEntity(level,pos)==hatch){level.setBlockAndUpdate(pos,state.setValue(OPEN,true));level.scheduleTick(pos,this,10);}
  player.sendOverlayMessage(Component.translatable(heldOnly?"create.item_hatch.deposit_item":"create.item_hatch.deposit_inventory"));return InteractionResult.SUCCESS;
 }
 @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(state.getValue(OPEN))level.setBlockAndUpdate(pos,state.setValue(OPEN,false));}
 @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPES.get(state.getValue(FACING));}
 @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
 @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
 @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
 private static Map<Direction,VoxelShape> shapes(){var map=new EnumMap<Direction,VoxelShape>(Direction.class);double[][] boxes={{1,0,0,15,16,2},{2,2,0,14,13,3.8},{2,4,0,14,11,5.8},{2,6,0,14,9,7.8}};for(var side:Direction.Plane.HORIZONTAL){var shape=Shapes.empty();for(var b:boxes){double x0=b[0],z0=b[2],x1=b[3],z1=b[5];if(side==Direction.SOUTH){x0=16-b[3];x1=16-b[0];z0=16-b[5];z1=16-b[2];}else if(side==Direction.EAST){x0=16-b[5];x1=16-b[2];z0=b[0];z1=b[3];}else if(side==Direction.WEST){x0=b[2];x1=b[5];z0=16-b[3];z1=16-b[0];}shape=Shapes.or(shape,Block.box(x0,b[1],z0,x1,b[4],z1));}map.put(side,shape);}return Map.copyOf(map);}
}
