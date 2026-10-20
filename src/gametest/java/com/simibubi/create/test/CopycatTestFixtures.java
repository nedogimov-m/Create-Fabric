package com.simibubi.create.test;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.redstone.Orientation;

/** Actual registered neighbor callbacks and Fabric protections, never manual Copycat completion/publication. */
public final class CopycatTestFixtures implements ModInitializer {
    public static Block UPDATE_ERROR;
    public static final class NativeRefusal extends Error {public NativeRefusal(String message){super(message);}}
    private record Target(Level level,BlockPos pos){}
    private static final Map<Target,NativeRefusal> ARMED=new HashMap<>();
    private static final Set<Target> DENY_USE=new HashSet<>(),DENY_BREAK=new HashSet<>();
    public static void arm(Level level,BlockPos pos,NativeRefusal failure){ARMED.put(new Target(level,pos.immutable()),failure);}
    public static void denyUse(Level level,BlockPos pos){DENY_USE.add(new Target(level,pos.immutable()));}
    public static void denyBreak(Level level,BlockPos pos){DENY_BREAK.add(new Target(level,pos.immutable()));}
    public static void clear(){ARMED.clear();DENY_USE.clear();DENY_BREAK.clear();}
    @Override public void onInitialize(){
        var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test","copycat_native_update_error"));
        UPDATE_ERROR=Registry.register(BuiltInRegistries.BLOCK,key,new Block(BlockBehaviour.Properties.of().setId(key).strength(1)){
            @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,Orientation orientation,boolean moving){var failure=ARMED.remove(new Target(level,pos));if(failure!=null)throw failure;super.neighborChanged(state,level,pos,source,orientation,moving);}
        });
        UseBlockCallback.EVENT.register((player,level,hand,hit)->DENY_USE.contains(new Target(level,hit.getBlockPos()))?InteractionResult.FAIL:InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,entity)->!DENY_BREAK.contains(new Target(level,pos)));
    }
}
