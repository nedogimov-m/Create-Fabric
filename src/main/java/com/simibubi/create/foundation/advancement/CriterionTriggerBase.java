package com.simibubi.create.foundation.advancement;

import java.util.List;
import java.util.function.Supplier;
import com.simibubi.create.Create;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Native 26.3 owns the real player listener map, predicates, reload and award dispatch. */
public abstract class CriterionTriggerBase<T extends CriterionTriggerBase.Instance>
        extends SimpleCriterionTrigger<T> {
    private final Identifier id;

    public CriterionTriggerBase(String id) {
        this(Create.asResource(id));
    }

    protected CriterionTriggerBase(Identifier id) {
        this.id = id;
    }

    public Identifier getId() {
        return id;
    }

    protected void trigger(ServerPlayer player, @Nullable List<Supplier<Object>> suppliers) {
        super.trigger(player, instance -> instance.test(suppliers));
    }

    public abstract static class Instance implements SimpleCriterionTrigger.SimpleInstance {
        protected abstract boolean test(@Nullable List<Supplier<Object>> suppliers);
    }
}
