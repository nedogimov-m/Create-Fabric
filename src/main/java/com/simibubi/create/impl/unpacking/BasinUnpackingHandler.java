package com.simibubi.create.impl.unpacking;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public enum BasinUnpackingHandler implements UnpackingHandler {
    INSTANCE;
    @Override public boolean unpack(Level level,BlockPos pos,BlockState state,Direction side,List<ItemStack> items,PackageOrderWithCrafts context,TransactionContext transaction){
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof BasinBlockEntity basin))return false;
        // Packaging can fill multiple equal input stacks; ordinary belt automation keeps unique inputs.
        return DefaultUnpackingHandler.insertAll(new CombinedStorage<ItemVariant,com.simibubi.create.foundation.item.ProcessingInventory.Slot>(basin.inventory.inputs),items,transaction);
    }
}
