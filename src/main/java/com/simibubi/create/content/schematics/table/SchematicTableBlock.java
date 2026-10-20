package com.simibubi.create.content.schematics.table;

import com.simibubi.create.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Actual source cardinal table shape/collision, native menu and server upload lifecycle. */
public final class SchematicTableBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape COLLISION=Shapes.or(box(4,0,4,12,2,12),box(5,2,5,11,14,11));
    private static final VoxelShape SOUTH=Shapes.or(box(4,0,4,12,12,12),box(0,11,2,16,14,14)),EAST=Shapes.or(box(4,0,4,12,12,12),box(2,11,0,14,14,16));
    public SchematicTableBlock(Properties properties){super(properties.pushReaction(PushReaction.IMMOVEABLE));registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return COLLISION;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(FACING).getAxis()==Direction.Axis.Z?SOUTH:EAST;}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SchematicTableBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide()?null:createTickerHelper(type,AllBlockEntityTypes.SCHEMATIC_TABLE,SchematicTableBlockEntity::tick);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos) instanceof SchematicTableBlockEntity table&&table.canPlayerUse(player)){if(player instanceof ServerPlayer server)server.openMenu(table);return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return stack.is(AllItems.WRENCH)?InteractionResult.PASS:useWithoutItem(state,level,pos,player,hit);}
}
