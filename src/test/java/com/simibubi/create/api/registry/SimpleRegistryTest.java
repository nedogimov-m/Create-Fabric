package com.simibubi.create.api.registry;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class SimpleRegistryTest {
    @Test void keysUseIdentityAndDirectRegistrationsTakePriority(){
        var registry=SimpleRegistry.<Object,String>create();var a=new String("equal");var b=new String("equal");
        registry.registerProvider(key->"provider");registry.register(a,"direct");
        assertEquals("direct",registry.get(a));assertEquals("provider",registry.get(b));
        assertThrows(IllegalArgumentException.class,()->registry.register(a,"duplicate"));
    }
    @Test void newerProvidersInvalidateCachedHitsAndMisses(){
        var registry=SimpleRegistry.<Object,String>create();var key=new Object();assertNull(registry.get(key));
        registry.registerProvider(ignored->"first");assertEquals("first",registry.get(key));
        registry.registerProvider(ignored->"second");assertEquals("second",registry.get(key));
        registry.register(key,"explicit");assertEquals("explicit",registry.get(key));
    }
    @Test void providersCacheUntilTheirCallbackInvalidates(){
        var registry=SimpleRegistry.<Object,String>create();var key=new Object();var value=new AtomicReference<>("old");var callback=new AtomicReference<Runnable>();var calls=new AtomicInteger();
        registry.registerProvider(new SimpleRegistry.Provider<>(){
            @Override public String get(Object ignored){calls.incrementAndGet();return value.get();}
            @Override public void onRegister(Runnable invalidate){callback.set(invalidate);}
        });
        assertEquals("old",registry.get(key));value.set("new");assertEquals("old",registry.get(key));assertEquals(1,calls.get());
        callback.get().run();assertEquals("new",registry.get(key));assertEquals(2,calls.get());
    }
    @Test void multiIncludesDirectThenNewestProviderAndUpdatesAfterRegistration(){
        var registry=SimpleRegistry.Multi.<Object,String>create();var key=new Object();assertEquals(List.of(),registry.get(key));
        registry.addProvider(ignored->"old");registry.addProvider(ignored->"new");registry.add(key,"one");
        assertEquals(List.of("one","new","old"),registry.get(key));registry.add(key,"two");
        assertEquals(List.of("one","two","new","old"),registry.get(key));
        assertThrows(UnsupportedOperationException.class,()->registry.get(key).add("external"));
    }
}
