package com.simibubi.create.infrastructure.config;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Existing real native JSON values seed missing source scope fields without replacing user choices. */
final class NativeConfigAliases {
 private record FileFields(String file,String section,List<String> names){}
 private static final List<FileFields> SERVER_FILES=List.of(
  new FileFields("create-legacy-materials.json","recipes",List.of("bulkPressing","bulkCutting","allowBrewingInMixer","allowShapelessInMixer","allowShapedSquareInPress","allowRegularCraftingInCrafter","maxFireworkIngredientsInCrafter","allowStonecuttingOnSaw","allowCastingBySpout","displayLogStrippingRecipes","lightSourceCountForRefinedRadiance","enableRefinedRadianceRecipe","enableShadowSteelRecipe")),
  new FileFields("create-equipment.json","equipment",List.of("airInBacktank","enchantedBacktankCapacity","maxExtendoGripActions","maxPotatoCannonShots")),
  new FileFields("create-schematics.json","schematics",List.of("creativePrintIncludesAir","maxSchematics","maxTotalSchematicSize","maxSchematicPacketSize","schematicIdleTimeout","schematicannonDelay","schematicannonShotsPerGunpowder")),
  new FileFields("create-trains.json","trains",List.of("trainsCauseDamage","maxTrackPlacementLength","maxAssemblyLength","maxBogeyCount","manualTrainSpeedModifier","trainTopSpeed","trainTurningTopSpeed","trainAcceleration","poweredTrainTopSpeed","poweredTrainTurningTopSpeed","poweredTrainAcceleration")),
  new FileFields("create-weighted-ejector.json","kinetics",List.of("maxEjectorDistance","ejectorScanInterval")),
  new FileFields("create-toolbox.json","equipment",List.of("toolboxRange")),
  new FileFields("create-mechanical-arm.json","logistics",List.of("mechanicalArmRange")),
  new FileFields("create-symmetry-wand.json","equipment",List.of("maxSymmetryWandRange")),
  new FileFields("create-minecart.json","kinetics",List.of("maxCartCouplingLength","survivalContraptionPickup","minecartContraptionInContainers")),
  new FileFields("create-copycat.json","equipment",List.of("placementAssistRange"))
 );
 static JsonObject server(Path directory,NativeConfigStore store){var result=new JsonObject();for(var entry:SERVER_FILES){var old=normalizedLegacy(entry.file,NativeConfigFiles.read(directory.resolve(entry.file)));for(var definition:store.definitions()){var path=definition.path();if(path.isEmpty()||!path.getFirst().equals(entry.section)||!entry.names.contains(definition.getName())||!old.has(definition.getName()))continue;NativeConfigStore.put(result,path,old.get(definition.getName()));}}return result;}
 static JsonObject common(Path directory,NativeConfigStore store){var result=new JsonObject();var old=NativeConfigFiles.read(directory.resolve("create-worldgen.json"));if(old.has("disableWorldGen"))NativeConfigStore.put(result,List.of("worldgen","disableWorldGen"),old.get("disableWorldGen"));return result;}
 static JsonObject client(JsonObject actual,NativeConfigStore store){var result=actual.deepCopy();var aliases=Map.of("overlayOffsetX","overlayOffsetX","overlayOffsetY","overlayOffsetY","overlayCustomColor","customColorsOverlay","enableOverstressedTooltip","enableOverstressedTooltip","customBackgroundOverlay","customBackgroundOverlay","customBorderTopOverlay","customBorderTopOverlay","customBorderBotOverlay","customBorderBotOverlay");for(var entry:aliases.entrySet()){if(!actual.has(entry.getKey()))continue;for(var definition:store.definitions())if(definition.getName().equals(entry.getValue())&&NativeConfigStore.find(actual,definition.path())==null)NativeConfigStore.put(result,definition.path(),legacyOverlayValue(actual.get(entry.getKey()),definition));}return result;}
 /** Old Gson loaders accepted numeric/boolean strings and integer narrowing; preserve those real files. */
 static JsonObject normalizedLegacy(String file,JsonObject actual){
  if(!file.equals("create-minecart.json")&&!file.equals("create-copycat.json"))return actual;
  var result=actual.deepCopy();
  if(file.equals("create-copycat.json")){coerceInteger(result,"placementAssistRange");return result;}
  coerceInteger(result,"maxCartCouplingLength");coerceBoolean(result,"survivalContraptionPickup");coerceBoolean(result,"minecartContraptionInContainers");return result;
 }
 private static void coerceInteger(JsonObject document,String key){if(document.has(key))document.addProperty(key,document.get(key).getAsInt());}
 private static void coerceBoolean(JsonObject document,String key){if(document.has(key))document.addProperty(key,document.get(key).getAsBoolean());}
 private static JsonElement legacyOverlayValue(JsonElement input,NativeConfigBase.CValue<?> definition){
  try{if(definition instanceof NativeConfigBase.ConfigBool)return new JsonPrimitive(input.getAsBoolean());return new JsonPrimitive(input.getAsInt());}
  catch(RuntimeException malformed){return new Gson().toJsonTree(definition.defaultValue());}
 }
 private NativeConfigAliases(){}
}
