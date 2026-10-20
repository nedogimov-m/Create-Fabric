package com.simibubi.create;

import com.simibubi.create.api.behaviour.spouting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import java.util.List;
import java.util.function.Predicate;

public final class AllBlockSpoutingBehaviours {
    private AllBlockSpoutingBehaviours(){}
    public static void registerDefaults(){
        Predicate<Fluid> water=fluid->fluid.isSame(Fluids.WATER);
        var mud=StateChangingBehavior.setTo(250*81L,water,Blocks.MUD);
        for(var block:List.of(Blocks.DIRT,Blocks.COARSE_DIRT,Blocks.ROOTED_DIRT))BlockSpoutingBehaviour.BY_BLOCK.register(block,mud);
        BlockSpoutingBehaviour.BY_BLOCK.register(Blocks.FARMLAND,StateChangingBehavior.incrementingState(100*81L,water,FarmlandBlock.MOISTURE));
        BlockSpoutingBehaviour.BY_BLOCK.register(Blocks.WATER_CAULDRON,StateChangingBehavior.incrementingState(250*81L,water,LayeredCauldronBlock.LEVEL));
        BlockSpoutingBehaviour.BY_BLOCK.register(Blocks.CAULDRON,CauldronSpoutingBehavior.INSTANCE);
        // Lazy exact source BE identifiers remain valid regardless of optional-mod initialization order.
        BlockSpoutingBehaviour.BY_BLOCK_ENTITY.registerProvider(type->{var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);return id!=null&&id.getNamespace().equals("tconstruct")&&(id.getPath().equals("table")||id.getPath().equals("basin"))?com.simibubi.create.compat.tconstruct.SpoutCasting.INSTANCE:null;});
    }
}
