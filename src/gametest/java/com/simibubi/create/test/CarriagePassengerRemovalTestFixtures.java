package com.simibubi.create.test;

import java.util.*;
import java.util.function.*;
import net.minecraft.world.entity.Entity;

/** Observes actual native removal boundaries; never supplies success or mutates ownership metadata. */
public final class CarriagePassengerRemovalTestFixtures {
    private record Hook(Predicate<Entity> matches,Consumer<Entity> callback){}
    private static final ThreadLocal<ArrayDeque<Hook>> BEFORE=new ThreadLocal<>(),AFTER=new ThreadLocal<>(),UNLINK=new ThreadLocal<>(),SAVE=new ThreadLocal<>();
    private static AutoCloseable arm(ThreadLocal<ArrayDeque<Hook>> target,Predicate<Entity> matches,Consumer<Entity> callback){var hooks=target.get();if(hooks==null){hooks=new ArrayDeque<>();target.set(hooks);}var owned=hooks;var hook=new Hook(matches,callback);owned.addFirst(hook);return ()->{owned.removeIf(actual->actual==hook);if(owned.isEmpty()&&target.get()==owned)target.remove();};}
    public static AutoCloseable beforeReason(Predicate<Entity> matches,Consumer<Entity> callback){return arm(BEFORE,matches,callback);}
    public static AutoCloseable beforeWorldCallback(Predicate<Entity> matches,Consumer<Entity> callback){return arm(AFTER,matches,callback);}
    public static AutoCloseable afterUnlink(Predicate<Entity> matches,Consumer<Entity> callback){return arm(UNLINK,matches,callback);}
    public static AutoCloseable beforeNativeSave(Predicate<Entity> matches,Consumer<Entity> callback){return arm(SAVE,matches,callback);}
    private static void fire(ThreadLocal<ArrayDeque<Hook>> target,Entity actual){var hooks=target.get();if(hooks==null)return;try{for(var hook:List.copyOf(hooks))if(hooks.stream().anyMatch(current->current==hook)&&hook.matches().test(actual)){hooks.removeIf(current->current==hook);hook.callback().accept(actual);}}finally{if(hooks.isEmpty()&&target.get()==hooks)target.remove();}}
    public static void before(Entity actual){fire(BEFORE,actual);}
    public static void after(Entity actual){fire(AFTER,actual);}
    public static void unlinked(Entity actual){fire(UNLINK,actual);}
    public static void saving(Entity actual){fire(SAVE,actual);}
    private CarriagePassengerRemovalTestFixtures(){}
}
