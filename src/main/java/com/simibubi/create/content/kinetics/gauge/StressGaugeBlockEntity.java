package com.simibubi.create.content.kinetics.gauge;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import com.simibubi.create.content.kinetics.KineticGraph;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.block.state.BlockState;

public class StressGaugeBlockEntity extends GaugeBlockEntity {
    private java.util.UUID advancementOwner;
    private boolean initializedOwner;
    public java.util.UUID advancementOwner(){return advancementOwner;}
    public void placedBy(net.minecraft.server.level.ServerPlayer player){if(!com.simibubi.create.foundation.advancement.GaugeAdvancements.actualPlacer(this,player))return;advancementOwner=player.getUUID();removeCompletedOwner();setChanged();}
    public void removeCompletedOwner(){if(!com.simibubi.create.foundation.advancement.AdvancementBehaviour.hasPendingFor(this,advancementOwner)&&com.simibubi.create.foundation.advancement.GaugeAdvancements.complete(this,advancementOwner,"stressometer","stressometer_maxed")){advancementOwner=null;setChanged();}}
    public void onObserved(){com.simibubi.create.foundation.advancement.GaugeAdvancements.award(this,advancementOwner,"stressometer");if(net.minecraft.util.Mth.equal(dialTarget,1))com.simibubi.create.foundation.advancement.GaugeAdvancements.award(this,advancementOwner,"stressometer_maxed");removeCompletedOwner();}
    public static void tick(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,StressGaugeBlockEntity gauge){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, gauge)) {
            if (!createTick.shouldTick()) return;
GaugeBlockEntity.tick(level,pos,state,gauge);if(!level.isClientSide()&&!gauge.initializedOwner){gauge.initializedOwner=true;gauge.removeCompletedOwner();}
        }
    }
    @Override protected void read(net.minecraft.world.level.storage.ValueInput input, boolean clientPacket){super.read(input, clientPacket);initializedOwner=false;}

    public StressGaugeBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.STRESSOMETER, pos, state); }
    @Override protected void updateReading(float speed, float theoreticalSpeed, KineticGraph.Stress stress) {
        dialTarget = theoreticalSpeed == 0 ? 0 : stress.overstressed() ? 1.125f : stress.capacity() == 0 ? 0 : (float)(stress.impact() / stress.capacity());
        if (dialTarget > 0) color = dialTarget < 0.5f ? mix(0x00FF00, 0xFFFF00, dialTarget * 2) : dialTarget < 1 ? mix(0xFFFF00, 0xFF0000, dialTarget * 2 - 1) : 0xFF0000;
    }
    public double getNetworkStress() { return getStressImpact(); }
    public double getNetworkCapacity() { return getStressCapacity(); }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        super.addToGoggleTooltip(tooltip, sneaking); tooltip.add(GoggleTooltip.translate("gui.stressometer.title").withStyle(ChatFormatting.GRAY));
        if (getTheoreticalSpeed() == 0) { tooltip.add(Component.literal(bar(0)).append(GoggleTooltip.translate("gui.stressometer.no_rotation")).withStyle(ChatFormatting.DARK_GRAY)); return true; }
        double capacity = getNetworkCapacity(), stress = getNetworkStress(), fraction = stress / (capacity == 0 ? 1 : capacity);
        int level = fraction > 1 ? 3 : fraction > 0.75 ? 2 : fraction > 0.5 ? 1 : 0;
        var color = new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.GOLD, ChatFormatting.RED}[level];
        tooltip.add(Component.literal(bar(Math.min(3, level + 1))).append(GoggleTooltip.translate("tooltip.stressImpact." + new String[]{"low", "medium", "high", "overstressed"}[level])).append(" (" + (int)(fraction * 100) + "%) ").withStyle(color));
        tooltip.add(GoggleTooltip.translate("gui.stressometer.capacity").withStyle(ChatFormatting.GRAY));
        var remaining = GoggleTooltip.units(capacity - stress, "stress").withStyle(color);
        if (stress != 0) remaining.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY)).append(GoggleTooltip.units(capacity, "stress").withStyle(ChatFormatting.DARK_GRAY));
        GoggleTooltip.line(tooltip, remaining, 1); return true;
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("stressometer");
        ids.add("stressometer_maxed");
    }
    private final com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage canonicalAdvancementOwner=new com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage() {
        @Override public java.util.UUID getPlayerId(){return advancementOwner;}
        @Override public void setPlayerId(java.util.UUID id){advancementOwner=id;}
    };
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return canonicalAdvancementOwner;}
}
