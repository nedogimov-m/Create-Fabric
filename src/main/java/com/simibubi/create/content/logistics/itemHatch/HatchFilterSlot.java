package com.simibubi.create.content.logistics.itemHatch;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
/** Pinned 8/5.15/9.5 voxel position, source yaw,45-degree pitch and .965 scale factor. */
public final class HatchFilterSlot {
 public static final float SCALE=.5f*.965f;
 public static float angle(BlockState state){var facing=state.getValue(ItemHatchBlock.FACING);return facing.getAxis()==Direction.Axis.X?-facing.toYRot():facing.toYRot();}
 public static Vec3 position(BlockState state){return new Vec3(8/16d,5.15/16d,9.5/16d).subtract(.5,.5,.5).yRot((float)Math.toRadians(angle(state))).add(.5,.5,.5);}
 public static boolean testHit(BlockState state,BlockPos pos,BlockHitResult hit){return hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)).distanceTo(position(state).subtract(0,.125,0))<SCALE/2;}
 private HatchFilterSlot(){}
}
