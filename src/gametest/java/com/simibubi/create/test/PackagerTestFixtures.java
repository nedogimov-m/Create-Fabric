package com.simibubi.create.test;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** An addon-style block handler whose behavior is isolated to each test position. */
public final class PackagerTestFixtures implements ModInitializer {
    public static final Map<BlockPos,UnpackingHandler> HANDLERS=new ConcurrentHashMap<>();
    @Override public void onInitialize(){UnpackingHandler.REGISTRY.register(Blocks.IRON_BLOCK,(level,pos,state,side,items,order,tx)->{var handler=HANDLERS.get(pos);return handler!=null&&handler.unpack(level,pos,state,side,items,order,tx);});}
}
