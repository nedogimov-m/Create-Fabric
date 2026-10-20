package com.simibubi.create.foundation.advancement;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.Nullable;

public class SimpleCreateTrigger extends CriterionTriggerBase<SimpleCreateTrigger.Instance> {
    public SimpleCreateTrigger(String id) { super(id); }
    public SimpleCreateTrigger(Identifier id) { super(id); }
    public void trigger(ServerPlayer player) { super.trigger(player, (List<Supplier<Object>>) null); }
    public Instance instance() { return new Instance(); }
    @Override public Codec<Instance> codec() { return Instance.CODEC; }

    public static class Instance extends CriterionTriggerBase.Instance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player)
        ).apply(instance, Instance::new));
        private final Optional<Holder<LootItemCondition>> player;
        public Instance() { this(Optional.empty()); }
        public Instance(Optional<Holder<LootItemCondition>> player) { this.player = player; }
        @Override protected boolean test(@Nullable List<Supplier<Object>> suppliers) { return true; }
        @Override public Optional<Holder<LootItemCondition>> player() { return player; }
    }
}
