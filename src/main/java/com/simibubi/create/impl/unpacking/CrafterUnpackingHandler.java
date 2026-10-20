package com.simibubi.create.impl.unpacking;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.kinetics.crafter.*;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public enum CrafterUnpackingHandler implements UnpackingHandler {
    INSTANCE;
    @Override public boolean unpack(Level level,BlockPos pos,BlockState state,Direction side,List<ItemStack> items,PackageOrderWithCrafts context,TransactionContext transaction){
        if(!PackageOrderWithCrafts.hasCraftingInformation(context))return DEFAULT.unpack(level,pos,state,side,items,null,transaction);
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof MechanicalCrafterBlockEntity crafter))return false;
        var members=ConnectedInputHandler.getMembers(crafter);if(members.isEmpty())return false;var pattern=context.getCraftingInformation();var remaining=items.stream().map(ItemStack::copy).toList();
        try(var tx=transaction.openNested()){
            outer:for(int slot=0;slot<Math.min(members.size(),pattern.size());slot++){
                var sample=pattern.get(slot).stack;var inventory=members.get(slot).inventory;if(sample.isEmpty()||!inventory.isResourceBlank())continue;
                for(var item:remaining)if(!item.isEmpty()&&ItemStack.isSameItemSameComponents(item,sample)&&inventory.insert(ItemVariant.of(item),1,tx)==1){item.shrink(1);continue outer;}
            }
            if(remaining.stream().anyMatch(item->!item.isEmpty()))return false;
            tx.addOuterCloseCallback(result->{if(result.wasCommitted())crafter.requestRecipeCheck(true);});tx.commit();return true;
        }
    }
}
