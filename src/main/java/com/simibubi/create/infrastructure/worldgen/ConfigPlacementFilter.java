package com.simibubi.create.infrastructure.worldgen;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.infrastructure.config.CWorldGen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.*;
/** Source filter, now a genuine polymorphic native26.3 PlacementFilter. */
public class ConfigPlacementFilter implements PlacementFilter {
    public static final ConfigPlacementFilter INSTANCE=new ConfigPlacementFilter();
    public static final MapCodec<ConfigPlacementFilter> CODEC=MapCodec.unit(()->INSTANCE);
    @Override public boolean shouldPlace(PlacementContext context,RandomSource random,BlockPos pos){return !CWorldGen.get().disableWorldGen();}
    @Override public MapCodec<ConfigPlacementFilter> codec(){return CODEC;}
}
