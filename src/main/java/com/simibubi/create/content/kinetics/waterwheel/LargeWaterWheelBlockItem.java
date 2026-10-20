package com.simibubi.create.content.kinetics.waterwheel;

import java.util.function.Consumer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Source first-place/retry behavior; ordinary native BlockItem owns validation and exactly one survival debit. */
public final class LargeWaterWheelBlockItem extends BlockItem {
    /** Installed exclusively by the client entrypoint; dedicated servers never resolve a client class. */
    public static Consumer<BlockPlaceContext> SHOW_BOUNDS=context->{};
    public LargeWaterWheelBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult place(BlockPlaceContext context){
        var result=super.place(context);
        if(result!=InteractionResult.FAIL)return result;
        var clickedFace=context.getClickedFace();
        if(clickedFace.getAxis()!=((LargeWaterWheelBlock)getBlock()).getAxisForPlacement(context))
            result=super.place(BlockPlaceContext.at(context,context.getClickedPos().relative(clickedFace),clickedFace));
        if(result==InteractionResult.FAIL&&context.getLevel().isClientSide())showBounds(context);
        return result;
    }
    public void showBounds(BlockPlaceContext context){if(context.getLevel().isClientSide())SHOW_BOUNDS.accept(context);}
}
