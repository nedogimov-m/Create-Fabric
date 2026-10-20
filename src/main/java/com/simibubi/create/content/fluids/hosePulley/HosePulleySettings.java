package com.simibubi.create.content.fluids.hosePulley;

import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;

/** Source defaults plus the actual native server configuration snapshot. */
public record HosePulleySettings(int range,int blockThreshold,boolean fillInfinite,
                                  BottomlessFluidMode bottomlessMode,boolean placeSources) {
    public static final HosePulleySettings DEFAULT=new HosePulleySettings(128,10000,false,BottomlessFluidMode.ALLOW_BY_TAG,true);
    public static HosePulleySettings configured(){var config=com.simibubi.create.infrastructure.config.AllConfigs.server().fluids;return new HosePulleySettings(config.hosePulleyRange.get(),config.hosePulleyBlockThreshold.get(),config.fillInfinite.get(),config.bottomlessFluidMode.get(),config.fluidFillPlaceFluidSourceBlocks.get());}
    public HosePulleySettings {
        if(range<1||blockThreshold < -1||bottomlessMode==null)throw new IllegalArgumentException("Invalid hose pulley settings");
    }
}
