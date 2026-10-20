package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the block entity while the server decides which quarter can be removed. */
@Mixin(MultiPlayerGameMode.class)
public abstract class FactoryPanelClientBreakMixin {
    @Shadow @Final private Minecraft minecraft;
    @Inject(method="destroyBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;",shift=At.Shift.AFTER),cancellable=true)
    private void create$preserveOtherPanels(BlockPos pos,CallbackInfoReturnable<Boolean> result) {
        if(LoadedLevelAccess.blockEntity(minecraft.level,pos) instanceof FactoryPanelBlockEntity gauge&&gauge.activePanels()>1)result.setReturnValue(false);
    }
}
