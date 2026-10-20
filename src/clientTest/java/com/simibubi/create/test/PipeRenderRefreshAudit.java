package com.simibubi.create.test;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Observes native chunk rebuild requests only after the real pipe attachments have arrived. */
public final class PipeRenderRefreshAudit {
    private static final Set<BlockPos> REFRESHED = new HashSet<>();
    public static void clear() { REFRESHED.clear(); }
    public static boolean refreshed(BlockPos pos) { return REFRESHED.contains(pos); }
    public static void updated(ClientLevel level, BlockPos pos) {
        if (level.getBlockEntityRenderData(pos) instanceof FluidTransportBehaviour.PipeRenderData data
            && data.attachments().stream().anyMatch(attachment -> attachment != FluidTransportBehaviour.AttachmentTypes.NONE))
            REFRESHED.add(pos.immutable());
    }
}
