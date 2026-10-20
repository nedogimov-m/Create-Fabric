package com.simibubi.create.content.redstone.displayLink;

import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.network.chat.*;

/** Actual source numeric/fluid units, with deterministic native common-side formatting and mB conversion. */
public final class DisplayFormatting {
    public static MutableComponent number(double value){return Component.literal(NumberFormat.getNumberInstance(Locale.ROOT).format(Math.abs(value)<1e-5?0:value).replace('\u00A0',' '));}
    public static MutableComponent translate(String key,Object... arguments){return Component.translatable("create."+key,arguments);}
    public static long millibuckets(long nativeAmount){return nativeAmount/81;}
    public static String fluid(long amount,boolean shorten){return shorten&&amount>=1000?String.format(Locale.ROOT,"%.1f",amount/1000d)+" "+translate("generic.unit.buckets").getString():amount+" "+translate("generic.unit.millibuckets").getString();}
    private DisplayFormatting(){}
}
