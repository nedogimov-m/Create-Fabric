package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.decoration.placard.PlacardBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.ItemStack;

/** Real source centered transported-item scan, plus an explicit native held-item adapter for the actual Placard. */
public final class ItemNameDisplaySource extends SingleLineDisplaySource {
    @Override public boolean isAvailable(DisplayLinkContext context){if(!super.isAvailable(context))return false;if(context.getSourceBlockEntity()instanceof PlacardBlockEntity)return true;var pos=context.getSourcePos();for(int i=0;i<32;i++){if(!LoadedLevelAccess.isLoaded(context.level(),pos))return false;if(LoadedLevelAccess.blockEntity(context.level(),pos)instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity belt){var controller=belt.getControllerBE();if(controller==null||!controller.chainLoaded())return false;}if(BlockEntityBehaviour.get(context.level(),pos,TransportedItemStackHandlerBehaviour.TYPE)==null)return true;pos=pos.relative(context.blockEntity().getDirection());}return true;}
    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){
        if(context.getSourceBlockEntity()instanceof PlacardBlockEntity placard)return placard.getHeldItem().isEmpty()?EMPTY_LINE:placard.getHeldItem().getHoverName().copy();var combined=EMPTY_LINE.copy();var pos=context.getSourcePos();var direction=context.blockEntity().getDirection();
        for(int i=0;i<32;i++){if(!LoadedLevelAccess.isLoaded(context.level(),pos))break;var handler=BlockEntityBehaviour.get(context.level(),pos,TransportedItemStackHandlerBehaviour.TYPE);pos=pos.relative(direction);if(handler==null)break;var holder=new ItemStack[]{ItemStack.EMPTY};handler.handleCenteredProcessingOnAllItems(.25f,item->{holder[0]=item.stack;return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();});if(!holder[0].isEmpty())combined.append(holder[0].getHoverName());}return combined;
    }
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override protected String getTranslationKey(){return "combine_item_names";}
    @Override protected String getFlapDisplayLayoutName(DisplayLinkContext context){return "Number";}
}
