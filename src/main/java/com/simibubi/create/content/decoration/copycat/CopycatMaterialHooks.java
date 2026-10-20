package com.simibubi.create.content.decoration.copycat;

import net.fabricmc.fabric.api.event.player.PlayerPickItemEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Native consumers supply their actual contacted position; this bridge never owns or transfers an item. */
public final class CopycatMaterialHooks {
    public static BlockState material(BlockGetter level,BlockPos pos,BlockState state){
        if(!(state.getBlock() instanceof CopycatBlock))return state;
        var material=CopycatBlock.getMaterial(level,pos);return material.isAir()?state:material;
    }
    public static Block frictionBlock(Entity entity,Block original){
        if(!(original instanceof CopycatBlock))return original;
        var pos=entity.getBlockPosBelowThatAffectsMyMovement();var state=entity.level().getBlockState(pos);
        return state.getBlock()==original?material(entity.level(),pos,state).getBlock():original;
    }
    public static ItemStack pickForToolbox(Player player,LevelReader level,BlockPos pos,boolean includeData){
        var state=level.getBlockState(pos);return player!=null&&player.isShiftKeyDown()&&state.getBlock() instanceof CopycatBlock?new ItemStack(state.getBlock()):state.getCloneItemStack(level,pos,includeData);
    }
    public static void register(){
        // Fabric invokes this after the real native range/load checks, then performs native finite inventory picking.
        PlayerPickItemEvents.BLOCK.register((player,pos,state,includeData)->player.isShiftKeyDown()&&state.getBlock() instanceof CopycatBlock?new ItemStack(state.getBlock()):null);
    }
    private CopycatMaterialHooks(){}
}
