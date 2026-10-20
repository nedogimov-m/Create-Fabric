package com.simibubi.create.content.kinetics;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import java.util.LinkedList;
import net.minecraft.world.level.block.state.BlockState;

/** Shaft and cog connection rules adapted from Create 6.0.10 RotationPropagator. */
public class RotationPropagator {
    public static double getRotationSpeedModifier(LevelReader level, BlockPos fromPos, BlockState from,
                                                  BlockPos toPos, BlockState to) {
        if (!(from.getBlock() instanceof IRotate fromBlock) || !(to.getBlock() instanceof IRotate toBlock)) {
            return 0;
        }
        double gantry = com.simibubi.create.content.contraptions.gantry.GantryKinetics.speedModifier(fromPos, from, toPos, to);
        if (gantry != 0) return gantry;
        BlockPos diff = toPos.subtract(fromPos);
        if (com.simibubi.create.content.kinetics.belt.BeltBlock.areConnected(fromPos, from, toPos, to)) return 1;
        if (diff.distManhattan(BlockPos.ZERO) == 1 && fromBlock instanceof ChainDriveBlock && toBlock instanceof ChainDriveBlock) {
            Direction face = Direction.getNearest(diff, Direction.NORTH);
            if (ChainDriveBlock.areBlocksConnected(from, to, face)
                    && ChainDriveBlock.areBlocksConnected(to, from, face.getOpposite())) {
                return ChainDriveBlock.speedModifier(level, fromPos, from) / ChainDriveBlock.speedModifier(level, toPos, to);
            }
        }
        if (diff.distManhattan(BlockPos.ZERO) == 1) {
            Direction direction = Direction.getNearest(diff, Direction.NORTH);
            if (fromBlock.hasShaftTowards(level, fromPos, from, direction)
                    && toBlock.hasShaftTowards(level, toPos, to, direction.getOpposite())) {
                return 1;
            }
        }
        if (!(fromBlock instanceof ICogWheel fromCog) || !(toBlock instanceof ICogWheel toCog)) {
            return 0;
        }
        Direction.Axis fromAxis = fromBlock.getRotationAxis(from);
        Direction.Axis toAxis = toBlock.getRotationAxis(to);
        if (fromCog.isLargeCog() && toCog.isLargeCog()) {
            if (fromAxis == toAxis) {
                return 0;
            }
            for (Direction.Axis axis : Direction.Axis.values()) {
                int delta = coordinate(axis, diff);
                if (axis == fromAxis || axis == toAxis) {
                    if (Math.abs(delta) != 1) {
                        return 0;
                    }
                } else if (delta != 0) {
                    return 0;
                }
            }
            return (coordinate(fromAxis, diff) > 0) ^ (coordinate(toAxis, diff) > 0) ? -1 : 1;
        }
        if (fromAxis != toAxis || coordinate(fromAxis, diff) != 0) {
            return 0;
        }
        if (fromCog.isLargeCog() != toCog.isLargeCog()) {
            for (Direction.Axis axis : Direction.Axis.values()) {
                if (axis != fromAxis && Math.abs(coordinate(axis, diff)) != 1) {
                    return 0;
                }
            }
            return fromCog.isLargeCog() ? -2 : -0.5;
        }
        return diff.distManhattan(BlockPos.ZERO) == 1 ? -1 : 0;
    }

