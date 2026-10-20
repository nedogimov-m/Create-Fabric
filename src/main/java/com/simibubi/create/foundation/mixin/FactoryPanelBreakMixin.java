package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs after break protection and vanilla break effects, before world removal. */
@Mixin(ServerPlayerGameMode.class)
public abstract class FactoryPanelBreakMixin {
    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;
    @Inject(method="destroyBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;",shift=At.Shift.AFTER),cancellable=true)
    private void create$destroyOnePanel(BlockPos pos,CallbackInfoReturnable<Boolean> result) {
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof FactoryPanelBlockEntity gauge)||gauge.activePanels()<2)return;
        var hit=player.pick(player.blockInteractionRange()+1,1,false);var slot=FactoryPanelBlock.getTargetedSlot(pos,gauge.getBlockState(),hit.getLocation());
        if(!gauge.removePanel(slot))return;
        if(!player.isCreative())Block.popResource(level,pos,new ItemStack(com.simibubi.create.AllBlocks.FACTORY_GAUGE));
        player.connection.send(new ClientboundBlockUpdatePacket(level,pos));player.connection.send(gauge.getUpdatePacket());result.setReturnValue(false);
    }
}
