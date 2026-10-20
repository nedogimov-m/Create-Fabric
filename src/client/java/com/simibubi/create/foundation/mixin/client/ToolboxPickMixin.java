package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.equipment.toolbox.ToolboxHandlerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class ToolboxPickMixin {
    @Inject(method="handlePickItemFromBlock",at=@At("HEAD"),cancellable=true)
    private void create$pickToolboxBlock(BlockPos pos,boolean includeData,CallbackInfo callback){var client=Minecraft.getInstance();if(client.level!=null&&client.level.isLoaded(pos)&&ToolboxHandlerClient.onPickItem(com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks.pickForToolbox(client.player,client.level,pos,includeData)))callback.cancel();}
    @Inject(method="handlePickItemFromEntity",at=@At("HEAD"),cancellable=true)
    private void create$pickToolboxEntity(Entity entity,boolean includeData,CallbackInfo callback){if(ToolboxHandlerClient.onPickItem(entity.getPickResult()))callback.cancel();}
}
