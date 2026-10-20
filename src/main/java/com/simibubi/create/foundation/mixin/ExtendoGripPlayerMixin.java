package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class ExtendoGripPlayerMixin {
    @Inject(method="tick",at=@At("HEAD")) private void create$actualHeldRange(CallbackInfo ci){ExtendoGripItem.holdingExtendoGripIncreasesRange((Player)(Object)this);}
}
