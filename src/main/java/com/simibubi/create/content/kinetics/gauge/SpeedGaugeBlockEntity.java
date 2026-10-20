package com.simibubi.create.content.kinetics.gauge;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import com.simibubi.create.content.kinetics.KineticGraph;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class SpeedGaugeBlockEntity extends GaugeBlockEntity {
    public SpeedGaugeBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.SPEEDOMETER, pos, state); }
    public static float getDialTarget(float speed) {
        speed = Math.abs(speed);
        var config=com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics;float medium=config.mediumSpeed.getF(),fast=config.fastSpeed.getF(),max=config.maxRotationSpeed.get();
        return speed==0?0:speed<medium?Mth.lerp(speed/medium,0,.45f):speed<fast?Mth.lerp((speed-medium)/(fast-medium),.45f,.75f):Mth.lerp((speed-fast)/(max-fast),.75f,1.125f);
    }
    @Override protected void updateReading(float speed, float theoreticalSpeed, KineticGraph.Stress stress) {
        dialTarget = getDialTarget(speed);
        color = mix(switch (speedLevel(speed)) { case 1 -> 0x22FF22; case 2 -> 0x0084FF; case 3 -> 0xFF55FF; default -> 0; }, 0xFFFFFF, 0.25f);
    }
    private static int speedLevel(float speed) { speed = Math.abs(speed); return speed >= com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.fastSpeed.get() ? 3 : speed >= com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.mediumSpeed.get() ? 2 : speed >= 1 ? 1 : 0; }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        super.addToGoggleTooltip(tooltip, sneaking); tooltip.add(GoggleTooltip.translate("gui.speedometer.title").withStyle(ChatFormatting.GRAY));
        int level = speedLevel(getSpeed()); var value = Component.literal(bar(level)).append(GoggleTooltip.translate("tooltip.speedRequirement." + new String[]{"none", "slow", "medium", "fast"}[level]))
            .append(" (").append(GoggleTooltip.number(Math.abs(getSpeed()))).append(" ").append(GoggleTooltip.translate("generic.unit.rpm")).append(") ");
        value.withStyle(new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE}[level]);
        if (isOverstressed()) value.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
        tooltip.add(value); return true;
    }
}