    /** BE extension points feed the same native port graph, without applying its internal port ratio twice. */
    static record GraphConnection(double ratio,boolean custom) {}
    public static double getGraphSpeedModifier(KineticBlockEntity from,KineticBlockEntity to) {
        return getGraphConnection(from,to).ratio();
    }
    static GraphConnection getGraphConnection(KineticBlockEntity from, KineticBlockEntity to) {
        var a=from.getBlockState(); var b=to.getBlockState();
        if (!(a.getBlock() instanceof IRotate left) || !(b.getBlock() instanceof IRotate right)) return new GraphConnection(0,false);
        BlockPos diff=to.getBlockPos().subtract(from.getBlockPos());
        Direction face=Direction.getNearest(diff, Direction.NORTH);
        boolean aligned=diff.distManhattan(BlockPos.ZERO)==1;
        boolean shafts=aligned && left.hasShaftTowards(from.getLevel(),from.getBlockPos(),a,face)
            && right.hasShaftTowards(to.getLevel(),to.getBlockPos(),b,face.getOpposite());
        boolean cogs=a.getBlock() instanceof ICogWheel ac && !ac.isLargeCog()
            && b.getBlock() instanceof ICogWheel bc && !bc.isLargeCog();
        float custom=from.propagateRotationTo(to,a,b,diff,shafts,cogs);
        if (custom!=0) return new GraphConnection(custom,true);
        if (from instanceof com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity conveyor
                && to instanceof com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity target
                && conveyor.isConnectedTo(target)) return new GraphConnection(1,false);
        return new GraphConnection(getRotationSpeedModifier(from.getLevel(),from.getBlockPos(),a,to.getBlockPos(),b),false);
    }
    private static float getRotationSpeedModifier(KineticBlockEntity from, KineticBlockEntity to) {
        var connection=getGraphConnection(from,to);
        if(connection.custom()) return (float)connection.ratio();
        var diff=to.getBlockPos().subtract(from.getBlockPos());
        var face=Direction.getNearest(diff,Direction.NORTH);
        if(diff.distManhattan(BlockPos.ZERO)==1
                && from.getBlockState().getBlock() instanceof IRotate a
                && to.getBlockState().getBlock() instanceof IRotate b
                && a.hasShaftTowards(from.getLevel(),from.getBlockPos(),from.getBlockState(),face)
                && b.hasShaftTowards(to.getLevel(),to.getBlockPos(),to.getBlockState(),face.getOpposite())) {
            float targetModifier=getAxisModifier(to,face.getOpposite());
            return targetModifier==0?0:getAxisModifier(from,face)/targetModifier;
        }
        return (float)connection.ratio();
    }
    private static float getAxisModifier(KineticBlockEntity be,Direction direction) {
        if (!(be.hasSource()||be.isSource())) return 1;
        var source=be.getKineticSourceFace();
        if(be instanceof com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity split)
            return split.getRotationSpeedModifier(direction);
        if(source==null) return 1;
        if(be.getBlockState().getBlock() instanceof com.simibubi.create.content.kinetics.gearbox.GearboxBlock box)
            return (float)(box.getPortModifier(be.getBlockState(),direction)/box.getPortModifier(be.getBlockState(),source));
        // Existing native aliases retain the same port behavior until actual source factories replace them.
        if(be.getBlockState().getBlock() instanceof com.simibubi.create.content.kinetics.transmission.ClutchBlock
                && direction!=source && be.getBlockState().getValue(com.simibubi.create.content.kinetics.transmission.GearshiftBlock.POWERED)) return 0;
        if(be.getBlockState().getBlock() instanceof com.simibubi.create.content.kinetics.transmission.GearshiftBlock
                && direction!=source && be.getBlockState().getValue(com.simibubi.create.content.kinetics.transmission.GearshiftBlock.POWERED)) return -1;
        return 1;
    }
    private static boolean isLargeCogToSpeedController(net.minecraft.world.level.block.state.BlockState from,
            net.minecraft.world.level.block.state.BlockState to,BlockPos diff) {
        return diff.equals(BlockPos.ZERO.below())
            && com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock.connectsToCog(to,from);
    }
    private static float getConveyedSpeed(KineticBlockEntity from,KineticBlockEntity to) {
        if(isLargeCogToSpeedController(from.getBlockState(),to.getBlockState(),to.getBlockPos().subtract(from.getBlockPos())))
            return com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity.getConveyedSpeed(from,to,true);
        if(isLargeCogToSpeedController(to.getBlockState(),from.getBlockState(),from.getBlockPos().subtract(to.getBlockPos())))
            return com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity.getConveyedSpeed(to,from,false);
        return from.getTheoreticalSpeed()*getRotationSpeedModifier(from,to);
    }

	public static void handleAdded(Level worldIn, BlockPos pos, KineticBlockEntity addedTE) {
		if (worldIn == null || worldIn.isClientSide())
			return;
		if (addedTE.isVirtual() || !(worldIn instanceof ServerLevel server) || !KineticNetworks.isLive(server, addedTE))
			return;
		KineticNetworks.track(server, addedTE);
		KineticNetworks.markDirty(server);
		propagateNewSource(addedTE);
	}

