package com.simibubi.create.content.kinetics.mechanicalArm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ArmItem extends BlockItem {
    public ArmItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();
        if(ArmInteractionPoint.isInteractable(level,pos,level.getBlockState(pos)))return InteractionResult.SUCCESS;
        return super.useOn(context);
    }
    @Override public boolean canDestroyBlock(ItemStack stack,BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.LivingEntity player){return !ArmInteractionPoint.isInteractable(level,pos,state);}
}
