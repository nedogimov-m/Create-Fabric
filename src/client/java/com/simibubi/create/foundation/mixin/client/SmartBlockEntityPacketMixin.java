package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Source clientPacket flag around vanilla's real component and custom data application. */
@Mixin(ClientPacketListener.class)
abstract class SmartBlockEntityPacketMixin {
    @WrapOperation(method = "lambda$handleBlockEntityData$0", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/world/level/storage/ValueInput;)V"))
    private void create$readNativeBlockEntityPacket(BlockEntity owner, ValueInput input,
            Operation<Void> original) {
        if (owner instanceof SyncedBlockEntity synced)
            synced.withClientRead(() -> original.call(owner, input));
        else
            original.call(owner, input);
    }
}
