package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;

public class ToolGameTests {
    @GameTest
    public void sandingConsumesOneQuartzAndProducesPolishedQuartz(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack paper = new ItemStack(AllItems.SAND_PAPER);
        player.setItemInHand(InteractionHand.MAIN_HAND, paper);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AllItems.ROSE_QUARTZ, 2));
        AllItems.SAND_PAPER.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(paper.has(AllDataComponents.SAND_PAPER_POLISHING), "Paper holds in-progress quartz");
        helper.assertValueEqual(player.getOffhandItem().getCount(), 1, "Only one quartz reserved");
        AllItems.SAND_PAPER.finishUsingItem(paper, helper.getLevel(), player);
        helper.assertTrue(!paper.has(AllDataComponents.SAND_PAPER_POLISHING), "Polishing component removed");
        helper.assertValueEqual(paper.getDamageValue(), 1, "One durability consumed");
        helper.assertTrue(player.getInventory().contains(new ItemStack(AllItems.POLISHED_ROSE_QUARTZ)), "Polished quartz returned to inventory");
        helper.succeed();
    }

    @GameTest
    public void interruptedSandingReturnsInputWithoutDurabilityLoss(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack paper = new ItemStack(AllItems.RED_SAND_PAPER);
        player.setItemInHand(InteractionHand.MAIN_HAND, paper);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AllItems.ROSE_QUARTZ));
        AllItems.RED_SAND_PAPER.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        AllItems.RED_SAND_PAPER.releaseUsing(paper, helper.getLevel(), player, 20);
        helper.assertFalse(paper.has(AllDataComponents.SAND_PAPER_POLISHING), "Cancelled polishing clears component");
        helper.assertTrue(player.getInventory().contains(new ItemStack(AllItems.ROSE_QUARTZ)), "Input returned after cancellation");
        helper.assertValueEqual(paper.getDamageValue(), 0, "Cancellation does not damage paper");
        helper.succeed();
    }

    @GameTest
    public void sandingComponentSurvivesItemSerialization(GameTestHelper helper) {
        var paper = new ItemStack(AllItems.SAND_PAPER);
        paper.set(AllDataComponents.SAND_PAPER_POLISHING, new ItemStack(AllItems.ROSE_QUARTZ));
        var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var nbt = ItemStack.CODEC.encodeStart(ops, paper).getOrThrow();
        var restored = ItemStack.CODEC.parse(ops, nbt).getOrThrow();
        helper.assertTrue(restored.get(AllDataComponents.SAND_PAPER_POLISHING).is(AllItems.ROSE_QUARTZ), "In-progress item persists");
        helper.succeed();
    }

    @GameTest
    public void sandingRemovesCopperOxidation(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.COPPER_BLOCK.weathering().exposed());
        var paper = new ItemStack(AllItems.SAND_PAPER);
        player.setItemInHand(InteractionHand.MAIN_HAND, paper);
        var absolute = helper.absolutePos(pos);
        AllItems.SAND_PAPER.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        helper.assertBlockPresent(Blocks.COPPER_BLOCK.weathering().unaffected(), pos);
        helper.assertValueEqual(paper.getDamageValue(), 1, "Copper sanding damages paper");
        helper.succeed();
    }

    @GameTest
    public void wrenchRotatesShaftAndKeepsWaterlogging(GameTestHelper helper) {
        var state = AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X).setValue(ShaftBlock.WATERLOGGED, true);
        var rotated = WrenchItem.rotate(state, Direction.UP);
        helper.assertValueEqual(rotated.getValue(ShaftBlock.AXIS), Direction.Axis.Z, "Wrench rotates about clicked face");
        helper.assertTrue(rotated.getValue(ShaftBlock.WATERLOGGED), "Rotation keeps water");
        helper.succeed();
    }

    @GameTest
    public void verticalGearboxDropsCorrectItem(GameTestHelper helper) {
        var state = AllBlocks.GEARBOX.defaultBlockState().setValue(GearboxBlock.AXIS, Direction.Axis.Z);
        var absolute = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(absolute, state);
        var drops = net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(), absolute, helper.getLevel().getBlockEntity(absolute));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(AllItems.VERTICAL_GEARBOX), "Vertical gearbox drops vertical variant");
        helper.succeed();
    }
}
