package com.simibubi.create.test.mixin;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.test.CarriageNativeChunkTestFixtures;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only a fixture-owned Train waits; native chunk tickets, storage and unload keep running normally. */
@Mixin(value=Train.class,remap=false)
public abstract class NativeTrainTickLeaseMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void create$waitForFixtureNativeUnload(Level level,CallbackInfo callback){
        if(CarriageNativeChunkTestFixtures.trainTickDeferred((Train)(Object)this))callback.cancel();
    }
}
