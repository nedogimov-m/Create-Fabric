package com.simibubi.create.foundation.mixin.client;
import com.simibubi.create.content.equipment.zapper.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Minecraft.class)
abstract class ZapperSelectionMixin {
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true) private void create$sourceLongRangeSelection(CallbackInfoReturnable<Boolean> cir){var client=(Minecraft)(Object)this;if(client.player!=null&&client.gui.screen()==null&&!client.player.isShiftKeyDown()&&client.player.getMainHandItem().getItem() instanceof ZapperItem){ClientPlayNetworking.send(ZapperSelectPacket.INSTANCE);cir.setReturnValue(false);}}
}
