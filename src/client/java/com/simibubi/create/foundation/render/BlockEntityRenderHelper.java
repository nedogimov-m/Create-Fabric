package com.simibubi.create.foundation.render;

import com.simibubi.create.Create;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source virtual-render failure isolation over detached native extraction and submit states. */
public final class BlockEntityRenderHelper {
    public static final class FailureState {
        private final String type;
        private final AtomicBoolean failed = new AtomicBoolean();

        public FailureState(BlockEntity owner) {
            type = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType()));
        }

        public boolean hasFailed() { return failed.get(); }
        public void skip() { failed.set(true); }

        public void report(Exception exception, boolean explain) {
            if (!failed.compareAndSet(false, true)) return;
            String message = "BlockEntity " + type + " could not be rendered virtually.";
            if (explain) Create.LOGGER.error(message, exception);
            else Create.LOGGER.error(message);
        }
    }

    private BlockEntityRenderHelper() {}
}
