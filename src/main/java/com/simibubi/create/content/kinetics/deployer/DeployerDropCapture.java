package com.simibubi.create.content.kinetics.deployer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Captures synchronous drops produced by the acting hand, without changing unrelated drops. */
public final class DeployerDropCapture {
    private record Capture(ServerLevel level,BlockPos pos,List<ItemStack> items){}
    private static final ThreadLocal<Capture> ACTIVE=new ThreadLocal<>();
    public static List<ItemStack> run(ServerLevel level,BlockPos pos,Runnable action){var old=ACTIVE.get();var capture=new Capture(level,pos,new ArrayList<>());ACTIVE.set(capture);try{action.run();}finally{if(old==null)ACTIVE.remove();else ACTIVE.set(old);}return capture.items;}
    public static boolean capture(ServerLevel level,Entity entity){var active=ACTIVE.get();if(active==null||active.level!=level||!(entity instanceof ItemEntity item)||entity.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(active.pos))>16)return false;active.items.add(item.getItem().copy());item.discard();return true;}
    private DeployerDropCapture(){}
}
