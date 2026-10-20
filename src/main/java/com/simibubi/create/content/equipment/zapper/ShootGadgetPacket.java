package com.simibubi.create.content.equipment.zapper;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/** Common source packet API; client effects are installed by the actual client entrypoint. */
public abstract class ShootGadgetPacket implements CustomPacketPayload {
    public final Vec3 location;
    public final InteractionHand hand;
    public final boolean self;
    protected ShootGadgetPacket(Vec3 location,InteractionHand hand,boolean self){this.location=location;this.hand=hand;this.self=self;}
}
