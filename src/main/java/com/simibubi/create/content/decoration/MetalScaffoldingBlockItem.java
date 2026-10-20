package com.simibubi.create.content.decoration;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ScaffoldingBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public final class MetalScaffoldingBlockItem extends ScaffoldingBlockItem {
    public MetalScaffoldingBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public BlockPlaceContext updatePlacementContext(BlockPlaceContext context){
        var level=context.getLevel();var pos=context.getClickedPos();
        if(!LoadedLevelAccess.blockState(level,pos).is(getBlock()))return context;
        Direction direction=context.isSecondaryUseActive()?context.isInside()?context.getClickedFace().getOpposite():context.getClickedFace()
                :context.getClickedFace()==Direction.UP?context.getHorizontalDirection():Direction.UP;
        var at=pos.mutable().move(direction);int horizontal=0;
        while(horizontal<7){
            if(!level.isInValidBounds(at)){
                if(!level.isClientSide()&&context.getPlayer() instanceof ServerPlayer player&&at.getY()>level.getMaxY())
                    player.sendSystemMessage(Component.translatable("build.tooHigh",level.getMaxY()).withStyle(ChatFormatting.RED),true);
                return null;
            }
            if(!LoadedLevelAccess.isLoaded(level,at))return null;
            var state=LoadedLevelAccess.blockState(level,at);
            if(!state.is(getBlock()))return state.canBeReplaced(context)?BlockPlaceContext.at(context,at.immutable(),direction):null;
            at.move(direction);if(direction.getAxis().isHorizontal())horizontal++;
        }
        return null;
    }
}
