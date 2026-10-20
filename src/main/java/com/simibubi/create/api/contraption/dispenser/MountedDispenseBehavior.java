package com.simibubi.create.api.contraption.dispenser;

import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.behaviour.dispenser.MountedDispenseExecution;
import com.simibubi.create.impl.contraption.dispenser.DispenserBehaviorConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

/** Public, provider-extensible counterpart of vanilla's dispenser behaviour registry. */
@FunctionalInterface
public interface MountedDispenseBehavior {
    SimpleRegistry<Item, MountedDispenseBehavior> REGISTRY = createRegistry();

    private static SimpleRegistry<Item, MountedDispenseBehavior> createRegistry() {
        var registry = SimpleRegistry.<Item, MountedDispenseBehavior>create();
        registry.registerProvider(DispenserBehaviorConverter.INSTANCE);
        return registry;
    }

    /** The stack is detached from storage. Return its remaining contents after one world action. */
    ItemStack dispense(ItemStack stack, MovementContext context, BlockPos pos);

    static Vec3 getDispenserNormal(MovementContext context) {
        var facing = context.state.getValue(DispenserBlock.FACING);
        return context.rotation.apply(Vec3.atLowerCornerOf(facing.getUnitVec3i())).normalize();
    }

    static Direction getClosestFacingDirection(Vec3 facing) {
        return Direction.getApproximateNearest(facing.x, facing.y, facing.z);
    }

    /**
     * Return a remainder to private slots, then shared cargo, then the world.
     * Only the part that did not fit in either storage is dropped. During an actor invocation,
     * its selected slot is reserved for the result returned by {@link #dispense}.
     */
    static void placeItemInInventory(ItemStack stack, MovementContext context, BlockPos pos) {
        MountedDispenseExecution.placeItemInInventory(stack, context, pos);
    }
}
