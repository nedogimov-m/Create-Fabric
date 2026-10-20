package com.simibubi.create.test;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.*;
import java.util.function.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.ChunkEntities;

/** Observes genuine native storage/unload; every accessor is read-only. */
public final class CarriageNativeChunkTestFixtures {
    public interface ManagerView {LongSet create$actualQueuedChunks();Long2ObjectMap<?> create$actualChunkLoadStatuses();}
    public record Storage(ServerLevel world,ChunkEntities<Entity> entries){}
    public record Process(Object manager,long chunk,boolean completed){}
    private record Hook<T>(Predicate<T> matches,Consumer<T> callback){}
    private static final ThreadLocal<ArrayDeque<Hook<Storage>>> STORAGE=new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<Hook<Process>>> PROCESS=new ThreadLocal<>();
    // Each registration owns only its actual hook; overlapping native tests cannot mask or revive it.
    private static <T> AutoCloseable arm(ThreadLocal<ArrayDeque<Hook<T>>> target,Predicate<T> matches,Consumer<T> callback){var hooks=target.get();if(hooks==null){hooks=new ArrayDeque<>();target.set(hooks);}var owned=hooks;var hook=new Hook<>(matches,callback);owned.addFirst(hook);return ()->{owned.removeIf(actual->actual==hook);if(owned.isEmpty()&&target.get()==owned)target.remove();};}
    private static <T> void fire(ThreadLocal<ArrayDeque<Hook<T>>> target,T event){var hooks=target.get();if(hooks==null)return;try{for(var hook:List.copyOf(hooks))if(hooks.stream().anyMatch(actual->actual==hook)&&hook.matches().test(event)){hooks.removeIf(actual->actual==hook);hook.callback().accept(event);}}finally{if(hooks.isEmpty()&&target.get()==hooks)target.remove();}}
    public static AutoCloseable storage(Predicate<Storage> matches,Consumer<Storage> callback){return arm(STORAGE,matches,callback);}
    public static AutoCloseable process(Predicate<Process> matches,Consumer<Process> callback){return arm(PROCESS,matches,callback);}
    public static void storing(ServerLevel world,ChunkEntities<Entity> entries){fire(STORAGE,new Storage(world,entries));}
    public static void processed(Object manager,long chunk,boolean completed){fire(PROCESS,new Process(manager,chunk,completed));}
    private CarriageNativeChunkTestFixtures(){}
}
