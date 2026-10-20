package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.List;

public class KineticGameTests {
    private static final BlockPos MOTOR = new BlockPos(1, 2, 1);
    private static final BlockPos SHAFT = new BlockPos(2, 2, 1);

    private static void placeDrive(GameTestHelper helper) {
        helper.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        helper.setBlock(SHAFT, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
    }

    private static KineticBlockEntity entity(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, KineticBlockEntity.class);
    }

    private static void assertSpeed(GameTestHelper helper, BlockPos pos, float speed) {
        helper.assertValueEqual(entity(helper, pos).getSpeed(), speed, "Kinetic speed at " + pos);
    }

    @GameTest(maxTicks = 40)
    public void motorDrivesShaft(GameTestHelper helper) {
        placeDrive(helper);
        helper.succeedWhen(() -> assertSpeed(helper, SHAFT, 16));
    }

    @GameTest(maxTicks = 40)
    public void motorOnlyConnectsOnOutputFace(GameTestHelper helper) {
        placeDrive(helper);
        var behind = new BlockPos(0, 2, 1);
        helper.setBlock(behind, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        helper.runAfterDelay(5, () -> {
            assertSpeed(helper, SHAFT, 16);
            assertSpeed(helper, behind, 0);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void disconnectedShaftStops(GameTestHelper helper) {
        placeDrive(helper);
        helper.runAfterDelay(5, () -> {
            assertSpeed(helper, SHAFT, 16);
            helper.setBlock(MOTOR, Blocks.AIR);
        });
        helper.runAfterDelay(10, () -> {
            assertSpeed(helper, SHAFT, 0);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void reversedMotorUpdatesNetwork(GameTestHelper helper) {
        placeDrive(helper);
        helper.runAfterDelay(5, () -> {
            assertSpeed(helper, SHAFT, 16);
            entity(helper, MOTOR).adjustGeneratedSpeed(true);
        });
        helper.runAfterDelay(10, () -> {
            assertSpeed(helper, SHAFT, -16);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void smallCogReversesSpeed(GameTestHelper helper) {
        placeDrive(helper);
        var second = new BlockPos(2, 2, 2);
        helper.setBlock(SHAFT, AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        helper.setBlock(second, AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        helper.succeedWhen(() -> {
            assertSpeed(helper, SHAFT, 16);
            assertSpeed(helper, second, -16);
        });
    }

    @GameTest(maxTicks = 40)
    public void largeCogDoublesSmallCogSpeed(GameTestHelper helper) {
        placeDrive(helper);
        var small = new BlockPos(2, 3, 2);
        helper.setBlock(SHAFT, AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        helper.setBlock(small, AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        helper.succeedWhen(() -> {
            assertSpeed(helper, SHAFT, 16);
            assertSpeed(helper, small, -32);
        });
    }

    @GameTest(maxTicks = 40)
    public void rotatedShaftDisconnects(GameTestHelper helper) {
        placeDrive(helper);
        helper.runAfterDelay(5, () -> {
            assertSpeed(helper, SHAFT, 16);
            helper.setBlock(SHAFT, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Y));
        });
        helper.runAfterDelay(10, () -> {
            assertSpeed(helper, SHAFT, 0);
            helper.succeed();
        });
    }

    @GameTest
    public void perpendicularLargeCogsUseOriginalSignRule(GameTestHelper helper) {
        var first = AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X);
        var second = AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Y);
        helper.assertValueEqual(RotationPropagator.getRotationSpeedModifier(helper.getLevel(), BlockPos.ZERO,
                first, new BlockPos(1, 1, 0), second), 1.0, "Same-quadrant perpendicular gears");
        helper.assertValueEqual(RotationPropagator.getRotationSpeedModifier(helper.getLevel(), BlockPos.ZERO,
                first, new BlockPos(1, -1, 0), second), -1.0, "Opposite-quadrant perpendicular gears");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void valueStorageRoundTrip(GameTestHelper helper) {
        placeDrive(helper);
        helper.runAfterDelay(5, () -> {
            KineticBlockEntity motor = entity(helper, MOTOR);
            motor.adjustGeneratedSpeed(true);
            var tag = motor.saveWithFullMetadata(helper.getLevel().registryAccess());
            BlockEntity restored = BlockEntity.loadStatic(helper.absolutePos(MOTOR), motor.getBlockState(),
                    tag, helper.getLevel().registryAccess());
            helper.assertTrue(restored instanceof KineticBlockEntity, "Block entity must survive serialization");
            helper.assertValueEqual(((KineticBlockEntity) restored).getGeneratedSpeedSetting(), -16, "Motor speed must persist");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void waterloggedShaftStillTransmitsRotation(GameTestHelper helper) {
        placeDrive(helper);
        helper.setBlock(SHAFT, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X)
                .setValue(ShaftBlock.WATERLOGGED, true));
        helper.succeedWhen(() -> {
            assertSpeed(helper, SHAFT, 16);
            helper.assertTrue(helper.getBlockState(SHAFT).getFluidState().getType() == Fluids.WATER,
                    "Waterlogged shaft must preserve its water source");
        });
    }

    @GameTest(maxTicks = 40)
    public void newlyAttachedShaftUsesSameRotationClock(GameTestHelper helper) {
        placeDrive(helper);
        var extension = new BlockPos(3, 2, 1);
        helper.runAfterDelay(5, () -> helper.setBlock(extension,
                AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X)));
        helper.runAfterDelay(10, () -> {
            assertSpeed(helper, extension, 16);
            helper.assertValueEqual(entity(helper, SHAFT).getAngle(0.5f), entity(helper, extension).getAngle(0.5f),
                    "Attaching a shaft later must not change its rotation phase");
            helper.succeed();
        });
    }

    private static void placeCrank(GameTestHelper helper) {
        helper.setBlock(SHAFT, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        helper.setBlock(MOTOR, AllBlocks.HAND_CRANK.defaultBlockState().setValue(HandCrankBlock.FACING, Direction.WEST));
    }

    @GameTest(maxTicks = 40)
    public void handCrankRunsForTenTicksThenStops(GameTestHelper helper) {
        placeCrank(helper);
        entity(helper, MOTOR).turnCrank(false);
        helper.runAfterDelay(5, () -> assertSpeed(helper, SHAFT, -32));
        helper.runAfterDelay(15, () -> {
            assertSpeed(helper, SHAFT, 0);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void handCrankCanReverse(GameTestHelper helper) {
        placeCrank(helper);
        entity(helper, MOTOR).turnCrank(true);
        helper.succeedWhen(() -> assertSpeed(helper, SHAFT, 32));
    }

    @GameTest(maxTicks = 40)
    public void handCrankRequiresSupport(GameTestHelper helper) {
        placeCrank(helper);
        helper.runAfterDelay(5, () -> helper.setBlock(SHAFT, Blocks.AIR));
        helper.runAfterDelay(10, () -> {
            helper.assertBlockNotPresent(AllBlocks.HAND_CRANK, MOTOR);
            helper.succeed();
        });
    }

    @GameTest
    public void survivalRecipesLoad(GameTestHelper helper) {
        for (String path : new String[]{"materials/andesite_alloy", "kinetics/shaft", "kinetics/cogwheel",
                "kinetics/large_cogwheel", "kinetics/large_cogwheel_from_little", "kinetics/hand_crank"}) {
            var key = ResourceKey.create(Registries.RECIPE, Create.asResource("crafting/" + path));
            helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
                    "Ported recipe did not load: " + key.identifier());
        }
        helper.succeed();
    }

    @GameTest
    public void andesiteAlloyCanActuallyBeCrafted(GameTestHelper helper) {
        var input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.IRON_NUGGET), new ItemStack(Items.ANDESITE),
                new ItemStack(Items.ANDESITE), new ItemStack(Items.IRON_NUGGET)));
        var recipe = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "Iron nuggets and andesite must match the alloy recipe");
        helper.assertTrue(recipe.orElseThrow().value().assemble(input).is(AllItems.ANDESITE_ALLOY),
                "Alloy recipe must produce andesite alloy");
        helper.succeed();
    }

    @GameTest
    public void shaftRecipeKeepsOriginalYield(GameTestHelper helper) {
        var input = CraftingInput.of(1, 2, List.of(new ItemStack(AllItems.ANDESITE_ALLOY), new ItemStack(AllItems.ANDESITE_ALLOY)));
        var recipe = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "Two alloys must match the shaft recipe");
        var result = recipe.orElseThrow().value().assemble(input);
        helper.assertTrue(result.is(AllBlocks.SHAFT.asItem()), "Shaft recipe must produce shafts");
        helper.assertValueEqual(result.getCount(), 8, "Shaft recipe yield");
        helper.succeed();
    }

    @GameTest
    public void kineticEntitiesUseUpstreamSaveIdentifiers(GameTestHelper helper) {
        var expected = java.util.Map.of(
                AllBlocks.SHAFT, "simple_kinetic", AllBlocks.COGWHEEL, "simple_kinetic",
                AllBlocks.CREATIVE_MOTOR, "motor", AllBlocks.HAND_CRANK, "hand_crank",
                AllBlocks.GEARSHIFT, "gearshift", AllBlocks.CLUTCH, "clutch", AllBlocks.GEARBOX, "gearbox",
                AllBlocks.ENCASED_CHAIN_DRIVE, "encased_shaft", AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT, "adjustable_chain_gearshift");
        expected.forEach((block, id) -> {
            var entity = block.newBlockEntity(helper.absolutePos(MOTOR), block.defaultBlockState());
            var saved = entity.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.assertValueEqual(saved.getStringOr("id", ""), "create:" + id, "Upstream block entity id");
            var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(helper.absolutePos(MOTOR), block.defaultBlockState(), saved, helper.getLevel().registryAccess());
            helper.assertTrue(restored != null && restored.getType() == entity.getType(), "Entity round trips through its upstream id");
        });
        helper.succeed();
    }

    @GameTest
    public void initialDevelopmentSavesMigrateToTypedEntities(GameTestHelper helper) {
        var state = AllBlocks.CREATIVE_MOTOR.defaultBlockState();
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "create:kinetic"); tag.putInt("GeneratedSpeed", -64);
        var restored = (KineticBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(helper.absolutePos(MOTOR), state, tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored != null, "Legacy development id remains readable");
        helper.assertValueEqual(restored.getGeneratedSpeedSetting(), -64, "Generated speed preserved");
        helper.assertValueEqual(restored.saveWithFullMetadata(helper.getLevel().registryAccess()).getStringOr("id", ""), "create:motor", "Next save uses upstream id");
        helper.succeed();
    }

    @GameTest
    public void motorReadsUpstreamScrollValue(GameTestHelper helper) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "create:motor"); tag.putInt("ScrollValue", -128);
        var restored = (KineticBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(helper.absolutePos(MOTOR), AllBlocks.CREATIVE_MOTOR.defaultBlockState(), tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored != null, "Upstream motor data readable");
        helper.assertValueEqual(restored.getGeneratedSpeedSetting(), -128, "Original motor setting preserved"); helper.succeed();
    }
}
