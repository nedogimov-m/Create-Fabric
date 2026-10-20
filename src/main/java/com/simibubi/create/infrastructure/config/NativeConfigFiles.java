package com.simibubi.create.infrastructure.config;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Explicit bridge for the accepted per-system JSONs; never rewrites an existing file with defaults. */
public final class NativeConfigFiles {
 public static JsonObject read(Path file){try{if(!Files.exists(file))return new JsonObject();var result=JsonParser.parseString(Files.readString(file));if(!result.isJsonObject())throw new IllegalArgumentException("Expected native Create settings object at "+file);return result.getAsJsonObject();}catch(IOException failure){throw new UncheckedIOException("Unable to read native Create settings "+file,failure);}}
 public static void reloadSection(NativeConfigBase section,Path file,Set<String> originalKeys){
  var document=read(file);var imported=new LinkedHashMap<String,Object>();var initial=new JsonObject();
  for(var value:section.directValues()){
   if(originalKeys.contains(value.getName()))initial.add(value.getName(),encode(value,value.defaultValue()));
   var actual=document.get(value.getName());if(actual!=null)imported.put(value.key(),value.decode(actual));else if(originalKeys.contains(value.getName()))imported.put(value.key(),value.defaultValue());
  }
  if(!Files.exists(file))NativeConfigStore.persist(file,initial);
  var store=section.store();store.importValues(imported,store.file().isPresent());
 }
 public static <T>void reloadAdapter(Path file,Codec<T> codec,T defaults,Function<T,Map<String,Object>> mapped,Consumer<T> initialInstall){
  boolean existing=Files.exists(file);var actual=existing?codec.parse(JsonOps.INSTANCE,NativeConfigAliases.normalizedLegacy(file.getFileName().toString(),read(file))).getOrThrow():defaults;
  var values=mapped.apply(actual);if(!existing){var encoded=codec.encodeStart(JsonOps.INSTANCE,defaults).getOrThrow();if(!encoded.isJsonObject())throw new IllegalArgumentException("Expected native adapter settings object");NativeConfigStore.persist(file,encoded.getAsJsonObject());}
  AllConfigs.importLegacyValues(values,()->initialInstall.accept(actual));
 }
 @SuppressWarnings("unchecked") private static <V>JsonElement encode(NativeConfigBase.CValue<V> definition,Object value){return definition.encode((V)value);}
 private NativeConfigFiles(){}
}
