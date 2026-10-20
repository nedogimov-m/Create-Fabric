package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Native random position/count/velocity remains intact; only the actual particle material changes. */
@Mixin(Entity.class)
abstract class CopycatSprintParticlesMixin {
    @ModifyArg(method="spawnSprintParticle()V",at=@At(value="INVOKE",target="Lnet/minecraft/core/particles/BlockParticleOption;<init>(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;)V"),index=1)
    private BlockState create$materialSprint(BlockState state){var entity=(Entity)(Object)this;return CopycatMaterialHooks.material(entity.level(),entity.getOnPosLegacy(),state);}
}
