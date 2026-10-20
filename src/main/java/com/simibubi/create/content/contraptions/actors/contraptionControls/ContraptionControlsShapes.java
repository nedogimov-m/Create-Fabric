package com.simibubi.create.content.contraptions.actors.contraptionControls;

import java.util.EnumMap;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source north-facing boxes, rotated without reading or loading the surrounding world. */
final class ContraptionControlsShapes {
    private static final EnumMap<Direction, VoxelShape> OUTLINE = new EnumMap<>(Direction.class);
    private static final EnumMap<Direction, VoxelShape> COLLISION = new EnumMap<>(Direction.class);
    static {
        var sides = Shapes.or(box(0, 0, 6, 2, 16, 16), box(14, 0, 6, 16, 16, 16), box(0, 0, 14, 16, 16, 16));
        var outline = Shapes.or(sides, box(0, 0, 6, 16, 12, 16), box(0, 0, 4, 16, 2, 16));
        var collision = Shapes.or(sides, box(0, 0, 7, 16, 12, 16));
        for (var facing : Direction.Plane.HORIZONTAL) {
            OUTLINE.put(facing, rotate(outline, facing));
            COLLISION.put(facing, rotate(collision, facing));
        }
    }
    private static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }
    private static VoxelShape rotate(VoxelShape source, Direction facing) {
        if (facing == Direction.NORTH) return source;
        var result = new VoxelShape[]{Shapes.empty()};
        source.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            var next = switch (facing) {
                case EAST -> Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2);
                case SOUTH -> Shapes.box(1 - x2, y1, 1 - z2, 1 - x1, y2, 1 - z1);
                case WEST -> Shapes.box(z1, y1, 1 - x2, z2, y2, 1 - x1);
                default -> throw new IllegalArgumentException("Horizontal controls facing required");
            };
            result[0] = Shapes.or(result[0], next);
        });
        return result[0];
    }
    static VoxelShape outline(Direction facing) { return OUTLINE.get(facing); }
    static VoxelShape collision(Direction facing) { return COLLISION.get(facing); }
    private ContraptionControlsShapes() {}
}
