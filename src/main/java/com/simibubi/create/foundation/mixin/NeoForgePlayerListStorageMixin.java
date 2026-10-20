package com.simibubi.create.foundation.mixin;

import com.simibubi.create.foundation.mixin.accessor.PlayerDataStorageAccessor;
import com.simibubi.create.foundation.utility.NeoForgePlayerStorageImport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class NeoForgePlayerListStorageMixin {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private PlayerDataStorage playerIo;
    @Inject(method="<init>",at=@At("RETURN"))
    private void create$bindActualPlayerStorage(CallbackInfo callback) {
        NeoForgePlayerStorageImport.bind(playerIo, server, ((PlayerDataStorageAccessor)playerIo).create$getPlayerDirectory());
    }
}
