package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.foundation.block.BigOutlines;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class BigOutlinePickMixin {
    @Inject(method="pick(F)V",at=@At("TAIL"))
    private void create$pickExtendedOutline(float partial,CallbackInfo ci){BigOutlines.pick((Minecraft)(Object)this,partial);com.simibubi.create.content.trains.track.TrackBlockOutline.pickCurves((Minecraft)(Object)this,partial);}
}
