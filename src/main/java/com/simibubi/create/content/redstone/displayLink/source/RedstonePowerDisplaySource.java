package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class RedstonePowerDisplaySource extends PercentOrProgressBarDisplaySource {
    @Override protected Float getProgress(DisplayLinkContext context){return Math.max(new DisplayLinkBlock.SignalView(context.level()).getDirectSignalTo(context.getSourcePos()),LoadedLevelAccess.blockState(context.level(),context.getSourcePos()).getOptionalValue(BlockStateProperties.POWER).orElse(0))/15f;}
    @Override protected MutableComponent formatNumeric(DisplayLinkContext context,Float value){return Component.literal(Integer.toString((int)(value*15)));}
    @Override protected boolean progressBarActive(DisplayLinkContext context){return context.sourceConfig().getIntOr("Mode",0)!=0;}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override protected String getTranslationKey(){return "redstone_power";}
    @Override public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context){var options=new ArrayList<>(super.configuration(context));options.add(DisplaySourceConfiguration.choice("Mode","create.display_source.redstone_power.number","create.display_source.redstone_power.progress_bar"));return options;}
}
