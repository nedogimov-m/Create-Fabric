package com.simibubi.create.foundation.item;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlock;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** Pinned kinetic item statistics on the real Fabric item-tooltip callback. */
public class KineticStats implements TooltipModifier {
    protected final Block block;
    private static final ChatFormatting[] IMPACT_COLORS = {
        ChatFormatting.YELLOW, ChatFormatting.GOLD, ChatFormatting.RED
    };
    private static final String[] LEVELS = {"low", "medium", "high"};

    public KineticStats(Block block) { this.block = block; }

    @Nullable
    public static KineticStats create(Item item) {
        if (item instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
            if (block instanceof IRotate || block instanceof SteamEngineBlock)
                return new KineticStats(block);
        }
        return null;
    }

    public static void register() { ItemTooltipModifiers.register(); }

    @Override public void modify(TooltipModifier.Context context) {
        modify(context.getEntity(), context.getToolTip());
    }

    public void modify(Player player, List<Component> tooltip) {
        var lines = getKineticStats(block, player);
        if (!lines.isEmpty()) {
            tooltip.add(Component.empty());
            tooltip.addAll(lines);
        }
    }

    public static List<Component> getKineticStats(Block block, @Nullable Player player) {
        var lines = new ArrayList<Component>();
        var config = AllConfigs.server().kinetics;
        if (config.disableStress.get()) return lines;
        boolean goggles = player != null && GogglesItem.isWearingGoggles(player);
        boolean showImpact = !(block instanceof IRotate rotate) || !rotate.hideStressImpact();
        // Source coloured handles share the canonical copper handle's configured values.
        if (block instanceof ValveHandleBlock) block = AllBlocks.COPPER_VALVE_HANDLE;
        double impact = BlockStressValues.getImpact(block);
        if (showImpact && impact > 0) {
            lines.add(GoggleTooltip.translate("tooltip.stressImpact").withStyle(ChatFormatting.GRAY));
            int level = level(impact, config.mediumStressImpact.get(), config.highStressImpact.get());
            var value = bar(level).withStyle(IMPACT_COLORS[level]);
            value.append(goggles ? number(impact).append("x ").append(rpm())
                : GoggleTooltip.translate("tooltip.stressImpact." + LEVELS[level]));
            lines.add(value);
        }
        double capacity = BlockStressValues.getCapacity(block);
        if (capacity > 0) {
            lines.add(GoggleTooltip.translate("tooltip.capacityProvided").withStyle(ChatFormatting.GRAY));
            int level = level(capacity, config.mediumCapacity.get(), config.highCapacity.get());
            var value = bar(level).withStyle(IMPACT_COLORS[2 - level]);
            value.append(goggles ? number(capacity).append("x ").append(rpm())
                : GoggleTooltip.translate("tooltip.capacityProvided." + LEVELS[level]));
            lines.add(value);
            var generatedRpm = BlockStressValues.RPM.get(block);
            if (goggles && generatedRpm != null) {
                var amount = number(capacity * generatedRpm.value())
                    .append(GoggleTooltip.translate("generic.unit.stress"));
                lines.add(Component.literal(" -> ")
                    .append(generatedRpm.mayGenerateLess() ? GoggleTooltip.translate("tooltip.up_to", amount) : amount)
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    private static int level(double value, double medium, double high) {
        return value >= high ? 2 : value >= medium ? 1 : 0;
    }
    private static MutableComponent bar(int level) {
        return Component.literal(TooltipHelper.makeProgressBar(3, level + 1));
    }
    private static MutableComponent rpm() { return GoggleTooltip.translate("generic.unit.rpm"); }
    private static MutableComponent number(double value) {
        return com.simibubi.create.foundation.utility.CreateLang.number(value).component();
    }
}
