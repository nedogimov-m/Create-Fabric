package com.simibubi.create.api.contraption.train;

import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.trains.track.AllPortalTracks;
import com.simibubi.create.content.trains.track.TrackPortalAccess;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Genuine source portal-track registry. Providers must discover actual exits without loading terrain or changing ownership. */
@FunctionalInterface
public interface PortalTrackProvider {
    SimpleRegistry<Block,PortalTrackProvider> REGISTRY=SimpleRegistry.create();
    @Nullable Exit findExit(ServerLevel level,BlockFace face);
    static boolean isSupportedPortal(BlockState state){return REGISTRY.get(state)!=null;}
    @Nullable static Exit getOtherSide(ServerLevel level,BlockFace inboundTrack){
        var state=TrackPortalAccess.state(level,inboundTrack.getConnectedPos());var provider=REGISTRY.get(state);
        return provider==null?null:provider.findExit(level,inboundTrack);
    }
    @Nullable static Exit fromPortal(ServerLevel level,BlockFace face,ResourceKey<Level> first,ResourceKey<Level> second,Portal portal){return AllPortalTracks.fromPortal(level,face,first,second,portal);}
    record Exit(ServerLevel level,BlockFace face){}
}
