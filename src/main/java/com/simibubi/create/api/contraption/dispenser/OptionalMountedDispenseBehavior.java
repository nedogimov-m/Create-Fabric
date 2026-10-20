package com.simibubi.create.api.contraption.dispenser;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A nullable execution result means failure; each invocation owns its sound result. */
public class OptionalMountedDispenseBehavior extends DefaultMountedDispenseBehavior {
    // Behaviours are shared registry values and may invoke another dispenser recursively.
    private final ThreadLocal<ArrayDeque<Boolean>> outcomes = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public ItemStack dispense(ItemStack stack, MovementContext context, BlockPos pos) {
        var calls = outcomes.get();
        calls.push(false);
        try {
            return super.dispense(stack, context, pos);
        } finally {
            calls.pop();
            if (calls.isEmpty()) outcomes.remove();
        }
    }

    @Override
    protected final ItemStack execute(ItemStack stack, MovementContext context, BlockPos pos, Vec3 facing) {
        var result = doExecute(stack, context, pos, facing);
        var calls = outcomes.get();
        calls.pop();
        calls.push(result != null);
        return result == null ? stack : result;
    }

    @Override
    protected void playSound(LevelAccessor level, BlockPos pos) {
        if (outcomes.get().peek()) super.playSound(level, pos);
        else level.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL, pos, 0);
    }

    protected @Nullable ItemStack doExecute(ItemStack stack, MovementContext context, BlockPos pos, Vec3 facing) {
        return super.execute(stack, context, pos, facing);
    }
}
