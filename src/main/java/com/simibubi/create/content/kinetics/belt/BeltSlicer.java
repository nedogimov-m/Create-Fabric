package com.simibubi.create.content.kinetics.belt;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/** Edit complete, loaded chains while retaining cargo identity and world position. */
public final class BeltSlicer {
    private static BlockPos vector(BlockState state) {
        var facing = state.getValue(BeltBlock.HORIZONTAL_FACING);
        var slope = state.getValue(BeltBlock.SLOPE);
        return slope == BeltSlope.VERTICAL ? new BlockPos(0, facing.getAxisDirection().getStep(), 0)
                : new BlockPos(facing.getStepX(), slope == BeltSlope.UPWARD ? 1 : slope == BeltSlope.DOWNWARD ? -1 : 0, facing.getStepZ());
    }
    private static boolean towardEnd(BlockState state, BlockHitResult hit) {
        return hit.getLocation().subtract(Vec3.atCenterOf(hit.getBlockPos())).dot(Vec3.atLowerCornerOf(vector(state))) > 0;
    }
    private static boolean hoveringEnd(BlockState state, BlockHitResult hit) {
        var part = state.getValue(BeltBlock.PART);
        return (part == BeltPart.START || part == BeltPart.END) && towardEnd(state, hit) == (part == BeltPart.END);
    }
    private static void moveCargo(List<TransportedItemStack> cargo, float offset, boolean reverse, int length) {
        for (var item : cargo) {
            if (reverse) { item.beltPosition = length - item.beltPosition; item.prevBeltPosition = length - item.prevBeltPosition; }
            item.beltPosition += offset; item.prevBeltPosition += offset;
            item.insertedAt = (int) Math.floor(item.beltPosition);
        }
    }
    private static void restore(Level level, BlockPos controller, List<TransportedItemStack> cargo) {
        BeltBlock.initBelt(level, controller);
        var belt = BeltHelper.getSegmentBE(level, controller);
        if (belt == null || !belt.chainLoaded()) throw new IllegalStateException("Invalid edited belt chain");
        belt.getInventory().acceptReconfigured(cargo);
    }
    private static void give(Player player, ItemStack stack) {
        if (!player.isCreative() && !stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }
    private static boolean payForSplit(Player player, int shafts, boolean simulate) {
        if (player.isCreative()) return true;
        int available = 0, connectors = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.is(AllBlocks.SHAFT.asItem())) available += stack.getCount();
            if (stack.is(AllItems.BELT_CONNECTOR)) connectors += stack.getCount();
        }
        if (available < shafts || connectors == 0) return false;
        if (simulate) return true;
        int remaining = shafts; boolean connectorTaken = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.is(AllBlocks.SHAFT.asItem())) { int taken = Math.min(remaining, stack.getCount()); stack.shrink(taken); remaining -= taken; }
            else if (!connectorTaken && stack.is(AllItems.BELT_CONNECTOR)) { stack.shrink(1); connectorTaken = true; }
        }
        inventory.setChanged(); return true;
    }
    public static InteractionResult useWrench(Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var state = level.getBlockState(pos);
        var controller = BeltHelper.getControllerBE(level, pos);
        if (controller == null || !controller.chainLoaded()) return InteractionResult.PASS;
        if (state.getValue(BeltBlock.CASING) && hit.getDirection() != Direction.UP
                || state.getValue(BeltBlock.PART) == BeltPart.PULLEY && hit.getDirection().getAxis() != Direction.Axis.Y) return InteractionResult.PASS;
        if (controller.beltLength == 2) return InteractionResult.FAIL;
        var chain = new ArrayList<>(BeltBlock.getBeltChain(level, controller.getBlockPos()));
        var segment = BeltHelper.getSegmentBE(level, pos);
        if (hoveringEnd(state, hit)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            boolean start = state.getValue(BeltBlock.PART) == BeltPart.START;
            var next = start ? chain.get(1) : chain.get(chain.size() - 2);
            boolean refundShaft = level.getBlockState(next).getValue(BeltBlock.PART) == BeltPart.PULLEY;
            var cargo = controller.getInventory().takeForReconfiguration();
            var retained = new ArrayList<TransportedItemStack>();
            for (var item : cargo) {
                if (start ? item.beltPosition < 1 : item.beltPosition >= chain.size() - 1) controller.getInventory().eject(item);
                else retained.add(item);
            }
            if (start) moveCargo(retained, -1, false, 0);
            BeltBlock.editChain(level, () -> {
                level.setBlockAndUpdate(next, level.getBlockState(next).setValue(BeltBlock.PART, start ? BeltPart.START : BeltPart.END));
                level.setBlock(pos, (state.getValue(BeltBlock.WATERLOGGED) ? Blocks.WATER : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
            });
            restore(level, start ? next : controller.getBlockPos(), retained);
            if (refundShaft) give(player, new ItemStack(AllBlocks.SHAFT));
        } else {
            int cut = segment.index + (towardEnd(state, hit) ? 1 : 0);
            if (cut < 2 || cut > chain.size() - 2) return InteractionResult.FAIL;
            var left = chain.get(cut - 1); var right = chain.get(cut);
            int shafts = (level.getBlockState(left).getValue(BeltBlock.PART) == BeltPart.MIDDLE ? 1 : 0)
                    + (level.getBlockState(right).getValue(BeltBlock.PART) == BeltPart.MIDDLE ? 1 : 0);
            if (!payForSplit(player, shafts, true)) return InteractionResult.FAIL;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            payForSplit(player, shafts, false);
            var cargo = controller.getInventory().takeForReconfiguration();
            var leftCargo = new ArrayList<TransportedItemStack>(); var rightCargo = new ArrayList<TransportedItemStack>();
            for (var item : cargo) (item.beltPosition < cut ? leftCargo : rightCargo).add(item);
            moveCargo(rightCargo, -cut, false, 0);
            BeltBlock.editChain(level, () -> {
                level.setBlockAndUpdate(left, level.getBlockState(left).setValue(BeltBlock.PART, BeltPart.END));
                level.setBlockAndUpdate(right, level.getBlockState(right).setValue(BeltBlock.PART, BeltPart.START));
            });
            restore(level, chain.getFirst(), leftCargo); restore(level, right, rightCargo);
        }
        level.playSound(null, pos, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 0.5f, 2.3f);
        return InteractionResult.SUCCESS;
    }
    public static InteractionResult useConnector(Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var state = level.getBlockState(pos);
        var controller = BeltHelper.getControllerBE(level, pos);
        if (controller == null || !controller.chainLoaded() || !hoveringEnd(state, hit)) return InteractionResult.PASS;
        boolean start = state.getValue(BeltBlock.PART) == BeltPart.START;
        var step = vector(state);
        var next = start ? pos.subtract(step) : pos.offset(step);
        if (!level.isLoaded(next) || !level.mayInteract(player, next) || controller.beltLength >= BeltConnectorItem.maxLength()) return InteractionResult.FAIL;
        var nextState = level.getBlockState(next);
        var other = BeltHelper.getControllerBE(level, next);
        if (!nextState.canBeReplaced()) {
            if (other == null || !other.chainLoaded() || other == controller || !compatible(state, nextState)
                    || (long)controller.beltLength + other.beltLength > BeltConnectorItem.maxLength()) return InteractionResult.FAIL;
            if (nextState.getValue(BeltBlock.PART) != BeltPart.START && nextState.getValue(BeltBlock.PART) != BeltPart.END) return InteractionResult.FAIL;
            boolean reverse = state.getValue(BeltBlock.HORIZONTAL_FACING) != nextState.getValue(BeltBlock.HORIZONTAL_FACING);
            var expected = start ? BeltPart.END : BeltPart.START;
            if ((reverse ? flip(nextState) : nextState).getValue(BeltBlock.PART) != expected) return InteractionResult.FAIL;
        } else other = null;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var cargo = controller.getInventory().takeForReconfiguration();
        BlockPos newController = controller.getBlockPos();
        if (other == null) {
            if (start) { moveCargo(cargo, 1, false, 0); newController = next; }
            BeltBlock.editChain(level, () -> {
                level.setBlockAndUpdate(pos, state.setValue(BeltBlock.PART, BeltPart.MIDDLE));
                level.setBlockAndUpdate(next, state.setValue(BeltBlock.CASING, false)
                        .setValue(BeltBlock.WATERLOGGED, level.getFluidState(next).is(net.minecraft.tags.FluidTags.WATER)));
            });
        } else {
            var otherChain = new ArrayList<>(BeltBlock.getBeltChain(level, other.getBlockPos()));
            var otherCargo = other.getInventory().takeForReconfiguration();
            boolean flip = state.getValue(BeltBlock.HORIZONTAL_FACING) != nextState.getValue(BeltBlock.HORIZONTAL_FACING);
            moveCargo(otherCargo, start ? 0 : controller.beltLength, flip, other.beltLength);
            if (start) moveCargo(cargo, other.beltLength, false, 0);
            cargo.addAll(otherCargo);
            if (start) newController = flip ? otherChain.getLast() : otherChain.getFirst();
            BeltBlock.editChain(level, () -> {
                if (flip) for (var cell : otherChain) level.setBlockAndUpdate(cell, flip(level.getBlockState(cell)));
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(BeltBlock.PART, BeltPart.MIDDLE));
                level.setBlockAndUpdate(next, level.getBlockState(next).setValue(BeltBlock.PART, BeltPart.MIDDLE));
            });
            give(player, new ItemStack(AllBlocks.SHAFT, 2)); give(player, new ItemStack(AllItems.BELT_CONNECTOR));
        }
        restore(level, newController, cargo);
        var first = BeltHelper.getSegmentBE(level, newController);
        first.applyColor(controller.getColor());
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1);
        return InteractionResult.SUCCESS;
    }
    public static boolean compatible(BlockState a, BlockState b) {
        if (!(b.getBlock() instanceof BeltBlock)) return false;
        var facing = a.getValue(BeltBlock.HORIZONTAL_FACING); var otherFacing = b.getValue(BeltBlock.HORIZONTAL_FACING);
        var slope = a.getValue(BeltBlock.SLOPE); var otherSlope = b.getValue(BeltBlock.SLOPE);
        if (slope.isDiagonal()) return slope == otherSlope && facing == otherFacing
                || slope != otherSlope && otherSlope.isDiagonal() && facing == otherFacing.getOpposite();
        return slope == otherSlope && facing.getAxis() == otherFacing.getAxis();
    }
    public static BlockState flip(BlockState state) {
        var slope = state.getValue(BeltBlock.SLOPE); var part = state.getValue(BeltBlock.PART);
        return state.setValue(BeltBlock.HORIZONTAL_FACING, state.getValue(BeltBlock.HORIZONTAL_FACING).getOpposite())
                .setValue(BeltBlock.SLOPE, slope == BeltSlope.UPWARD ? BeltSlope.DOWNWARD : slope == BeltSlope.DOWNWARD ? BeltSlope.UPWARD : slope)
                .setValue(BeltBlock.PART, part == BeltPart.START ? BeltPart.END : part == BeltPart.END ? BeltPart.START : part);
    }
    private BeltSlicer() {}
}
