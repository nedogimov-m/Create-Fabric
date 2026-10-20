package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.network.chat.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;

/** Actual modern enchanting-power-provider tag/vanilla cost, with complete loaded bookshelf/gap preflight. */
public final class EnchantPowerDisplaySource extends NumericSingleLineDisplaySource {
    @Override public boolean isAvailable(DisplayLinkContext context){if(!super.isAvailable(context))return false;for(var offset:EnchantingTableBlock.BOOKSHELF_OFFSETS)if(!LoadedLevelAccess.isLoaded(context.level(),context.getSourcePos().offset(offset))||!LoadedLevelAccess.isLoaded(context.level(),context.getSourcePos().offset(offset.getX()/2,offset.getY(),offset.getZ()/2)))return false;return true;}
    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){if(!(context.getSourceBlockEntity()instanceof EnchantingTableBlockEntity))return ZERO.copy();int power=0;for(var offset:EnchantingTableBlock.BOOKSHELF_OFFSETS)if(EnchantingTableBlock.isValidBookShelf(context.level(),context.getSourcePos(),offset))power++;return Component.literal(Integer.toString(EnchantmentHelper.getEnchantmentCost(RandomSource.create(),2,power,new ItemStack(Items.DIAMOND_PICKAXE))));}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override protected String getTranslationKey(){return "max_enchant_level";}
}
