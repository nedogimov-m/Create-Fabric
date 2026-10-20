package com.simibubi.create.content.logistics.item.filter.attribute.attributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.logistics.item.filter.attribute.AllItemAttributeTypes;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;

public record ColorAttribute(DyeColor color) implements ItemAttribute {
	public static final MapCodec<ColorAttribute> CODEC = DyeColor.CODEC
			.xmap(ColorAttribute::new, ColorAttribute::color)
			.fieldOf("value");

	public static final StreamCodec<ByteBuf, ColorAttribute> STREAM_CODEC = DyeColor.STREAM_CODEC
		.map(ColorAttribute::new, ColorAttribute::color);

	private static Collection<DyeColor> findMatchingDyeColors(ItemStack stack) {
		DyeColor color = stack.getOrDefault(DataComponents.DYE,stack.get(DataComponents.BASE_COLOR));
		if (color != null)
			return Collections.singletonList(color);

		Set<DyeColor> colors = new HashSet<>();
        if(stack.has(DataComponents.FIREWORK_EXPLOSION))colors.addAll(getFireworkStarColors(stack.get(DataComponents.FIREWORK_EXPLOSION)));
		if (stack.has(DataComponents.FIREWORKS)) {
			if (stack.getItem() instanceof FireworkRocketItem) {
				List<FireworkExplosion> explosions = stack.get(DataComponents.FIREWORKS).explosions();
				for (FireworkExplosion explosion : explosions) {
					colors.addAll(getFireworkStarColors(explosion));
				}
			}
		}

		Arrays.stream(DyeColor.values()).filter(c -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith(c.getName() + "_")).forEach(colors::add);
        for(var candidate:DyeColor.values())if(stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,net.minecraft.resources.Identifier.fromNamespaceAndPath("c","dyed/"+candidate.getName()))))colors.add(candidate);

		return colors;
	}

	private static Collection<DyeColor> getFireworkStarColors(FireworkExplosion explosion) {
		Set<DyeColor> colors = new HashSet<>();
        Arrays.stream(explosion.colors().toIntArray()).mapToObj(DyeColor::byFireworkColor).filter(java.util.Objects::nonNull).forEach(colors::add);
        Arrays.stream(explosion.fadeColors().toIntArray()).mapToObj(DyeColor::byFireworkColor).filter(java.util.Objects::nonNull).forEach(colors::add);
		return colors;
	}

	@Override
	public boolean appliesTo(ItemStack itemStack, Level level) {
		return findMatchingDyeColors(itemStack).stream().anyMatch(color::equals);
	}

	@Override
	public String getTranslationKey() {
		return "color";
	}

	@Override
	public Object[] getTranslationParameters() {
		return new Object[]{net.minecraft.network.chat.Component.translatable("color.minecraft." + color.getName())};
	}

	@Override
	public ItemAttributeType getType() {
		return AllItemAttributeTypes.HAS_COLOR;
	}

	public static class Type implements ItemAttributeType {
		@Override
		public @NotNull ItemAttribute createAttribute() {
			return new ColorAttribute(DyeColor.PURPLE);
		}

		@Override
		public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
			List<ItemAttribute> list = new ArrayList<>();

			for (DyeColor color : ColorAttribute.findMatchingDyeColors(stack)) {
				list.add(new ColorAttribute(color));
			}

			return list;
		}

		@Override
		public MapCodec<? extends ItemAttribute> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
