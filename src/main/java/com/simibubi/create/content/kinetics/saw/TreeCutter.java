package com.simibubi.create.content.kinetics.saw;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;
import java.util.function.*;

/** Tree topology and leaf-distance rules from the pinned Create source. Never loads chunks. */
public final class TreeCutter {
    private static final TagKey<Block> ROOTS = TagKey.create(Registries.BLOCK, Create.asResource("roots"));
    private static final TagKey<Block> SLIMY_LOGS = TagKey.create(Registries.BLOCK, Create.asResource("slimy_logs"));
    private static final TagKey<Block> TREE_ATTACHMENTS = TagKey.create(Registries.BLOCK, Create.asResource("tree_attachments"));
    public static final Tree NO_TREE = new Tree(List.of(), List.of(), List.of());
    private static final class UnloadedTreeChunk extends RuntimeException {}
    private static BlockState stateAt(BlockGetter reader, BlockPos pos) {
        if (reader instanceof Level level && !level.isLoaded(pos)) throw new UnloadedTreeChunk();
        return reader.getBlockState(pos);
    }
    public static Tree findTree(BlockGetter reader, BlockPos pos, BlockState brokenState) {
        try { return findTreeLoaded(reader, pos, brokenState); } catch (UnloadedTreeChunk ignored) { return NO_TREE; }
    }
	/**
	 * Finds a tree at the given pos. Block at the position should be air
	 *
	 * @param reader      the level that will be searched for a tree
	 * @param pos         position that the saw cut at
	 * @param brokenState block state what was broken by the saw
	 */
	private static Tree findTreeLoaded(BlockGetter reader, BlockPos pos, BlockState brokenState) {
		if (reader == null)
			return NO_TREE;

		List<BlockPos> logs = new ArrayList<>();
		List<BlockPos> leaves = new ArrayList<>();
		List<BlockPos> attachments = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		List<BlockPos> frontier = new LinkedList<>();

		BlockState stateAbove = stateAt(reader, pos.above());
		// Bamboo, Sugar Cane, Cactus
		if (isVerticalPlant(brokenState)) {
			if (!isVerticalPlant(stateAbove))
				return NO_TREE;

			logs.add(pos.above());
			for (int i = 1; i < reader.getHeight(); i++) {
				BlockPos current = pos.above(i);
				if (!isVerticalPlant(stateAt(reader, current)))
					break;
				logs.add(current);
			}
			Collections.reverse(logs);
			return new Tree(logs, leaves, attachments);
		}

		// Chorus
		if (isChorus(brokenState)) {
			if (!isChorus(stateAbove))
				return NO_TREE;

			frontier.add(pos.above());
			while (!frontier.isEmpty()) {
				BlockPos current = frontier.remove(0);
				visited.add(current);
				logs.add(current);
				for (Direction direction : Direction.values()) {
					BlockPos offset = current.relative(direction);
					if (visited.contains(offset))
						continue;
					if (!isChorus(stateAt(reader, offset)))
						continue;
					frontier.add(offset);
				}
			}
			Collections.reverse(logs);
			return new Tree(logs, leaves, attachments);
		}

		// Regular Tree
		if (!validateCut(reader, pos))
			return NO_TREE;

		visited.add(pos);
		BlockPos.betweenClosedStream(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))
			.forEach(p -> frontier.add(p.immutable()));

		// Find all logs & roots
		boolean hasRoots = false;
		while (!frontier.isEmpty()) {
			BlockPos currentPos = frontier.remove(0);
			if (!visited.add(currentPos))
				continue;

			BlockState currentState = stateAt(reader, currentPos);
			if (isRoot(currentState))
				hasRoots = true;
			else if (!isLog(currentState))
				continue;
			logs.add(currentPos);
			forNeighbours(currentPos, visited, SearchDirection.UP, p -> frontier.add(p.immutable()));
		}

		visited.clear();
		visited.addAll(logs);
		frontier.addAll(logs);

		if (hasRoots) {
			Set<BlockPos> oldLogs = new HashSet<>(logs);
			while (!frontier.isEmpty()) {
				BlockPos currentPos = frontier.remove(0);

				BlockState currentState = stateAt(reader, currentPos);
				if (!isRoot(currentState))
					continue;
				if (!oldLogs.contains(currentPos))
					logs.add(currentPos);
				forNeighbours(currentPos, visited, SearchDirection.DOWN, p -> {
					BlockPos neighbourPos = p.immutable();
					if (visited.add(neighbourPos))
						frontier.add(neighbourPos);
				});
			}

			visited.clear();
			visited.addAll(logs);
			frontier.addAll(logs);
		}

