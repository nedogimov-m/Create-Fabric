package com.simibubi.create.test;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;

/** Server-thread requests for a real native preparation tick window, never a readiness override. */
public final class NativeGameTestPreparationWait {
    private static final Set<GameTestServer> WAITING = Collections.newSetFromMap(new WeakHashMap<>());

    public static void request(ServerLevel level) {
        if (level.getServer() instanceof GameTestServer server) WAITING.add(server);
    }

    public static boolean consume(GameTestServer server) {
        return WAITING.remove(server);
    }

    private NativeGameTestPreparationWait() {}
}
