package com.simibubi.create.content.kinetics.millstone;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.item.ProcessingInventory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

public final class MillstoneBlockEntity extends KineticBlockEntity {
    public final ProcessingInventory inventory = new ProcessingInventory(1, 9, this::canProcess, this::inventoryChanged);
    private int timer;
    private ItemVariant processing = ItemVariant.blank();
    private List<ItemStack> pendingResults;

    public MillstoneBlockEntity(BlockPos pos, BlockState state) {
        super(AllBlockEntityTypes.MILLSTONE, pos, state);
    }

    private boolean canProcess(ItemVariant variant) {
        return !variant.isBlank() && level instanceof ServerLevel server && server.getServer().getRecipeManager()
                .getRecipeFor(AllRecipeTypes.MILLING, new SingleRecipeInput(variant.toStack()), level).isPresent();
    }

    private void inventoryChanged() {
        setChanged();
        sync();
    }

    public int getProcessingTimer() { return timer; }
    public int getProcessingSpeed() { return Math.clamp((int) Math.abs(getSpeed() / 16f), 1, 512); }
    public void spawnParticles() {
        var stack = inventory.inputs.getFirst().stack();
        if (stack.isEmpty() || level == null || !level.isClientSide()) return;
        var data = new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
            net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack));
        float angle = level.getRandom().nextFloat() * 360;
        var offset = net.createmod.catnip.math.VecHelper.rotate(new net.minecraft.world.phys.Vec3(0, 0, .5f), angle, net.minecraft.core.Direction.Axis.Y);
        var target = net.createmod.catnip.math.VecHelper.rotate(offset, getSpeed() > 0 ? 25 : -25, net.minecraft.core.Direction.Axis.Y);
        var center = offset.add(net.createmod.catnip.math.VecHelper.getCenterOf(worldPosition));
        target = net.createmod.catnip.math.VecHelper.offsetRandomly(target.subtract(offset), level.getRandom(), 1 / 128f);
        level.addParticle(data, center.x, center.y, center.z, target.x, target.y, target.z);
    }


    public static void tick(Level level, BlockPos pos, BlockState state, MillstoneBlockEntity mill) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, mill)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level, pos, state, mill);
        if (mill.isVirtual()) {
            if (mill.getSpeed() == 0 || mill.inventory.inputs.getFirst().isResourceBlank()
                || mill.inventory.outputs.stream().anyMatch(slot -> slot.getAmount() == slot.getCapacity())) return;
            // Source no-recipe local client branch: display the real input without a host recipe manager.
            if (mill.timer > 0) { mill.timer -= mill.getProcessingSpeed(); mill.spawnParticles(); }
            else mill.timer = 100;
            return;
        }
        if (!(level instanceof ServerLevel server) || mill.getSpeed() == 0) return;
        var input = mill.inventory.inputs.getFirst();
        if (input.isResourceBlank()) {
            mill.timer = 0;
            mill.processing = ItemVariant.blank();
            mill.pendingResults = null;
            return;
        }
        if (!input.getResource().equals(mill.processing)) {
            mill.timer = 0;
            mill.pendingResults = null;
        }
        var match = server.getServer().getRecipeManager().getRecipeFor(AllRecipeTypes.MILLING,
                new SingleRecipeInput(input.stack()), level);
        if (match.isEmpty()) return;
        MillingRecipe recipe = match.get().value();
        if (mill.timer == 0 && mill.pendingResults == null) {
            mill.processing = input.getResource();
            mill.timer = recipe.processingTime();
            mill.inventoryChanged();
            return;
        }
        if (mill.timer > 0) {
            mill.timer -= Math.clamp((int) Math.abs(mill.getSpeed() / 16f), 1, 512);
            mill.setChanged();
            if (mill.timer > 0) return;
        }
        if (mill.pendingResults == null) mill.pendingResults = recipe.rollResults(level.getRandom());
        try (Transaction transaction = Transaction.openOuter()) {
            for (ItemStack result : mill.pendingResults) {
                if (!mill.inventory.insertOutput(result, transaction)) return;
            }
            var remainder = input.stack().getItem().getCraftingRemainder();
            if (remainder != null && !mill.inventory.insertOutput(remainder.create(), transaction)) return;
            if (input.extract(mill.processing, 1, transaction) != 1) return;
            transaction.commit();
        }
        mill.timer = 0;
        mill.pendingResults = null;
        mill.inventoryChanged();
        mill.award(AllAdvancements.MILLSTONE);
    
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            inventory.inputs.forEach(slot -> Block.popResource(level, worldPosition, slot.stack()));
            inventory.outputs.forEach(slot -> Block.popResource(level, worldPosition, slot.stack()));
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void write(ValueOutput output, boolean clientPacket) {
        super.write(output, clientPacket);
        inventory.save(output.child("Inventory"));
        output.putInt("Timer", timer);
        output.store("Processing", ItemVariant.CODEC, processing);
        if (pendingResults != null) output.store("PendingResults", ItemStack.CODEC.listOf(), pendingResults);
    }

    @Override
    protected void read(ValueInput input, boolean clientPacket) {
        
        inventory.load(input.childOrEmpty("Inventory"));
        timer = Math.clamp(input.getIntOr("Timer", 0), -512, 1000000);
        processing = input.read("Processing", ItemVariant.CODEC).orElse(ItemVariant.blank());
        pendingResults = input.read("PendingResults", ItemStack.CODEC.listOf()).orElse(null);
    
        super.read(input, clientPacket);
    }
    private final com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour directBeltInput = com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.createNative(this);
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (directBeltInput != null) behaviours.add(directBeltInput);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("millstone");
    }
}