		// Find all leaves
		while (!frontier.isEmpty()) {
			BlockPos prevPos = frontier.remove(0);

			BlockState prevState = stateAt(reader, prevPos);
			int prevLeafDistance = isLeaf(prevState) ? getLeafDistance(prevState) : 0;

			forNeighbours(prevPos, visited, SearchDirection.BOTH, currentPos -> {
				BlockState state = stateAt(reader, currentPos);
				BlockPos subtract = currentPos.subtract(pos);
				BlockPos currentPosImmutable = currentPos.immutable();

				if (state.is(TREE_ATTACHMENTS)) {
					attachments.add(currentPosImmutable);
					visited.add(currentPosImmutable);
					return;
				}

				int horizontalDistance = Math.max(Math.abs(subtract.getX()), Math.abs(subtract.getZ()));
				if (horizontalDistance <= nonDecayingLeafDistance(state) && visited.add(currentPosImmutable)) {
					leaves.add(currentPosImmutable);
					frontier.add(currentPosImmutable);
					return;
				}

				if (isLeaf(state) && getLeafDistance(state) > prevLeafDistance && visited.add(currentPosImmutable)) {
					leaves.add(currentPosImmutable);
					frontier.add(currentPosImmutable);
					return;
				}

			});
		}
		return new Tree(logs, leaves, attachments);
	}

	private static int getLeafDistance(BlockState state) {
		IntegerProperty distanceProperty = LeavesBlock.DISTANCE;
		for (Property<?> property : state.getProperties())
			if (property instanceof IntegerProperty ip && property.getName()
				.equals("distance"))
				distanceProperty = ip;
		return state.getValue(distanceProperty);
	}

	public static boolean isChorus(BlockState stateAbove) {
		return stateAbove.getBlock() instanceof ChorusPlantBlock || stateAbove.getBlock() instanceof ChorusFlowerBlock;
	}

	public static boolean isVerticalPlant(BlockState stateAbove) {
		Block block = stateAbove.getBlock();
		if (block instanceof BambooStalkBlock)
			return true;
		if (block instanceof CactusBlock)
			return true;
		if (block instanceof SugarCaneBlock)
			return true;
		if (block instanceof KelpPlantBlock)
			return true;
		return block instanceof KelpBlock;
	}

	/**
	 * Checks whether a tree was fully cut by seeing whether the layer above the cut
	 * is not supported by any more logs.
	 *
	 * @param reader
	 * @param pos
	 * @return
	 */
	private static boolean validateCut(BlockGetter reader, BlockPos pos) {
		Set<BlockPos> visited = new HashSet<>();
		List<BlockPos> frontier = new LinkedList<>();
		frontier.add(pos);
		frontier.add(pos.above());
		int posY = pos.getY();

		while (!frontier.isEmpty()) {
			BlockPos currentPos = frontier.remove(0);
			BlockPos belowPos = currentPos.below();

			visited.add(currentPos);
			boolean lowerLayer = currentPos.getY() == posY;

			BlockState currentState = stateAt(reader, currentPos);
			BlockState belowState = stateAt(reader, belowPos);

			if (!isLog(currentState) && !isRoot(currentState))
				continue;
			if (!lowerLayer && !pos.equals(belowPos) && (isLog(belowState) || isRoot(belowState)))
				return false;

			for (Direction direction : Direction.values()) {
				if (direction == Direction.DOWN)
					continue;
				if (direction == Direction.UP && !lowerLayer)
					continue;
				BlockPos offset = currentPos.relative(direction);
				if (visited.contains(offset))
					continue;
				frontier.add(offset);
			}

		}

		return true;
	}

	private enum SearchDirection {
		UP(0, 1), DOWN(-1, 0), BOTH(-1, 1);

		int minY;
		int maxY;

		private SearchDirection(int minY, int maxY) {
			this.minY = minY;
			this.maxY = maxY;
		}
	}

	private static void forNeighbours(BlockPos pos, Set<BlockPos> visited, SearchDirection direction,
									  Consumer<BlockPos> acceptor) {
		BlockPos.betweenClosedStream(pos.offset(-1, direction.minY, -1), pos.offset(1, direction.maxY, 1))
			.filter(((Predicate<BlockPos>) visited::contains).negate())
			.forEach(acceptor);
	}

	public static boolean isRoot(BlockState state) {
		return state.is(ROOTS);
	}

	public static boolean isLog(BlockState state) {
		return state.is(BlockTags.LOGS) || state.is(SLIMY_LOGS)
			|| state.is(Blocks.MUSHROOM_STEM);
	}

	private static int nonDecayingLeafDistance(BlockState state) {
		if (state.is(Blocks.RED_MUSHROOM_BLOCK))
			return 2;
		if (state.is(Blocks.BROWN_MUSHROOM_BLOCK))
			return 3;
		if (state.is(BlockTags.WART_BLOCKS) || state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT))
			return 3;
		return -1;
	}

	private static boolean isLeaf(BlockState state) {
		for (Property<?> property : state.getProperties())
			if (property instanceof IntegerProperty && property.getName().equals("distance") && property != BlockStateProperties.STABILITY_DISTANCE)
				return true;
		return false;
	}

    public static final class Tree {
        public final List<BlockPos> logs, leaves, attachments;
        public Tree(List<BlockPos> logs, List<BlockPos> leaves, List<BlockPos> attachments) {
            this.logs = List.copyOf(new LinkedHashSet<>(logs)); this.leaves = List.copyOf(new LinkedHashSet<>(leaves)); this.attachments = List.copyOf(new LinkedHashSet<>(attachments));
        }
        public void destroyBlocks(ServerLevel level, BiConsumer<BlockPos, ItemStack> drop) {
            for (var pos : attachments) destroy(level, pos, 1f / 32, drop);
            for (var pos : logs) destroy(level, pos, 0.5f, drop);
            for (var pos : leaves) destroy(level, pos, 0.125f, drop);
        }
        private static void destroy(ServerLevel level, BlockPos pos, float chance, BiConsumer<BlockPos, ItemStack> drop) {
            if (!level.isLoaded(pos)) return;
            var state = level.getBlockState(pos);
            if (!com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity.isBreakable(state, state.getDestroySpeed(level, pos))) return;
            BlockHelper.destroyBlock(level, pos, chance, stack -> drop.accept(pos, stack));
        }
    }
    private TreeCutter() {}
}
