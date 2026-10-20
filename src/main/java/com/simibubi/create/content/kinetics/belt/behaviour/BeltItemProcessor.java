package com.simibubi.create.content.kinetics.belt.behaviour;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;

/** Centre crossings and held cargo share one dispatch path for ported processing machines. */
public interface BeltItemProcessor {
    boolean onBeltItemReceived(TransportedItemStack item);
    boolean tryHoldBeltItem(BeltBlockEntity belt, TransportedItemStack item);
}
