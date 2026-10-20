package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class ToolboxBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private final DyeColor color;
    public ToolboxBlock(Properties properties,DyeColor color){super(properties);this.color=color;registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(WATERLOGGED,false));}
    public DyeColor getColor(){return color;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,WATERLOGGED);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ToolboxBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.TOOLBOX,ToolboxBlockEntity::tick);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER);}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos otherPos,BlockState other,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(FACING).getAxis()==Direction.Axis.Z?Block.box(1,0,4,15,9,12):Block.box(4,0,1,12,9,15);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected boolean shouldChangedStateKeepBlockEntity(BlockState replacement){return replacement.getBlock() instanceof ToolboxBlock;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack item){super.setPlacedBy(level,pos,state,placer,item);if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos) instanceof ToolboxBlockEntity box){box.readInventory(item.get(AllDataComponents.TOOLBOX_INVENTORY));box.setUniqueId(item.get(AllDataComponents.TOOLBOX_UUID));if(item.has(DataComponents.CUSTOM_NAME))box.setCustomName(item.getHoverName());box.notifyUpdate();}}
    @Override protected InteractionResult useItemOn(ItemStack held,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(player.isCrouching()||player.isSpectator()||!level.mayInteract(player,pos))return InteractionResult.PASS;DyeColor requested=dyeColor(held);if(requested!=null&&requested!=color){if(!level.isClientSide()){var replacement=AllBlocks.TOOLBOXES.get(requested).defaultBlockState().setValue(FACING,state.getValue(FACING)).setValue(WATERLOGGED,state.getValue(WATERLOGGED));level.setBlockAndUpdate(pos,replacement);if(LoadedLevelAccess.blockEntity(level,pos) instanceof ToolboxBlockEntity box)box.notifyUpdate();}return InteractionResult.SUCCESS;}if(player instanceof FakePlayer)return InteractionResult.PASS;if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos) instanceof ToolboxBlockEntity box)player.openMenu(box);return InteractionResult.SUCCESS;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
    @Override protected void attack(BlockState state,Level level,BlockPos pos,Player player){if(level.isClientSide()||player instanceof FakePlayer||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos))return;if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof ToolboxBlockEntity box))return;box.unequipTracked();ItemStack item=getCloneItemStack(level,pos,state,true);if(level.destroyBlock(pos,false)&&!(level.getBlockState(pos).getBlock() instanceof ToolboxBlock))player.getInventory().placeItemBackInInventory(item,Prediction.SERVER_ONLY);}
    @Override protected ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData){ItemStack stack=new ItemStack(this);if(level.getBlockEntity(pos) instanceof ToolboxBlockEntity box)stack.applyComponents(box.collectComponents());return stack;}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side){return LoadedLevelAccess.blockEntity(level,pos) instanceof ToolboxBlockEntity box?AbstractContainerMenu.getRedstoneSignalFromContainer(box.inventory.asContainer()):0;}
    /** Native26.3 dye semantics use the actual DYE data component, including addon dyes. */
    public static DyeColor dyeColor(ItemStack stack){return stack.get(DataComponents.DYE);}
}
