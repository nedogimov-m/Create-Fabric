package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.redstone.displayLink.DisplayTargetReservations;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla signs/lecterns and actual Create target BEs retain real ownership through ordinary native saves. */
@Mixin(BlockEntity.class)
public abstract class DisplayTargetReservationsMixin implements DisplayTargetReservations {
    @Unique private final Table create$displayOwners=new Table();
    @Override public Table create$displayReservations(){return create$displayOwners;}
    @Inject(method="saveAdditional",at=@At("TAIL"))private void create$writeDisplayOwners(ValueOutput out,CallbackInfo ci){create$displayOwners.write(out);}
    @Inject(method="loadAdditional",at=@At("TAIL"))private void create$readDisplayOwners(ValueInput in,CallbackInfo ci){create$displayOwners.read(in);}
}
