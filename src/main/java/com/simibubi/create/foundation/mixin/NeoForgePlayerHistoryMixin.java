package com.simibubi.create.foundation.mixin;

import com.simibubi.create.foundation.utility.NeoForgePlayerHistory;
import com.simibubi.create.content.contraptions.ContraptionPassengerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Source history remains inert; current grounded state persists its real native attachment value. */
@Mixin(ServerPlayer.class)
public abstract class NeoForgePlayerHistoryMixin {
    @Unique private NeoForgePlayerHistory create$originalPlayerHistory;
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void create$readOriginalPlayerMetadata(ValueInput input,CallbackInfo callback){create$originalPlayerHistory=input.read(NeoForgePlayerHistory.KEY,NeoForgePlayerHistory.CODEC).orElse(null);if(input.getBooleanOr("CreatePassengerGrounded",false))ContraptionPassengerData.markGrounded((ServerPlayer)(Object)this);}
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void create$saveActualOriginalPlayerMetadata(ValueOutput output,CallbackInfo callback){if(create$originalPlayerHistory!=null)output.store(NeoForgePlayerHistory.KEY,NeoForgePlayerHistory.CODEC,create$originalPlayerHistory);if(ContraptionPassengerData.isGrounded((ServerPlayer)(Object)this))output.putBoolean("CreatePassengerGrounded",true);}
}
