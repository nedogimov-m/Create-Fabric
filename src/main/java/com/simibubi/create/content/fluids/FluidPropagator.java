package com.simibubi.create.content.fluids;
import java.util.*;
import net.createmod.catnip.data.Pair;
import com.simibubi.create.content.fluids.PipeConnection.Flow;
import com.simibubi.create.content.fluids.pipes.*;
import com.simibubi.create.content.fluids.pump.*;
import net.minecraft.core.*;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
public class FluidPropagator {

	public static void propagateChangedPipe(LevelAccessor world, BlockPos pipePos, BlockState pipeState) {
		List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Set<Pair<PumpBlockEntity, Direction>> discoveredPumps = new HashSet<>();

		frontier.add(Pair.of(0, pipePos));

		// Visit all connected pumps to update their network
		while (!frontier.isEmpty()) {
			Pair<Integer, BlockPos> pair = frontier.remove(0);
			BlockPos currentPos = pair.getSecond();
			if (visited.contains(currentPos))
				continue;
			visited.add(currentPos);
			if(world instanceof Level currentLevel&&!currentLevel.isLoaded(currentPos))continue;
            BlockState currentState = stateAt(world,currentPos);
			FluidTransportBehaviour pipe = getPipe(world, currentPos);
			if (pipe == null)
				continue;
			pipe.wipePressure();

			for (Direction direction : getPipeConnections(currentState, pipe)) {
				BlockPos target = currentPos.relative(direction);
				if (world instanceof Level l && !l.isLoaded(target))
					continue;

				BlockEntity blockEntity = blockEntityAt(world,target);
				BlockState targetState = stateAt(world,target);
				if (blockEntity instanceof PumpBlockEntity) {
					if (!(targetState.getBlock() instanceof PumpBlock) || targetState.getValue(PumpBlock.FACING)
						.getAxis() != direction.getAxis())
						continue;
					discoveredPumps.add(Pair.of((PumpBlockEntity) blockEntity, direction.getOpposite()));
					continue;
				}
				if (visited.contains(target))
					continue;
				FluidTransportBehaviour targetPipe = getPipe(world, target);
				if (targetPipe == null)
					continue;
				Integer distance = pair.getFirst();
				if (distance >= getPumpRange() && !targetPipe.hasAnyPressure())
					continue;
				if (targetPipe.canHaveFlowToward(targetState, direction.getOpposite()))
					frontier.add(Pair.of(distance + 1, target));
			}
		}

		discoveredPumps.forEach(pair -> pair.getFirst()
			.updatePipesOnSide(pair.getSecond()));
	}

	public static void resetAffectedFluidNetworks(Level world, BlockPos start, Direction side) {
		List<BlockPos> frontier = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		frontier.add(start);

		while (!frontier.isEmpty()) {
			BlockPos pos = frontier.remove(0);
			if (visited.contains(pos))
				continue;
			visited.add(pos);
			FluidTransportBehaviour pipe = getPipe(world, pos);
			if (pipe == null)
				continue;

			for (Direction d : Direction.values()) {
				if (pos.equals(start) && d != side)
					continue;
				BlockPos target = pos.relative(d);
				if (visited.contains(target))
					continue;

				PipeConnection connection = pipe.getConnection(d);
				if (connection == null)
					continue;
				if (!connection.hasFlow())
					continue;

				Flow flow = connection.flow.get();
				if (!flow.inbound)
					continue;

				connection.resetNetwork();
				frontier.add(target);
			}
		}
	}

	public static FluidTransportBehaviour getPipe(BlockGetter reader, BlockPos pos) {
		return reader instanceof Level world && !world.isLoaded(pos) ? null : blockEntityAt(reader,pos) instanceof FluidTransportBehaviour.Provider provider ? provider.transport() : null;
	}

	public static boolean isOpenEnd(BlockGetter reader, BlockPos pos, Direction side) {
		BlockPos connectedPos = pos.relative(side);
		if(reader instanceof Level level&&!level.isLoaded(connectedPos))return false;
        BlockState connectedState = stateAt(reader,connectedPos);
		FluidTransportBehaviour pipe = FluidPropagator.getPipe(reader, connectedPos);
		if (pipe != null && pipe.canHaveFlowToward(connectedState, side.getOpposite()))
			return false;
		if (PumpBlock.isPump(connectedState) && connectedState.getValue(PumpBlock.FACING)
			.getAxis() == side.getAxis())
			return false;
		if (VanillaFluidTargets.canProvideFluidWithoutCapability(connectedState))
			return true;
		if (connectedState.isFaceSturdy(reader, connectedPos, side.getOpposite())
			&& !connectedState.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, com.simibubi.create.Create.asResource("fan_transparent"))))
			return false;
		if (hasFluidCapability(reader, connectedPos, side.getOpposite()))
			return false;
		if (!(connectedState.canBeReplaced() && connectedState.getDestroySpeed(reader, connectedPos) != -1)
			&& !connectedState.hasProperty(BlockStateProperties.WATERLOGGED))
			return false;
		return true;
	}

	public static List<Direction> getPipeConnections(BlockState state, FluidTransportBehaviour pipe) {
		List<Direction> list = new ArrayList<>();
		for (Direction d : Direction.values())
			if (pipe.canHaveFlowToward(state, d))
				list.add(d);
		return list;
	}

	public static int getPumpRange() {
		return com.simibubi.create.infrastructure.config.AllConfigs.server().fluids.mechanicalPumpRange.get();
	}

    public static BlockState stateAt(BlockGetter world,BlockPos pos){return com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(world,pos);}
    public static BlockEntity blockEntityAt(BlockGetter world,BlockPos pos){return com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(world,pos);}
    public static net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> fluidStorageAt(Level level,BlockPos pos,Direction side){
        if(!level.isLoaded(pos))return null;return net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level,pos,stateAt(level,pos),blockEntityAt(level,pos),side);
    }
    public static boolean hasFluidCapability(BlockGetter world,BlockPos pos,Direction side){return world instanceof Level level&&fluidStorageAt(level,pos,side)!=null;}

	@Nullable
	public static Axis getStraightPipeAxis(BlockState state) {
		if (state.getBlock() instanceof PumpBlock)
			return state.getValue(PumpBlock.FACING)
				.getAxis();
		if (state.getBlock() instanceof IAxisPipe pipe)
            return pipe.getAxis(state);
		if (!FluidPipeBlock.isPipe(state))
			return null;
		Axis axisFound = null;
		int connections = 0;
		for (Axis axis : Direction.Axis.values()) {
			Direction d1 = Direction.get(AxisDirection.NEGATIVE, axis);
			Direction d2 = Direction.get(AxisDirection.POSITIVE, axis);
			boolean openAt1 = FluidPipeBlock.isOpenAt(state, d1);
			boolean openAt2 = FluidPipeBlock.isOpenAt(state, d2);
			if (openAt1)
				connections++;
			if (openAt2)
				connections++;
			if (openAt1 && openAt2)
				if (axisFound != null)
					return null;
				else
					axisFound = axis;
		}
		return connections == 2 ? axisFound : null;
	}

}
