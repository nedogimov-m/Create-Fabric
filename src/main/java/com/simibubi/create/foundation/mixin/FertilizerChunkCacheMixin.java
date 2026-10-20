package com.simibubi.create.foundation.mixin;
import com.simibubi.create.content.equipment.TreeFertilizerSimulation;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fails before the ORIGINAL cache lookup; never fabricates a chunk, holder, ticket or loaded status. */
@Mixin(value=ServerChunkCache.class,priority=2000)
abstract class FertilizerChunkCacheMixin {
 @Inject(method={"getChunk","getChunkNow","getChunkFuture","getChunkForLighting"},at=@At("HEAD"),require=4)private void create$noDreamOriginalChunk(CallbackInfoReturnable<?> ci){if(TreeFertilizerSimulation.ownsCache(this))throw new UnsupportedOperationException("Native fertilizer feature attempted an unreviewed original-world chunk lookup");}
}
