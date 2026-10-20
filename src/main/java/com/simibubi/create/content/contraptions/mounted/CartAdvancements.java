package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** The genuine source pickup event and original builtin criterion; strict source threshold is >200. */
public final class CartAdvancements {
    private static SimpleCreateTrigger pickup;
    public static void register(){if(pickup==null)pickup=com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(Create.asResource("cart_pickup_builtin"));}
    public static void pickedUp(Player player,int blocks){if(blocks<=200||!(player instanceof ServerPlayer actual)||player instanceof net.fabricmc.fabric.api.entity.FakePlayer)return;if(pickup==null)throw new IllegalStateException("Actual native cart pickup criterion not registered");pickup.trigger(actual);}
    private CartAdvancements(){}
}
