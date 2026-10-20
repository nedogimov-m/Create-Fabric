package com.simibubi.create.content.logistics.vault;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public final class ItemVaultItem extends BlockItem {
    public ItemVaultItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult place(BlockPlaceContext context){
        var stack=context.getItemInHand();var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(data!=null){var tag=data.copyTagWithoutId();for(var key:new String[]{"Length","Size","Controller","LastKnownPos"})tag.remove(key);tag.putBoolean("Uninitialized",true);stack.set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(AllBlockEntityTypes.ITEM_VAULT,tag));}
        var result=super.place(context);if(result.consumesAction())tryMultiPlace(context);return result;
    }
    private void tryMultiPlace(BlockPlaceContext context){
        var player=context.getPlayer();if(player==null||player.isShiftKeyDown()||com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem.presentInHotbar(player))return;var level=context.getLevel();var face=context.getClickedFace();var placed=context.getClickedPos();var support=placed.relative(face.getOpposite());
        if(!(LoadedLevelAccess.blockEntity(level,support) instanceof ItemVaultBlockEntity part))return;var master=part.getControllerBE();if(master==null||master.getWidth()==1||face.getAxis()!=master.getMainConnectionAxis())return;
        var start=face.getAxisDirection()==Direction.AxisDirection.NEGATIVE?master.getBlockPos().relative(face):master.getBlockPos().relative(face,master.getHeight());if(face.getAxis().choose(start.getX(),start.getY(),start.getZ())!=face.getAxis().choose(placed.getX(),placed.getY(),placed.getZ()))return;
        int required=0;for(int x=0;x<master.getWidth();x++)for(int y=0;y<master.getWidth();y++){var pos=face.getAxis()==Direction.Axis.X?start.offset(0,x,y):start.offset(x,y,0);if(!level.isLoaded(pos)||!level.mayInteract(player,pos))return;var state=LoadedLevelAccess.blockState(level,pos);if(state.is(getBlock()))continue;if(!state.canBeReplaced())return;required++;}
        if(!player.isCreative()&&context.getItemInHand().getCount()<required)return;
        for(int x=0;x<master.getWidth();x++)for(int y=0;y<master.getWidth();y++){var pos=face.getAxis()==Direction.Axis.X?start.offset(0,x,y):start.offset(x,y,0);if(!LoadedLevelAccess.blockState(level,pos).is(getBlock()))super.place(BlockPlaceContext.at(context,pos,face));}
    }
}
