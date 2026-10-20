package com.simibubi.create.api.packager.unpacking;

import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.impl.unpacking.DefaultUnpackingHandler;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Unpacking participates in the same transaction as consumption of the outer package. */
@FunctionalInterface
public interface UnpackingHandler {
    SimpleRegistry<Block,UnpackingHandler> REGISTRY=SimpleRegistry.create();
    UnpackingHandler DEFAULT=DefaultUnpackingHandler.INSTANCE;
    boolean unpack(Level level,BlockPos pos,BlockState state,Direction side,List<ItemStack> items,PackageOrderWithCrafts context,TransactionContext transaction);
    default boolean unpack(Level level,BlockPos pos,BlockState state,Direction side,List<ItemStack> items,PackageOrderWithCrafts context,boolean simulate){try(var tx=Transaction.openOuter()){boolean unpacked=unpack(level,pos,state,side,items,context,tx);if(unpacked&&!simulate)tx.commit();return unpacked;}}
}
