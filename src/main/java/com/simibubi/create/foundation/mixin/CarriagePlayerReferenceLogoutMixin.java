package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.trains.entity.CarriagePlayerReferenceTransitions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;

/** PlayerList saves PlayerData before unRide; preserve the exact current parent intent beforehand. */
@Mixin(PlayerList.class)
public abstract class CarriagePlayerReferenceLogoutMixin {
    @WrapMethod(method="remove")
    private void create$saveExactOfflineParentIntent(ServerPlayer player,Operation<Void> original){try(var scope=CarriagePlayerReferenceTransitions.preserve(player,null)){original.call(player);}}
}
