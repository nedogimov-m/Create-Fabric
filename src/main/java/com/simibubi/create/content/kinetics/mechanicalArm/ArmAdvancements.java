package com.simibubi.create.content.kinetics.mechanicalArm;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

/** Criteria fire for the real placer and real completed operations. */
public final class ArmAdvancements {
    public static void register(){
        for(var id:new String[]{"mechanical_arm","arm_blaze_burner","arm_many_targets","musical_arm","crusher_maxed_0000","linked_controller","extendo_grip","potato_cannon"}){
            var key=Create.asResource(id+"_builtin");
            if(!BuiltInRegistries.TRIGGER_TYPES.containsKey(key))com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(key);
        }
    }
    static void award(ArmBlockEntity arm,String id){
        if(arm.advancementOwner()==null||arm.getLevel()==null||!(arm.getLevel().getPlayerByUUID(arm.advancementOwner()) instanceof ServerPlayer player))return;
        var advancement=player.level().getServer().getAdvancements().get(Create.asResource(id));
        if(advancement==null||player.getAdvancements().getOrStartProgress(advancement).isDone())return;
        var trigger=BuiltInRegistries.TRIGGER_TYPES.getValue(Create.asResource(id+"_builtin"));
        if(!(trigger instanceof SimpleCreateTrigger actual))throw new IllegalStateException("Mechanical arm criterion missing: "+id);
        actual.trigger(player);
    }
    private ArmAdvancements(){}
}
