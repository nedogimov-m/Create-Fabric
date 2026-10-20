package com.simibubi.create.api.event;

import java.util.Map;
import java.util.function.Consumer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Fired on the concrete native owner before its first behaviour read or initialization. */
public final class BlockEntityBehaviourEvent {
    @FunctionalInterface
    public interface Callback {
        void onBehaviours(BlockEntityBehaviourEvent event);
    }

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class,
        listeners -> event -> {
            for (Callback listener : listeners)
                listener.onBehaviours(event);
        });

    private final SmartBlockEntity smartBlockEntity;
    private final Map<BehaviourType<?>, BlockEntityBehaviour> behaviours;

    public BlockEntityBehaviourEvent(SmartBlockEntity blockEntity,
            Map<BehaviourType<?>, BlockEntityBehaviour> behaviours) {
        this.smartBlockEntity = blockEntity;
        this.behaviours = behaviours;
    }

    public SmartBlockEntity getBlockEntity() {
        return smartBlockEntity;
    }

    @SuppressWarnings("unchecked")
    public <T extends SmartBlockEntity> void forType(BlockEntityType<T> type, Consumer<T> action) {
        if (smartBlockEntity.getType() == type)
            action.accept((T) smartBlockEntity);
    }

    public void attach(BlockEntityBehaviour behaviour) {
        if (behaviour.blockEntity != smartBlockEntity)
            throw new IllegalArgumentException("An attached behaviour must belong to the actual event owner");
        behaviours.put(behaviour.getType(), behaviour);
    }

    public BlockEntityBehaviour remove(BehaviourType<?> type) {
        return behaviours.remove(type);
    }
}
