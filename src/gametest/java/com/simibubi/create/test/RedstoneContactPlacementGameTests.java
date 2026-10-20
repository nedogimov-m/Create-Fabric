package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.elevator.ElevatorColumn;
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlockEntity;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
import com.simibubi.create.content.redstone.contact.RedstoneContactItem;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real registered floor placement, native block factories and survival payment. */
public final class RedstoneContactPlacementGameTests {
    private static final BlockPos FIRST = new BlockPos(4, 4, 4);
    private static final BlockPos NEXT = new BlockPos(4, 10, 4);

    private static void existing(GameTestHelper h) {
        h.setBlock(FIRST.below(), Blocks.STONE);
        h.setBlock(FIRST, AllBlocks.ELEVATOR_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING, Direction.NORTH));
    }
    private static ElevatorColumn column(GameTestHelper h) {
        var at = h.absolutePos(FIRST);
        return ElevatorColumn.get(h.getLevel(), new ElevatorColumn.ColumnCoords(at.getX(), at.getZ(), Direction.NORTH));
    }
    private static net.minecraft.world.InteractionResult place(GameTestHelper h, ServerPlayer player, ItemStack stack, BlockPos at) {
        var position = Vec3.atCenterOf(h.absolutePos(at)).add(0, 0, 2);
        h.assertTrue(player.teleportTo(h.getLevel(), position.x, position.y, position.z, Set.of(), player.getYRot(), player.getXRot(), false), "Real player stands within native reach of each actual placement");
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var support = h.absolutePos(at.below());
        return player.gameMode.useItemOn(player, h.getLevel(), stack, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false));
    }

    @GameTest(structure = "create_test:reservoir")
    public void newContactUsesActualExistingColumnAndNativeElevatorFactoryWithOneSurvivalDebit(GameTestHelper h) {
        existing(h); h.setBlock(NEXT.below(), Blocks.STONE);
        h.startSequence().thenIdle(2).thenExecute(() -> {
            var column = column(h);
            h.assertTrue(column != null && column.contains(h.absolutePos(FIRST).getY()),
                "Actual first elevator contact must register its native world column before the new floor is placed");
            try (var joined = NativeSurvivalTestPlayer.join(h, Vec3.atCenterOf(h.absolutePos(NEXT)).add(0, 0, 2))) {
                var player = joined.player(); player.setXRot(0); player.setYRot(0); player.setShiftKeyDown(false);
                var stack = new ItemStack(AllBlocks.REDSTONE_CONTACT, 2);
                h.assertTrue(stack.getItem() instanceof RedstoneContactItem, "Actual registered ordinary contact item owns source elevator placement");
                h.assertTrue(place(h, player, stack, NEXT).consumesAction(), "Actual native game-mode interaction places the new contact");
                var state = h.getLevel().getBlockState(h.absolutePos(NEXT));
                h.assertTrue(state.is(AllBlocks.ELEVATOR_CONTACT) && state.getValue(RedstoneContactBlock.FACING) == Direction.NORTH
                    && h.getLevel().getBlockEntity(h.absolutePos(NEXT)) instanceof ElevatorContactBlockEntity && stack.getCount() == 1 && player.getMainHandItem() == stack,
                    "One normal contact becomes one real elevator-floor block/BE of the matching orientation and pays exactly one held item");
                h.assertTrue(h.getLevel().getBlockState(h.absolutePos(FIRST)).is(AllBlocks.ELEVATOR_CONTACT), "Existing floor remains its actual owner");
            }
        }).thenIdle(2).thenExecute(() -> {
            var column = column(h);
            h.assertTrue(column != null && column.contains(h.absolutePos(NEXT).getY()) && column.getContacts().size() == 2
                && column.compileNamesList().size() == 2, "Native new floor ticker joins the same column and exposes both actual floor names");
        }).thenSucceed();
    }

    @GameTest(structure = "create_test:reservoir")
    public void absentOppositeAndVerticalColumnsKeepOrdinaryContactAndBlockedTargetDoesNotDebit(GameTestHelper h) {
        existing(h);
        h.startSequence().thenIdle(2).thenExecute(() -> {
            h.assertTrue(column(h) != null, "Actual existing native floor has initialized its real column");
            try (var joined = NativeSurvivalTestPlayer.join(h, Vec3.atCenterOf(h.absolutePos(NEXT)).add(0, 0, 2))) {
                var player = joined.player(); player.setXRot(0);
                var targets = new BlockPos[] { NEXT.east(4), NEXT, NEXT.above(4) };
                var expected = new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.DOWN };
                for (int index = 0; index < targets.length; index++) {
                    var at = targets[index]; h.setBlock(at.below(), Blocks.STONE);
                    player.setYRot(index == 1 ? 180 : 0); player.setShiftKeyDown(index == 2);
                    var stack = new ItemStack(AllBlocks.REDSTONE_CONTACT, 2);
                    h.assertTrue(place(h, player, stack, at).consumesAction(), "Real native ordinary contact placement succeeds");
                    var state = h.getLevel().getBlockState(h.absolutePos(at));
                    h.assertTrue(state.is(AllBlocks.REDSTONE_CONTACT) && state.getValue(RedstoneContactBlock.FACING) == expected[index]
                        && h.getLevel().getBlockEntity(h.absolutePos(at)) == null && stack.getCount() == 1,
                        "Absent column, opposite facing and vertical contact preserve ordinary block ownership and one finite debit");
                }
                var blocked = NEXT.above(8); h.setBlock(blocked.below(), Blocks.STONE); h.setBlock(blocked, Blocks.OBSIDIAN);
                player.setYRot(0); player.setShiftKeyDown(false); var stack = new ItemStack(AllBlocks.REDSTONE_CONTACT, 2);
                h.assertTrue(!place(h, player, stack, blocked).consumesAction() && stack.getCount() == 2
                    && h.getLevel().getBlockState(h.absolutePos(blocked)).is(Blocks.OBSIDIAN)
                    && h.getLevel().getBlockState(h.absolutePos(blocked.above())).isAir(),
                    "Blocked real placement preserves the obstacle and all stock without a fallback floor or invented column member");
            }
        }).thenSucceed();
    }
}
