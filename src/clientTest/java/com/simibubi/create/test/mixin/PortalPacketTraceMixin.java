package com.simibubi.create.test.mixin;

import com.simibubi.create.Create;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only actual packet ordering diagnostics; never changes packets, prediction or native state. */
@Mixin(ClientPacketListener.class)
abstract class PortalPacketTraceMixin {
    @Unique private static final BlockPos createTest$source=new BlockPos(39007,10,8);
    @Unique private static void createTest$trace(String phase,Object packet){
        var client=Minecraft.getInstance();var level=client.level;
        if(level==null||!level.dimension().equals(Level.OVERWORLD)||client.player==null||client.player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(createTest$source))>4096)return;
        var be=level.getBlockEntity(createTest$source);
        Create.LOGGER.info("Native portal packet order: phase={}, packet={}, state={}, owner={}, data={}",phase,packet,level.getBlockState(createTest$source),be,be==null?null:be.getUpdateTag(level.registryAccess()));
    }
    @Inject(method="handleBlockEntityData",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntityType;)Ljava/util/Optional;"))
    private void createTest$beforeNativeBlockEntityLookup(ClientboundBlockEntityDataPacket packet,CallbackInfo ci){if(packet.getPos().equals(createTest$source))createTest$trace("before-BE-packet-lookup",packet.getTag());}
    @Inject(method="handleBlockChangedAck",at=@At("RETURN"))
    private void createTest$afterNativePredictionAck(ClientboundBlockChangedAckPacket packet,CallbackInfo ci){createTest$trace("after-prediction-ack",packet.sequence());}
    @Inject(method="handleBlockUpdate",at=@At("RETURN"))
    private void createTest$afterNativeBlockUpdate(ClientboundBlockUpdatePacket packet,CallbackInfo ci){if(packet.getPos().equals(createTest$source))createTest$trace("after-block-update",packet.getBlockState());}
}
