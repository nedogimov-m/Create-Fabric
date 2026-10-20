package com.simibubi.create.foundation.item;

import com.simibubi.create.Create;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;

/** Source burn durations use native context providers so fast furnaces retain their real fuel reduction. */
public final class CreateCookingFuels {
    public static CookingFuel component(String name) {
        return new CookingFuel(ResourceKey.create(Registries.CONTEXT_INT_PROVIDER,
            Create.asResource("cooking/time_" + name)), ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER);
    }
    private CreateCookingFuels() {}
}
