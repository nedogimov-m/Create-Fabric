package com.simibubi.create.impl.unpacking;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.funnel.FunnelTransfer;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public enum DefaultUnpackingHandler implements UnpackingHandler {
    INSTANCE;
    @Override public boolean unpack(Level level,BlockPos pos,BlockState state,Direction side,List<ItemStack> items,PackageOrderWithCrafts context,TransactionContext transaction){return insertAll(FunnelTransfer.find(level,pos,side),items,transaction);}
    public static boolean insertAll(Storage<ItemVariant> storage,List<ItemStack> items,TransactionContext transaction){
        if(storage==null)return false;try(var tx=transaction.openNested()){for(var item:items)if(!item.isEmpty()&&storage.insert(ItemVariant.of(item),item.getCount(),tx)!=item.getCount())return false;tx.commit();return true;}
    }
}
