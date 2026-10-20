package com.simibubi.create.content.trains.schedule;

import com.simibubi.create.content.trains.schedule.condition.*;
import com.simibubi.create.content.trains.schedule.destination.*;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.mutable.MutableObject;

/** Actual source widget configuration, isolated from the dedicated-server schedule types. */
public final class ScheduleInputClient {
    private static final Map<Identifier, BiConsumer<IScheduleInput, ModularGuiLineBuilder>> CUSTOM = new HashMap<>();
    private ScheduleInputClient() {}

    public static void register(Identifier id, BiConsumer<IScheduleInput, ModularGuiLineBuilder> configuration) {
        if (CUSTOM.putIfAbsent(id, configuration) != null) throw new IllegalArgumentException("Duplicate schedule editor " + id);
    }

    public static void initConfigurationWidgets(IScheduleInput input, ModularGuiLineBuilder builder) {
        var custom = CUSTOM.get(input.getId());
        if (custom != null) { custom.accept(input, builder); return; }
        if (input instanceof ItemThresholdCondition item) { itemThresholdCondition(item, builder); return; }
        if (input instanceof FluidThresholdCondition fluid) { fluidThresholdCondition(fluid, builder); return; }
        if (input instanceof CargoThresholdCondition cargo) { cargoThresholdCondition(cargo, builder); return; }
        if (input instanceof PlayerPassengerCondition players) { playerPassengerCondition(players, builder); return; }
        if (input instanceof RedstoneLinkCondition link) { redstoneLinkCondition(link, builder); return; }
        if (input instanceof TimeOfDayCondition time) { timeOfDayCondition(time, builder); return; }
        if (input instanceof TimedWaitCondition timed) { timedWaitCondition(timed, builder); return; }
        if (input instanceof ChangeThrottleInstruction throttle) { changeThrottleInstruction(throttle, builder); return; }
        if (input instanceof TextScheduleInstruction text) textScheduleInstruction(text, builder);
    }

    private static void cargoThresholdCondition(CargoThresholdCondition input, ModularGuiLineBuilder builder) {
		builder.addSelectionScrollInput(0, 24, (i, l) -> {
			i.forOptions(CargoThresholdCondition.Ops.translatedOptions())
				.titled(ScheduleText.translate("schedule.condition.threshold.train_holds", ""))
				.format(state -> {
                    return Component.literal(" " + CargoThresholdCondition.Ops.values()[state].formatted);
                });
		}, "Operator");
		builder.addIntegerTextInput(29, 41, (e, t) -> {
		}, "Threshold");
	
    }

    private static void fluidThresholdCondition(FluidThresholdCondition input, ModularGuiLineBuilder builder) {
		cargoThresholdCondition(input, builder);
		builder.addSelectionScrollInput(71, 50, (i, l) -> {
			i.forOptions(ImmutableList.of(ScheduleText.translate("schedule.condition.threshold.buckets")))
				.titled(null);
		}, "Measure");
	
    }

    private static void itemThresholdCondition(ItemThresholdCondition input, ModularGuiLineBuilder builder) {
		cargoThresholdCondition(input, builder);
		builder.addSelectionScrollInput(71, 50, (i, l) -> {
			i.forOptions(ImmutableList.of(ScheduleText.translate("schedule.condition.threshold.items"),
				ScheduleText.translate("schedule.condition.threshold.stacks")))
				.titled(ScheduleText.translate("schedule.condition.threshold.item_measure"));
		}, "Measure");
	
    }

    private static void playerPassengerCondition(PlayerPassengerCondition input, ModularGuiLineBuilder builder) {
		builder.addScrollInput(0, 31, (i, l) -> {
			i.titled(ScheduleText.translate("schedule.condition.player_count.players"))
				.withShiftStep(5)
				.withRange(0, 21);
		}, "Count");

		builder.addSelectionScrollInput(36, 85, (i, l) -> {
			i.forOptions(ScheduleText.options("schedule.condition.player_count", "exactly", "or_above"))
				.titled(ScheduleText.translate("schedule.condition.player_count.condition"));
		}, "Exact");
	
    }

    private static void redstoneLinkCondition(RedstoneLinkCondition input, ModularGuiLineBuilder builder) {
		builder.addSelectionScrollInput(20, 101,
			(i, l) -> i.forOptions(ScheduleText.options("schedule.condition.redstone_link", "powered", "unpowered"))
				.titled(ScheduleText.translate("schedule.condition.redstone_link.frequency_state")),
			"Inverted");
	
    }

