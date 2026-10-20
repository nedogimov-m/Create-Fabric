package com.simibubi.create.content.decoration.encasing;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Source casing: normal wrench rotation fails; actual sneak pickup is handled by the wrench. */
public class CasingBlock extends Block {
    public CasingBlock(Properties properties){super(properties);}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){return InteractionResult.FAIL;}
}
