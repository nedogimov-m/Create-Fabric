package com.simibubi.create.content.kinetics.belt;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BeltHelper {
    public static BeltBlockEntity getSegmentBE(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BeltBlockEntity belt ? belt : null;
    }
    public static BeltBlockEntity getControllerBE(Level level, BlockPos pos) {
        var belt = getSegmentBE(level, pos);
        return belt == null ? null : getSegmentBE(level, belt.getController());
    }
    public static BlockPos getPositionForOffset(BeltBlockEntity controller, int offset) {
        var state = controller.getBlockState();
        var facing = state.getValue(BeltBlock.HORIZONTAL_FACING);
        var slope = state.getValue(BeltBlock.SLOPE);
        if (slope == BeltSlope.VERTICAL) return controller.getBlockPos().above(offset * facing.getAxisDirection().getStep());
        int vertical = slope == BeltSlope.UPWARD ? 1 : slope == BeltSlope.DOWNWARD ? -1 : 0;
        return controller.getBlockPos().relative(facing, offset).above(vertical * Math.clamp(offset, 0, Math.max(0, controller.beltLength - 1)));
    }
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> UPRIGHT = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, com.simibubi.create.Create.asResource("upright_on_belt"));
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> NOT_UPRIGHT = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, com.simibubi.create.Create.asResource("not_upright_on_belt"));
    public static boolean isItemUpright(net.minecraft.world.item.ItemStack stack) {
        if (stack.is(NOT_UPRIGHT)) return false;
        return stack.is(UPRIGHT) || net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.withConstant(stack)
                .find(net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.ITEM) != null;
    }
    public static Vec3 getVectorForOffset(BeltBlockEntity controller, float offset) {
        var state = controller.getBlockState();
        var facing = state.getValue(BeltBlock.HORIZONTAL_FACING);
        var slope = state.getValue(BeltBlock.SLOPE);
        int vertical = slope == BeltSlope.UPWARD ? 1 : slope == BeltSlope.DOWNWARD ? -1 : 0;
        double y = vertical * Math.clamp(offset - 0.5, 0, Math.max(0, controller.beltLength - 1));
        if (slope == BeltSlope.VERTICAL) return Vec3.atCenterOf(controller.getBlockPos()).add(0, (offset - 0.5) * facing.getAxisDirection().getStep(), 0);
        return Vec3.atCenterOf(controller.getBlockPos()).add(facing.getStepX() * (offset - 0.5), y, facing.getStepZ() * (offset - 0.5));
    }
    private BeltHelper() {}
}
