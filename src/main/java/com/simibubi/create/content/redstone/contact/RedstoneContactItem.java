package com.simibubi.create.content.redstone.contact;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.elevator.ElevatorColumn;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Original normal contact placement joins an already existing horizontal elevator column. */
public class RedstoneContactItem extends BlockItem {
    public RedstoneContactItem(Block block, Properties properties) { super(block, properties); }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        var state = super.getPlacementState(context);
        if (state == null || !(state.getBlock() instanceof RedstoneContactBlock)) return state;
        var facing = state.getValue(RedstoneContactBlock.FACING);
        if (!facing.getAxis().isHorizontal()) return state;
        var pos = context.getClickedPos();
        if (ElevatorColumn.get(context.getLevel(), new ElevatorColumn.ColumnCoords(pos.getX(), pos.getZ(), facing)) == null)
            return state;
        // These are exactly the two shared source contact properties. New floor-only state keeps its real defaults.
        return AllBlocks.ELEVATOR_CONTACT.defaultBlockState()
            .setValue(RedstoneContactBlock.FACING, facing)
            .setValue(RedstoneContactBlock.POWERED, state.getValue(RedstoneContactBlock.POWERED));
    }
}
