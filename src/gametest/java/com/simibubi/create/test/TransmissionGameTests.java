package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.transmission.GearshiftBlock;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public class TransmissionGameTests {
    private static final BlockPos MOTOR = new BlockPos(1, 2, 1);
    private static final BlockPos BOX = new BlockPos(2, 2, 1);
    private static final BlockPos OUT = new BlockPos(3, 2, 1);

    private static KineticBlockEntity entity(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, KineticBlockEntity.class);
    }

    private static void motor(GameTestHelper helper) {
        helper.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        helper.setBlock(OUT, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
    }

    @GameTest(maxTicks = 40)
    public void gearshiftReversesAndRestoresWithRedstone(GameTestHelper helper) {
        motor(helper);
        helper.setBlock(BOX, AllBlocks.GEARSHIFT.defaultBlockState().setValue(GearshiftBlock.AXIS, Direction.Axis.X));
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(entity(helper, OUT).getSpeed(), 16f, "Unpowered gearshift");
            helper.setBlock(BOX.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(entity(helper, OUT).getSpeed(), -16f, "Powered gearshift");
            helper.assertValueEqual(entity(helper, BOX).getPortSpeed(Direction.WEST), 16f, "Gearshift input port");
            helper.setBlock(BOX.above(), Blocks.AIR);
        });
        helper.runAfterDelay(15, () -> {
            helper.assertValueEqual(entity(helper, OUT).getSpeed(), 16f, "Restored gearshift");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void clutchSeparatesItsTwoSides(GameTestHelper helper) {
        motor(helper);
        helper.setBlock(BOX, AllBlocks.CLUTCH.defaultBlockState().setValue(GearshiftBlock.AXIS, Direction.Axis.X));
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(entity(helper, OUT).getSpeed(), 16f, "Engaged clutch");
            helper.setBlock(BOX.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(entity(helper, OUT).getSpeed(), 0f, "Clutch output must stop");
            helper.assertValueEqual(entity(helper, BOX).getPortSpeed(Direction.WEST), 16f, "Clutch input keeps turning");
            helper.assertValueEqual(entity(helper, BOX).getPortSpeed(Direction.EAST), 0f, "Clutch output half stops");
            helper.setBlock(BOX.above(), Blocks.AIR);
        });
        helper.runAfterDelay(15, () -> {
            helper.assertValueEqual(entity(helper, OUT).getSpeed(), 16f, "Clutch reconnects");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void gearboxReproducesInputRelativeSigns(GameTestHelper helper) {
        motor(helper);
        helper.setBlock(BOX, AllBlocks.GEARBOX.defaultBlockState());
        var north = BOX.north();
        var south = BOX.south();
        helper.setBlock(north, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
        helper.setBlock(south, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(entity(helper, OUT).getSpeed(), -16f, "Opposite gearbox shaft");
            helper.assertValueEqual(entity(helper, north).getSpeed(), -16f, "Same-sign perpendicular gearbox shaft");
            helper.assertValueEqual(entity(helper, south).getSpeed(), 16f, "Opposite-sign perpendicular gearbox shaft");
        });
    }

    @GameTest(maxTicks = 40)
    public void verticalGearboxTransmitsToVerticalShaft(GameTestHelper helper) {
        motor(helper);
        helper.setBlock(BOX, AllBlocks.GEARBOX.defaultBlockState().setValue(GearboxBlock.AXIS, Direction.Axis.Z));
        helper.setBlock(BOX.above(), AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Y));
        helper.succeedWhen(() -> helper.assertValueEqual(entity(helper, BOX.above()).getSpeed(), 16f, "Vertical output"));
    }

    @GameTest(maxTicks = 40)
    public void overloadedNetworkRetainsTheoreticalRpmAndRecovers(GameTestHelper helper) {
        var cog = new BlockPos(1, 2, 3);
        var crank = cog.above();
        helper.setBlock(cog, AllBlocks.COGWHEEL.defaultBlockState());
        helper.setBlock(crank, AllBlocks.HAND_CRANK.defaultBlockState().setValue(HandCrankBlock.FACING, Direction.UP));
        helper.setBlock(cog.east(), AllBlocks.MILLSTONE);
        helper.setBlock(cog.east(2), AllBlocks.MILLSTONE);
        helper.setBlock(cog.east(3), AllBlocks.MILLSTONE);
        entity(helper, crank).turnCrank(false);
        helper.runAfterDelay(4, () -> {
            var mill = entity(helper, cog.east());
            helper.assertTrue(mill.isOverstressed(), "Three millstones overload one crank");
            helper.assertValueEqual(mill.getSpeed(), 0f, "Actual RPM when overstressed");
            helper.assertValueEqual(mill.getTheoreticalSpeed(), -32f, "Theoretical RPM survives overload");
            helper.assertValueEqual(mill.getStressCapacity(), 256.0, "Crank capacity");
            helper.assertValueEqual(mill.getStressImpact(), 384.0, "Millstone load");
            helper.setBlock(cog.east(3), Blocks.AIR);
        });
        helper.runAfterDelay(7, () -> {
            helper.assertFalse(entity(helper, cog.east()).isOverstressed(), "Removing excess load restarts network");
            helper.assertValueEqual(entity(helper, cog.east()).getSpeed(), -32f, "Recovered RPM");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 50)
    public void chainDriveConnectsAndUpdatesAfterMiddleRemoval(GameTestHelper helper) {
        motor(helper);
        helper.setBlock(BOX, AllBlocks.ENCASED_CHAIN_DRIVE.defaultBlockState().setValue(ChainDriveBlock.AXIS, Direction.Axis.X));
        helper.setBlock(BOX.south(), AllBlocks.ENCASED_CHAIN_DRIVE.defaultBlockState().setValue(ChainDriveBlock.AXIS, Direction.Axis.X));
        helper.setBlock(BOX.south(2), AllBlocks.ENCASED_CHAIN_DRIVE.defaultBlockState().setValue(ChainDriveBlock.AXIS, Direction.Axis.X));
        var output = BOX.south(2).east();
        helper.setBlock(output, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(entity(helper, output).getSpeed(), 16f, "Chain transmits across three shafts");
            helper.assertBlockProperty(BOX.south(), ChainDriveBlock.PART, ChainDriveBlock.Part.MIDDLE);
            helper.setBlock(BOX.south(), Blocks.AIR);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(entity(helper, output).getSpeed(), 0f, "Broken chain stops output");
            helper.assertBlockProperty(BOX, ChainDriveBlock.PART, ChainDriveBlock.Part.NONE);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 50)
    public void adjustableChainDriveUsesAnalogSignalRatio(GameTestHelper helper) {
        motor(helper);
        helper.setBlock(BOX, AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT.defaultBlockState().setValue(ChainDriveBlock.AXIS, Direction.Axis.X));
        helper.setBlock(BOX.south(), AllBlocks.ENCASED_CHAIN_DRIVE.defaultBlockState().setValue(ChainDriveBlock.AXIS, Direction.Axis.X));
        var output = BOX.south().east();
        helper.setBlock(output, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(entity(helper, output).getSpeed(), 16f, "Unpowered chain ratio");
            helper.setBlock(BOX.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(entity(helper, output).getSpeed(), 32f, "Full signal doubles chain output");
            helper.succeed();
        });
    }
}
