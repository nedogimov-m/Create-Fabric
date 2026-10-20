package com.simibubi.create.content.equipment.blueprint;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.*;
import com.simibubi.create.content.logistics.filter.AttributeFilterWhitelistMode;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry;
import com.simibubi.create.content.logistics.item.filter.attribute.attributes.InTagAttribute;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

/** Converts actual native/Fabric ingredients to source ghost filters through their public codecs. */
public final class BlueprintIngredients {
 public static boolean assignCompleteRecipe(Level level,SimpleContainer ghosts,CraftingRecipe recipe){
  var converted=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));
  if(recipe instanceof ShapedRecipe shaped){
   if(shaped.getWidth()>3||shaped.getHeight()>3)return false;
   var ingredients=shaped.getIngredients();
   for(int row=0;row<shaped.getHeight();row++)for(int col=0;col<shaped.getWidth();col++){
    var ingredient=ingredients.get(row*shaped.getWidth()+col);
    if(ingredient.isPresent()){var sample=convert(level,ingredient.get());if(sample.isEmpty())return false;converted.set(row*3+col,sample);}
   }
  }else{
   var placement=recipe.placementInfo();if(placement.isImpossibleToPlace()||placement.ingredients().size()>9)return false;
   for(int i=0;i<placement.ingredients().size();i++){var sample=convert(level,placement.ingredients().get(i));if(sample.isEmpty())return false;converted.set(i,sample);}
  }
  var output=recipe.display().stream().map(display->display.result().resolveForFirstStack(SlotDisplayContext.fromLevel(level))).filter(stack->!stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
  if(output.isEmpty())return false;
  for(int i=0;i<9;i++)ghosts.setItem(i,converted.get(i));ghosts.setItem(9,output);return true;
 }
 public static ItemStack convert(Level level,Ingredient ingredient){
  var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
  var encoded=Ingredient.CODEC.encodeStart(ops,ingredient).result();
  return encoded.map(json->convertEncoded(level,ingredient,json)).orElse(ItemStack.EMPTY);
 }
 private static ItemStack convertEncoded(Level level,Ingredient ingredient,JsonElement json){
  if(json.isJsonPrimitive()&&json.getAsJsonPrimitive().isString()){
   var value=json.getAsString();if(value.startsWith("#")){var id=Identifier.tryParse(value.substring(1));if(id==null)return ItemStack.EMPTY;var filter=new ItemStack(AllItems.ATTRIBUTE_FILTER);filter.set(AllDataComponents.ATTRIBUTE_FILTER_WHITELIST_MODE,AttributeFilterWhitelistMode.WHITELIST_DISJ);filter.set(AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES,List.of(new ItemAttributeEntry(new InTagAttribute(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,id)),false)));return filter;}
   var id=Identifier.tryParse(value);return id==null?ItemStack.EMPTY:BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
  }
  if(json.isJsonArray())return alternatives(level,json.getAsJsonArray());
  if(!json.isJsonObject())return ItemStack.EMPTY;
  var object=json.getAsJsonObject();String type=object.has("fabric:type")?object.get("fabric:type").getAsString():"";
  if(type.equals("fabric:any")&&object.has("ingredients")&&object.get("ingredients").isJsonArray())return alternatives(level,object.getAsJsonArray("ingredients"));
  if(type.equals("fabric:components")||type.equals("fabric:custom_data")){
   var samples=ingredient.display().resolveForStacks(SlotDisplayContext.fromLevel(level));
   if(samples.isEmpty()||samples.size()>18||samples.stream().anyMatch(sample->sample.isEmpty()||!ingredient.test(sample)))return ItemStack.EMPTY;
   return list(samples,true);
  }
  // Unknown predicates must never be widened to their display alternatives or Fabric's dummy STONE set.
  return ItemStack.EMPTY;
 }
 private static ItemStack alternatives(Level level,JsonArray alternatives){
  if(alternatives.isEmpty()||alternatives.size()>18)return ItemStack.EMPTY;var samples=new ArrayList<ItemStack>();var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
  for(var encoded:alternatives){var ingredient=Ingredient.CODEC.parse(ops,encoded).result();if(ingredient.isEmpty())return ItemStack.EMPTY;var sample=convertEncoded(level,ingredient.get(),encoded);if(sample.isEmpty())return ItemStack.EMPTY;samples.add(sample);}
  return samples.size()==1?samples.getFirst():list(samples,false);
 }
 private static ItemStack list(List<ItemStack> samples,boolean respect){var filter=new ItemStack(AllItems.FILTER);filter.set(AllDataComponents.FILTER_ITEMS,ItemContainerContents.fromItems(samples));if(respect)filter.set(AllDataComponents.FILTER_ITEMS_RESPECT_NBT,true);return filter;}
 private BlueprintIngredients(){}
}
