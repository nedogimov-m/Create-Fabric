package com.simibubi.create.foundation.data.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

/** Resource predicates use the actual reload lookup rather than stale globally bound tags. */
public final class CreateResourceConditions {
    public static final ResourceConditionType<ItemTagEmpty> ITEM_TAG_EMPTY =
        ResourceConditionType.create(Create.asResource("item_tag_empty"), ItemTagEmpty.CODEC);
    private static boolean registered;

    public static void register() {
        if (registered) return;
        ResourceConditions.register(ITEM_TAG_EMPTY);
        registered = true;
    }

    /** A present empty tag is empty too; Fabric tags_populated only checks tag presence. */
    public record ItemTagEmpty(Identifier tag) implements ResourceCondition {
        public static final MapCodec<ItemTagEmpty> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(Identifier.CODEC.fieldOf("tag").forGetter(ItemTagEmpty::tag))
                .apply(instance, ItemTagEmpty::new));

        @Override
        public ResourceConditionType<?> getType() {
            return ITEM_TAG_EMPTY;
        }

        @Override
        public boolean test(RegistryOps.@Nullable RegistryInfoLookup lookup) {
            // The source compat conditions negate this predicate. No lookup admits no optional ore recipe.
            return lookup == null || lookup.lookup(Registries.ITEM)
                .map(items -> PendingItemTagContents.isEmpty(items, TagKey.create(Registries.ITEM, tag)))
                .orElse(true);
        }
    }

    private CreateResourceConditions() {}
}
