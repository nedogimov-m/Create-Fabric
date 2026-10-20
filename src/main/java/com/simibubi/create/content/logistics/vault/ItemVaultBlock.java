package com.simibubi.create.content.logistics.vault;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;

public final class ItemVaultBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction.Axis> HORIZONTAL_AXIS=BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty LARGE=BooleanProperty.create("large");
    public ItemVaultBlock(Properties properties){super(properties.strength(3,1200).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK).mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_BLUE));registerDefaultState(defaultBlockState().setValue(HORIZONTAL_AXIS,Direction.Axis.Z).setValue(LARGE,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HORIZONTAL_AXIS,LARGE);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ItemVaultBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.ITEM_VAULT,ItemVaultBlockEntity::tick);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        if(context.getPlayer()==null||!context.getPlayer().isShiftKeyDown()){var placedOn=LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos().relative(context.getClickedFace().getOpposite()));if(placedOn.is(this))return defaultBlockState().setValue(HORIZONTAL_AXIS,placedOn.getValue(HORIZONTAL_AXIS));}
        return defaultBlockState().setValue(HORIZONTAL_AXIS,context.getHorizontalDirection().getAxis());
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moved){if(!level.isClientSide()&&previous.getBlock()!=this&&!moved&&LoadedLevelAccess.blockEntity(level,pos) instanceof ItemVaultBlockEntity vault)vault.requestConnectivityUpdate();}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,Orientation orientation,boolean moved){if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos) instanceof ItemVaultBlockEntity vault)vault.requestConnectivityUpdate();}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){if(!context.getClickedFace().getAxis().isVertical())return InteractionResult.PASS;if(!context.getLevel().isClientSide()&&LoadedLevelAccess.blockEntity(context.getLevel(),context.getClickedPos()) instanceof ItemVaultBlockEntity vault){ConnectivityHandler.splitMulti(vault);vault.removeController(true);context.getLevel().setBlockAndUpdate(context.getClickedPos(),state.setValue(LARGE,false).cycle(HORIZONTAL_AXIS));}return InteractionResult.SUCCESS;}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side){return LoadedLevelAccess.blockEntity(level,pos) instanceof ItemVaultBlockEntity vault?vault.comparatorOutput():0;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(HORIZONTAL_AXIS,rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE,state.getValue(HORIZONTAL_AXIS))).getAxis());}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state;}
}
