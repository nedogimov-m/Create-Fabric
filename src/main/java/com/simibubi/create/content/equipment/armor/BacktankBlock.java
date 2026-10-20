package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.*;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.schematics.SchematicWorldAccess;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import java.util.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public class BacktankBlock extends HorizontalKineticBlock implements SimpleWaterloggedBlock,SpecialBlockItemRequirement {
    private static final VoxelShape SHAPE=Shapes.or(box(3,0,3,13,12,13),box(5,0,5,11,16,11));
    public BacktankBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(BlockStateProperties.WATERLOGGED);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BacktankBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.BACKTANK,BacktankBlockEntity::tick);}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction face){return face==Direction.UP;}
    @Override public Direction.Axis getRotationAxis(BlockState state){return Direction.Axis.Y;}
    @Override public FluidState getFluidState(BlockState state){return state.getValue(BlockStateProperties.WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return super.getStateForPlacement(context).setValue(BlockStateProperties.WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos neighborPos,BlockState neighbor,net.minecraft.util.RandomSource random){if(state.getValue(BlockStateProperties.WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction direction){return SchematicWorldAccess.blockEntity(level,pos) instanceof BacktankBlockEntity tank?tank.getComparatorOutput():0;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(level.isClientSide()||!(SchematicWorldAccess.blockEntity(level,pos) instanceof BacktankBlockEntity tank))return;int capacity=0;for(var enchantment:stack.getEnchantments().entrySet())if(enchantment.getKey().is(AllEnchantments.CAPACITY)){capacity=enchantment.getIntValue();break;}tank.setCapacityEnchantLevel(capacity);tank.setAirLevel(stack.getOrDefault(AllDataComponents.BACKTANK_AIR,0));tank.setCustomName(stack.get(DataComponents.CUSTOM_NAME));tank.setComponentPatch(stack.getComponentsPatch());if(placer instanceof net.minecraft.server.level.ServerPlayer player)tank.setPlacedBy(player);tank.notifyUpdate();}
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder params){var drops=super.getDrops(state,params);if(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BacktankBlockEntity tank){var patch=tank.getComponentPatch().forget(component->component==AllDataComponents.BACKTANK_AIR);for(var drop:drops)if(drop.getItem() instanceof BacktankItem){drop.applyComponents(patch);drop.set(AllDataComponents.BACKTANK_AIR,tank.getAirLevel());}}return drops;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return equip(state,level,pos,player);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return equip(state,level,pos,player);}
    private InteractionResult equip(BlockState state,Level level,BlockPos pos,Player player){
        if(player instanceof FakePlayer||player.isShiftKeyDown()||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos)||player.getMainHandItem().getItem() instanceof BlockItem||!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty())return InteractionResult.PASS;
        if(!level.isClientSide()){
            if(!(SchematicWorldAccess.blockEntity(level,pos) instanceof BacktankBlockEntity tank))return InteractionResult.PASS;var item=getCloneItemStack(level,pos,state,true);
            if(SchematicWorldAccess.blockEntity(level,pos)!=tank||!level.destroyBlock(pos,false)||SchematicWorldAccess.blockEntity(level,pos)==tank)return InteractionResult.PASS;
            if(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty())player.setItemSlot(EquipmentSlot.CHEST,item);else player.getInventory().placeItemBackInInventory(item,Prediction.SERVER_ONLY);
            level.playSound(null,pos,SoundEvents.ITEM_PICKUP,SoundSource.PLAYERS,.75f,1);
        }return InteractionResult.SUCCESS;
    }
    @Override public Item asItem(){return this==AllBlocks.NETHERITE_BACKTANK?AllItems.NETHERITE_BACKTANK:AllItems.COPPER_BACKTANK;}
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData){var stack=new ItemStack(asItem());if(SchematicWorldAccess.blockEntity(level,pos) instanceof BacktankBlockEntity tank){stack.applyComponents(tank.getComponentPatch());stack.set(AllDataComponents.BACKTANK_AIR,tank.getAirLevel());}return stack;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    public Class<BacktankBlockEntity> getBlockEntityClass(){return BacktankBlockEntity.class;}
    public BlockEntityType<? extends BacktankBlockEntity> getBlockEntityType(){return AllBlockEntityTypes.BACKTANK;}
    @Override public ItemRequirement getRequiredItems(BlockState state,BlockEntity entity){return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME,new ItemStack(asItem()));}
}
