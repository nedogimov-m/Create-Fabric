package com.simibubi.create.foundation.mixin;
import com.simibubi.create.content.equipment.tool.CardboardSwordItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Source rejects the complete mixed book when ANY stored enchantment is unsupported. */
@Mixin(AnvilMenu.class)
abstract class CardboardSwordAnvilMixin extends ItemCombinerMenu {
 @Shadow @Final private DataSlot cost;
 protected CardboardSwordAnvilMixin(MenuType<?> type,int id,Inventory inventory,ContainerLevelAccess access,ItemCombinerMenuSlotDefinition slots){super(type,id,inventory,access,slots);}
 @Inject(method="createResult",at=@At("HEAD"),cancellable=true)private void create$nativeOriginalBookRule(CallbackInfo ci){var left=inputSlots.getItem(0);if(left.getItem() instanceof CardboardSwordItem sword&&!sword.isBookEnchantable(left,inputSlots.getItem(1))){resultSlots.setItem(0,ItemStack.EMPTY);cost.set(0);ci.cancel();}}
}
