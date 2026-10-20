package com.simibubi.create.test.mixin;

import com.simibubi.create.test.CarriageNativeChunkTestFixtures;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentEntitySectionManager.class)
public abstract class CarriageNativeUnloadObservationMixin implements CarriageNativeChunkTestFixtures.ManagerView {
    @Override @Accessor("chunksToUnload") public abstract LongSet create$actualQueuedChunks();
    @Override @Accessor("chunkLoadStatuses") public abstract Long2ObjectMap<?> create$actualChunkLoadStatuses();
    @Inject(method="processChunkUnload",at=@At("RETURN"))
    private void create$observeActualNativeProcessResult(long chunk,CallbackInfoReturnable<Boolean> callback){CarriageNativeChunkTestFixtures.processed(this,chunk,callback.getReturnValueZ());}
}
