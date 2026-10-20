package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class PotatoCannonAmmoOverlayMixin {
    @Inject(method="itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",at=@At("TAIL"))
    private void create$actualAmmoIcon(Font font,ItemStack stack,int x,int y,String count,CallbackInfo ci){var player=Minecraft.getInstance().player;if(player==null||!(stack.getItem() instanceof PotatoCannonItem))return;var ammo=PotatoCannonItem.getAmmo(player,stack);if(ammo==null||ammo.stack().getItem() instanceof PotatoCannonItem)return;var graphics=(GuiGraphicsExtractor)(Object)this;graphics.pose().pushMatrix();graphics.pose().translate(x,y+8);graphics.pose().scale(.5f,.5f);graphics.item(ammo.stack(),0,0);graphics.pose().popMatrix();}
}
