package com.simibubi.create.content.fluids.tank;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public final class FluidTankItem extends BlockItem {
    public FluidTankItem(Block block, Properties properties) { super(block, properties); }
    @Override public InteractionResult place(BlockPlaceContext context) {
        var stack = context.getItemInHand(); var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            var tag = data.copyTagWithoutId();
            for (var key : new String[]{"Luminosity", "Size", "Height", "Controller", "LastKnownPos", "Boiler"}) tag.remove(key);
            tag.putBoolean("Uninitialized", true);
            stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(((FluidTankBlock) getBlock()).creative ? com.simibubi.create.AllBlockEntityTypes.CREATIVE_FLUID_TANK : com.simibubi.create.AllBlockEntityTypes.FLUID_TANK, tag));
        }
        var result = super.place(context); if (result.consumesAction()) tryMultiPlace(context); return result;
    }
    private void tryMultiPlace(BlockPlaceContext context) {
        var player = context.getPlayer(); if (player == null || player.isShiftKeyDown() || com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem.presentInHotbar(player) || !context.getClickedFace().getAxis().isVertical()) return;
        var level = context.getLevel(); var placed = context.getClickedPos(); var support = placed.relative(context.getClickedFace().getOpposite());
        if (!level.isLoaded(support) || !(level.getBlockEntity(support) instanceof FluidTankBlockEntity part) || part.getBlockState().getBlock() != getBlock()) return;
        var master = part.getControllerBE(); if (master == null || master.getWidth() == 1) return;
        var start = context.getClickedFace() == Direction.DOWN ? master.getBlockPos().below() : master.getBlockPos().above(master.getHeight());
        if (start.getY() != placed.getY()) return;
        int count = 0;
        for (int x = 0; x < master.getWidth(); x++) for (int z = 0; z < master.getWidth(); z++) {
            var pos = start.offset(x, 0, z); if (!level.isLoaded(pos)) return;
            var state = level.getBlockState(pos); if (state.is(getBlock())) continue;
            if (!state.canBeReplaced() || !level.mayInteract(player, pos)) return; count++;
        }
        if (!player.isCreative() && context.getItemInHand().getCount() < count) return;
        for (int x = 0; x < master.getWidth(); x++) for (int z = 0; z < master.getWidth(); z++) {
            var pos = start.offset(x, 0, z); if (level.getBlockState(pos).is(getBlock())) continue;
            super.place(BlockPlaceContext.at(context, pos, context.getClickedFace()));
        }
    }
}
