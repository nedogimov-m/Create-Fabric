package com.simibubi.create.test;

import com.simibubi.create.content.kinetics.deployer.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;

/** Per-position hooks exercise the same Fabric callbacks used by protection/addon mods. */
public final class DeployerTestFixtures implements ModInitializer {
    static final Set<BlockPos> DENY_USE=ConcurrentHashMap.newKeySet(),DENY_BREAK=ConcurrentHashMap.newKeySet();
    static final Map<BlockPos,Consumer<DeployerRecipeSearchEvent>> SEARCH=new ConcurrentHashMap<>();
    @Override public void onInitialize(){
        UseBlockCallback.EVENT.register((player,level,hand,hit)->player instanceof DeployerFakePlayer&&DENY_USE.contains(hit.getBlockPos())?InteractionResult.FAIL:InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,entity)->!(player instanceof DeployerFakePlayer)||!DENY_BREAK.contains(pos));
        DeployerRecipeSearchEvent.EVENT.register(search->{var callback=SEARCH.get(search.getBlockEntity().getBlockPos());if(callback!=null)callback.accept(search);});
    }
}
