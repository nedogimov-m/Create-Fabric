package com.simibubi.create.test;

import java.util.function.*;
import net.minecraft.world.entity.Entity;

/** Observes the genuine native callsite after section admission and before LevelCallback.onCreated. */
public final class CartAcceptanceTestFixtures {
    private record Hook(Predicate<Entity> matches,Consumer<Entity> callback){}
    private static final ThreadLocal<Hook> ACTIVE=new ThreadLocal<>();
    private static final ThreadLocal<Hook> ATTEMPT=new ThreadLocal<>();
    public static AutoCloseable beforeCreated(Predicate<Entity> matches,Consumer<Entity> callback){var old=ACTIVE.get();var next=new Hook(matches,callback);ACTIVE.set(next);return ()->{if(old==null)ACTIVE.remove();else ACTIVE.set(old);};}
    public static AutoCloseable beforeNativeUuidCheck(Predicate<Entity> matches,Consumer<Entity> callback){var old=ATTEMPT.get();ATTEMPT.set(new Hook(matches,callback));return ()->{if(old==null)ATTEMPT.remove();else ATTEMPT.set(old);};}
    public static void atNativeUuidCheck(Object candidate){if(!(candidate instanceof Entity actual))return;var hook=ATTEMPT.get();if(hook==null||!hook.matches.test(actual))return;ATTEMPT.remove();hook.callback.accept(actual);}
    public static void atNativeCallsite(Object candidate){if(!(candidate instanceof Entity actual))return;var hook=ACTIVE.get();if(hook==null||!hook.matches.test(actual))return;ACTIVE.remove();hook.callback.accept(actual);}
    private CartAcceptanceTestFixtures(){}
}
