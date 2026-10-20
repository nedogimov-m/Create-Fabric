package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelStructuralBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

public class WaterWheelGameTests {
    private static final BlockPos WHEEL = new BlockPos(3, 3, 3);

    private static WaterWheelBlockEntity place(GameTestHelper helper) {
        helper.setBlock(WHEEL, AllBlocks.WATER_WHEEL.defaultBlockState().setValue(WaterWheelBlock.FACING, Direction.EAST));
        helper.setBlock(WHEEL.east(), AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        return helper.getBlockEntity(WHEEL, WaterWheelBlockEntity.class);
    }

    @GameTest(maxTicks = 40)
    public void dryWheelDoesNotGenerateRotation(GameTestHelper helper) {
        var wheel = place(helper);
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(wheel.getSpeed(), 0f, "Dry water wheel stops");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void bubbleFlowDrivesShaftAndSuppliesExpectedStress(GameTestHelper helper) {
        var wheel = place(helper);
        helper.setBlock(WHEEL.south().below(), Blocks.SOUL_SAND);
        helper.setBlock(WHEEL.south(), Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(Math.abs(wheel.getSpeed()), 8f, "Water wheel speed");
            helper.assertValueEqual(wheel.getStressCapacity(), 256.0, "Water wheel stress capacity");
            helper.assertValueEqual(helper.getBlockEntity(WHEEL.east(), KineticBlockEntity.class).getSpeed(), wheel.getSpeed(), "Shaft follows wheel");
        });
    }

    @GameTest(maxTicks = 40)
    public void woodMaterialPersists(GameTestHelper helper) {
        var wheel = place(helper);
        wheel.setMaterial(Blocks.BIRCH_PLANKS.defaultBlockState());
        var tag = wheel.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = (WaterWheelBlockEntity) BlockEntity.loadStatic(helper.absolutePos(WHEEL), wheel.getBlockState(), tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getMaterial().is(Blocks.BIRCH_PLANKS), "Custom wood material persists");
        helper.succeed();
    }

    private static LargeWaterWheelBlockEntity large(GameTestHelper helper, Direction.Axis axis) {
        helper.setBlock(WHEEL, AllBlocks.LARGE_WATER_WHEEL.defaultBlockState().setValue(LargeWaterWheelBlock.AXIS, axis));
        return helper.getBlockEntity(WHEEL, LargeWaterWheelBlockEntity.class);
    }

    @GameTest(maxTicks = 40)
    public void largeWheelBuildsEightLinkedCells(GameTestHelper helper) {
        large(helper, Direction.Axis.X);
        helper.succeedWhen(() -> LargeWaterWheelBlock.structure(WHEEL, Direction.Axis.X).forEach((pos, face) -> {
            var state = helper.getBlockState(pos);
            helper.assertTrue(state.is(AllBlocks.WATER_WHEEL_STRUCTURAL), "Large wheel reserves its whole plane");
            helper.assertValueEqual(state.getValue(WaterWheelStructuralBlock.FACING), face, "Structure points towards centre");
            helper.assertValueEqual(WaterWheelStructuralBlock.master(helper.getLevel(), helper.absolutePos(pos), state), helper.absolutePos(WHEEL), "Part resolves its centre");
        }));
    }

    @GameTest(maxTicks = 40)
    public void largeWheelUsesOuterFlowAnd512StressUnits(GameTestHelper helper) {
        var wheel = large(helper, Direction.Axis.X);
        helper.setBlock(WHEEL.east(), AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        helper.setBlock(WHEEL.south(2).below(), Blocks.SOUL_SAND);
        helper.setBlock(WHEEL.south(2), Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(Math.abs(wheel.getSpeed()), 4f, "Large wheel produces 4 RPM");
            helper.assertValueEqual(wheel.getStressCapacity(), 512.0, "Large wheel produces 512 SU");
            helper.assertValueEqual(helper.getBlockEntity(WHEEL.east(), KineticBlockEntity.class).getSpeed(), wheel.getSpeed(), "Large wheel drives shaft");
        });
    }

    @GameTest(maxTicks = 40)
    public void removingCornerRemovesWholeWheel(GameTestHelper helper) {
        large(helper, Direction.Axis.X);
        helper.runAfterDelay(5, () -> helper.getLevel().destroyBlock(helper.absolutePos(WHEEL.above().south()), true));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(helper.getBlockState(WHEEL).isAir(), "Breaking corner breaks centre");
            LargeWaterWheelBlock.structure(WHEEL, Direction.Axis.X).keySet().forEach(pos ->
                    helper.assertTrue(helper.getBlockState(pos).isAir(), "No orphan structural cell"));
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void removingCentreRemovesWholeWheel(GameTestHelper helper) {
        large(helper, Direction.Axis.Z);
        helper.runAfterDelay(5, () -> helper.getLevel().destroyBlock(helper.absolutePos(WHEEL), true));
        helper.runAfterDelay(10, () -> {
            LargeWaterWheelBlock.structure(WHEEL, Direction.Axis.Z).keySet().forEach(pos ->
                    helper.assertTrue(helper.getBlockState(pos).isAir(), "Removing master clears its cells"));
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void obstructedWheelPreservesExistingBlock(GameTestHelper helper) {
        helper.setBlock(WHEEL.above().south(), Blocks.DIAMOND_BLOCK);
        large(helper, Direction.Axis.X);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(helper.getBlockState(WHEEL).isAir(), "Obstructed wheel is removed");
            helper.assertTrue(helper.getBlockState(WHEEL.above().south()).is(Blocks.DIAMOND_BLOCK), "Obstruction is never overwritten");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void adjoiningLargeWheelsShareAxleAndUpdateExtension(GameTestHelper helper) {
        var first = large(helper, Direction.Axis.X);
        helper.setBlock(WHEEL.east(), AllBlocks.LARGE_WATER_WHEEL.defaultBlockState().setValue(LargeWaterWheelBlock.AXIS, Direction.Axis.X));
        helper.setBlock(WHEEL.south(2).below(), Blocks.SOUL_SAND);
        helper.setBlock(WHEEL.south(2), Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false));
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(helper.getBlockState(WHEEL.east()).getValue(LargeWaterWheelBlock.EXTENSION), "Adjacent wheel uses extension mesh");
            helper.assertValueEqual(helper.getBlockEntity(WHEEL.east(), LargeWaterWheelBlockEntity.class).getSpeed(), first.getSpeed(), "Adjacent wheel shares axle");
            helper.getLevel().destroyBlock(helper.absolutePos(WHEEL), false);
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(helper.getBlockState(WHEEL.east()).is(AllBlocks.LARGE_WATER_WHEEL), "Other wheel survives master removal");
            helper.assertTrue(!helper.getBlockState(WHEEL.east()).getValue(LargeWaterWheelBlock.EXTENSION), "Extension updates after neighbour removed");
            helper.succeed();
        });
    }
}
