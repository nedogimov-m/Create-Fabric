package com.simibubi.create.content.kinetics.gearbox;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public final class VerticalGearboxItem extends BlockItem {
    public VerticalGearboxItem(Properties properties) { super(AllBlocks.GEARBOX, properties); }

    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        // Both gearbox items place the same block; preserve the default block item mapping.
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        if (state == null) return null;
        Direction.Axis preferred = null;
        for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            var neighborPos = context.getClickedPos().relative(face);
            var neighbor = context.getLevel().getBlockState(neighborPos);
            if (neighbor.getBlock() instanceof IRotate rotation
                    && rotation.hasShaftTowards(context.getLevel(), neighborPos, neighbor, face.getOpposite())) {
                if (preferred != null && preferred != face.getAxis()) { preferred = null; break; }
                preferred = face.getAxis();
            }
        }
        Direction.Axis axis = preferred == null ? context.getHorizontalDirection().getClockWise().getAxis()
                : preferred == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return state.setValue(GearboxBlock.AXIS, axis);
    }
}
