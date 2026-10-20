package com.simibubi.create.content.fluids.pump;
import java.util.*;
import org.jetbrains.annotations.Nullable;
import com.simibubi.create.*;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createmod.catnip.data.*;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class PumpBlockEntity extends KineticBlockEntity implements FluidTransportBehaviour.Provider, net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"mechanical_pump_0","water_supply","cross_streams","honey_drain");
    private final Couple<Boolean> sidesToUpdate=Couple.create(true,true);
    private float previousSpeed=Float.NaN;
    private Direction previousFacing;
    private boolean scheduleFlip;
    private final FluidTransportBehaviour transport=new FluidTransportBehaviour(this){
        @Override public boolean canHaveFlowToward(BlockState state,Direction side){return state.getBlock() instanceof PumpBlock&&PumpBlock.isOpenAt(state,side);}
    };
    public PumpBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.MECHANICAL_PUMP,pos,state);}
    @Override public FluidTransportBehaviour transport(){return getBehaviour(FluidTransportBehaviour.TYPE);}
    @Override public Object getRenderData(){return transport.getRenderData();}
    public static void tick(Level level,BlockPos pos,BlockState state,PumpBlockEntity pump){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, pump)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,pump);
        if(!level.isClientSide() || pump.isVirtual()){
            if(pump.scheduleFlip){pump.scheduleFlip=false;level.setBlockAndUpdate(pos,state.setValue(PumpBlock.FACING,state.getValue(PumpBlock.FACING).getOpposite()));return;}
            if(Math.abs(pump.getSpeed())!=pump.previousSpeed||pump.previousFacing!=state.getValue(PumpBlock.FACING)){
                pump.previousSpeed=Math.abs(pump.getSpeed());pump.previousFacing=state.getValue(PumpBlock.FACING);pump.updatePressureChange();
            }
            for(boolean front:new boolean[]{true,false})if(pump.sidesToUpdate.get(front)){pump.sidesToUpdate.set(front,false);pump.distributePressureTo(front?pump.getFront():pump.getFront().getOpposite());}
        }
        
        for(var entry:pump.transport.interfaces.entrySet()){
            boolean pull=pump.isPullingOnSide(pump.isFront(entry.getKey()));var pressure=entry.getValue().getPressure();pressure.set(pull,Math.abs(pump.getSpeed()));pressure.set(!pull,0f);
        }
    
        }
    }
    @Override public void onSpeedChanged(float previousSpeed){
        super.onSpeedChanged(previousSpeed);
        if (Math.abs(previousSpeed) == Math.abs(getSpeed())) return;
        if (getSpeed() != 0) sourceAdvancements.award("mechanical_pump_0");
        if (level != null && (!level.isClientSide() || isVirtual())) updatePressureChange();
    }
    public void updatePressureChange(){
        for(var side:new Direction[]{getFront(),getFront().getOpposite()}){var next=worldPosition.relative(side);if(level.isLoaded(next))FluidPropagator.propagateChangedPipe(level,next,FluidPropagator.stateAt(level,next));}
        transport.wipePressure();sidesToUpdate.replace(value->true);
    }
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);scheduleFlip=in.getBooleanOr("Reversed",false);previousSpeed=Float.NaN;}
	protected void distributePressureTo(Direction side) {
		if (getSpeed() == 0)
			return;

		BlockFace start = new BlockFace(worldPosition, side);
		boolean pull = isPullingOnSide(isFront(side));
		Set<BlockFace> targets = new HashSet<>();
		Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

		if (!pull)
			FluidPropagator.resetAffectedFluidNetworks(level, worldPosition, side.getOpposite());

		if (!hasReachedValidEndpoint(level, start, pull)) {

			pipeGraph.computeIfAbsent(worldPosition, $ -> Pair.of(0, new IdentityHashMap<>()))
				.getSecond()
				.put(side, pull);
			pipeGraph.computeIfAbsent(start.getConnectedPos(), $ -> Pair.of(1, new IdentityHashMap<>()))
				.getSecond()
				.put(side.getOpposite(), !pull);

			List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
			Set<BlockPos> visited = new HashSet<>();
			int maxDistance = FluidPropagator.getPumpRange();
			frontier.add(Pair.of(1, start.getConnectedPos()));

			while (!frontier.isEmpty()) {
				Pair<Integer, BlockPos> entry = frontier.remove(0);
				int distance = entry.getFirst();
				BlockPos currentPos = entry.getSecond();

				if (!level.isLoaded(currentPos))
					continue;
				if (visited.contains(currentPos))
					continue;
				visited.add(currentPos);
				BlockState currentState = FluidPropagator.stateAt(level,currentPos);
				FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
				if (pipe == null)
					continue;

				for (Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
					BlockFace blockFace = new BlockFace(currentPos, face);
					BlockPos connectedPos = blockFace.getConnectedPos();

					if (!level.isLoaded(connectedPos))
						continue;
					if (blockFace.isEquivalent(start))
						continue;
					if (hasReachedValidEndpoint(level, blockFace, pull)) {
						pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
							.getSecond()
							.put(face, pull);
						targets.add(blockFace);
						continue;
					}

					FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, connectedPos);
					if (pipeBehaviour == null)
						continue;
					if (pipeBehaviour.blockEntity instanceof PumpBlockEntity)
						continue;
					if (visited.contains(connectedPos))
						continue;
					if (distance + 1 >= maxDistance) {
						pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
							.getSecond()
							.put(face, pull);
						targets.add(blockFace);
						continue;
					}

					pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
						.getSecond()
						.put(face, pull);
					pipeGraph.computeIfAbsent(connectedPos, $ -> Pair.of(distance + 1, new IdentityHashMap<>()))
						.getSecond()
						.put(face.getOpposite(), !pull);
					frontier.add(Pair.of(distance + 1, connectedPos));
				}
			}
		}

		// DFS
		Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
		searchForEndpointRecursively(pipeGraph, targets, validFaces,
			new BlockFace(start.getPos(), start.getOppositeFace()), pull);

		float pressure = Math.abs(getSpeed());
		for (Set<BlockFace> set : validFaces.values()) {
			int parallelBranches = Math.max(1, set.size() - 1);
			for (BlockFace face : set) {
				BlockPos pipePos = face.getPos();
				Direction pipeSide = face.getFace();

				if (pipePos.equals(worldPosition))
					continue;

				boolean inbound = pipeGraph.get(pipePos)
					.getSecond()
					.get(pipeSide);
				FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, pipePos);
				if (pipeBehaviour == null)
					continue;

				pipeBehaviour.addPressure(pipeSide, inbound, pressure / parallelBranches);
			}
		}

	}

	protected boolean searchForEndpointRecursively(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
												   Set<BlockFace> targets, Map<Integer, Set<BlockFace>> validFaces, BlockFace currentFace, boolean pull) {
		BlockPos currentPos = currentFace.getPos();
		if (!pipeGraph.containsKey(currentPos))
			return false;
		Pair<Integer, Map<Direction, Boolean>> pair = pipeGraph.get(currentPos);
		int distance = pair.getFirst();

		boolean atLeastOneBranchSuccessful = false;
		for (Direction nextFacing : Direction.values()) {
			if (nextFacing == currentFace.getFace())
				continue;
			Map<Direction, Boolean> map = pair.getSecond();
			if (!map.containsKey(nextFacing))
				continue;

			BlockFace localTarget = new BlockFace(currentPos, nextFacing);
			if (targets.contains(localTarget)) {
				validFaces.computeIfAbsent(distance, $ -> new HashSet<>())
					.add(localTarget);
				atLeastOneBranchSuccessful = true;
				continue;
			}

			if (map.get(nextFacing) != pull)
				continue;
			if (!searchForEndpointRecursively(pipeGraph, targets, validFaces,
				new BlockFace(currentPos.relative(nextFacing), nextFacing.getOpposite()), pull))
				continue;

			validFaces.computeIfAbsent(distance, $ -> new HashSet<>())
				.add(localTarget);
			atLeastOneBranchSuccessful = true;
		}

		if (atLeastOneBranchSuccessful)
			validFaces.computeIfAbsent(distance, $ -> new HashSet<>())
				.add(currentFace);

		return atLeastOneBranchSuccessful;
	}

	private boolean hasReachedValidEndpoint(LevelAccessor world, BlockFace blockFace, boolean pull) {
		BlockPos connectedPos = blockFace.getConnectedPos();
        if(world instanceof Level check&&!check.isLoaded(connectedPos))return false;
		BlockState connectedState = FluidPropagator.stateAt(world,connectedPos);
		BlockEntity blockEntity = FluidPropagator.blockEntityAt(world,connectedPos);
		Direction face = blockFace.getFace();

		// facing a pump
		if (PumpBlock.isPump(connectedState) && connectedState.getValue(PumpBlock.FACING)
			.getAxis() == face.getAxis() && blockEntity instanceof PumpBlockEntity pumpBE) {
			return pumpBE.isPullingOnSide(pumpBE.isFront(blockFace.getOppositeFace())) != pull;
		}

		// other pipe, no endpoint
		FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, connectedPos);
		if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace()))
			return false;

		// fluid handler endpoint
		if (blockEntity != null) {
			if (FluidPropagator.fluidStorageAt(blockEntity.getLevel(), blockEntity.getBlockPos(),face.getOpposite()) != null)
				return true;
		}

		// open endpoint
		return FluidPropagator.isOpenEnd(world, blockFace.getPos(), face);
	}

	public void updatePipesOnSide(Direction side) {
		if (!isSideAccessible(side))
			return;
		updatePipeNetwork(isFront(side));
		transport.wipePressure();
	}

	protected boolean isFront(Direction side) {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof PumpBlock))
			return false;
		Direction front = blockState.getValue(PumpBlock.FACING);
		boolean isFront = side == front;
		return isFront;
	}

	@Nullable
	protected Direction getFront() {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof PumpBlock))
			return null;
		return blockState.getValue(PumpBlock.FACING);
	}

	protected void updatePipeNetwork(boolean front) {
		sidesToUpdate.set(front, true);
	}

	public boolean isSideAccessible(Direction side) {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof PumpBlock))
			return false;
		return blockState.getValue(PumpBlock.FACING)
			.getAxis() == side.getAxis();
	}

	public boolean isPullingOnSide(boolean front) {
		return !front;
	}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (transport != null) behaviours.add(transport);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("mechanical_pump_0");
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
