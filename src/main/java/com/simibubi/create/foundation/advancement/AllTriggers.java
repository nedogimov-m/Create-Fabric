package com.simibubi.create.foundation.advancement;

import java.util.LinkedHashMap;
import java.util.Map;
import com.simibubi.create.Create;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** One registered source trigger identity is shared by source and native device award hooks. */
public final class AllTriggers {
    private static final Map<Identifier, CriterionTriggerBase<?>> triggers = new LinkedHashMap<>();

    public static SimpleCreateTrigger addSimple(String id) {
        return simple(Create.asResource(id));
    }

    private static SimpleCreateTrigger simple(Identifier id) {
        var trigger = triggers.computeIfAbsent(id, SimpleCreateTrigger::new);
        if (!(trigger instanceof SimpleCreateTrigger simple))
            throw new IllegalStateException("Create trigger has another actual type: " + id);
        return simple;
    }

    public static SimpleCreateTrigger registerSimple(Identifier id) {
        var trigger = simple(id);
        register(trigger);
        return trigger;
    }

    private static void register(CriterionTriggerBase<?> trigger) {
        var actual = BuiltInRegistries.TRIGGER_TYPES.getValue(trigger.getId());
        if (actual == trigger)
            return;
        if (BuiltInRegistries.TRIGGER_TYPES.containsKey(trigger.getId()))
            throw new IllegalStateException("A different trigger already owns " + trigger.getId());
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, trigger.getId(), trigger);
    }

    public static void register() {
        for (var trigger : triggers.values())
            register(trigger);
    }

    private AllTriggers() {}
}
