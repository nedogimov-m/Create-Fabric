package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.rail.ControllerRailBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.RailShape;

/** Real source rail overlay item; only the five actual supported straight rail states are accepted. */
public final class CartAssemblerBlockItem extends BlockItem {
    public CartAssemblerBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext context){if(tryPlaceAssembler(context)){context.getLevel().playSound(null,context.getClickedPos(),SoundEvents.STONE_PLACE,SoundSource.BLOCKS,1,1);return InteractionResult.SUCCESS;}return super.useOn(context);}
    public boolean tryPlaceAssembler(UseOnContext context){
        var player=context.getPlayer();var world=context.getLevel();var pos=context.getClickedPos();if(player==null||!player.mayBuild()||player.isSpectator()||!world.mayInteract(player,pos)||!LoadedLevelAccess.isLoaded(world,pos))return false;
        var state=LoadedLevelAccess.blockState(world,pos);if(!(state.getBlock() instanceof BaseRailBlock rail)){player.sendOverlayMessage(Component.translatable("create.block.cart_assembler.invalid"));return false;}
        var shape=state.getValue(rail.getShapeProperty());if(shape!=RailShape.EAST_WEST&&shape!=RailShape.NORTH_SOUTH)return false;
        CartAssembleRailType selected=null;for(var type:CartAssembleRailType.values())if(type.matches(state))selected=type;if(selected==null)return false;
        if(world.isClientSide())return true;
        boolean backwards=state.hasProperty(ControllerRailBlock.BACKWARDS)?state.getValue(ControllerRailBlock.BACKWARDS):player.getMotionDirection().getAxisDirection()==Direction.AxisDirection.POSITIVE;
        var next=AllBlocks.CART_ASSEMBLER.defaultBlockState().setValue(CartAssemblerBlock.RAIL_SHAPE,shape).setValue(CartAssemblerBlock.RAIL_TYPE,selected).setValue(CartAssemblerBlock.BACKWARDS,backwards).setValue(CartAssemblerBlock.POWERED,world.hasNeighborSignal(pos));
        if(!world.setBlockAndUpdate(pos,next))return false;if(LoadedLevelAccess.blockEntity(world,pos) instanceof CartAssemblerBlockEntity assembler)assembler.sourceAdvancements.place(player);if(!player.isCreative())context.getItemInHand().shrink(1);return true;
    }
}
