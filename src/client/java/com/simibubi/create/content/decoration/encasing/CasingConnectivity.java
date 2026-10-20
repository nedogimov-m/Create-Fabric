package com.simibubi.create.content.decoration.encasing;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Source casing identity and exact per-state/per-face predicates, using native material descriptors. */
public final class CasingConnectivity {
    private final Map<Block,Entry> entries = new IdentityHashMap<>();
    public Entry get(BlockState state) { return entries.get(state.getBlock()); }
    public void makeCasing(Block block,CTSpriteShiftEntry casing) { make(block,casing); }
    public void make(Block block,CTSpriteShiftEntry casing) { make(block,casing,(state,face) -> true); }
    public void make(Block block,CTSpriteShiftEntry casing,BiPredicate<BlockState,Direction> predicate) { entries.put(block,new Entry(casing,predicate)); }
    public record Entry(CTSpriteShiftEntry casing,BiPredicate<BlockState,Direction> predicate) {
        public CTSpriteShiftEntry getCasing() { return casing; }
        public boolean isSideValid(BlockState state,Direction face) { return predicate.test(state,face); }
    }
}
