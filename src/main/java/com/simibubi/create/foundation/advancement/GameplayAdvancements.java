package com.simibubi.create.foundation.advancement;

import com.simibubi.create.Create;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Source gameplay criteria, emitted by the actual interaction or exact device owner. */
public final class GameplayAdvancements {
    private static final Set<String> CRITERIA=Set.of("anvil_plough","belt_funnel_kiss","chained_drain","crafter_lazy_000","cross_streams","fist_bump","glass_pipe","hand_crank_000","honey_drain","hose_pulley","lava_wheel_00000","mechanical_pump_0","package_chute_throw","pipe_organ","self_deploying","water_supply");
    public static void register(){for(var id:CRITERIA){var key=Create.asResource(id+"_builtin");if(!BuiltInRegistries.TRIGGER_TYPES.containsKey(key))AllTriggers.registerSimple(key);}}
    public static void award(Player player,String id){PrerequisiteAdvancements.award(player,id);}
    public static void awardOwner(Level level,UUID owner,String id){if(owner!=null)award(level.getPlayerByUUID(owner),id);}
    public static void awardAt(Level level,BlockPos pos,String id){
        var device=LoadedLevelAccess.blockEntity(level,pos);
        if(device instanceof FluidPipeBlockEntity pipe)pipe.sourceAdvancements.award(id);
        else if(device instanceof com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity pipe)pipe.sourceAdvancements.award(id);
        else if(device instanceof PumpBlockEntity pump)pump.sourceAdvancements.award(id);
        else if(device instanceof com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity valve)valve.sourceAdvancements.award(id);
    }
    private GameplayAdvancements(){}
}
