package com.simibubi.create.content.kinetics.base;

import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

// SpeedLevel sourced from Create6.0.10; progress-bar loop retained without client font linkage.
public interface IRotate {
	enum SpeedLevel {
		NONE(ChatFormatting.DARK_GRAY, 0x000000, 0),
		SLOW(ChatFormatting.GREEN, 0x22FF22, 10),
		MEDIUM(ChatFormatting.AQUA, 0x0084FF, 20),
		FAST(ChatFormatting.LIGHT_PURPLE, 0xFF55FF, 30);

		private final ChatFormatting textColor;
		private final int color;
		private final int particleSpeed;

		SpeedLevel(ChatFormatting textColor, int color, int particleSpeed) {
			this.textColor = textColor;
			this.color = color;
			this.particleSpeed = particleSpeed;
		}

		public ChatFormatting getTextColor() {
			return textColor;
		}

		public int getColor() {
			return color;
		}

		public int getParticleSpeed() {
			return particleSpeed;
		}

		public float getSpeedValue() {
			switch (this) {
			case FAST:
				return AllConfigs.server().kinetics.fastSpeed.get()
					.floatValue();
			case MEDIUM:
				return AllConfigs.server().kinetics.mediumSpeed.get()
					.floatValue();
			case SLOW:
				return 1;
			case NONE:
			default:
				return 0;
			}
		}

		public static SpeedLevel of(float speed) {
			speed = Math.abs(speed);

			if (speed >= AllConfigs.server().kinetics.fastSpeed.get())
				return FAST;
			if (speed >= AllConfigs.server().kinetics.mediumSpeed.get())
				return MEDIUM;
			if (speed >= 1)
				return SLOW;
			return NONE;
		}

		public static LangBuilder getFormattedSpeedText(float speed, boolean overstressed) {
			SpeedLevel speedLevel = of(speed);
			LangBuilder builder = CreateLang.text(makeProgressBar(3, speedLevel.ordinal()));

			builder.translate("tooltip.speedRequirement." + Lang.asId(speedLevel.name()))
				.space()
				.text("(")
				.add(CreateLang.number(Math.abs(speed)))
				.space()
				.translate("generic.unit.rpm")
				.text(")")
				.space();

			if (overstressed)
				builder.style(ChatFormatting.DARK_GRAY)
					.style(ChatFormatting.STRIKETHROUGH);
			else
				builder.style(speedLevel.getTextColor());

			return builder;
		}

	}

	private static String makeProgressBar(int length, int filledLength) {
		String bar = " ";
		int emptySpaces = length - filledLength;
		for (int i = 0; i < filledLength; i++)
			bar += "\u2588";
		for (int i = 0; i < emptySpaces; i++)
			bar += "\u2592";
		return bar + " ";
	}

    default boolean hideStressImpact() { return false; }

    Direction.Axis getRotationAxis(BlockState state);

    boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face);
}
