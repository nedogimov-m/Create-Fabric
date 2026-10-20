package com.simibubi.create.foundation.mixin;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.EquipmentAdvancements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingMenu.class)
abstract class CardboardSmithingMixin {
    @Inject(method="onTake",at=@At("HEAD")) private void create$originalCardboardTrim(Player player,ItemStack result,CallbackInfo ci){if(result.is(AllItems.CARDBOARD_HELMET)||result.is(AllItems.CARDBOARD_CHESTPLATE)||result.is(AllItems.CARDBOARD_LEGGINGS)||result.is(AllItems.CARDBOARD_BOOTS))EquipmentAdvancements.award(player,"cardboard_armor_trim");}
}
