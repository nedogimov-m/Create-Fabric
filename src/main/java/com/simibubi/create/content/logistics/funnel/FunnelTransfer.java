package com.simibubi.create.content.logistics.funnel;

import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.simibubi.create.foundation.item.LoadedItemStorage;

public final class FunnelTransfer {
    public static Storage<ItemVariant> find(Level level,BlockPos pos,Direction side){
        return LoadedItemStorage.find(level,pos,side);
    }
    public static ItemStack extract(Storage<ItemVariant> source,Predicate<ItemStack> filter,int amount,boolean exact,TransactionContext tx){
        if(source==null||amount<=0)return ItemStack.EMPTY;var candidates=new LinkedHashSet<ItemVariant>();for(var view:source)if(!view.isResourceBlank()&&view.getAmount()>0)candidates.add(view.getResource());
        for(var variant:candidates){var sample=variant.toStack();if(!filter.test(sample))continue;int limit=Math.min(amount,sample.getMaxStackSize());if(exact&&limit<amount)continue;try(var attempt=tx.openNested()){long extracted=source.extract(variant,limit,attempt);if(extracted==0||exact&&extracted!=amount)continue;attempt.commit();return variant.toStack((int)extracted);}}return ItemStack.EMPTY;
    }
    /** Move one variant atomically; a blocked variant must not hide a later fitting batch. */
    public static ItemStack move(Storage<ItemVariant> source,Storage<ItemVariant> destination,Predicate<ItemStack> filter,int amount,boolean exact,boolean full,TransactionContext tx){
        if(source==null||destination==null||amount<=0)return ItemStack.EMPTY;
        var candidates=new LinkedHashSet<ItemVariant>();for(var view:source)if(!view.isResourceBlank()&&view.getAmount()>0)candidates.add(view.getResource());
        for(var variant:candidates){
            var sample=variant.toStack();if(!filter.test(sample))continue;int limit=Math.min(amount,sample.getMaxStackSize());if(exact&&limit<amount)continue;
            long available,accepted;
            try(var preview=tx.openNested()){
                available=source.extract(variant,limit,preview);if(available==0||exact&&available!=amount)continue;
                accepted=destination.insert(variant,available,preview);
                if(accepted==0||(full||exact)&&accepted!=available)continue;
            }
            try(var transfer=tx.openNested()){
                if(source.extract(variant,accepted,transfer)!=accepted||destination.insert(variant,accepted,transfer)!=accepted)continue;
                transfer.commit();return variant.toStack((int)accepted);
            }
        }
        return ItemStack.EMPTY;
    }
    private FunnelTransfer(){}
}
