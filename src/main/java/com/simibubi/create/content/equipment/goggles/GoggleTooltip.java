package com.simibubi.create.content.equipment.goggles;

import java.text.NumberFormat;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;

/** Shared formatting keeps Fabric units out of the player-facing Create tooltip. */
public final class GoggleTooltip {
    private GoggleTooltip(){}
    public static MutableComponent translate(String key,Object... args){return Component.translatable("create."+key,args);}
    public static MutableComponent number(double value){var format=NumberFormat.getNumberInstance(Locale.ROOT);format.setMaximumFractionDigits(2);return Component.literal(format.format(Double.isFinite(value)?value:0));}
    public static MutableComponent units(double value,String unit){return number(value).append(translate("generic.unit."+unit));}
    public static MutableComponent hint(String key,Object... args){var parts=translate(key,args).getString().split("_",-1);var result=Component.empty();for(int i=0;i<parts.length;i++)result.append(Component.literal(parts[i]).withStyle(i%2==0?ChatFormatting.GRAY:ChatFormatting.WHITE));return result;}
    public static void line(List<Component> tooltip,Component value,int indent){tooltip.add(Component.literal("    ".repeat(Math.max(0,indent))).append(value));}
    public static boolean containedFluid(List<Component> tooltip,Storage<FluidVariant> handler){
        if(handler==null)return false;var tanks=new ArrayList<StorageView<FluidVariant>>();handler.iterator().forEachRemaining(tanks::add);if(tanks.isEmpty())return false;
        int start=tooltip.size();line(tooltip,translate("gui.goggles.fluid_container"),0);boolean empty=true;
        for(var tank:tanks){if(tank.isResourceBlank()||tank.getAmount()==0)continue;empty=false;
            line(tooltip,FluidVariantAttributes.getName(tank.getResource()).copy().withStyle(ChatFormatting.GRAY),1);
            line(tooltip,units(tank.getAmount()/81d,"millibuckets").withStyle(ChatFormatting.GOLD).append(Component.literal(" / ").withStyle(ChatFormatting.GRAY)).append(units(tank.getCapacity()/81d,"millibuckets").withStyle(ChatFormatting.DARK_GRAY)),1);
        }
        if(empty){if(tanks.size()>1)tooltip.remove(start);else line(tooltip,translate("gui.goggles.fluid_container.capacity").withStyle(ChatFormatting.GRAY).append(units(tanks.getFirst().getCapacity()/81d,"millibuckets").withStyle(ChatFormatting.GOLD)),1);}
        return true;
    }
}
