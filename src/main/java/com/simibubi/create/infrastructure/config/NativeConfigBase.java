package com.simibubi.create.infrastructure.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.*;
import java.util.function.Supplier;

/** Source-facing typed values backed by real native scope files and immutable snapshots. */
public abstract class NativeConfigBase {
 protected int depth;
 protected final List<Registration> registrations=new ArrayList<>();
 protected final List<NativeConfigBase> children=new ArrayList<>();
 private NativeConfigStore store;
 public abstract String getName();
 protected ConfigScope defaultScope(){return ConfigScope.SERVER;}
 public void onLoad(){children.forEach(NativeConfigBase::onLoad);}
 public void onReload(){children.forEach(NativeConfigBase::onReload);}
 protected interface Registration{void register(NativeConfigStore.Builder builder);}
 public void registerAll(NativeConfigStore.Builder builder){registrations.forEach(entry->entry.register(builder));}
 final void bindStore(NativeConfigStore actual){store=actual;}
 public final NativeConfigStore store(){if(store==null)NativeConfigStore.detached(this);return store;}
 public final List<CValue<?>> directValues(){var result=new ArrayList<CValue<?>>();for(var entry:registrations)if(entry instanceof NativeConfigBase.CValue<?> value)result.add(value);return List.copyOf(result);}
 protected ConfigBool b(boolean value,String name,String... comment){return new ConfigBool(name,value,comment);}
 protected ConfigInt i(int value,int min,int max,String name,String... comment){return new ConfigInt(name,value,min,max,comment);}
 protected ConfigInt i(int value,int min,String name,String... comment){return i(value,min,Integer.MAX_VALUE,name,comment);}
 protected ConfigInt i(int value,String name,String... comment){return i(value,Integer.MIN_VALUE,Integer.MAX_VALUE,name,comment);}
 protected ConfigFloat f(float value,float min,float max,String name,String... comment){return new ConfigFloat(name,value,min,max,comment);}
 protected ConfigFloat f(float value,float min,String name,String... comment){return f(value,min,Float.MAX_VALUE,name,comment);}
 protected <T extends Enum<T>> ConfigEnum<T> e(T value,String name,String... comment){return new ConfigEnum<>(name,value,comment);}
 protected ConfigGroup group(int groupDepth,String name,String... comment){return new ConfigGroup(name,groupDepth,comment);}
 protected <T extends NativeConfigBase>T nested(int groupDepth,Supplier<T> factory,String... comment){
  var child=factory.get();group(groupDepth,child.getName(),comment);registrations.add(builder->{child.depth=groupDepth;child.bindStore(builder.store());child.registerAll(builder);if(child.depth>groupDepth)builder.pop(child.depth-groupDepth);});children.add(child);return child;
 }
 public abstract class CValue<V> implements Registration {
  private final String name;private final V defaultValue;private final List<String> comments;
  private NativeConfigStore boundStore;private List<String> path;
  protected CValue(String name,V value,String... comments){this.name=Objects.requireNonNull(name);defaultValue=Objects.requireNonNull(value);this.comments=List.of(comments);registrations.add(this);}
  public String getName(){return name;}
  public V defaultValue(){return defaultValue;}
  public List<String> comments(){return comments;}
  public List<String> path(){ensureBound();return path;}
  public String key(){return String.join(".",path());}
  public ConfigScope scope(){ensureBound();return boundStore.scope();}
  public V get(){ensureBound();return boundStore.read(this,false);}
  public V getServer(){ensureBound();return boundStore.read(this,true);}
  public void set(V value){ensureBound();boundStore.set(this,value);}
  public final NativeConfigStore backingStore(){ensureBound();return boundStore;}
  private void ensureBound(){if(boundStore==null)NativeConfigBase.this.store();}
  @Override public void register(NativeConfigStore.Builder builder){
   V inherited=boundStore==null?defaultValue:boundStore.read(this,true);path=builder.path(name);boundStore=builder.store();builder.add(this,inherited);
  }
  public abstract V decode(JsonElement value);
  public abstract JsonElement encode(V value);
  public final V validate(Object value){return decode(toJson(value));}
  private JsonElement toJson(Object value){if(value instanceof Enum<?> choice)return new JsonPrimitive(choice.name());if(value instanceof Number number)return new JsonPrimitive(number);if(value instanceof Boolean bool)return new JsonPrimitive(bool);if(value instanceof String text)return new JsonPrimitive(text);throw new IllegalArgumentException("Invalid "+key()+" configuration value "+value);}
  public Optional<Number> minimum(){return Optional.empty();}
  public Optional<Number> maximum(){return Optional.empty();}
 }
 public final class ConfigGroup implements Registration {
  private final String name;private final int groupDepth;private final List<String> comments;
  private ConfigGroup(String name,int groupDepth,String... comments){this.name=name;this.groupDepth=groupDepth;this.comments=List.of(comments);registrations.add(this);}
  public String getName(){return name;}
  public List<String> comments(){return comments;}
  @Override public void register(NativeConfigStore.Builder builder){if(depth>groupDepth)builder.pop(depth-groupDepth);depth=groupDepth;builder.push(name);depth++;}
 }
 public final class ConfigBool extends CValue<Boolean> {
  private ConfigBool(String name,boolean value,String... comment){super(name,value,comment);}
  @Override public Boolean decode(JsonElement value){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean for "+getName());return value.getAsBoolean();}
  @Override public JsonElement encode(Boolean value){return new JsonPrimitive(value);}
 }
 public final class ConfigInt extends CValue<Integer> {
  private final int min,max;
  private ConfigInt(String name,int value,int min,int max,String... comment){super(name,value,comment);this.min=min;this.max=max;}
  @Override public Integer decode(JsonElement value){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected integer for "+getName());try{int result=value.getAsBigDecimal().intValueExact();if(result<min||result>max)throw new IllegalArgumentException("Outside source bounds for "+getName());return result;}catch(ArithmeticException failure){throw new IllegalArgumentException("Expected source-bounded integer for "+getName(),failure);}}
  @Override public JsonElement encode(Integer value){return new JsonPrimitive(value);}
  @Override public Optional<Number> minimum(){return Optional.of(min);}
  @Override public Optional<Number> maximum(){return Optional.of(max);}
 }
 public final class ConfigFloat extends CValue<Double> {
  private final float min,max;
  private ConfigFloat(String name,float value,float min,float max,String... comment){super(name,(double)value,comment);this.min=min;this.max=max;}
  @Override public Double decode(JsonElement value){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected floating value for "+getName());double result=value.getAsDouble();if(!Double.isFinite(result)||result<min||result>max)throw new IllegalArgumentException("Outside source bounds for "+getName());return result;}
  @Override public JsonElement encode(Double value){return new JsonPrimitive(value);}
  @Override public Optional<Number> minimum(){return Optional.of(min);}
  @Override public Optional<Number> maximum(){return Optional.of(max);}
  public float getF(){return get().floatValue();}
 }
 public final class ConfigEnum<T extends Enum<T>> extends CValue<T> {
  private final Class<T> enumClass;
  private ConfigEnum(String name,T value,String... comment){super(name,value,comment);enumClass=value.getDeclaringClass();}
  @Override public T decode(JsonElement value){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected enum name for "+getName());try{return Enum.valueOf(enumClass,value.getAsString().toUpperCase(Locale.ROOT));}catch(IllegalArgumentException failure){throw new IllegalArgumentException("Unknown source enum value for "+getName(),failure);}}
  @Override public JsonElement encode(T value){return new JsonPrimitive(value.name());}
  public List<T> choices(){return List.of(enumClass.getEnumConstants());}
 }
 /** Exact source stress tables use unrestricted doubles, separate from bounded ConfigFloat. */
 protected final class ConfigDouble extends CValue<Double> {
  protected ConfigDouble(String name,double value,String... comment){super(name,value,comment);}
  @Override public Double decode(JsonElement value){if(!value.isJsonPrimitive())throw new IllegalArgumentException("Expected stress number for "+getName());if(value.getAsJsonPrimitive().isNumber())return value.getAsDouble();return switch(value.getAsString()){case "NaN"->Double.NaN;case "Infinity"->Double.POSITIVE_INFINITY;case "-Infinity"->Double.NEGATIVE_INFINITY;default->throw new IllegalArgumentException("Expected stress number for "+getName());};}
  @Override public JsonElement encode(Double value){return Double.isFinite(value)?new JsonPrimitive(value):new JsonPrimitive(value.toString());}
 }
}
