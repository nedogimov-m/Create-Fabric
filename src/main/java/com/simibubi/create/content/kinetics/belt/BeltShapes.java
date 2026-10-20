package com.simibubi.create.content.kinetics.belt;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import java.util.HashMap;
import java.util.Map;
import static net.minecraft.world.level.block.Block.box;

/** Original belt cross-sections, composed before rotating around the block centre. */
public final class BeltShapes {
    private static final Map<BlockState, VoxelShape> CACHE = new HashMap<>();
    private static VoxelShape halves(VoxelShape south, VoxelShape north) {
        return Shapes.or(Shapes.joinUnoptimized(box(0, -5, 8, 16, 21, 16), south, BooleanOp.AND),
                Shapes.joinUnoptimized(box(0, -5, 0, 16, 21, 8), north, BooleanOp.AND));
    }
    private static VoxelShape slope(boolean ascending) {
        var result = Shapes.empty();
        for (int i = 0; i < 16; i++) result = Shapes.or(result, box(1, 0, 15, 15, 11, 16).move(0, (ascending ? 10 - i : i - 5) / 16.0, -i / 16.0));
        return result;
    }
    private static VoxelShape rotate(VoxelShape shape, Direction.Axis axis, int quarterTurns) {
        for (int turn = 0; turn < Math.floorMod(quarterTurns, 4); turn++) {
            var boxes = new java.util.ArrayList<VoxelShape>();
            shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> boxes.add(switch (axis) {
                case X -> Shapes.box(x0, 1 - z1, y0, x1, 1 - z0, y1);
                case Y -> Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1);
                case Z -> Shapes.box(1 - y1, x0, z0, 1 - y0, x1, z1);
            }));
            shape = Shapes.or(Shapes.empty(), boxes.toArray(VoxelShape[]::new));
        }
        return shape;
    }
    public static VoxelShape getShape(BlockState state) { return CACHE.computeIfAbsent(state, BeltShapes::build); }
    public static VoxelShape getCollisionShape(BlockState state) {
        return Shapes.joinUnoptimized(getShape(state), box(0, 0, 0, 16, 19, 16), BooleanOp.AND);
    }
    private static VoxelShape build(BlockState state) {
        var slope = state.getValue(BeltBlock.SLOPE);
        var part = state.getValue(BeltBlock.PART);
        var facing = state.getValue(BeltBlock.HORIZONTAL_FACING);
        boolean middle = part == BeltPart.MIDDLE || part == BeltPart.PULLEY;
        var flat = box(1, 3, 0, 15, 13, 16);
        var end = Shapes.or(box(1, 4, 0, 15, 12, 16), box(1, 3, 1, 15, 13, 15));
        VoxelShape body;
        if (slope == BeltSlope.SIDEWAYS) {
            flat = box(3, 1, 0, 13, 15, 16);
            end = Shapes.or(box(4, 1, 0, 12, 15, 16), box(3, 1, 1, 13, 15, 15));
        }
        if (slope.isDiagonal()) body = slope(slope == BeltSlope.UPWARD);
        else body = flat;
        if (!middle) body = part == BeltPart.START ? halves(body, end) : halves(end, body);
        if (state.getValue(BeltBlock.CASING) && slope != BeltSlope.VERTICAL && slope != BeltSlope.SIDEWAYS) {
            VoxelShape casing = slope == BeltSlope.HORIZONTAL ? box(0, 0, 0, 16, 11, 16)
                    : (part == BeltPart.START && slope == BeltSlope.UPWARD || part == BeltPart.END && slope == BeltSlope.DOWNWARD)
                    ? box(0, 0, 0, 16, 11, 16) : box(0, 0, 5, 16, 11, 16);
            if (slope == BeltSlope.DOWNWARD) casing = rotate(casing, Direction.Axis.Y, 2);
            body = Shapes.or(body, casing);
        }
        if (slope == BeltSlope.VERTICAL) body = rotate(body, Direction.Axis.X, -1);
        if (slope == BeltSlope.VERTICAL && facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) body = rotate(body, Direction.Axis.X, 2);
        int yaw = switch (facing) { case SOUTH -> 0; case WEST -> 1; case NORTH -> 2; default -> 3; };
        return rotate(body, Direction.Axis.Y, yaw).optimize();
    }
    private BeltShapes() {}
}
