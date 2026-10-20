package com.simibubi.create.content.decoration.palettes;
import com.simibubi.create.*;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
/** The14 original source families, preserving their real base singleton and18 variant IDs each. */
public enum AllPaletteStoneTypes {
    GRANITE(()->Blocks.GRANITE),DIORITE(()->Blocks.DIORITE),ANDESITE(()->Blocks.ANDESITE),CALCITE(()->Blocks.CALCITE),DRIPSTONE(()->Blocks.DRIPSTONE_BLOCK),DEEPSLATE(()->Blocks.DEEPSLATE),TUFF(()->Blocks.TUFF),ASURINE(()->AllBlocks.ASURINE),CRIMSITE(()->AllBlocks.CRIMSITE),LIMESTONE(()->AllBlocks.LIMESTONE),OCHRUM(()->AllBlocks.OCHRUM),SCORIA(()->AllBlocks.SCORIA),SCORCHIA(()->AllBlocks.SCORCHIA),VERIDIUM(()->AllBlocks.VERIDIUM);
    public Supplier<Block> baseBlock;public PaletteBlockPattern[] variantTypes;public TagKey<Item> materialTag;private PalettesVariantEntry variants;private static boolean registered;
    AllPaletteStoneTypes(Supplier<Block> base){baseBlock=base;variantTypes=PaletteBlockPattern.STANDARD_RANGE;materialTag=TagKey.create(Registries.ITEM,Create.asResource("stone_types/"+name().toLowerCase(java.util.Locale.ROOT)));}
    public Supplier<Block> getBaseBlock(){return baseBlock;}
    public PalettesVariantEntry getVariants(){return variants;}
    public static void register(){if(registered)return;registered=true;for(var stone:values())stone.variants=new PalettesVariantEntry(stone.name().toLowerCase(java.util.Locale.ROOT),stone);Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,Create.asResource("palettes"),FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.create.palettes")).icon(()->new ItemStack(GRANITE.getVariants().registeredBlocks.getFirst())).displayItems((context,output)->{for(var stone:values()){if(stone.ordinal()>=ASURINE.ordinal())output.accept(stone.baseBlock.get());for(var block:stone.variants.registeredBlocks)output.accept(block);for(var block:stone.variants.registeredPartials)output.accept(block);}}).build());}
}
