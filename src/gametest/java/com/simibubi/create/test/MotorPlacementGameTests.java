package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Native item placement must follow the actual shaft, while sneaking preserves manual orientation. */
public final class MotorPlacementGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 4, 4);

    private static void clear(GameTestHelper helper) {
        for (var side : Direction.values()) helper.setBlock(TARGET.relative(side), Blocks.AIR);
        helper.setBlock(TARGET, Blocks.AIR);
    }

    private static BlockHitResult hit(GameTestHelper helper, Direction towardSupport) {
        var support = helper.absolutePos(TARGET.relative(towardSupport));
        var face = towardSupport.getOpposite();
        return new BlockHitResult(Vec3.atCenterOf(support).add(face.getUnitVec3().scale(.5)), face, support, false);
    }

    private static void place(GameTestHelper helper, ServerPlayer player, BlockHitResult hit, Direction expected) {
        var stack = new ItemStack(AllBlocks.CREATIVE_MOTOR, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var result = player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
        var state = helper.getLevel().getBlockState(helper.absolutePos(TARGET));
        helper.assertTrue(result.consumesAction() && state.is(AllBlocks.CREATIVE_MOTOR)
            && state.getValue(CreativeMotorBlock.FACING) == expected
            && AllBlocks.CREATIVE_MOTOR.hasShaftTowards(helper.getLevel(), helper.absolutePos(TARGET), state, expected)
            && stack.getCount() == 1, "Native motor placement faces " + expected + " and consumes exactly one motor");
    }

    @GameTest(structure = "create_test:reservoir")
    public void motorConnectsToEveryAdjacentShaftRegardlessOfPlayerFacing(GameTestHelper helper) {
        try (var joined = NativeSurvivalTestPlayer.join(helper, Vec3.atCenterOf(helper.absolutePos(TARGET)).add(2.5, 1, 2.5))) {
            var player = joined.player();
            player.setShiftKeyDown(false);
            player.setXRot(0);
            for (var side : Direction.values()) for (int yaw : new int[]{0, 90, 180, 270}) {
                clear(helper);
                helper.setBlock(TARGET.relative(side), AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, side.getAxis()));
                player.setYRot(yaw);
                place(helper, player, hit(helper, side), side);
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "create_test:reservoir")
    public void sneakingBypassesShaftPreferenceAndEmptySpaceUsesPlayerFacing(GameTestHelper helper) {
        try (var joined = NativeSurvivalTestPlayer.join(helper, Vec3.atCenterOf(helper.absolutePos(TARGET)).add(2.5, 1, 2.5))) {
            var player = joined.player();
            for (boolean sneaking : new boolean[]{false, true}) for (int yaw : new int[]{0, 90, 180, 270}) {
                clear(helper);
                player.setShiftKeyDown(sneaking);
                player.setXRot(0);
                player.setYRot(yaw);
                if (sneaking) helper.setBlock(TARGET.below(), AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Y));
                else helper.setBlock(TARGET.below(), Blocks.STONE);
                var hit = hit(helper, Direction.DOWN);
                var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.CREATIVE_MOTOR), hit);
                var looking = context.getNearestLookingDirection();
                place(helper, player, hit, sneaking ? looking : looking.getOpposite());
            }
        }
        helper.succeed();
    }
}
