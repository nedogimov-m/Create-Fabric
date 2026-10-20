package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual native chunk ownership, ticker lookup and packet components remain authoritative. */
@Mixin(LevelChunk.class)
abstract class SmartBlockEntityChunkMixin {
    @WrapOperation(method = "updateBlockEntityTicker", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;getTicker(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/BlockEntityType;)Lnet/minecraft/world/level/block/entity/BlockEntityTicker;"))
    private <T extends BlockEntity> BlockEntityTicker<T> create$smartTicker(BlockState state,
            Level level, BlockEntityType<T> type, Operation<BlockEntityTicker<T>> original,
            BlockEntity actualOwner) {
        var nativeTicker = original.call(state, level, type);
        if (!(actualOwner instanceof SmartBlockEntity smart))
            return nativeTicker;
        return (world, pos, currentState, entity) -> {
            if (entity != actualOwner)
                return;
            SmartBlockEntity.tickNative(world, pos, currentState, smart, () -> {
                if (nativeTicker == null)
                    smart.tick();
                else
                    nativeTicker.tick(world, pos, currentState, entity);
            });
        };
    }

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void create$markActualChunkUnload(CallbackInfo ci) {
        for (var owner : ((LevelChunk) (Object) this).getBlockEntities().values())
            if (owner instanceof SmartBlockEntity smart)
                smart.onChunkUnloaded();
    }

    @WrapOperation(method = "lambda$replaceWithPacketData$0", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/world/level/storage/ValueInput;)V"))
    private void create$readNativeChunkPacket(BlockEntity owner, ValueInput input,
            Operation<Void> original) {
        if (owner instanceof SmartBlockEntity smart)
            smart.withClientChunkRead((LevelChunk) (Object) this, input, () -> original.call(owner, input));
        else if (owner instanceof SyncedBlockEntity synced)
            synced.withClientRead(() -> original.call(owner, input));
        else
            original.call(owner, input);
    }
}
