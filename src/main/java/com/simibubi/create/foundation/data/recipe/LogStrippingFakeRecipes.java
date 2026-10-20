package com.simibubi.create.foundation.data.recipe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.tags.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.stateproviders.*;
import net.minecraft.world.level.levelgen.blockpredicates.*;

/** Display-only source recipes from26.3's real axe-transform registry; never installed as crafting recipes. */
public final class LogStrippingFakeRecipes {
 public static ItemStack displayAxe(){var axe=new ItemStack(Items.IRON_AXE);axe.set(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);axe.set(DataComponents.CUSTOM_NAME,Component.translatable("create.recipe.item_application.any_axe").withStyle(style->style.withItalic(false)));return axe;}
 public static List<RecipeHolder<ItemApplicationRecipe>> createRecipes(HolderLookup.Provider registries){
  if(!AllConfigs.server().recipes.displayLogStrippingRecipes.get())return List.of();
  var transformer=registries.lookupOrThrow(Registries.BLOCK_TRANSFORMER).getOrThrow(BlockTransformers.AXE).value();
  var output=new LinkedHashMap<Identifier,RecipeHolder<ItemApplicationRecipe>>();
  for(var holder:BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.LOGS)){
   if(!(holder.value() instanceof BlockItem item))continue;Block target=null;
   for(var transform:transformer.transforms()){target=stripped(transform.blockStateProvider().value(),item.getBlock(),registries);if(target!=null)break;}
   if(target==null||target.asItem()==Items.AIR)continue;var result=target.asItem();var key=BuiltInRegistries.ITEM.getKey(result);var id=Identifier.fromNamespaceAndPath(key.getNamespace(),key.getPath()+"_via_vanilla_stripping");
   var recipe=new ItemApplicationRecipe(List.of(Ingredient.of(item),Ingredient.of(Items.IRON_AXE)),List.of(new ProcessingOutput(result,1,DataComponentPatch.EMPTY,1)),false,false);output.putIfAbsent(id,new RecipeHolder<>(ResourceKey.create(Registries.RECIPE,id),recipe));
  }
  return List.copyOf(output.values());
 }
 private static Block stripped(BlockStateProvider provider,Block input,HolderLookup.Provider registries){
  if(!(provider instanceof RuleBasedStateProvider rules))return null;
  var ops=registries.createSerializationContext(JsonOps.INSTANCE);
  for(var rule:rules.rules()){
   if(!(rule.ifTrue() instanceof MatchingBlocksPredicate))continue;
   var encoded=BlockPredicate.CODEC.encodeStart(ops,rule.ifTrue()).getOrThrow();if(!encoded.isJsonObject())continue;var predicate=encoded.getAsJsonObject();
   if(predicate.has("offset")&&predicate.getAsJsonArray("offset").asList().stream().anyMatch(value->value.getAsInt()!=0))continue;
   if(!matches(predicate.get("blocks"),input))continue;
   var result=rule.then().value();while(result instanceof CopyPropertiesProvider copy)result=copy.source().value();
   var state=BlockStateProvider.DIRECT_CODEC.encodeStart(ops,result).getOrThrow();if(!state.isJsonObject())return null;var data=state.getAsJsonObject();
   if(data.has("state")&&data.get("state").isJsonObject())data=data.getAsJsonObject("state");if(!data.has("id"))return null;var id=Identifier.tryParse(data.get("id").getAsString());return id==null?null:BuiltInRegistries.BLOCK.getValue(id);
  }return null;
 }
 private static boolean matches(JsonElement ids,Block input){
  if(ids==null)return false;if(ids.isJsonArray()){for(var id:ids.getAsJsonArray())if(matches(id,input))return true;return false;}
  if(!ids.isJsonPrimitive()||!ids.getAsJsonPrimitive().isString())return false;var text=ids.getAsString();
  if(text.startsWith("#")){var id=Identifier.tryParse(text.substring(1));return id!=null&&input.builtInRegistryHolder().is(TagKey.create(Registries.BLOCK,id));}
  return text.equals(BuiltInRegistries.BLOCK.getKey(input).toString());
 }
 private LogStrippingFakeRecipes(){}
}
