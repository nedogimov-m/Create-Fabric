package com.simibubi.create.api.packager.unpacking;

import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public enum VoidingUnpackingHandler implements UnpackingHandler {
    INSTANCE;
    @Override public boolean unpack(Level level,BlockPos pos,BlockState state,Direction side,List<ItemStack> items,PackageOrderWithCrafts context,TransactionContext transaction){return true;}
}
