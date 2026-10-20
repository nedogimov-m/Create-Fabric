package com.simibubi.create.content.decoration.bracket;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

public class BracketBlockItem extends BlockItem {
    public BracketBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();var player=context.getPlayer();
        if(!LoadedLevelAccess.isLoaded(level,pos)||player!=null&&(!player.mayBuild()||!level.mayInteract(player,pos)))return InteractionResult.FAIL;
        var behaviour=BlockEntityBehaviour.get(level,pos,BracketedBlockEntityBehaviour.TYPE);
        if(behaviour==null||!behaviour.canHaveBracket()||context.getItemInHand().isEmpty())return InteractionResult.FAIL;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        var block=(BracketBlock)getBlock();var state=level.getBlockState(pos);
        var suitable=block.getSuitableBracket(state,context.getClickedFace());
        if(suitable.isEmpty()&&player!=null)suitable=block.getSuitableBracket(state,Direction.orderedByNearest(player)[0].getOpposite());
        if(suitable.isEmpty())return InteractionResult.SUCCESS;
        var previous=behaviour.getBracket();var next=suitable.get();if(previous==next)return InteractionResult.SUCCESS;
        level.playSound(null,pos,next.getSoundType().getPlaceSound(),SoundSource.BLOCKS,.75f,1);
        behaviour.applyBracket(next);
        if(player==null||!player.isCreative()){
            context.getItemInHand().shrink(1);
            if(previous!=null){var returned=new ItemStack(previous.getBlock());if(player==null)Block.popResource(level,pos,returned);else player.getInventory().placeItemBackInInventory(returned,Prediction.SERVER_ONLY);}
        }
        return InteractionResult.SUCCESS;
    }
}
