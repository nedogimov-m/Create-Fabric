package com.simibubi.create.foundation.mixin;

import java.util.List;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact source checks the real beam section list, including base-less beacons; no invented powered bit. */
@Mixin(BeaconBlockEntity.class)
public interface LegacyBeaconAccessor {
 @Accessor("beamSections") List<?> create$legacyBeamSections();
}
