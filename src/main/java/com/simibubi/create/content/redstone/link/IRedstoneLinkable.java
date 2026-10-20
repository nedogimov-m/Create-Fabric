package com.simibubi.create.content.redstone.link;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;

public interface IRedstoneLinkable {
    int getTransmittedStrength();
    void setReceivedStrength(int strength);
    boolean isListening();
    boolean isAlive();
    Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey();
    BlockPos getLocation();
}
