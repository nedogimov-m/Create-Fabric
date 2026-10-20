package com.simibubi.create.content.decoration.palettes;
import com.simibubi.create.Create;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
/** Exact pinned properties and actual native registry owners for the stone palette. */
public final class PaletteStoneRegistry {
    private static final Map<String,Block> BLOCKS=new LinkedHashMap<>();
    public static BlockBehaviour.Properties properties(String family){
        Block base=switch(family){case "asurine","crimsite"->Blocks.DEEPSLATE;case "limestone"->Blocks.SANDSTONE;case "ochrum"->Blocks.CALCITE;case "scoria","scorchia"->Blocks.BLACKSTONE;case "veridium"->Blocks.TUFF;default->throw new IllegalArgumentException(family);};
        var properties=BlockBehaviour.Properties.ofFullCopy(base).requiresCorrectToolForDrops();
        if(!family.equals("scoria")&&!family.equals("scorchia"))properties.destroyTime(1.25f);
        return properties.mapColor(switch(family){case "asurine"->MapColor.COLOR_BLUE;case "crimsite"->MapColor.COLOR_RED;case "limestone"->MapColor.SAND;case "ochrum"->MapColor.TERRACOTTA_YELLOW;case "scoria"->MapColor.COLOR_BROWN;case "scorchia"->MapColor.TERRACOTTA_GRAY;case "veridium"->MapColor.WARPED_NYLIUM;default->throw new IllegalArgumentException(family);});
    }
    public static <B extends Block>B register(String name,BlockBehaviour.Properties properties,Function<BlockBehaviour.Properties,B> factory){
        if(BLOCKS.containsKey(name))throw new IllegalStateException("Palette registered twice: "+name);
        var id=Create.asResource(name);var key=ResourceKey.create(Registries.BLOCK,id);var block=Registry.register(BuiltInRegistries.BLOCK,key,factory.apply(properties.setId(key)));var itemKey=ResourceKey.create(Registries.ITEM,id);Registry.register(BuiltInRegistries.ITEM,itemKey,new BlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));BLOCKS.put(name,block);return block;
    }
    public static Map<String,Block> blocks(){return Collections.unmodifiableMap(BLOCKS);}
    private PaletteStoneRegistry(){}
}
