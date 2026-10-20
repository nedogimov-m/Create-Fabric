package com.simibubi.create.test.mixin;

import com.simibubi.create.test.StationMapRenderAudit;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ClientPacketListener.class,remap=false)
public abstract class StationMapPacketAuditMixin {
    @Inject(method="handleMapItemData",at=@At("RETURN"),require=1)
    private void create$actualNativePacketApplied(ClientboundMapItemDataPacket packet,CallbackInfo callback){StationMapRenderAudit.packetApplied(packet);}
}
