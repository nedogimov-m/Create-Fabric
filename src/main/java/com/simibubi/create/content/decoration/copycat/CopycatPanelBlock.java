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
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CopycatPanelBlock extends WaterloggedCopycatBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;


	public CopycatPanelBlock(Properties pProperties) {
		super(pProperties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
	}

	@Override
	public boolean isAcceptedRegardless(BlockState material) {
		return CopycatSpecialCases.isBarsMaterial(material) || CopycatSpecialCases.isTrapdoorMaterial(material);
	}

	@Override
	public BlockState prepareMaterial(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer,
									  InteractionHand pHand, BlockHitResult pHit, BlockState material) {
		if (!CopycatSpecialCases.isTrapdoorMaterial(material))
			return super.prepareMaterial(pLevel, pPos, pState, pPlayer, pHand, pHit, material);

		Direction panelFacing = pState.getValue(FACING);
		if (panelFacing == Direction.DOWN)
			material = material.setValue(TrapDoorBlock.HALF, Half.TOP);
		if (panelFacing.getAxis() == Axis.Y)
			return material.setValue(TrapDoorBlock.FACING, pPlayer.getDirection())
				.setValue(TrapDoorBlock.OPEN, false);

		boolean clickedNearTop = pHit.getLocation().y - .5 > pPos.getY();
		return material.setValue(TrapDoorBlock.OPEN, true)
			.setValue(TrapDoorBlock.HALF, clickedNearTop ? Half.TOP : Half.BOTTOM)
			.setValue(TrapDoorBlock.FACING, panelFacing);
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

		Direction facing = state.getValue(FACING);
		BlockState toState = reader.getBlockState(toPos);

		if (!toState.is(this))
			return facing != face.getOpposite();

		BlockPos diff = fromPos.subtract(toPos);
		int coord = facing.getAxis()
			.choose(diff.getX(), diff.getY(), diff.getZ());
		return facing == toState.getValue(FACING)
			.getOpposite()
			&& !(coord != 0 && coord == facing.getAxisDirection()
			.getStep());
	}

	@Override
	public boolean canConnectTexturesToward(BlockGetter reader, BlockPos fromPos, BlockPos toPos,
											BlockState state) {
		Direction facing = state.getValue(FACING);
		BlockState toState = reader.getBlockState(toPos);

		if (toPos.equals(fromPos.relative(facing)))
			return false;

		BlockPos diff = fromPos.subtract(toPos);
		int coord = facing.getAxis()
			.choose(diff.getX(), diff.getY(), diff.getZ());

		if (!toState.is(this))
			return coord != -facing.getAxisDirection()
				.getStep();

		if (isOccluded(state, toState, facing))
			return true;
		if (toState.setValue(WATERLOGGED, false).setValue(LIGHT,0) == state.setValue(WATERLOGGED, false).setValue(LIGHT,0) && coord == 0)
			return true;

		return false;
	}

	@Override
	public boolean canFaceBeOccluded(BlockState state, Direction face) {
		return state.getValue(FACING)
			.getOpposite() == face;
	}

	@Override
	public boolean shouldFaceAlwaysRender(BlockState state, Direction face) {
		return canFaceBeOccluded(state, face.getOpposite());
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
        if(pContext instanceof CopycatPlacement.Context extended)return extended.template.setValue(WATERLOGGED,stateForPlacement.getValue(WATERLOGGED)).setValue(LIGHT,0);
		return stateForPlacement.setValue(FACING, pContext.getNearestLookingDirection()
			.getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACING));
	}

	@Override
	public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return CopycatShapes.panel(pState.getValue(FACING));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState,
									 Direction dir) {
		if (state.is(this) == neighborState.is(this)) {
			if (CopycatSpecialCases.isBarsMaterial(getMaterial(level, pos))
				&& CopycatSpecialCases.isBarsMaterial(getMaterial(level, pos.relative(dir))))
				return state.getValue(FACING) == neighborState.getValue(FACING);
			if (getMaterial(level, pos).skipRendering(getMaterial(level, pos.relative(dir)), dir.getOpposite()))
				return isOccluded(state, neighborState, dir.getOpposite());
		}

		return state.getValue(FACING) == dir.getOpposite()
			&& getMaterial(level, pos).skipRendering(neighborState, dir.getOpposite());
	}

	public static boolean isOccluded(BlockState state, BlockState other, Direction pDirection) {
		state = state.setValue(WATERLOGGED, false).setValue(LIGHT,0);
		other = other.setValue(WATERLOGGED, false).setValue(LIGHT,0);
		Direction facing = state.getValue(FACING);
		if (facing.getOpposite() == other.getValue(FACING) && pDirection == facing)
			return true;
		if (other.getValue(FACING) != facing)
			return false;
		return pDirection.getAxis() != facing.getAxis();
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	@SuppressWarnings("deprecation")
	public BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
	}

}
