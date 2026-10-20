package com.simibubi.create.content.logistics.tableCloth;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.redstoneRequester.AutoRequestData;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class TableClothBlock extends BaseEntityBlock implements com.simibubi.create.foundation.block.IHaveBigOutline {
    public static final BooleanProperty HAS_BE=BooleanProperty.create("entity");
    private static final VoxelShape OUTLINE=box(-1,-9,-1,17,1,17),COLLISION=box(0,0,0,16,1,16);
    private final DyeColor color;
    public TableClothBlock(Properties properties,DyeColor color){super(color==null?properties.requiresCorrectToolForDrops():properties.strength(.1f).sound(SoundType.WOOL).ignitedByLava().mapColor(color.getMapColor()));this.color=color;registerDefaultState(defaultBlockState().setValue(HAS_BE,false));}
    public DyeColor getColor(){return color;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(HAS_BE,context.getItemInHand().has(AllDataComponents.AUTO_REQUEST_DATA));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HAS_BE);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.getValue(HAS_BE)?new TableClothBlockEntity(pos,state):null;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return OUTLINE;}
    @Override protected VoxelShape getInteractionShape(BlockState state,BlockGetter level,BlockPos pos){return OUTLINE;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return COLLISION;}
    @Override protected VoxelShape getOcclusionShape(BlockState state){return COLLISION;}
    @Override protected boolean skipRendering(BlockState state,BlockState neighbor,Direction direction){return direction.getAxis().isHorizontal()&&neighbor.getBlock() instanceof TableClothBlock||super.skipRendering(state,neighbor,direction);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.TABLE_CLOTH,(l,p,s,cloth)->cloth.tick());}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);if(level.isClientSide()||!(placer instanceof Player player))return;var data=AutoRequestData.readFromItem(level,player,pos,stack);if(data==null)return;
        level.setBlockAndUpdate(pos,state.setValue(HAS_BE,true));if(LoadedLevelAccess.blockEntity(level,pos) instanceof TableClothBlockEntity cloth){cloth.requestData=data;cloth.owner=player.getUUID();cloth.facing=player.getDirection().getOpposite();cloth.notifyUpdate();com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.award(player,"table_cloth_shop");}
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return interact(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return interact(stack,state,level,pos,player,hand,hit);}
    private InteractionResult interact(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(hand!=InteractionHand.MAIN_HAND||hit.getDirection()==Direction.DOWN||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos)||stack.is(AllItems.WRENCH))return InteractionResult.PASS;
        if(stack.getItem() instanceof TableClothBlockItem){
            if(player.isShiftKeyDown())return InteractionResult.PASS;if(!level.isClientSide()){
                var target=java.util.stream.Stream.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST).filter(side->LoadedLevelAccess.blockState(level,pos.relative(side)).canBeReplaced()).min(java.util.Comparator.comparingDouble(side->Vec3.atCenterOf(pos.relative(side)).distanceToSqr(hit.getLocation()))).orElse(null);
                if(target!=null)stack.useOn(new UseOnContext(player,hand,new BlockHitResult(Vec3.atCenterOf(pos.relative(target)).add(0,-.5,0),Direction.UP,pos.relative(target).below(),false)));
            }return InteractionResult.SUCCESS;
        }
        if(!state.getValue(HAS_BE)&&(player.isShiftKeyDown()||stack.isEmpty()))return InteractionResult.PASS;
        if(!level.isClientSide()){if(!state.getValue(HAS_BE))level.setBlockAndUpdate(pos,state.setValue(HAS_BE,true));if(LoadedLevelAccess.blockEntity(level,pos) instanceof TableClothBlockEntity cloth)cloth.use(player);}
        return InteractionResult.SUCCESS;
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder params){
        var drops=super.getDrops(state,params);if(params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof TableClothBlockEntity cloth&&cloth.isShop()&&drops.stream().anyMatch(stack->stack.getItem() instanceof TableClothBlockItem)){var drop=new ItemStack(this);cloth.requestData.writeToItem(cloth.getBlockPos(),drop);return java.util.List.of(drop);}return drops;
    }
}
