package com.simibubi.create.foundation.block;
import com.simibubi.create.content.decoration.palettes.PaletteDecorationsRegistry;
import java.util.*;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
/** Original2x4x2x3 copper owners; real Fabric maps feed native random ticks, axes and honeycomb. */
public final class CopperBlockSet {
 public interface Variant<B extends Block>{String getSuffix();}
 public static final class BlockVariant implements Variant<Block>{public static final BlockVariant INSTANCE=new BlockVariant();private BlockVariant(){}public String getSuffix(){return "";}}
 public static final class SlabVariant implements Variant<SlabBlock>{public static final SlabVariant INSTANCE=new SlabVariant();private SlabVariant(){}public String getSuffix(){return "_slab";}}
 public static final class StairVariant implements Variant<StairBlock>{public static final StairVariant INSTANCE=new StairVariant();private StairVariant(){}public String getSuffix(){return "_stairs";}}
 public static final Variant<?>[] DEFAULT_VARIANTS={BlockVariant.INSTANCE,SlabVariant.INSTANCE,StairVariant.INSTANCE};
 private final String name,endTextureName;private final Map<Variant<?>,Block[]> entries=new LinkedHashMap<>();
 public CopperBlockSet(String name,String endTextureName){this.name=name;this.endTextureName=endTextureName;for(var variant:DEFAULT_VARIANTS)entries.put(variant,new Block[8]);for(boolean waxed:new boolean[]{false,true})for(var variant:DEFAULT_VARIANTS)for(var age:WeatherState.values()){
  String id=(waxed?"waxed_":"")+getWeatherStatePrefix(age)+name;if(!variant.getSuffix().isEmpty())id=id.substring(0,id.length()-1);id+=variant.getSuffix();
  Block base=Blocks.COPPER_BLOCK.weathering().pick(age);
  var block=PaletteDecorationsRegistry.register(id,Properties.ofFullCopy(base).requiresCorrectToolForDrops(),p->{if(variant==BlockVariant.INSTANCE)return waxed?new Block(p):new WeatheringCopperFullBlock(age,p);if(variant==SlabVariant.INSTANCE)return waxed?new SlabBlock(p):new WeatheringCopperSlabBlock(age,p);return waxed?new CreateCopperStairBlock(p):new CreateWeatheringCopperStairBlock(age,p);});entries.get(variant)[getIndex(age,waxed)]=block;
  if(waxed)OxidizableBlocksRegistry.registerWaxable(get(variant,age,false),block);else if(age!=WeatherState.UNAFFECTED)OxidizableBlocksRegistry.registerNextStage(get(variant,WeatherState.values()[age.ordinal()-1],false),block);
 }}
 protected int getIndex(WeatherState age,boolean waxed){return age.ordinal()+(waxed?4:0);}
 public String getName(){return name;}public String getEndTextureName(){return endTextureName;}public Variant<?>[] getVariants(){return DEFAULT_VARIANTS.clone();}public boolean hasVariant(Variant<?> variant){return entries.containsKey(variant);}
 public Block get(Variant<?> variant,WeatherState age,boolean waxed){var values=entries.get(variant);return values==null?null:values[getIndex(age,waxed)];}
 public Block getStandard(){return get(BlockVariant.INSTANCE,WeatherState.UNAFFECTED,false);}
 public List<Block> blocks(){return entries.values().stream().flatMap(Arrays::stream).toList();}
 public static String getWeatherStatePrefix(WeatherState age){return age==WeatherState.UNAFFECTED?"":age.name().toLowerCase(Locale.ROOT)+"_";}
}
