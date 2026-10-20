package com.simibubi.create.content.decoration.copycat;



import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CopycatStepBlock extends WaterloggedCopycatBlock {

	public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;


	public CopycatStepBlock(Properties pProperties) {
		super(pProperties);
		registerDefaultState(defaultBlockState().setValue(HALF, Half.BOTTOM)
			.setValue(FACING, Direction.SOUTH));
	}

    @Override
    protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.isShiftKeyDown()&&player.mayBuild()&&stack.is(asItem()))return CopycatPlacement.extend(this,stack,state,level,pos,player,hand,hit);
        return super.useItemOn(stack,state,level,pos,player,hand,hit);
    }

	@Override
	public boolean isIgnoredConnectivitySide(BlockGetter reader, BlockState state, Direction face,BlockPos fromPos,BlockPos toPos) {
		if (fromPos == null || toPos == null)
			return true;

		BlockState toState = reader.getBlockState(toPos);

		if (!toState.is(this))
			return true;

		Direction facing = state.getValue(FACING);
		BlockPos diff = fromPos.subtract(toPos);
		int coord = facing.getAxis()
			.choose(diff.getX(), diff.getY(), diff.getZ());

		Half half = state.getValue(HALF);
		if (half != toState.getValue(HALF))
			return diff.getY() == 0;

		return facing == toState.getValue(FACING)
			.getOpposite()
			&& !(coord != 0 && coord != facing.getAxisDirection()
			.getStep());
	}

	@Override
	public boolean canConnectTexturesToward(BlockGetter reader, BlockPos fromPos, BlockPos toPos,
											BlockState state) {
		Direction facing = state.getValue(FACING);
		BlockState toState = reader.getBlockState(toPos);
		BlockPos diff = fromPos.subtract(toPos);

		if (fromPos.equals(toPos.relative(facing)))
			return false;
		if (!toState.is(this))
			return false;

		if (diff.getY() != 0) {
			if (isOccluded(toState, state, diff.getY() > 0 ? Direction.UP : Direction.DOWN))
				return true;
			return false;
		}

		if (isOccluded(state, toState, facing))
			return true;

		int coord = facing.getAxis()
			.choose(diff.getX(), diff.getY(), diff.getZ());
		if (state.setValue(WATERLOGGED, false).setValue(LIGHT,0) == toState.setValue(WATERLOGGED, false).setValue(LIGHT,0) && coord == 0)
			return true;

		return false;
	}

	@Override
	public boolean canFaceBeOccluded(BlockState state, Direction face) {
		if (face.getAxis() == Axis.Y)
			return (state.getValue(HALF) == Half.TOP) == (face == Direction.UP);
		return state.getValue(FACING) == face;
	}

	@Override
	public boolean shouldFaceAlwaysRender(BlockState state, Direction face) {
		return canFaceBeOccluded(state, face.getOpposite());
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        if(pContext instanceof CopycatPlacement.Context extended)return extended.template.setValue(WATERLOGGED,super.getStateForPlacement(pContext).getValue(WATERLOGGED)).setValue(LIGHT,0);
		BlockState stateForPlacement =
			super.getStateForPlacement(pContext).setValue(FACING, pContext.getHorizontalDirection());
		Direction direction = pContext.getClickedFace();
		if (direction == Direction.UP)
			return stateForPlacement;
		if (direction == Direction.DOWN || (pContext.getClickLocation().y - pContext.getClickedPos()
			.getY() > 0.5D))
			return stateForPlacement.setValue(HALF, Half.TOP);
		return stateForPlacement;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(HALF, FACING));
	}

	@Override
	public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return CopycatShapes.step(pState.getValue(FACING),pState.getValue(HALF)==Half.TOP);
	}

	@Override
	public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState,
									 Direction dir) {
		if (state.is(this) == neighborState.is(this)
			&& getMaterial(level, pos).skipRendering(getMaterial(level, pos.relative(dir)), dir.getOpposite()))
			return isOccluded(state, neighborState, dir);
		return false;
	}

	public static boolean isOccluded(BlockState state, BlockState other, Direction pDirection) {
		state = state.setValue(WATERLOGGED, false).setValue(LIGHT,0);
		other = other.setValue(WATERLOGGED, false).setValue(LIGHT,0);

		Half half = state.getValue(HALF);
		boolean vertical = pDirection.getAxis() == Axis.Y;
		if (half != other.getValue(HALF))
			return vertical && (pDirection == Direction.UP) == (half == Half.TOP);
		if (vertical)
			return false;

		Direction facing = state.getValue(FACING);
		if (facing.getOpposite() == other.getValue(FACING) && pDirection == facing)
			return true;
		if (other.getValue(FACING) != facing)
			return false;
		return pDirection.getAxis() != facing.getAxis();
	}

	@Override
	public BlockState rotate(BlockState pState, Rotation pRot) {
		return pState.setValue(FACING, pRot.rotate(pState.getValue(FACING)));
	}

	@Override
	@SuppressWarnings("deprecation")
	public BlockState mirror(BlockState pState, Mirror pMirror) {
		return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
	}

}
