package com.simibubi.create.content.decoration.palettes;
import com.simibubi.create.*;
import java.util.*;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
/** Native counterparts of source windows/panes, preserving the root framed-glass singleton. */
public final class AllPaletteBlocks {
 private static final Map<String,Block> BLOCKS=new LinkedHashMap<>();private static boolean registered;
 private static Properties glass(){return Properties.ofFullCopy(Blocks.GLASS).isValidSpawn((s,l,p,t)->false).isRedstoneConductor((s,l,p)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p,b)->false);}
 private static <B extends Block>B add(String name,Properties properties,java.util.function.Function<Properties,B> factory){var block=PaletteDecorationsRegistry.register(name,properties,factory);BLOCKS.put(name,block);return block;}
 private static WindowBlock window(String name,MapColor color,boolean translucent){return add(name,glass().mapColor(color),p->new WindowBlock(p,translucent));}
 private static ConnectedGlassPaneBlock pane(String name,Block base){return add(name+"_pane",Properties.ofFullCopy(Blocks.GLASS_PANE).mapColor(base.defaultMapColor()),ConnectedGlassPaneBlock::new);}
 public static final TransparentBlock TILED_GLASS=add("tiled_glass",Properties.ofFullCopy(Blocks.GLASS),TransparentBlock::new);
 public static final ConnectedGlassBlock FRAMED_GLASS=AllBlocks.FRAMED_GLASS,HORIZONTAL_FRAMED_GLASS=add("horizontal_framed_glass",glass(),ConnectedGlassBlock::new),VERTICAL_FRAMED_GLASS=add("vertical_framed_glass",glass(),ConnectedGlassBlock::new);
 public static final GlassPaneBlock TILED_GLASS_PANE=add("tiled_glass_pane",Properties.ofFullCopy(Blocks.GLASS_PANE).mapColor(TILED_GLASS.defaultMapColor()),GlassPaneBlock::new);
 public static final ConnectedGlassPaneBlock FRAMED_GLASS_PANE=pane("framed_glass",FRAMED_GLASS);
 public static final ConnectedGlassPaneBlock HORIZONTAL_FRAMED_GLASS_PANE=pane("horizontal_framed_glass",HORIZONTAL_FRAMED_GLASS);
 public static final ConnectedGlassPaneBlock VERTICAL_FRAMED_GLASS_PANE=pane("vertical_framed_glass",VERTICAL_FRAMED_GLASS);
 public static final WindowBlock OAK_WINDOW=window("oak_window",Blocks.OAK_PLANKS.defaultMapColor(),false);
 public static final WindowBlock SPRUCE_WINDOW=window("spruce_window",Blocks.SPRUCE_PLANKS.defaultMapColor(),false);
 public static final WindowBlock BIRCH_WINDOW=window("birch_window",Blocks.BIRCH_PLANKS.defaultMapColor(),true);
 public static final WindowBlock JUNGLE_WINDOW=window("jungle_window",Blocks.JUNGLE_PLANKS.defaultMapColor(),false);
 public static final WindowBlock ACACIA_WINDOW=window("acacia_window",Blocks.ACACIA_PLANKS.defaultMapColor(),false);
 public static final WindowBlock DARK_OAK_WINDOW=window("dark_oak_window",Blocks.DARK_OAK_PLANKS.defaultMapColor(),false);
 public static final WindowBlock MANGROVE_WINDOW=window("mangrove_window",Blocks.MANGROVE_PLANKS.defaultMapColor(),false);
 public static final WindowBlock CRIMSON_WINDOW=window("crimson_window",Blocks.CRIMSON_PLANKS.defaultMapColor(),false);
 public static final WindowBlock WARPED_WINDOW=window("warped_window",Blocks.WARPED_PLANKS.defaultMapColor(),false);
 public static final WindowBlock CHERRY_WINDOW=window("cherry_window",Blocks.CHERRY_PLANKS.defaultMapColor(),false);
 public static final WindowBlock BAMBOO_WINDOW=window("bamboo_window",Blocks.BAMBOO_PLANKS.defaultMapColor(),false);
 public static final WindowBlock ORNATE_IRON_WINDOW=window("ornate_iron_window",MapColor.TERRACOTTA_LIGHT_GRAY,false);
 public static final WindowBlock INDUSTRIAL_IRON_WINDOW=window("industrial_iron_window",MapColor.COLOR_GRAY,false);
 public static final WindowBlock WEATHERED_IRON_WINDOW=window("weathered_iron_window",MapColor.TERRACOTTA_LIGHT_GRAY,true);
 public static final ConnectedGlassPaneBlock OAK_WINDOW_PANE=pane("oak_window",OAK_WINDOW);
 public static final ConnectedGlassPaneBlock SPRUCE_WINDOW_PANE=pane("spruce_window",SPRUCE_WINDOW);
 public static final ConnectedGlassPaneBlock BIRCH_WINDOW_PANE=pane("birch_window",BIRCH_WINDOW);
 public static final ConnectedGlassPaneBlock JUNGLE_WINDOW_PANE=pane("jungle_window",JUNGLE_WINDOW);
 public static final ConnectedGlassPaneBlock ACACIA_WINDOW_PANE=pane("acacia_window",ACACIA_WINDOW);
 public static final ConnectedGlassPaneBlock DARK_OAK_WINDOW_PANE=pane("dark_oak_window",DARK_OAK_WINDOW);
 public static final ConnectedGlassPaneBlock MANGROVE_WINDOW_PANE=pane("mangrove_window",MANGROVE_WINDOW);
 public static final ConnectedGlassPaneBlock CRIMSON_WINDOW_PANE=pane("crimson_window",CRIMSON_WINDOW);
 public static final ConnectedGlassPaneBlock WARPED_WINDOW_PANE=pane("warped_window",WARPED_WINDOW);
 public static final ConnectedGlassPaneBlock CHERRY_WINDOW_PANE=pane("cherry_window",CHERRY_WINDOW);
 public static final ConnectedGlassPaneBlock BAMBOO_WINDOW_PANE=pane("bamboo_window",BAMBOO_WINDOW);
 public static final ConnectedGlassPaneBlock ORNATE_IRON_WINDOW_PANE=pane("ornate_iron_window",ORNATE_IRON_WINDOW);
 public static final ConnectedGlassPaneBlock INDUSTRIAL_IRON_WINDOW_PANE=pane("industrial_iron_window",INDUSTRIAL_IRON_WINDOW);
 public static final ConnectedGlassPaneBlock WEATHERED_IRON_WINDOW_PANE=pane("weathered_iron_window",WEATHERED_IRON_WINDOW);
 public static Map<String,Block> blocks(){return Collections.unmodifiableMap(BLOCKS);}
 public static void register(){if(registered)return;registered=true;CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB,Create.asResource("palettes"))).register(output->BLOCKS.values().forEach(output::accept));CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB,Create.asResource("base"))).register(output->{AllBlocks.COPPER_SHINGLES.blocks().forEach(output::accept);AllBlocks.COPPER_TILES.blocks().forEach(output::accept);output.accept(AllBlocks.WEATHERED_IRON_BLOCK);});}
 private AllPaletteBlocks(){}
}
