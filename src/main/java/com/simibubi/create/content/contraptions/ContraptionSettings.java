package com.simibubi.create.content.contraptions;

import com.simibubi.create.api.contraption.ContraptionMovementSetting;

/** Original defaults, exposed to suppliers until the common server configuration is ported. */
public final class ContraptionSettings {
    public static volatile ContraptionMovementSetting spawnerMovement=ContraptionMovementSetting.NO_PICKUP;
    public static volatile ContraptionMovementSetting amethystMovement=ContraptionMovementSetting.NO_PICKUP;
    public static volatile ContraptionMovementSetting obsidianMovement=ContraptionMovementSetting.UNMOVABLE;
    public static volatile ContraptionMovementSetting reinforcedDeepslateMovement=ContraptionMovementSetting.UNMOVABLE;
    public static int maxBlocksMoved=2048;
    public static volatile int psiTimeout=60;
    public static volatile int rollerFillDepth=12;
    public static int maxPistonPoles=64,minimumWindmillSails=8,windmillSailsPerRPM=8;
    public static boolean noDropWhenContraptionReplaceBlocks=false;
    public static volatile boolean stabiliseStableContraptions=false;
    public static volatile boolean rotateWhenSeated=true;
    public static volatile boolean moveItemsToStorage=true;
    public static volatile boolean harvesterReplants=true,harvestPartiallyGrown=false;
    private ContraptionSettings(){}
}
