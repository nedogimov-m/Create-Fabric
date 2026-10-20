package com.simibubi.create.content.logistics.crate;

import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class CreativeCrateBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    private static final VoxelShape SHAPE=Block.box(1,0,1,15,14,15);
    public static final Vec3 FILTER_POSITION=new Vec3(.5,13.5/16,.5);
    public CreativeCrateBlock(Properties properties){super(properties.strength(1.5f,6).mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE).sound(SoundType.STONE));registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CreativeCrateBlockEntity(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getNearestLookingDirection().getOpposite());}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return FilteringSettings.interact(level,player,hand,hit);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return FilteringSettings.interact(level,player,InteractionHand.MAIN_HAND,hit);}
    public static boolean testFilterHit(BlockPos pos,BlockHitResult hit){return hit.getDirection()==Direction.UP&&FILTER_POSITION.distanceTo(hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)))<.25;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
}
