package com.simibubi.create.content.decoration.palettes;
import com.simibubi.create.Create;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
/** Separate actual registry owners: never pollute the source252 stone-variant registry. */
public final class PaletteDecorationsRegistry {
 private static final Map<String,Block> BLOCKS=new LinkedHashMap<>();
 public static <B extends Block>B register(String name,Properties properties,Function<Properties,B> factory){if(BLOCKS.containsKey(name))throw new IllegalStateException(name);var id=Create.asResource(name);var key=ResourceKey.create(Registries.BLOCK,id);var block=Registry.register(BuiltInRegistries.BLOCK,key,factory.apply(properties.setId(key)));var itemKey=ResourceKey.create(Registries.ITEM,id);Registry.register(BuiltInRegistries.ITEM,itemKey,new BlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));BLOCKS.put(name,block);return block;}
 public static Map<String,Block> blocks(){return Collections.unmodifiableMap(BLOCKS);}
 private PaletteDecorationsRegistry(){}
}