    private static void timeOfDayCondition(TimeOfDayCondition input, ModularGuiLineBuilder builder) {
		MutableObject<ScrollInput> minuteInput = new MutableObject<>();
		MutableObject<ScrollInput> hourInput = new MutableObject<>();
		MutableObject<Label> timeLabel = new MutableObject<>();

		builder.addScrollInput(0, 16, (i, l) -> {
			i.withRange(0, 24);
			timeLabel.setValue(l);
			hourInput.setValue(i);
		}, "Hour");

		builder.addScrollInput(18, 16, (i, l) -> {
			i.withRange(0, 60);
			minuteInput.setValue(i);
			l.visible = false;
		}, "Minute");

		builder.addSelectionScrollInput(52, 62, (i, l) -> {
			i.forOptions(ScheduleText.options("schedule.condition.time_of_day.rotation", "every_24", "every_12",
				"every_6", "every_4", "every_3", "every_2", "every_1", "every_0_45", "every_0_30", "every_0_15"))
				.titled(ScheduleText.translate("schedule.condition.time_of_day.rotation"));
		}, "Rotation");

		hourInput.getValue()
			.titled(ScheduleText.translate("generic.daytime.hour"))
			.calling(t -> {
				input.getData().putInt("Hour", t);
				timeLabel.getValue().text = input.getDigitalDisplay(t, minuteInput.getValue()
					.getState(), true);
			})
			.writingTo(null)
			.withShiftStep(6);

		minuteInput.getValue()
			.titled(ScheduleText.translate("generic.daytime.minute"))
			.calling(t -> {
				input.getData().putInt("Minute", t);
				timeLabel.getValue().text = input.getDigitalDisplay(hourInput.getValue()
					.getState(), t, true);
			})
			.writingTo(null)
			.withShiftStep(15);

		minuteInput.getValue().lockedTooltipX = hourInput.getValue().lockedTooltipX = -15;
		minuteInput.getValue().lockedTooltipY = hourInput.getValue().lockedTooltipY = 35;

		hourInput.getValue()
			.setState(input.getData().getIntOr("Hour", 0));
		minuteInput.getValue()
			.setState(input.getData().getIntOr("Minute", 0))
			.onChanged();

		builder.customArea(0, 52);
		builder.customArea(52, 69);
	
    }

    private static void timedWaitCondition(TimedWaitCondition input, ModularGuiLineBuilder builder) {
		builder.addScrollInput(0, 31, (i, l) -> {
			i.titled(ScheduleText.translate("generic.duration"))
				.withShiftStep(15)
				.withRange(0, 121);
			i.lockedTooltipX = -15;
			i.lockedTooltipY = 35;
		}, "Value");

		builder.addSelectionScrollInput(36, 85, (i, l) -> {
			i.forOptions(TimedWaitCondition.TimeUnit.translatedOptions())
				.titled(ScheduleText.translate("generic.timeUnit"));
		}, "TimeUnit");
	
    }

    private static void changeThrottleInstruction(ChangeThrottleInstruction input, ModularGuiLineBuilder builder) {
		builder.addScrollInput(0, 50, (si, l) -> {
			si.withRange(5, 101)
				.withStepFunction(c -> c.shift ? 25 : 5)
				.titled(ScheduleText.translate("schedule.instruction.throttle_edit_box"));
			l.withSuffix("%");
		}, "Value");
	
    }

    private static void textScheduleInstruction(TextScheduleInstruction input, ModularGuiLineBuilder builder) {
		builder.addTextInput(0, 121, (e, t) -> {
            if (input instanceof DestinationInstruction || input instanceof FetchPackagesInstruction)
                e.setFilter(value -> org.apache.commons.lang3.StringUtils.countMatches(value, '*') <= 3);
        }, "Text");
	
    }

    public static boolean renderSpecialIcon(IScheduleInput input, GuiGraphicsExtractor gui, int x, int y) {
        if (!(input instanceof TimeOfDayCondition time)) return false;
        int displayHr = (time.getData().getIntOr("Hour", 0) + 12) % 24;
        float progress = (displayHr * 60f + time.getData().getIntOr("Minute", 0)) / (24 * 60);
        Identifier location = Identifier.withDefaultNamespace("textures/item/clock_"
                + time.twoDigits(Mth.clamp((int) (progress * 64), 0, 63)) + ".png");
        gui.blit(RenderPipelines.GUI_TEXTURED, location, x, y, 0, 0, 16, 16, 16, 16);
        return true;
    }
}
