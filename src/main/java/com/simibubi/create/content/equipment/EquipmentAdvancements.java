package com.simibubi.create.content.equipment;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Original actual equipment criteria; no synthetic players, progress or achievement flags. */
public final class EquipmentAdvancements {
    public static void register(){for(var id:new String[]{"extendo_grip","extendo_grip_dual","potato_cannon","potato_cannon_collide","backtank","diving_suit","diving_suit_lava","cardboard_armor","cardboard_armor_trim"}){var key=Create.asResource(id+"_builtin");if(!BuiltInRegistries.TRIGGER_TYPES.containsKey(key))com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(key);}}
    public static void award(Player player,String id){if(!(player instanceof ServerPlayer actual))return;var holder=actual.level().getServer().getAdvancements().get(Create.asResource(id));if(holder==null||actual.getAdvancements().getOrStartProgress(holder).isDone())return;var criterion=BuiltInRegistries.TRIGGER_TYPES.getValue(Create.asResource(id+"_builtin"));if(!(criterion instanceof SimpleCreateTrigger trigger))throw new IllegalStateException("Actual source equipment criterion missing: "+id);trigger.trigger(actual);}
    private EquipmentAdvancements(){}
}