	/**
	 * Search for sourceless networks attached to the given entity and update them.
	 *
	 * @param currentTE
	 */
	private static void propagateNewSource(KineticBlockEntity currentTE) {
		BlockPos pos = currentTE.getBlockPos();
		Level world = currentTE.getLevel();
        if (!(world instanceof ServerLevel server) || currentTE.isVirtual() || !KineticNetworks.isLive(server,currentTE)) return;

		for (KineticBlockEntity neighbourTE : getConnectedNeighbours(currentTE)) {
            if (!KineticNetworks.isLive(server,currentTE)) return;
            if (!KineticNetworks.isLive(server,neighbourTE)) continue;
			if (currentTE instanceof com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity shaft)
                shaft.followDrivenDirection(getConveyedSpeed(neighbourTE,currentTE));
            if (neighbourTE instanceof com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity shaft)
                shaft.followDrivenDirection(getConveyedSpeed(currentTE,neighbourTE));
            float speedOfCurrent = currentTE.getTheoreticalSpeed();
			float speedOfNeighbour = neighbourTE.getTheoreticalSpeed();
			float newSpeed = getConveyedSpeed(currentTE, neighbourTE);
			float oppositeSpeed = getConveyedSpeed(neighbourTE, currentTE);

			if (newSpeed == 0 && oppositeSpeed == 0)
				continue;

			boolean incompatible =
				Math.signum(newSpeed) != Math.signum(speedOfNeighbour) && (newSpeed != 0 && speedOfNeighbour != 0);

			boolean tooFast = Math.abs(newSpeed) > KineticNetworks.maxRotationSpeed()
				|| Math.abs(oppositeSpeed) > KineticNetworks.maxRotationSpeed();
			// Check for both the new speed and the opposite speed, just in case

			boolean speedChangedTooOften = currentTE.getFlickerScore() > 128;
			if (tooFast || speedChangedTooOften) {
				KineticNetworks.destroyForConflict(currentTE);
				return;
			}

			// Opposite directions
			if (incompatible) {
				KineticNetworks.destroyForConflict(currentTE);
				return;

				// Same direction: overpower the slower speed
			} else {

				// Neighbour faster, overpower the incoming tree
				if (Math.abs(oppositeSpeed) > Math.abs(speedOfCurrent)) {
					float prevSpeed = currentTE.getSpeed();
					currentTE.setSource(neighbourTE.getBlockPos());
					currentTE.setSpeed(getConveyedSpeed(neighbourTE, currentTE));
					currentTE.notifyKineticSpeedChange(prevSpeed);
					currentTE.sendData();

					propagateNewSource(currentTE);
					return;
				}

				// Current faster, overpower the neighbours' tree
				if (Math.abs(newSpeed) >= Math.abs(speedOfNeighbour)) {

					// Do not overpower you own network -> cycle
					if (!currentTE.hasNetwork() || currentTE.network.equals(neighbourTE.network)) {
						float epsilon = Math.abs(speedOfNeighbour) / 256f / 256f;
						if (Math.abs(newSpeed) > Math.abs(speedOfNeighbour) + epsilon)
							KineticNetworks.destroyForConflict(currentTE);
						continue;
					}

					if (currentTE.hasSource() && currentTE.source.equals(neighbourTE.getBlockPos()))
						currentTE.removeSource();

					float prevSpeed = neighbourTE.getSpeed();
					neighbourTE.setSource(currentTE.getBlockPos());
					neighbourTE.setSpeed(getConveyedSpeed(currentTE, neighbourTE));
					neighbourTE.notifyKineticSpeedChange(prevSpeed);
					neighbourTE.sendData();
					propagateNewSource(neighbourTE);
					continue;
				}
			}

			if (Math.abs(neighbourTE.getTheoreticalSpeed() - newSpeed) <= 1e-4f)
				continue;

			float prevSpeed = neighbourTE.getSpeed();
			neighbourTE.setSpeed(newSpeed);
			neighbourTE.setSource(currentTE.getBlockPos());
			neighbourTE.notifyKineticSpeedChange(prevSpeed);
			neighbourTE.sendData();
			propagateNewSource(neighbourTE);

		}
	}

	/**
	 * Remove the given entity from the network.
	 *
	 * @param worldIn
	 * @param pos
	 * @param removedBE
	 */
	public static void handleRemoved(Level worldIn, BlockPos pos, KineticBlockEntity removedBE) {
		if (worldIn == null || worldIn.isClientSide())
			return;
		if (removedBE == null || removedBE.isVirtual())
			return;
		if (worldIn instanceof ServerLevel server) KineticNetworks.markDirty(server);
		if (removedBE.getTheoreticalSpeed() == 0)
			return;

		for (BlockPos neighbourPos : getPotentialNeighbourLocations(removedBE)) {
			if (!LoadedLevelAccess.isLoaded(worldIn, neighbourPos)) continue;
			BlockState neighbourState = LoadedLevelAccess.blockState(worldIn, neighbourPos);
			if (!(neighbourState.getBlock() instanceof IRotate))
				continue;
			net.minecraft.world.level.block.entity.BlockEntity blockEntity = LoadedLevelAccess.blockEntity(worldIn, neighbourPos);
			if (!(blockEntity instanceof KineticBlockEntity neighbourBE))
				continue;

			if (!neighbourBE.hasSource() || !neighbourBE.source.equals(pos))
				continue;

			propagateMissingSource(neighbourBE);
		}

	}

