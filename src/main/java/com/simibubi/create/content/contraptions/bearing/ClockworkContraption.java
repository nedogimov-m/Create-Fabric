package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.tuple.Pair;

public class ClockworkContraption extends Contraption {
    public enum HandType { HOUR, MINUTE }
    private Direction facing = Direction.UP;
    public HandType handType = HandType.HOUR;
    public int offset;
    private final Set<BlockPos> ignoreBlocks = new HashSet<>();
    @Override public ContraptionType getType() { return AllContraptionTypes.CLOCKWORK.value(); }
    public Direction getFacing() { return facing; }
    @Override protected boolean isAnchoringBlockAt(BlockPos pos) { return pos.equals(anchor.relative(facing.getOpposite(), offset + 1)); }
    @Override public boolean assemble(Level level, BlockPos bearing) throws AssemblyException { return searchMovedStructure(level, bearing, facing); }
    @Override public boolean searchMovedStructure(Level level, BlockPos bearing, Direction direction) throws AssemblyException {
        return super.searchMovedStructure(level, bearing.relative(direction, offset + 1), null);
    }
    @Override protected boolean moveBlock(Level level, Direction forced, Queue<BlockPos> frontier, Set<BlockPos> visited) throws AssemblyException {
        if (ignoreBlocks.contains(frontier.peek())) { frontier.poll(); return true; }
        return super.moveBlock(level, forced, frontier, visited);
    }
    public static Pair<ClockworkContraption, ClockworkContraption> assembleClockworkAt(Level level, BlockPos pos, Direction facing) throws AssemblyException {
        var hour = new ClockworkContraption(); hour.facing = facing;
        if (!hour.assemble(level, pos) || hour.getBlocks().isEmpty()) return null;
        int length = 0;
        for (int i = 0; i < 16; i++) if (!hour.getBlocks().containsKey(BlockPos.ZERO.relative(facing, i))) { length = i; break; }
        ClockworkContraption minute = null;
        if (length > 0) {
            minute = new ClockworkContraption(); minute.facing = facing; minute.handType = HandType.MINUTE; minute.offset = length;
            for (var local : hour.getBlocks().keySet()) minute.ignoreBlocks.add(local.offset(hour.anchor));
            if (!minute.assemble(level, pos)) return null;
            if (minute.getBlocks().isEmpty()) minute = null;
        }
        hour.startMoving(level); hour.expandBoundsAroundAxis(facing.getAxis());
        if (minute != null) { minute.startMoving(level); minute.expandBoundsAroundAxis(facing.getAxis()); }
        return Pair.of(hour, minute);
    }
    @Override public boolean canBeStabilized(Direction face, BlockPos localPos) {
        return !localPos.equals(BlockPos.ZERO) && !localPos.relative(face).equals(BlockPos.ZERO) && face.getAxis() == facing.getAxis();
    }
    @Override public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnData) {
        var tag = super.writeNBT(registries, spawnData); tag.putInt("facing", facing.get3DDataValue()); tag.putInt("offset", offset); tag.putString("HandType", handType.name()); return tag;
    }
    @Override public void readNBT(Level level, CompoundTag tag, boolean spawnData) {
        facing = Direction.from3DDataValue(tag.getIntOr("facing", Direction.UP.get3DDataValue()));
        offset = tag.getIntOr("offset", 0);
        if (offset < 0 || offset > 15) throw new IllegalArgumentException("Invalid clockwork offset " + offset);
        try { handType = HandType.valueOf(tag.getStringOr("HandType", "HOUR").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid clockwork hand type", invalid); }
        super.readNBT(level, tag, spawnData);
    }
}
