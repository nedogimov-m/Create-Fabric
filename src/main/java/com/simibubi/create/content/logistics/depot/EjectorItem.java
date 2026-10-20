package com.simibubi.create.content.logistics.depot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class EjectorItem extends BlockItem {
    public EjectorItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext context){if(context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown())return InteractionResult.SUCCESS;return super.useOn(context);}
    @Override public boolean canDestroyBlock(ItemStack stack,BlockState state,Level level,BlockPos pos,LivingEntity player){return !player.isShiftKeyDown();}
}
