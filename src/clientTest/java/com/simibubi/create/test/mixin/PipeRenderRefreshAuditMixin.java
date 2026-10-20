package com.simibubi.create.test.mixin;

import com.simibubi.create.test.PipeRenderRefreshAudit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class PipeRenderRefreshAuditMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void create$recordPipeRefresh(BlockPos pos, BlockState previous, BlockState state, int flags, CallbackInfo callback) {
        PipeRenderRefreshAudit.updated((ClientLevel) (Object) this, pos);
    }
}
