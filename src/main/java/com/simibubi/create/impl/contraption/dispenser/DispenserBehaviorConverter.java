package com.simibubi.create.impl.contraption.dispenser;

import com.simibubi.create.Create;
import com.simibubi.create.api.contraption.dispenser.DefaultMountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedProjectileDispenseBehavior;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.mixin.accessor.DispenserBlockAccessor;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

/** A cached registry value resolves vanilla behaviour against the actual stack and current world. */
public enum DispenserBehaviorConverter implements SimpleRegistry.Provider<Item, MountedDispenseBehavior> {
    INSTANCE;

    public static final TagKey<Item> WRAP_BLACKLIST = TagKey.create(Registries.ITEM,
        Create.asResource("dispense_behavior_wrap_blacklist"));
    private static final MountedDispenseBehavior DYNAMIC = new DynamicBehaviour();

    @Override public MountedDispenseBehavior get(Item item) { return DYNAMIC; }

    @Override public void onRegister(Runnable invalidate) {
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> invalidate.run());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> invalidate.run());
    }

    private static final class DynamicBehaviour implements MountedDispenseBehavior {
        @Override public ItemStack dispense(ItemStack stack, MovementContext context, BlockPos pos) {
            if (!(context.world instanceof ServerLevel)) return stack;
            if (stack.is(WRAP_BLACKLIST)) return DefaultMountedDispenseBehavior.INSTANCE.dispense(stack, context, pos);
            var vanilla = ((DispenserBlockAccessor) Blocks.DISPENSER)
                .create$callGetDispenseMethod(context.world, stack);
            if (vanilla == DispenseItemBehavior.NOOP) return stack;
            if (vanilla.getClass() == DefaultDispenseItemBehavior.class)
                return DefaultMountedDispenseBehavior.INSTANCE.dispense(stack, context, pos);
            if (vanilla instanceof ProjectileDispenseBehavior projectile)
                return MountedProjectileDispenseBehavior.of(projectile).dispense(stack, context, pos);
            return new NativeFallback(vanilla).dispense(stack, context, pos);
        }
    }

    private static final class NativeFallback extends DefaultMountedDispenseBehavior {
        private static final String FAILED = "FailedNativeDispenseHandler";
        private final DispenseItemBehavior wrapped;
        private NativeFallback(DispenseItemBehavior wrapped) { this.wrapped = wrapped; }

        @Override protected ItemStack executeWithEvents(ItemStack stack, MovementContext context, BlockPos pos,
                                                         Vec3 facing, Direction direction) {
            if (!(context.world instanceof ServerLevel level)) return stack;
            var handlerId = wrapped.getClass().getName();
            if (handlerId.equals(context.data.getStringOr(FAILED, ""))) return stack;
            // Native behaviours can query the facing cell's neighbors (boats, equipment, plants).
            // Do not ask vanilla to cross an unavailable block/entity boundary.
            int chunkX = pos.getX() >> 4, chunkZ = pos.getZ() >> 4;
            for (int x = chunkX - 1; x <= chunkX + 1; x++)
                for (int z = chunkZ - 1; z <= chunkZ + 1; z++)
                    if (!LoadedEntityAccess.isReady(level, x, z)) return stack;
            var state = context.state.setValue(DispenserBlock.FACING, direction);
            var inventory = new MountedDispenserBlockEntity(pos, state, context, stack);
            inventory.setLevel(level);
            var source = new BlockSource(level, pos, state, inventory);
            try {
                var result = wrapped.dispense(source, stack);
                if (result == null) throw new IllegalStateException("Native dispense returned null");
                return result;
            } catch (RuntimeException exception) {
                // No rollback claim: a native/addon handler may already have changed the world.
                // Keep its actual stack/inventory mutations and persist a refusal to repeat it.
                context.data.putString(FAILED, handlerId);
                Create.LOGGER.error("Native dispenser handler {} failed on moving dispenser at {}; refusing retries",
                    handlerId, pos, exception);
                return stack;
            } finally {
                inventory.flush();
            }
        }
    }
}
