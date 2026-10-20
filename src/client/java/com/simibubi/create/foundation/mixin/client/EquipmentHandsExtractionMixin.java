package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.equipment.*;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripRenderHandler;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import net.minecraft.client.player.*;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class EquipmentHandsExtractionMixin {
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;
    @Unique private int create$lastSlot=-1;
    @Unique private boolean create$sameSlot;
    @Inject(method="tick",at=@At("HEAD")) private void create$actualSelectedSlot(LocalPlayer player,CallbackInfo ci){create$sameSlot=create$lastSlot==player.getInventory().getSelectedSlot();}
    @Inject(method="tick",at=@At("TAIL")) private void create$rememberSelectedSlot(LocalPlayer player,CallbackInfo ci){create$lastSlot=player.getInventory().getSelectedSlot();}
    @Inject(method="shouldInstantlyReplaceVisibleItem",at=@At("HEAD"),cancellable=true)
    private void create$sourceCannonReequip(ItemStack oldStack,ItemStack newStack,LocalPlayer player,CallbackInfoReturnable<Boolean> cir){if(create$sameSlot&&oldStack.getItem() instanceof PotatoCannonItem&&newStack.getItem()==oldStack.getItem())cir.setReturnValue(true);if(create$sameSlot&&oldStack.getItem() instanceof com.simibubi.create.content.equipment.zapper.ZapperItem zapper)cir.setReturnValue(!zapper.shouldCauseReequipAnimation(oldStack,newStack,false));}
    @Inject(method="extractRenderState",at=@At("HEAD")) private void create$prepareGrip(LocalPlayer player,float partial,FirstPersonHandsAndItemsRenderState state,CallbackInfo ci){ExtendoGripRenderHandler.prepare(player,mainHandItem,offHandItem,partial);}
    @Inject(method="extractRenderState",at=@At("TAIL")) private void create$extractEquipment(LocalPlayer player,float partial,FirstPersonHandsAndItemsRenderState state,CallbackInfo ci){EquipmentHands.extract(player,partial,state);}
}
