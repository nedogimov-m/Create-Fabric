package com.simibubi.create.foundation.item;

import net.minecraft.core.registries.BuiltInRegistries;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.registry.SimpleRegistry;

import net.minecraft.world.item.Item;



@FunctionalInterface
public interface TooltipModifier {
	SimpleRegistry<Item, TooltipModifier> REGISTRY = SimpleRegistry.create();

	TooltipModifier EMPTY = new TooltipModifier() {
		@Override
		public void modify(TooltipModifier.Context context) {
		}

		@Override
		public TooltipModifier andThen(TooltipModifier after) {
			return after;
		}
	};

    /** Actual native callback arguments; the tooltip list is the live mutable list, not a copy. */
    record Context(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext itemContext,
            net.minecraft.world.item.TooltipFlag flags, @Nullable net.minecraft.world.entity.player.Player player,
            java.util.List<net.minecraft.network.chat.Component> tooltip) {
        public net.minecraft.world.item.ItemStack getItemStack() { return stack; }
        public net.minecraft.world.item.Item.TooltipContext getContext() { return itemContext; }
        public net.minecraft.world.item.TooltipFlag getFlags() { return flags; }
        @Nullable public net.minecraft.world.entity.player.Player getEntity() { return player; }
        public java.util.List<net.minecraft.network.chat.Component> getToolTip() { return tooltip; }
    }

	void modify(TooltipModifier.Context context);

	default TooltipModifier andThen(TooltipModifier after) {
		if (after == EMPTY) {
			return this;
		}
		return tooltip -> {
			modify(tooltip);
			after.modify(tooltip);
		};
	}

	static TooltipModifier mapNull(@Nullable TooltipModifier modifier) {
		if (modifier == null) {
			return EMPTY;
		}
		return modifier;
	}
}
