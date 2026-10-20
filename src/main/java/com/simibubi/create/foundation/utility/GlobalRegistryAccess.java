package com.simibubi.create.foundation.utility;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** Actual connected-client/current-server lookup with an explicit query scope for entity-bound equipment. */
public final class GlobalRegistryAccess {
    private static volatile MinecraftServer server;
    private static final ThreadLocal<RegistryAccess> QUERY=new ThreadLocal<>();
    public static Supplier<RegistryAccess> CLIENT_REGISTRIES=()->null;
    @Nullable public static RegistryAccess get(){var scope=QUERY.get();if(scope!=null)return scope;var client=CLIENT_REGISTRIES.get();if(client!=null)return client;var current=server;return current==null?null:current.registryAccess();}
    public static RegistryAccess getOrThrow(){var access=get();if(access==null)throw new IllegalStateException("Could not get actual RegistryAccess");return access;}
    public static <T>T with(RegistryAccess access,Supplier<T> query){var previous=QUERY.get();QUERY.set(access);try{return query.get();}finally{if(previous==null)QUERY.remove();else QUERY.set(previous);}}
    private static boolean registered;
    public static void register(){if(registered)return;registered=true;ServerLifecycleEvents.SERVER_STARTING.register(current->server=current);ServerLifecycleEvents.SERVER_STOPPED.register(current->{if(server==current)server=null;});}
    private GlobalRegistryAccess(){}
}
