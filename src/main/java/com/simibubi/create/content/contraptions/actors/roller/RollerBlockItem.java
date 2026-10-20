package com.simibubi.create.content.contraptions.actors.roller;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class RollerBlockItem extends BlockItem {
    public RollerBlockItem(Block block, Properties properties) { super(block, properties); }
    @Override public InteractionResult place(BlockPlaceContext context) {
        var below = context.getClickedPos().below();
        if (!LoadedLevelAccess.isLoaded(context.getLevel(), below)) return InteractionResult.FAIL;
        var support = LoadedLevelAccess.blockState(context.getLevel(), below);
        return super.place(Block.isFaceFull(support.getCollisionShape(context.getLevel(), below), Direction.UP)
            ? BlockPlaceContext.at(context, context.getClickedPos().above(), context.getClickedFace()) : context);
    }
    public InteractionResult placeInRow(BlockPlaceContext context, Direction facing) {
        var below = context.getClickedPos().below();
        if (!LoadedLevelAccess.isLoaded(context.getLevel(), below)) return InteractionResult.FAIL;
        var support = LoadedLevelAccess.blockState(context.getLevel(), below);
        var adjusted = Block.isFaceFull(support.getCollisionShape(context.getLevel(), below), Direction.UP)
            ? BlockPlaceContext.at(context, context.getClickedPos().above(), context.getClickedFace()) : context;
        var fixed = new BlockPlaceContext(adjusted) { @Override public Direction getHorizontalDirection() { return facing.getOpposite(); } };
        return super.place(fixed);
    }
}
