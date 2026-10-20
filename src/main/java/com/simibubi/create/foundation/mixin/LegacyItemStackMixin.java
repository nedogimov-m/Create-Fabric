package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.legacy.ChromaticCompoundItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Implements the inherited native ItemInstance default method on real ItemStack, without component edits. */
@Mixin(ItemStack.class)
public abstract class LegacyItemStackMixin {
 public int getMaxStackSize(){var stack=(ItemStack)(Object)this;return stack.getItem() instanceof ChromaticCompoundItem compound?compound.getMaxStackSize(stack):stack.getOrDefault(DataComponents.MAX_STACK_SIZE,1);}
}
