package com.simibubi.create.foundation.mixin.client;

import net.minecraft.client.gui.components.CommandSuggestions;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.class)
public interface ScheduleSuggestionsAccessor {
    @Accessor("suggestions") @Nullable CommandSuggestions.SuggestionsList create$getScheduleSuggestions();
    @Accessor("suggestions") void create$setScheduleSuggestions(@Nullable CommandSuggestions.SuggestionsList list);
}
