package com.simibubi.create.content.kinetics.simpleRelays.encased;

import java.util.function.Supplier;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class EncasedShaftBlock extends AbstractEncasedShaftBlock implements EncasedBlock, SpecialBlockItemRequirement {
    private final Supplier<Block> casing;
    public EncasedShaftBlock(Properties properties, Supplier<Block> casing) { super(properties); this.casing = casing; }
    @Override public Block getCasing() { return casing.get(); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public ItemRequirement getRequiredItems(BlockState state, BlockEntity entity) { return ItemRequirement.of(AllBlocks.SHAFT.defaultBlockState(), null); }
    public ItemStack getCloneItemStack(BlockState state, Direction face) {
        return new ItemStack(face.getAxis() == getRotationAxis(state) ? AllBlocks.SHAFT : getCasing());
    }
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide()) {
            context.getLevel().levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, context.getClickedPos(), Block.getId(state));
            KineticBlockEntity.switchToBlockState(context.getLevel(), context.getClickedPos(), AllBlocks.SHAFT.defaultBlockState().setValue(AXIS, state.getValue(AXIS)));
        }
        return InteractionResult.SUCCESS;
    }
    @Override public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack heldItem,
            Player player, InteractionHand hand, BlockHitResult ray) {
        KineticBlockEntity.switchToBlockState(level, pos, defaultBlockState().setValue(AXIS, state.getValue(AXIS)));
    }
}
