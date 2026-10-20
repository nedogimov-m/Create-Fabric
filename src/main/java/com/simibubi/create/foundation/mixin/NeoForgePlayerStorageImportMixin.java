package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.utility.NeoForgePlayerStorageImport;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerDataStorage.class)
public abstract class NeoForgePlayerStorageImportMixin {
    @WrapMethod(method="load(Lnet/minecraft/server/players/NameAndId;)Ljava/util/Optional;")
    private Optional<CompoundTag> create$actualFilenameOwner(NameAndId owner, Operation<Optional<CompoundTag>> original) {
        return NeoForgePlayerStorageImport.load((PlayerDataStorage)(Object)this, owner, () -> original.call(owner));
    }
    @WrapOperation(method={"load(Lnet/minecraft/server/players/NameAndId;)Ljava/util/Optional;", "lambda$load$0"},
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/storage/PlayerDataStorage;load(Lnet/minecraft/server/players/NameAndId;Ljava/lang/String;)Ljava/util/Optional;"), require=2)
    private Optional<CompoundTag> create$actualRawFile(PlayerDataStorage storage, NameAndId owner, String suffix,
            Operation<Optional<CompoundTag>> original) {
        return NeoForgePlayerStorageImport.raw(storage, owner, suffix, () -> original.call(storage,owner,suffix));
    }
    @Inject(method="lambda$load$1",at=@At("HEAD"),cancellable=true)
    private void create$prepareBeforeNativePlayerDfu(CompoundTag raw, CallbackInfoReturnable<CompoundTag> callback) {
        var converted = NeoForgePlayerStorageImport.convert((PlayerDataStorage)(Object)this, raw);
        if (converted != null) callback.setReturnValue(converted);
    }
    @Inject(method="save",at=@At("HEAD"),cancellable=true)
    private void create$fenceBeforePlayerSerialization(Player player, CallbackInfo callback) {
        if (!NeoForgePlayerStorageImport.saveAllowed((PlayerDataStorage)(Object)this)) callback.cancel();
    }
}
