package com.simibubi.create.foundation.mixin.client;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.List;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CommandSuggestions.SuggestionsList.class)
public interface ScheduleSuggestionsListInvoker {
    @Invoker("<init>")
    static CommandSuggestions.SuggestionsList create$scheduleList(CommandSuggestions owner, int x, int y, int width,
                                                                  List<Suggestion> suggestions, boolean narrateFirst) {
        throw new AssertionError("Native CommandSuggestions.SuggestionsList constructor invoker was not transformed");
    }
}
