package com.simibubi.create.content.kinetics.belt.item;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import net.fabricmc.fabric.api.entity.FakePlayer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;

public final class BeltConnectorItem extends Item {
    /** Historical default retained for API compatibility; construction reads maxLength(). */
    public static final int MAX_LENGTH = 20;
    public static int maxLength(){return com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxBeltLength.get();}
    public BeltConnectorItem(Properties properties) { super(properties); }
    public static boolean validateAxis(Level level, BlockPos pos) { return level.isLoaded(pos) && level.getBlockState(pos).is(AllBlocks.SHAFT); }

    /** Mirrors upstream axis, 45-degree slope, range and source-direction constraints. */
    public static boolean canConnect(Level level, BlockPos first, BlockPos second) {
        if (!second.closerThan(first, maxLength())) return false;
        return canConnectStored(level, first, second, chainBetween(first, second));
    }
    /** Real endpoint/axis/speed/path checks for a previously admitted saved pending operation. */
    public static boolean canConnectStored(Level level, BlockPos first, BlockPos second, List<BlockPos> chain) {
        if (first.equals(second) || !validateAxis(level, first) || !validateAxis(level, second)) return false;
        var axis = level.getBlockState(first).getValue(ShaftBlock.AXIS);
        var diff = second.subtract(first);
        int x = diff.getX(), y = diff.getY(), z = diff.getZ();
        int same = (Math.abs(x) == Math.abs(y) ? 1 : 0) + (Math.abs(y) == Math.abs(z) ? 1 : 0) + (Math.abs(x) == Math.abs(z) ? 1 : 0);
        if (axis.choose(x, y, z) != 0 || same != 1 || axis != level.getBlockState(second).getValue(ShaftBlock.AXIS)) return false;
        if (axis == Direction.Axis.Y && x != 0 && z != 0) return false;
        if (!(level.getBlockEntity(first) instanceof KineticBlockEntity start) || !(level.getBlockEntity(second) instanceof KineticBlockEntity end)) return false;
        float a = start.getTheoreticalSpeed(), b = end.getTheoreticalSpeed();
        if (a != 0 && b != 0 && Math.signum(a) != Math.signum(b)) return false;
        int count = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (chain.size() != (long) count + 1 || !chain.getFirst().equals(first) || !chain.getLast().equals(second)) return false;
        for (int i = 0; i < chain.size(); i++) {
            var pos = chain.get(i);
            if (!pos.equals(first.offset(Integer.signum(x) * i, Integer.signum(y) * i, Integer.signum(z) * i))) return false;
            if (!level.isLoaded(pos)) return false;
            var state = level.getBlockState(pos);
            if (state.is(AllBlocks.SHAFT) && state.getValue(ShaftBlock.AXIS) == axis) continue;
            if (!state.canBeReplaced()) return false;
        }
        return true;
    }
    public static List<BlockPos> chainBetween(BlockPos first, BlockPos second) {
        var diff = second.subtract(first);
        int count = Math.max(Math.abs(diff.getX()), Math.max(Math.abs(diff.getY()), Math.abs(diff.getZ())));
        if (count > maxLength() || count == 0) return List.of();
        var result = new ArrayList<BlockPos>();
        for (int i = 0; i <= count; i++) result.add(first.offset(Integer.signum(diff.getX()) * i, Integer.signum(diff.getY()) * i, Integer.signum(diff.getZ()) * i));
        return result;
    }
    public static boolean createBelts(Level level, BlockPos first, BlockPos second) {
        if (level.isClientSide() || !canConnect(level, first, second)) return false;
        var diff = second.subtract(first);
        var axis = level.getBlockState(first).getValue(ShaftBlock.AXIS);
        BeltSlope slope = axis == Direction.Axis.Y ? BeltSlope.SIDEWAYS : diff.getY() == 0 ? BeltSlope.HORIZONTAL
                : diff.getX() == 0 && diff.getZ() == 0 ? BeltSlope.VERTICAL : diff.getY() > 0 ? BeltSlope.UPWARD : BeltSlope.DOWNWARD;
        Direction.Axis runAxis = diff.getX() != 0 ? Direction.Axis.X : diff.getZ() != 0 ? Direction.Axis.Z : axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        int sign = diff.getX() == 0 && diff.getZ() == 0 ? Integer.signum(diff.getY()) : Integer.signum(runAxis.choose(diff.getX(), 0, diff.getZ()));
        Direction facing = Direction.get(sign > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, runAxis);
        var chain = chainBetween(first, second);
        for (int i = 0; i < chain.size(); i++) {
            var pos = chain.get(i);
            boolean pulley = level.getBlockState(pos).is(AllBlocks.SHAFT);
            boolean water = level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
            var state = AllBlocks.BELT.defaultBlockState().setValue(BeltBlock.HORIZONTAL_FACING, facing).setValue(BeltBlock.SLOPE, slope)
                    .setValue(BeltBlock.PART, i == 0 ? BeltPart.START : i == chain.size() - 1 ? BeltPart.END : pulley ? BeltPart.PULLEY : BeltPart.MIDDLE)
                    .setValue(BeltBlock.WATERLOGGED, water);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        BeltBlock.initBelt(level, first);
        chain.forEach(pos -> level.updateNeighborsAt(pos, AllBlocks.BELT));
        level.playSound(null, first, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1);
        return true;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer(); var level = context.getLevel(); var stack = context.getItemInHand(); var pos = context.getClickedPos();
        if (player == null || !player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        if (level.getBlockState(pos).getBlock() instanceof BeltBlock && !player.isShiftKeyDown()) return BeltSlicer.useConnector(level, pos, player,
                new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, context.isInside()));
        if (player.isShiftKeyDown()) { if (!level.isClientSide()) stack.remove(AllDataComponents.BELT_FIRST_SHAFT); return InteractionResult.SUCCESS; }
        if (!validateAxis(level, pos)) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var first = stack.get(AllDataComponents.BELT_FIRST_SHAFT);
        if (first != null && (!validateAxis(level, first) || !first.closerThan(pos, maxLength() * 2.0))) { stack.remove(AllDataComponents.BELT_FIRST_SHAFT); first = null; }
        if (first == null) stack.set(AllDataComponents.BELT_FIRST_SHAFT, pos);
        else {
            if (!level.mayInteract(player, first) || !createBelts(level, first, pos)) return InteractionResult.FAIL;
            if (!(player instanceof FakePlayer)) AllAdvancements.BELT.awardTo(player);
            stack.remove(AllDataComponents.BELT_FIRST_SHAFT); stack.consume(1, player);
        }
        player.getCooldowns().addCooldown(stack, 5);
        return InteractionResult.SUCCESS;
    }
}
