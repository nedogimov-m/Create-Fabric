package com.simibubi.create.content.equipment.tool;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
public final class CardboardSwordClient {
 public static void register(){ClientPlayNetworking.registerGlobalReceiver(KnockbackPacket.TYPE,(packet,context)->{if(context.player()!=null)CardboardSwordItem.knockback(context.player(),packet.strength(),packet.yRot());});}
 private CardboardSwordClient(){}
}
