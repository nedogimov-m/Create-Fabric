package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The ordinary shaft item must connect to the real engine through native survival interaction. */
public final class SteamEnginePlacementGameTests {
    private static final BlockPos ENGINE = new BlockPos(5, 6, 5);

    @GameTest(structure = "create_test:reservoir")
    public void shaftHelperKeepsPerpendicularAxisForEveryEngineMountingFace(GameTestHelper helper) {
        try (var joined = NativeSurvivalTestPlayer.join(helper, Vec3.atCenterOf(helper.absolutePos(ENGINE)).add(0, 0, 3))) {
            var player = joined.player();
            for (var direction : Direction.values()) {
                for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++)
                    helper.setBlock(ENGINE.offset(x, y, z), Blocks.AIR);
                var state = AllBlocks.STEAM_ENGINE.defaultBlockState()
                    .setValue(SteamEngineBlock.FACE, direction == Direction.UP ? AttachFace.FLOOR
                        : direction == Direction.DOWN ? AttachFace.CEILING : AttachFace.WALL)
                    .setValue(SteamEngineBlock.FACING, direction.getAxis().isHorizontal() ? direction : Direction.NORTH);
                helper.setBlock(ENGINE.relative(direction.getOpposite()), AllBlocks.FLUID_TANK);
                helper.setBlock(ENGINE, state);
                var position = Vec3.atCenterOf(helper.absolutePos(ENGINE)).subtract(direction.getUnitVec3().scale(2.5));
                helper.assertTrue(player.teleportTo(helper.getLevel(), position.x, position.y, position.z, Set.of(), 0, 0, false), "Native player reaches the engine");
                player.setYRot(switch (direction) { case EAST -> -90; case WEST -> 90; case NORTH -> 180; default -> 0; });
                player.setXRot(direction == Direction.UP ? -90 : direction == Direction.DOWN ? 90 : 0);
                player.setShiftKeyDown(false);
                var stack = new ItemStack(AllBlocks.SHAFT, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var engine = helper.absolutePos(ENGINE);
                var hit = new BlockHitResult(Vec3.atCenterOf(engine), direction, engine, false);
                var result = player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
                var shaftPos = SteamEngineBlock.getShaftPos(state, engine);
                var shaft = helper.getLevel().getBlockState(shaftPos);
                helper.assertTrue(result.consumesAction() && shaft.is(AllBlocks.POWERED_SHAFT)
                    && shaft.getValue(ShaftBlock.AXIS) != direction.getAxis()
                    && SteamEngineBlock.isShaftValid(state, shaft)
                    && helper.getLevel().getBlockEntity(shaftPos) instanceof PoweredShaftBlockEntity
                    && stack.getCount() == 1,
                    "Native engine interaction creates one connected perpendicular powered shaft and debits one item: " + direction + " got " + shaft);
            }
        }
        helper.succeed();
    }
}
