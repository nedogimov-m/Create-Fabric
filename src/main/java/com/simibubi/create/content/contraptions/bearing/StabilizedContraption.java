package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Source stabilized child; exclusions model parent/sibling cells already removed by source. */
public class StabilizedContraption extends Contraption {
    private Direction facing = Direction.UP;
    private final Set<BlockPos> excludedWorld;
    public StabilizedContraption() { this(Direction.UP, Set.of()); }
    public StabilizedContraption(Direction facing) { this(facing, Set.of()); }
    public StabilizedContraption(Direction facing, Set<BlockPos> excluded) { this.facing = Objects.requireNonNull(facing); excludedWorld = Set.copyOf(excluded); }
    public Direction getFacing() { return facing; }
    @Override public boolean assemble(Level level, BlockPos bearing) throws AssemblyException {
        if (!searchMovedStructure(level, bearing.relative(facing), null) || getBlocks().isEmpty()) return false;
        startMoving(level); return true;
    }
    @Override protected boolean isAnchoringBlockAt(BlockPos pos) { return excludedWorld.contains(pos); }
    @Override protected boolean moveBlock(Level level, Direction forced, Queue<BlockPos> frontier, Set<BlockPos> visited) throws AssemblyException {
        if (excludedWorld.contains(frontier.peek())) { visited.add(frontier.poll()); return true; }
        return super.moveBlock(level, forced, frontier, visited);
    }
    @Override public ContraptionType getType() { return AllContraptionTypes.STABILIZED.value(); }
    @Override public boolean canBeStabilized(Direction face, BlockPos localPos) { return false; }
    @Override public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnData) {
        var tag = super.writeNBT(registries, spawnData); tag.putInt("Facing", facing.get3DDataValue()); return tag;
    }
    @Override public void readNBT(Level level, CompoundTag tag, boolean spawnData) {
        int value = tag.getIntOr("Facing", Direction.UP.get3DDataValue()); if (value < 0 || value >= 6) throw new IllegalArgumentException("Invalid stabilized facing");
        facing = Direction.from3DDataValue(value); super.readNBT(level, tag, spawnData);
    }
}
