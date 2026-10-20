package com.simibubi.create.content.logistics.packagerLink;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.EnumMap;
import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.*;

public final class PackagerLinkBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<AttachFace> FACE=BlockStateProperties.ATTACH_FACE;
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED,WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private static final EnumMap<Direction,VoxelShape> SHAPES=new EnumMap<>(Direction.class);
    static { SHAPES.put(Direction.UP,Block.box(1,0,1,15,5,15));SHAPES.put(Direction.DOWN,Block.box(1,11,1,15,16,15));SHAPES.put(Direction.NORTH,Block.box(1,1,11,15,15,16));SHAPES.put(Direction.SOUTH,Block.box(1,1,0,15,15,5));SHAPES.put(Direction.WEST,Block.box(11,1,1,16,15,15));SHAPES.put(Direction.EAST,Block.box(0,1,1,5,15,15)); }
    public PackagerLinkBlock(Properties properties) { super(properties.strength(3,6).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK).noOcclusion().isRedstoneConductor((s,l,p)->false));registerDefaultState(defaultBlockState().setValue(FACE,AttachFace.FLOOR).setValue(FACING,Direction.NORTH).setValue(POWERED,false).setValue(WATERLOGGED,false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACE,FACING,POWERED,WATERLOGGED); }
    public static Direction getConnectedDirection(BlockState state) { return switch(state.getValue(FACE)){case FLOOR->Direction.UP;case CEILING->Direction.DOWN;case WALL->state.getValue(FACING);}; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var direction=context.getNearestLookingDirection();
        var state=defaultBlockState().setValue(FACE,direction==Direction.DOWN?AttachFace.FLOOR:direction==Direction.UP?AttachFace.CEILING:AttachFace.WALL).setValue(FACING,direction.getAxis().isHorizontal()?direction.getOpposite():context.getHorizontalDirection());
        if(direction==Direction.UP)state=state.setValue(FACING,state.getValue(FACING).getOpposite());
        return state.setValue(POWERED,getPower(state,context.getLevel(),context.getClickedPos())>0).setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
    }
    public static int getPower(BlockState state,Level level,BlockPos pos) { int result=0;for(var side:Direction.values())if(side.getOpposite()!=getConnectedDirection(state)&&level.isLoaded(pos.relative(side))){var target=pos.relative(side);var neighbor=LoadedLevelAccess.blockState(level,target);int signal=neighbor.getSignal(level,target,side);if(neighbor.isRedstoneConductor(level,target))for(var direct:Direction.values()){var source=target.relative(direct);if(level.isLoaded(source))signal=Math.max(signal,LoadedLevelAccess.blockState(level,source).getDirectSignal(level,source,direct));}result=Math.max(result,signal);}return result; }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moving) {
        if(level.isClientSide())return;int power=getPower(state,level,pos);if(state.getValue(POWERED)!=(power>0))level.setBlock(pos,state.setValue(POWERED,power>0),Block.UPDATE_CLIENTS);
        if(LoadedLevelAccess.blockEntity(level,pos) instanceof PackagerLinkBlockEntity link)link.behaviour.redstonePowerChanged(power);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) { super.setPlacedBy(level,pos,state,placer,stack);if(placer instanceof Player player&&LoadedLevelAccess.blockEntity(level,pos) instanceof PackagerLinkBlockEntity link){link.placedBy=player.getUUID();link.initialize();link.notifyUpdate();} }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighbor,BlockState neighborState,net.minecraft.util.RandomSource random) { if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state; }
    @Override public FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return SHAPES.get(getConnectedDirection(state)); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new PackagerLinkBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,AllBlockEntityTypes.PACKAGER_LINK,PackagerLinkBlockEntity::tick); }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
}
