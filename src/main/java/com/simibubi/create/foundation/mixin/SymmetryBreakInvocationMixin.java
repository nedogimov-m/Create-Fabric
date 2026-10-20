package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;

/** One genuine native invocation owns its mirror suppression; nested genuine actions get a fresh frame. */
@Mixin(ServerPlayerGameMode.class)
public abstract class SymmetryBreakInvocationMixin {
    @Shadow @Final protected ServerPlayer player;
    @WrapMethod(method="destroyBlock")
    private boolean create$exactMirroredBreakInvocation(BlockPos target,Operation<Boolean> original){try(var capture=SymmetryPlacement.captureBreak(player,target)){return original.call(target);}}
}