	/**
	 * Clear the entire subnetwork depending on the given entity and find a new
	 * source
	 *
	 * @param updateTE
	 */
	private static void propagateMissingSource(KineticBlockEntity updateTE) {
		final Level world = updateTE.getLevel();

		List<KineticBlockEntity> potentialNewSources = new LinkedList<>();
		List<BlockPos> frontier = new LinkedList<>();
		frontier.add(updateTE.getBlockPos());
		BlockPos missingSource = updateTE.hasSource() ? updateTE.source : null;

		while (!frontier.isEmpty()) {
			final BlockPos pos = frontier.remove(0);
			net.minecraft.world.level.block.entity.BlockEntity blockEntity = LoadedLevelAccess.blockEntity(world, pos);
			if (!(blockEntity instanceof KineticBlockEntity currentBE))
				continue;

			currentBE.removeSource();
			currentBE.sendData();

			for (KineticBlockEntity neighbourBE : getConnectedNeighbours(currentBE)) {
				if (neighbourBE.getBlockPos()
					.equals(missingSource))
					continue;
				if (!neighbourBE.hasSource())
					continue;

				if (!neighbourBE.source.equals(pos)) {
					potentialNewSources.add(neighbourBE);
					continue;
				}

				if (neighbourBE.isSource())
					potentialNewSources.add(neighbourBE);

				frontier.add(neighbourBE.getBlockPos());
			}
		}

		for (KineticBlockEntity newSource : potentialNewSources) {
			if (newSource.hasSource() || newSource.isSource()) {
				propagateNewSource(newSource);
				return;
			}
		}
	}

	private static KineticBlockEntity findConnectedNeighbour(KineticBlockEntity currentTE, BlockPos neighbourPos) {
		BlockState neighbourState = LoadedLevelAccess.blockState(currentTE.getLevel(), neighbourPos);
		if (!(neighbourState.getBlock() instanceof IRotate))
			return null;
		if (!neighbourState.hasBlockEntity())
			return null;
		net.minecraft.world.level.block.entity.BlockEntity neighbourBE = LoadedLevelAccess.blockEntity(currentTE.getLevel(), neighbourPos);
		if (!(neighbourBE instanceof KineticBlockEntity neighbourKBE))
			return null;
        if (currentTE.getLevel() instanceof ServerLevel server && !KineticNetworks.isLive(server,neighbourKBE)) return null;
		if (!(neighbourKBE.getBlockState()
			.getBlock() instanceof IRotate))
			return null;
		if (!isConnected(currentTE, neighbourKBE) && !isConnected(neighbourKBE, currentTE))
			return null;
		return neighbourKBE;
	}

	public static boolean isConnected(KineticBlockEntity from, KineticBlockEntity to) {
		final BlockState stateFrom = from.getBlockState();
		final BlockState stateTo = to.getBlockState();
		return isLargeCogToSpeedController(stateFrom, stateTo, to.getBlockPos()
			.subtract(from.getBlockPos())) || getRotationSpeedModifier(from, to) != 0
			|| from.isCustomConnection(to, stateFrom, stateTo);
	}

	public static List<KineticBlockEntity> getConnectedNeighbours(KineticBlockEntity be) {
		List<KineticBlockEntity> neighbours = new LinkedList<>();
		for (BlockPos neighbourPos : getPotentialNeighbourLocations(be)) {
			final KineticBlockEntity neighbourBE = findConnectedNeighbour(be, neighbourPos);
			if (neighbourBE == null)
				continue;

			neighbours.add(neighbourBE);
		}
		return neighbours;
	}

	public static List<BlockPos> getPotentialNeighbourLocations(KineticBlockEntity be) {
		List<BlockPos> neighbours = new LinkedList<>();
		BlockPos blockPos = be.getBlockPos();
		Level level = be.getLevel();

		if (level == null || !LoadedLevelAccess.isLoaded(level, blockPos))
			return neighbours;

		for (Direction facing : Direction.values()) {
			BlockPos relative = blockPos.relative(facing);
			if (level.isLoaded(relative))
				neighbours.add(relative);
		}

		BlockState blockState = be.getBlockState();
		if (!(blockState.getBlock() instanceof IRotate block))
			return neighbours;
		return be.addPropagationLocations(block, blockState, neighbours).stream().filter(pos -> LoadedLevelAccess.isLoaded(level,pos)).distinct().toList();
	}

    private static int coordinate(Direction.Axis axis, BlockPos pos) {
        return axis.choose(pos.getX(),pos.getY(),pos.getZ());
    }
    public RotationPropagator() {}
}
