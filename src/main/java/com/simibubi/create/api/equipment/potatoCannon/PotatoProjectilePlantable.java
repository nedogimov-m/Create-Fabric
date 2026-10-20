package com.simibubi.create.api.equipment.potatoCannon;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;

/** Fabric counterpart of the original SpecialPlantable hook for real source crop-hit actions. */
public interface PotatoProjectilePlantable {
    boolean canPlace(ItemStack projectile,LevelAccessor level,BlockPos position);
    void plant(ItemStack projectile,LevelAccessor level,BlockPos position);
    Map<Block,PotatoProjectilePlantable> REGISTRY=Collections.synchronizedMap(new IdentityHashMap<>());
    static void register(Block block,PotatoProjectilePlantable action){if(REGISTRY.putIfAbsent(Objects.requireNonNull(block),Objects.requireNonNull(action))!=null)throw new IllegalArgumentException("Duplicate potato plant action for "+block);}
    static PotatoProjectilePlantable get(Block block){
        var registered=REGISTRY.get(block);if(registered!=null)return registered;if(block instanceof PotatoProjectilePlantable custom)return custom;
        if(!(block instanceof VegetationBlock))return null;
        return new PotatoProjectilePlantable(){public boolean canPlace(ItemStack projectile,LevelAccessor level,BlockPos position){return block.defaultBlockState().canSurvive(level,position);}public void plant(ItemStack projectile,LevelAccessor level,BlockPos position){level.setBlock(position,block.defaultBlockState(),Block.UPDATE_ALL);}};
    }
}
