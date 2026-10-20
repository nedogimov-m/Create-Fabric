package com.simibubi.create.content.kinetics.simpleRelays;

import com.simibubi.create.content.kinetics.base.IRotate;

public interface ICogWheel extends IRotate {
    static boolean isSmallCog(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof ICogWheel cog && cog.isSmallCog();
    }
    static boolean isLargeCog(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof ICogWheel cog && cog.isLargeCog();
    }
    static boolean isSmallCog(net.minecraft.world.level.block.Block block) { return block instanceof ICogWheel cog && cog.isSmallCog(); }
    static boolean isLargeCog(net.minecraft.world.level.block.Block block) { return block instanceof ICogWheel cog && cog.isLargeCog(); }
    static boolean isSmallCog(net.minecraft.world.item.ItemStack stack) { return stack.getItem() instanceof net.minecraft.world.item.BlockItem item && isSmallCog(item.getBlock()); }
    static boolean isLargeCog(net.minecraft.world.item.ItemStack stack) { return stack.getItem() instanceof net.minecraft.world.item.BlockItem item && isLargeCog(item.getBlock()); }
    static boolean isDedicatedCogWheel(net.minecraft.world.level.block.Block block) { return block instanceof ICogWheel cog && cog.isDedicatedCogWheel(); }
    static boolean isDedicatedCogItem(net.minecraft.world.item.ItemStack stack) { return stack.getItem() instanceof net.minecraft.world.item.BlockItem item && isDedicatedCogWheel(item.getBlock()); }
    static boolean isSmallCogItem(net.minecraft.world.item.ItemStack stack) { return isSmallCog(stack); }
    static boolean isLargeCogItem(net.minecraft.world.item.ItemStack stack) { return isLargeCog(stack); }
    default boolean isDedicatedCogWheel() { return false; }
    default boolean isLargeCog() { return false; }
    default boolean isSmallCog() { return !isLargeCog(); }
}
