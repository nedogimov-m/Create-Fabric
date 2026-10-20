package com.simibubi.create.content.equipment.zapper;
import com.simibubi.create.foundation.gui.AllIcons;
/** Client atlas resolution for source metadata; common enum codecs never link client classes. */
public final class ClientZapperIcons {
    public static AllIcons get(String key){return switch(key){case "I_FILL"->AllIcons.I_FILL;case "I_PLACE"->AllIcons.I_PLACE;case "I_REPLACE"->AllIcons.I_REPLACE;case "I_CLEAR"->AllIcons.I_CLEAR;case "I_OVERLAY"->AllIcons.I_OVERLAY;case "I_FLATTEN"->AllIcons.I_FLATTEN;case "I_CENTERED"->AllIcons.I_CENTERED;case "I_ATTACHED"->AllIcons.I_ATTACHED;case "I_INSERTED"->AllIcons.I_INSERTED;case "I_PATTERN_SOLID"->AllIcons.I_PATTERN_SOLID;case "I_PATTERN_CHECKERED"->AllIcons.I_PATTERN_CHECKERED;case "I_PATTERN_CHECKERED_INVERSED"->AllIcons.I_PATTERN_CHECKERED_INVERSED;case "I_PATTERN_CHANCE_25"->AllIcons.I_PATTERN_CHANCE_25;case "I_PATTERN_CHANCE_50"->AllIcons.I_PATTERN_CHANCE_50;case "I_PATTERN_CHANCE_75"->AllIcons.I_PATTERN_CHANCE_75;default->throw new IllegalArgumentException("Unknown original source zapper icon "+key);};}
    private ClientZapperIcons(){}
}
