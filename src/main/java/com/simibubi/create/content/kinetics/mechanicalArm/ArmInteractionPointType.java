package com.simibubi.create.content.kinetics.mechanicalArm;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Addons register actual target factories in Create's synchronized Fabric registry. */
public abstract class ArmInteractionPointType {
    private static final List<ArmInteractionPointType> SORTED_TYPES = new ArrayList<>();
    public static final List<ArmInteractionPointType> SORTED_TYPES_VIEW = Collections.unmodifiableList(SORTED_TYPES);
    public static void init() {
        SORTED_TYPES.clear();
        CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.forEach(SORTED_TYPES::add);
        SORTED_TYPES.sort(Comparator.comparingInt(ArmInteractionPointType::getPriority).reversed());
    }
    @Nullable public static ArmInteractionPointType getPrimaryType(Level level, BlockPos pos, BlockState state) {
        if (SORTED_TYPES.size() != CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.size()) init();
        for (var type : SORTED_TYPES) if (type.canCreatePoint(level, pos, state)) return type;
        return null;
    }
    public abstract boolean canCreatePoint(Level level, BlockPos pos, BlockState state);
    @Nullable public abstract ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state);
    public int getPriority() { return 0; }
}
