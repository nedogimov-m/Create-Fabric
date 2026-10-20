package com.simibubi.create.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.ChunkEntities;

/** Test-only delay of the actual persisted payload; no replacement entities or load-status edits. */
public final class NativeEntityLoadGate {
    private static volatile Held held;
    public static Held arm(ServerLevel level,ChunkPos chunk) {
        if(held!=null)throw new IllegalStateException("An entity load is already held");
        return held=new Held(level,chunk.pack());
    }
    public static CompletableFuture<ChunkEntities<Entity>> wrap(ServerLevel level,ChunkPos chunk,CompletableFuture<ChunkEntities<Entity>> actual) {
        var gate=held;
        if(gate==null||gate.level!=level||gate.chunk!=chunk.pack())return actual;
        gate.loads.incrementAndGet();gate.actual=actual;
        return actual.thenCombine(gate.release,(payload,ignored)->payload);
    }
    public static final class Held implements AutoCloseable {
        private final ServerLevel level;
        private final long chunk;
        private final CompletableFuture<Void> release=new CompletableFuture<>();
        private final AtomicInteger loads=new AtomicInteger();
        private volatile CompletableFuture<ChunkEntities<Entity>> actual;
        private Held(ServerLevel level,long chunk){this.level=level;this.chunk=chunk;}
        public boolean holdsCompletedPayload(){var future=actual;return loads.get()>0&&future!=null&&future.isDone()&&!future.isCompletedExceptionally()&&!release.isDone();}
        @Override public void close(){release.complete(null);if(held==this)held=null;}
    }
    private NativeEntityLoadGate(){}
}
