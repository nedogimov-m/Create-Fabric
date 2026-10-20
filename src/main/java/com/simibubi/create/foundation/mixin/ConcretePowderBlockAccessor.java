package com.simibubi.create.foundation.mixin;

import net.minecraft.world.level.block.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ConcretePowderBlock.class)
public interface ConcretePowderBlockAccessor {
    @Accessor("concrete") Block create$getConcrete();
}
