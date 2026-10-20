package com.simibubi.create.test;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Test-only fault on a real completed native Level.setBlock. Never a fake level/chunk/ready owner. */
public final class PortalFrameFaultFixtures {
    private static final ThreadLocal<Hook> ACTIVE=new ThreadLocal<>();
    private static final class Hook {
        final ServerLevel level;final Set<BlockPos> positions;final Runnable action;int skip;
        Hook(ServerLevel level,Set<BlockPos> positions,int skip,Runnable action){this.level=level;this.positions=Set.copyOf(positions);this.skip=skip;this.action=action;}
    }
    public static AutoCloseable afterWrite(ServerLevel level,Set<BlockPos> positions,int skip,Runnable action){
        if(!level.getServer().isSameThread()||ACTIVE.get()!=null||skip<0)throw new IllegalStateException("Native frame fault requires isolated actual test server thread");
        var hook=new Hook(level,positions,skip,action);ACTIVE.set(hook);return ()->{if(ACTIVE.get()==hook)ACTIVE.remove();};
    }
    public static void afterNativeWrite(Level level,BlockPos pos){
        var hook=ACTIVE.get();if(hook==null||hook.level!=level||!hook.positions.contains(pos))return;if(hook.skip-->0)return;
        ACTIVE.remove();hook.action.run();
    }
    private PortalFrameFaultFixtures(){}
}
