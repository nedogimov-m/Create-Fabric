package com.simibubi.create.content.kinetics.base;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class BlockBreakingKineticBlockEntity extends KineticBlockEntity {
    private static final AtomicInteger NEXT_BREAKER = new AtomicInteger();
    public static final TagKey<net.minecraft.world.level.block.Block> NON_BREAKABLE = TagKey.create(Registries.BLOCK, Create.asResource("non_breakable"));
    public static int nextBreakerId(){return -NEXT_BREAKER.incrementAndGet();}
    private final int breakerId = nextBreakerId();
    protected int ticksUntilNextProgress, destroyProgress;
    protected BlockPos breakingPos;
    private BlockState breakingState;
    protected BlockBreakingKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    protected abstract BlockPos getBreakingPos();
    protected boolean shouldRun() { return true; }
    public int getDestroyProgress() { return destroyProgress; }
    public void destroyNextTick() { ticksUntilNextProgress = 1; }
    public static boolean isBreakable(BlockState state, float hardness) { return !state.isAir() && !state.liquid() && hardness >= 0 && !state.is(NON_BREAKABLE); }
    public boolean canBreak(BlockState state, float hardness) { return isBreakable(state, hardness); }
    protected float getBreakSpeed() { return Math.abs(getSpeed() / 100f); }
    private void clearProgress() {
        if (level instanceof ServerLevel server && breakingPos != null && destroyProgress != 0) server.destroyBlockProgress(breakerId, breakingPos, -1);
        destroyProgress = 0; breakingState = null;
    }
    protected void tickBreaking() {
        if (!(level instanceof ServerLevel server)) return;
        if (!shouldRun()) { clearProgress(); return; }
        if (getSpeed() == 0) return;
        var next = getBreakingPos(); if (!level.isLoaded(next)) return;
        var state = level.getBlockState(next);
        if (!next.equals(breakingPos) || breakingState != null && state != breakingState) { clearProgress(); ticksUntilNextProgress = 0; }
        breakingPos = next; breakingState = state;
        if (ticksUntilNextProgress < 0) { if (level.getGameTime() % 20 != 0) return; destroyNextTick(); }
        if (ticksUntilNextProgress-- > 0) return;
        float hardness = state.getDestroySpeed(level, next);
        if (!canBreak(state, hardness)) { clearProgress(); ticksUntilNextProgress = 20; return; }
        float speed = getBreakSpeed();
        destroyProgress += Math.clamp(hardness == 0 ? 10 : (int) (speed / hardness), 1, 10 - destroyProgress);
        level.playSound(null, worldPosition, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.25f, 1);
        if (destroyProgress >= 10) {
            onBlockBroken(state); clearProgress(); ticksUntilNextProgress = -1; setChanged(); return;
        }
        ticksUntilNextProgress = (int) (hardness / speed); server.destroyBlockProgress(breakerId, next, destroyProgress); setChanged();
    }
    public void onBlockBroken(BlockState state) {
        if (!(level instanceof ServerLevel server)) return;
        var random = server.getRandom();
        var pos = Vec3.atCenterOf(breakingPos).add((random.nextDouble() - 0.5) * 0.25, (random.nextDouble() - 0.5) * 0.25, (random.nextDouble() - 0.5) * 0.25);
        BlockHelper.destroyBlock(server, breakingPos, 1, stack -> {
            if (stack.isEmpty()) return;
            var entity = new ItemEntity(level, pos.x, pos.y, pos.z, stack, 0, 0, 0); entity.setDefaultPickUpDelay(); level.addFreshEntity(entity);
        });
    }
    @Override public void invalidate() { clearProgress(); super.invalidate(); }
    @Override protected void write(ValueOutput out, boolean clientPacket) {
        super.write(out, clientPacket); out.putInt("Progress", destroyProgress); out.putInt("NextTick", ticksUntilNextProgress);
        if (breakingPos != null) out.store("Breaking", BlockPos.CODEC, breakingPos);
        if (breakingState != null) out.store("BreakingState", BlockState.CODEC, breakingState);
    }
    @Override protected void read(ValueInput in, boolean clientPacket) {
         destroyProgress = Math.clamp(in.getIntOr("Progress", 0), 0, 9); ticksUntilNextProgress = Math.max(-1, in.getIntOr("NextTick", 0));
        breakingPos = in.read("Breaking", BlockPos.CODEC).orElse(null); breakingState = in.read("BreakingState", BlockState.CODEC).orElse(null);
    
        super.read(in, clientPacket);
    }
}
