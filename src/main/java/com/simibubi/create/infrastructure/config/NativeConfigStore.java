package com.simibubi.create.infrastructure.config;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Real scope persistence: validate a whole candidate, persist it atomically, then publish one snapshot. */
public final class NativeConfigStore {
 private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
 private final ConfigScope scope;
 private final NativeConfigBase root;
 private final LinkedHashMap<String,NativeConfigBase.CValue<?>> definitions=new LinkedHashMap<>();
 private volatile Map<String,Object> values=Map.of(),remote;
 private volatile boolean remoteComplete;
 private JsonObject document=new JsonObject();
 private Path file;
 private BooleanSupplier serverThread=()->false;
 private Consumer<Runnable> writeDispatcher=Runnable::run;
 private Consumer<NativeConfigStore> changed=ignored->{};
 private Function<Map<String,Object>,Runnable> prepareApply=ignored->()->{};

 public NativeConfigStore(ConfigScope scope,NativeConfigBase root){this.scope=scope;this.root=root;var builder=new Builder(this);root.depth=0;root.bindStore(this);root.registerAll(builder);values=Map.copyOf(builder.initial);}
 static NativeConfigStore detached(NativeConfigBase root){return new NativeConfigStore(root.defaultScope(),root);}
 public ConfigScope scope(){return scope;}
 public NativeConfigBase root(){return root;}
 public Optional<Path> file(){return Optional.ofNullable(file);}
 public List<NativeConfigBase.CValue<?>> definitions(){return List.copyOf(definitions.values());}
 public Map<String,Object> serverValues(){return values;}
 public Map<String,Object> effectiveValues(){var received=remote;return received==null||serverThread.getAsBoolean()?values:received;}
 public boolean hasRemote(){return remote!=null;}
 public boolean hasCompleteRemoteSnapshot(){return remote!=null&&remoteComplete;}
 public void configure(BooleanSupplier serverThread,Consumer<Runnable> dispatcher,Function<Map<String,Object>,Runnable> prepare,Consumer<NativeConfigStore> changed){this.serverThread=Objects.requireNonNull(serverThread);writeDispatcher=Objects.requireNonNull(dispatcher);prepareApply=Objects.requireNonNull(prepare);this.changed=Objects.requireNonNull(changed);}
 @SuppressWarnings("unchecked") <V>V read(NativeConfigBase.CValue<V> definition,boolean authoritative){var snapshot=authoritative?values:effectiveValues();return (V)snapshot.getOrDefault(definition.key(),definition.defaultValue());}
 @SuppressWarnings("unchecked") public <V>V readClient(NativeConfigBase.CValue<V> definition){var received=remote;return (V)(received==null?values:received).getOrDefault(definition.key(),definition.defaultValue());}
 public NativeConfigBase.CValue<?> definition(String path){var value=definitions.get(path);if(value==null)throw new IllegalArgumentException("Unknown "+scope+" configuration path "+path);return value;}
 public void reload(Path path){reload(path,new JsonObject(),false);}
 public void reload(Path path,JsonObject inherited,boolean reloading){
  reload(path,inherited,reloading,null);
 }
 /** Explicit original source selection is used only for the native default SERVER scope. */
 public void reload(Path path,JsonObject inherited,boolean reloading,Path legacySource){
  var migration=NeoForgeTomlImport.prepare(path,this,legacySource);
  JsonObject actual=new JsonObject();
  try{if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)){var element=JsonParser.parseString(Files.readString(path));if(!element.isJsonObject())throw new IllegalArgumentException("Expected "+scope+" configuration object at "+path);actual=element.getAsJsonObject();migration=null;}else if(migration!=null)actual=migration.document();}
  catch(IOException failure){throw new UncheckedIOException("Unable to read actual Create configuration "+path,failure);}
  var combined=merge(inherited,actual);
  // NeoForge world override files are complete source scopes: absent known fields get source
  // defaults, rather than another world's/global custom values. Unknown inherited fields survive.
  var candidate=decode(migration==null?combined:actual);var application=prepareApply.apply(candidate);var rendered=render(combined,candidate);
  if(migration!=null){if(!migration.publish(rendered)){reload(path,inherited,reloading,legacySource);return;}}else persist(path,rendered);
  file=path;document=rendered;values=Map.copyOf(candidate);application.run();if(reloading)root.onReload();else root.onLoad();changed.accept(this);
 }
 public <V>void set(NativeConfigBase.CValue<V> definition,V value){update(Map.of(definition.key(),definition.validate(value)),true);}
 /** Existing native adapters import only real supplied settings; all other scope values survive. */
 public void importValues(Map<String,?> imported,boolean persist){update(imported,persist);}
 private void update(Map<String,?> updates,boolean save){
  var validated=new LinkedHashMap<String,Object>();updates.forEach((key,value)->validated.put(key,definition(key).validate(value)));
  writeDispatcher.accept(()->{
   var candidate=new LinkedHashMap<>(values);candidate.putAll(validated);var snapshot=Map.copyOf(candidate);var application=prepareApply.apply(snapshot);var rendered=render(document,snapshot);
   if(save&&file!=null)persist(file,rendered);else if(save&&file==null)throw new IllegalStateException("No actual backing file for "+scope+" configuration");document=rendered;values=snapshot;application.run();root.onReload();changed.accept(this);
  });
 }
 public void installRemote(JsonObject source){remote=Map.copyOf(decode(source));remoteComplete=true;}
 public void installRemoteValues(Map<String,?> imported){if(remoteComplete)return;var actual=new LinkedHashMap<>(remote==null?values:remote);imported.forEach((key,value)->actual.put(key,definition(key).validate(value)));remote=Map.copyOf(actual);}
 public void clearRemote(){remote=null;remoteComplete=false;}
 public void clearRemoteValues(Collection<String> keys){var actual=remote;if(actual==null)return;var next=new LinkedHashMap<>(actual);for(var key:keys)next.put(key,values.get(key));remote=Map.copyOf(next);}
 public JsonObject serverDocument(){return render(new JsonObject(),values);}
 public JsonObject effectiveDocument(){return render(new JsonObject(),effectiveValues());}
 public Map<String,Object> decode(JsonObject source){var result=new LinkedHashMap<String,Object>();definitions.forEach((key,definition)->{var input=find(source,definition.path());result.put(key,input==null?definition.defaultValue():definition.decode(input));});return Map.copyOf(result);}
 private JsonObject render(JsonObject source,Map<String,Object> snapshot){var result=source.deepCopy();definitions.forEach((key,definition)->put(result,definition.path(),encoded(definition,snapshot.get(key))));return result;}
 @SuppressWarnings("unchecked") private static <V>JsonElement encoded(NativeConfigBase.CValue<V> definition,Object value){return definition.encode((V)value);}
 public static JsonElement find(JsonObject document,List<String> path){JsonElement current=document;for(var key:path){if(!current.isJsonObject()||!current.getAsJsonObject().has(key))return null;current=current.getAsJsonObject().get(key);}return current;}
 public static void put(JsonObject document,List<String> path,JsonElement value){var parent=document;for(int i=0;i<path.size()-1;i++){var key=path.get(i);if(!parent.has(key))parent.add(key,new JsonObject());if(!parent.get(key).isJsonObject())throw new IllegalArgumentException("Configuration section collides with existing value at "+String.join(".",path.subList(0,i+1)));parent=parent.getAsJsonObject(key);}parent.add(path.getLast(),value.deepCopy());}
 public static JsonObject merge(JsonObject inherited,JsonObject actual){var result=inherited.deepCopy();actual.entrySet().forEach(entry->{var old=result.get(entry.getKey());result.add(entry.getKey(),old!=null&&old.isJsonObject()&&entry.getValue().isJsonObject()?merge(old.getAsJsonObject(),entry.getValue().getAsJsonObject()):entry.getValue().deepCopy());});return result;}
 static void persist(Path path,JsonObject value){
  try{
   var text=JSON.toJson(value)+"\n";if(Files.isRegularFile(path)&&Files.readString(path).equals(text))return;var parent=path.toAbsolutePath().getParent();Files.createDirectories(parent);var temporary=Files.createTempFile(parent,"create-config-",".json");
   try{Files.writeString(temporary,text,StandardCharsets.UTF_8);try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}
  }catch(IOException failure){throw new UncheckedIOException("Unable to persist actual Create configuration "+path,failure);}
 }
 public static final class Builder {
  private final NativeConfigStore store;private final List<String> path=new ArrayList<>();private final Map<String,Object> initial=new LinkedHashMap<>();
  private Builder(NativeConfigStore store){this.store=store;}
  public NativeConfigStore store(){return store;}
  public void push(String name){path.addAll(List.of(name.split("\\.")));}
  public void pop(int count){if(count<0||count>path.size())throw new IllegalArgumentException("Invalid source configuration group depth");path.subList(path.size()-count,path.size()).clear();}
  public List<String> path(String name){var result=new ArrayList<>(path);result.add(name);return List.copyOf(result);}
  <V>void add(NativeConfigBase.CValue<V> definition,V inherited){var key=definition.key();if(store.definitions.putIfAbsent(key,definition)!=null)throw new IllegalStateException("Duplicate source configuration path "+key);initial.put(key,definition.validate(inherited));}
 }
}
