package com.simibubi.create.foundation.mixin;

import com.simibubi.create.foundation.utility.NeoForgeLivingHistory;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact source evidence persists beside current native living state without becoming active state. */
@Mixin(LivingEntity.class)
public abstract class NeoForgeLivingHistoryMixin implements NeoForgeLivingHistory.Holder {
    @Unique private NeoForgeLivingHistory create$originalLivingHistory;
    @Override public Optional<NeoForgeLivingHistory> create$originalLivingHistory() { return Optional.ofNullable(create$originalLivingHistory); }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void create$readOriginalLivingHistory(ValueInput input, CallbackInfo callback) {
        create$originalLivingHistory = input.read(NeoForgeLivingHistory.KEY, NeoForgeLivingHistory.CODEC).orElse(null);
        if (create$originalLivingHistory != null && create$originalLivingHistory.originalEntity().isPresent()
                && !create$originalLivingHistory.originalEntity().get().equals(((LivingEntity) (Object) this).getUUID()))
            throw new IllegalStateException("Original complete living history belongs to another saved entity UUID");
    }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void create$saveOriginalLivingHistory(ValueOutput output, CallbackInfo callback) {
        if (create$originalLivingHistory != null) output.store(NeoForgeLivingHistory.KEY, NeoForgeLivingHistory.CODEC, create$originalLivingHistory);
    }
}
