package com.simibubi.create.content.trains.track;

import com.simibubi.create.Create;
import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import org.jspecify.annotations.Nullable;

/** Actual source provider entrypoints, using the native vanilla Nether adapter and genuine optional addon registration. */
public final class AllPortalTracks {
    private static boolean registered;
    static final PortalTrackProvider VANILLA_NETHER=(level,face)->fromPortal(level,face,Level.OVERWORLD,Level.NETHER,(Portal)Blocks.NETHER_PORTAL);
    public static void tryRegisterIntegration(Identifier id,PortalTrackProvider provider){
        var block=BuiltInRegistries.BLOCK.getOptional(id);
        if(block.isPresent())PortalTrackProvider.REGISTRY.register(block.get(),provider);
        else Create.LOGGER.warn("Portal for integration wasn't found: {}. Compat outdated?",id);
    }
    public static void registerDefaults(){
        if(registered)return;registered=true;
        PortalTrackProvider.REGISTRY.register(Blocks.NETHER_PORTAL,VANILLA_NETHER);
    }
    public static PortalTrackProvider.@Nullable Exit fromPortal(ServerLevel level,BlockFace face,ResourceKey<Level> first,ResourceKey<Level> second,Portal portal){
        if(portal==Blocks.NETHER_PORTAL&&first.equals(Level.OVERWORLD)&&second.equals(Level.NETHER))return NetherPortalTrackProvider.findExit(level,face);
        // Other native/mod portal implementations can mutate terrain while calculating destinations.
        // Their real loaded-only provider must be registered through the genuine source registry.
        throw new UnsupportedOperationException("This native portal requires its actual loaded-only PortalTrackProvider");
    }
    private AllPortalTracks(){}
}
