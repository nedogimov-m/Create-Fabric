package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The same top interaction, real landing transfer and comparator rules for Depot and Ejector. */
public final class SharedDepotBlockMethods {
    public static DepotBehaviour get(BlockGetter level, BlockPos pos) { return BlockEntityBehaviour.get(level, pos, DepotBehaviour.TYPE); }
    public static InteractionResult onUse(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                         InteractionHand hand, BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var depot = get(level, pos); if (depot == null) return InteractionResult.PASS;
        if (!depot.canAcceptItems()) return InteractionResult.SUCCESS;
        boolean emptyHand = stack.isEmpty(); boolean arm = stack.is(AllBlocks.MECHANICAL_ARM.asItem());
        var returned = new ArrayList<ItemStack>();
        try (var tx = Transaction.openOuter()) {
            for (int index = 0; index < depot.itemHandler.getSlots(); index++) {
                var slot = index == 0 ? depot.inventory.inputs.getFirst() : depot.inventory.outputs.get(index - 1);
                if (slot.isResourceBlank()) continue;
                returned.add(slot.stack()); slot.extract(slot.getResource(), slot.getAmount(), tx);
            }
            tx.commit();
        }
        for (var item : returned) player.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY);
        if (!returned.isEmpty()) level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f, 1f + level.getRandom().nextFloat());
        if (!emptyHand && !arm) {
            var transport = new TransportedItemStack(stack.copy(), .25f); transport.insertedFrom = player.getDirection();
            depot.setHeldItem(transport); player.setItemInHand(hand, ItemStack.EMPTY);
            level.playSound(null, pos, AllSoundEvents.DEPOT_SLIDE, SoundSource.BLOCKS, 1, 1);
        }
        depot.blockEntity.setChanged();
        level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        return InteractionResult.SUCCESS;
    }
    public static void onLanded(BlockGetter level, Entity entity) {
        ItemStack stack = entity instanceof ItemEntity item ? item.getItem()
                : entity instanceof PackageEntity parcel ? parcel.getBox() : ItemStack.EMPTY;
        if (stack.isEmpty() || entity.level().isClientSide() || entity.isRemoved()) return;
        var pos = entity.blockPosition(); var input = BlockEntityBehaviour.get(level, pos, DirectBeltInputBehaviour.TYPE);
        if (input == null || !PackageEntity.centerPackage(entity, Vec3.atCenterOf(pos).add(0, 5 / 16f, 0))) return;
        var remainder = input.handleInsertion(stack, Direction.DOWN, false);
        if (entity instanceof ItemEntity item) item.setItem(remainder);
        if (remainder.isEmpty()) entity.discard();
    }
    public static int getComparatorInputOverride(BlockState state, Level level, BlockPos pos) {
        var depot = get(level, pos); if (depot == null) return 0;
        int max = depot.getConfiguredMaxStackSize();
        float fraction = (float) depot.getPresentStackSize() / (max == 0 ? 64 : max);
        return Mth.clamp(Mth.floor(fraction * 14) + (fraction > 0 ? 1 : 0), 0, 15);
    }
    private SharedDepotBlockMethods() {}
}
