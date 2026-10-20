package com.simibubi.create.content.kinetics.crusher;

import com.simibubi.create.*;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import java.util.*;

public final class CrushingWheelControllerBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    public static java.util.function.Consumer<CrushingWheelControllerBlockEntity> CLIENT_AUDIO = crusher -> {};
    public void tickAudio() { CLIENT_AUDIO.accept(this); }
    public static final AttachmentType<BlockPos> BYPASS = AttachmentRegistry.createPersistent(Create.asResource("bypass_crushing_wheel"), BlockPos.CODEC);
    /** Initialize this native persistent type before any saved entity attachments are decoded. */
    public static void registerAttachments() { Objects.requireNonNull(BYPASS); }
    public static final ResourceKey<DamageType> CRUSH = ResourceKey.create(Registries.DAMAGE_TYPE, Create.asResource("crush"));
    public final CrushingInventory inventory = new CrushingInventory(this);
    public Entity processingEntity;
    private UUID processingUUID;
    private float crushingSpeed;
    public CrushingWheelControllerBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.CRUSHING_WHEEL_CONTROLLER, pos, state); }
    public boolean hasEntity() { return processingEntity != null || processingUUID != null; }
    public float getCrushingSpeed() { return crushingSpeed; }
    public void contentsChanged() {
        setChanged(); if (level instanceof ServerLevel) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    public void refreshDrive() {
        if (!(level instanceof ServerLevel)) return;
        CrushingWheelBlockEntity left = null, right = null;
        Direction separation = null; boolean unknown = false;
        for (var axis : Direction.Axis.values()) {
            var side = Direction.get(Direction.AxisDirection.POSITIVE, axis);
            var a = worldPosition.relative(side.getOpposite()); var b = worldPosition.relative(side);
            if (!level.isLoaded(a) || !level.isLoaded(b)) { unknown = true; continue; }
            if (!(level.getBlockEntity(a) instanceof CrushingWheelBlockEntity first) || !(level.getBlockEntity(b) instanceof CrushingWheelBlockEntity second)) continue;
            var axle = first.getBlockState().getValue(CrushingWheelBlock.AXIS);
            if (axle == axis || second.getBlockState().getValue(CrushingWheelBlock.AXIS) != axle) continue;
            left = first; right = second; separation = side; break;
        }
        if (left == null && !unknown) { level.removeBlock(worldPosition, false); return; }
        boolean valid = left != null && left.getSpeed() != 0 && right.getSpeed() != 0 && Math.signum(left.getSpeed()) != Math.signum(right.getSpeed());
        Direction facing = Direction.DOWN; float speed = 0;
        if (valid) {
            var axle = Direction.get(Direction.AxisDirection.POSITIVE, left.getBlockState().getValue(CrushingWheelBlock.AXIS));
            var vector = new Vec3(axle.getStepX(), axle.getStepY(), axle.getStepZ()).cross(new Vec3(separation.getStepX(), separation.getStepY(), separation.getStepZ())).scale(Math.signum(left.getSpeed()));
            facing = Direction.getNearest((int) vector.x, (int) vector.y, (int) vector.z, Direction.DOWN);
            speed = Math.abs(left.getSpeed() / 50f);
        }
        var state = getBlockState().setValue(CrushingWheelControllerBlock.VALID, valid).setValue(CrushingWheelControllerBlock.FACING, facing);
        boolean changed = crushingSpeed != speed; crushingSpeed = speed;
        if (state != getBlockState()) { level.setBlockAndUpdate(worldPosition, state); changed = true; }
        if (changed) contentsChanged();
        // Pinned source awards the first actual wheel of a valid working pair.
        if (valid) {
            left.award(AllAdvancements.CRUSHING_WHEEL);
            if (Math.abs(left.getSpeed()) > com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get() - 1)
                left.award(AllAdvancements.CRUSHER_MAXED);
        }
    }
    private Recipe<?> findRecipe(ItemStack input) {
        if (!(level instanceof ServerLevel server) || input.isEmpty()) return null;
        var manager = server.getServer().getRecipeManager(); var recipeInput = new SingleRecipeInput(input);
        return manager.getRecipeFor(AllRecipeTypes.CRUSHING, recipeInput, level).<Recipe<?>>map(RecipeHolder::value)
                .orElseGet(() -> manager.getRecipeFor(AllRecipeTypes.MILLING, recipeInput, level).map(RecipeHolder::value).orElse(null));
    }
    int duration(ItemStack stack) {
        var recipe = findRecipe(stack);
        return recipe instanceof CrushingRecipe crushing ? crushing.processingTime() : recipe instanceof MillingRecipe milling ? milling.processingTime() : 100;
    }
    public boolean tryStartCrushing(Entity entity) {
        if (!(level instanceof ServerLevel) || crushingSpeed == 0 || hasEntity() || !inventory.isEmpty() || !entity.isAlive() || worldPosition.equals(entity.getAttached(BYPASS))) return false;
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL)) return false;
        processingEntity = entity; processingUUID = entity.getUUID(); contentsChanged(); return true;
    }
    private void clearEntity() { processingEntity = null; processingUUID = null; contentsChanged(); }
    private static void merge(List<ItemStack> stacks, ItemStack result) {
        if (result.isEmpty()) return;
        for (var existing : stacks) if (ItemStack.isSameItemSameComponents(existing, result)) {
            int moved = Math.min(result.getCount(), existing.getMaxStackSize() - existing.getCount()); existing.grow(moved); result.shrink(moved);
            if (result.isEmpty()) return;
        }
        while (!result.isEmpty()) { int count = Math.min(result.getCount(), result.getMaxStackSize()); stacks.add(result.copyWithCount(count)); result.shrink(count); }
    }
    private void applyRecipe() {
        var input = inventory.getInput(); var recipe = findRecipe(input); var results = new ArrayList<ItemStack>();
        var outputs = recipe instanceof CrushingRecipe crushing ? crushing.results() : recipe instanceof MillingRecipe milling ? milling.results() : List.<ProcessingOutput>of();
        for (int roll = 0; roll < input.getCount(); roll++) for (var output : outputs) merge(results, output.rollOutput(level.getRandom()));
        if (recipe != null && input.getItem().getCraftingRemainder() != null) merge(results, input.getItem().getCraftingRemainder().create());
        inventory.apply(results);
    }
    private Vec3 outPosition(Direction facing) { return Vec3.atCenterOf(worldPosition).add(facing.getStepX() * 0.55, facing.getStepY() * 0.55, facing.getStepZ() * 0.55); }
    private void tickEntity(Direction facing) {
        var entity = processingEntity;
        if (entity == null || !entity.isAlive() || !entity.getBoundingBox().intersects(new AABB(worldPosition).inflate(0.5))) { clearEntity(); return; }
        // 26.3 skips entityInside for source air blocks. Apply the source collision effect to
        // this actual captured entity so physical movement cannot skip the item intake plane.
        var axis = facing.getAxis();
        entity.makeStuckInBlock(getBlockState(), new Vec3(axis == Direction.Axis.X ? (double) .05f : .25,
                axis == Direction.Axis.Y ? (double) .05f : .25, axis == Direction.Axis.Z ? (double) .05f : .25));
        double sign = facing.getAxisDirection().getStep();
        double movement = Math.min(crushingSpeed, 0.5) * sign;
        double x = entity.isShiftKeyDown() ? 0 : (worldPosition.getX() + 0.5 - entity.getX()) / 2;
        double z = entity.isShiftKeyDown() ? 0 : (worldPosition.getZ() + 0.5 - entity.getZ()) / 2;
        entity.setDeltaMovement(facing.getAxis() == Direction.Axis.X ? movement : x, facing.getAxis() == Direction.Axis.Y ? movement : 0, facing.getAxis() == Direction.Axis.Z ? movement : z);
        if (!(level instanceof ServerLevel server)) return;
        if (entity instanceof ItemEntity item) {
            item.setPickUpDelay(20);
            double coordinate = facing.getAxis().choose(item.getX(), item.getY(), item.getZ());
            double center = facing.getAxis().choose(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
            if (coordinate * -sign >= (center - 0.25) * -sign) return;
            long accepted;
            try (Transaction tx = Transaction.openOuter()) {
                accepted = inventory.insert(ItemVariant.of(item.getItem()), item.getItem().getCount(), tx, true);
                if (accepted == 0) return; tx.commit();
            }
            var remaining = item.getItem().copy(); remaining.shrink((int) accepted);
            if (remaining.isEmpty()) item.discard(); else item.setItem(remaining);
            clearEntity(); return;
        }
        var exit = outPosition(facing).add(facing.getStepX() * 0.5, facing.getStepY() * 0.5, facing.getStepZ() * 0.5);
        int damage=com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.crushingDamage.get();
        boolean lethal = entity instanceof LivingEntity living && living.getHealth() <= damage && living.hurtTime <= 0;
        Set<UUID> previousDrops = Set.of();
        if (lethal) {
            previousDrops = new HashSet<>();
            for (var drop : server.getEntitiesOfClass(ItemEntity.class, new AABB(exit, exit).inflate(1))) previousDrops.add(drop.getUUID());
            entity.setPos(exit);
        }
        entity.hurtServer(server, new DamageSource(server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(CRUSH)), damage);
        if (!entity.isAlive()) {
            entity.setPos(exit);
            if (lethal) for (var drop : server.getEntitiesOfClass(ItemEntity.class, new AABB(exit, exit).inflate(1))) {
                if (previousDrops.contains(drop.getUUID())) continue;
                drop.setDeltaMovement(Vec3.ZERO); drop.setAttached(BYPASS, worldPosition);
            }
            clearEntity();
        }
    }
    private void output(Direction facing) {
        if (facing != Direction.UP) {
            var target = worldPosition.below().relative(facing, facing.getAxis().isVertical() ? 0 : 1);
            if (!level.isLoaded(target)) return;
            var storage = com.simibubi.create.foundation.item.LoadedItemStorage.find(level, target, facing.getOpposite());
            if (storage != null) { inventory.exportTo(storage); if (inventory.isEmpty()) inventory.clear(); return; }
        }
        var out = outPosition(facing);
        var velocity = new Vec3(facing.getStepX() * 0.25, facing == Direction.UP ? 0.5 : 0, facing.getStepZ() * 0.25);
        for (var stack : inventory.getOutputs()) {
            var item = new ItemEntity(level, out.x, out.y, out.z, stack); item.setDeltaMovement(velocity); item.setAttached(BYPASS, worldPosition); item.setDefaultPickUpDelay(); level.addFreshEntity(item);
        }
        inventory.clear();
    }
    public static void tick(Level level, BlockPos pos, BlockState state, CrushingWheelControllerBlockEntity crusher) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, crusher)) {
            if (!createTick.shouldTick()) return;

        if (!level.isClientSide()) crusher.refreshDrive();
        if (crusher.isRemoved() || crusher.crushingSpeed == 0) return;
        if (crusher.processingEntity == null && crusher.processingUUID != null) {
            crusher.processingEntity = level instanceof ServerLevel server ? server.getEntity(crusher.processingUUID)
                    : level.getEntities((Entity) null, new AABB(pos).inflate(1), entity -> entity.getUUID().equals(crusher.processingUUID)).stream().findFirst().orElse(null);
            if (crusher.processingEntity == null) crusher.clearEntity();
        }
        var facing = crusher.getBlockState().getValue(CrushingWheelControllerBlock.FACING);
        if (level.isClientSide()) crusher.tickAudio();
        if (!crusher.hasEntity() && crusher.inventory.isEmpty() && !level.isClientSide()) {
            for (var entity : level.getEntities(null, new AABB(pos).inflate(0.01))) if (crusher.tryStartCrushing(entity)) break;
        }
        if (crusher.hasEntity()) { crusher.tickEntity(facing); return; }
        if (crusher.inventory.isEmpty()) return;
        var input = crusher.inventory.getInput();
        if (level.isClientSide()) {
            if (!input.isEmpty()) for (int i = 0; i < 4; i++) level.addParticle(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(input)),
                    pos.getX() + level.getRandom().nextFloat(), pos.getY() + level.getRandom().nextFloat(), pos.getZ() + level.getRandom().nextFloat(), 0, 0, 0);
            return;
        }
        double divisor = crusher.inventory.appliedRecipe ? 1 : Math.log(input.getCount()) / Math.log(2);
        float decrement = (float) Math.clamp(crusher.crushingSpeed * 4 / divisor, 0.25, 20);
        crusher.inventory.remainingTime = Math.max(0, crusher.inventory.remainingTime - decrement); crusher.setChanged();
        if (crusher.inventory.remainingTime < 20 && !crusher.inventory.appliedRecipe) { crusher.applyRecipe(); return; }
        if (crusher.inventory.remainingTime <= 0) crusher.output(facing);
    
        }
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel) {
            Block.popResource(level, pos, inventory.getInput()); inventory.getOutputs().forEach(stack -> Block.popResource(level, pos, stack));
            inventory.clear(); clearEntity();
        }
        super.preRemoveSideEffects(pos, state);
    }
    @Override protected void write(ValueOutput out, boolean clientPacket) {
        super.write(out, clientPacket); inventory.save(out.child("Inventory")); out.putFloat("Speed", crushingSpeed);
        if (processingUUID != null) out.store("Entity", UUIDUtil.CODEC, processingUUID);
    }
    @Override protected void read(ValueInput in, boolean clientPacket) {
        super.read(in, clientPacket); inventory.load(in.childOrEmpty("Inventory"));
        float speed = in.getFloatOr("Speed", 0); crushingSpeed = Float.isFinite(speed) ? Math.clamp(speed, 0, com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get()/50f) : 0;
        processingEntity = null; processingUUID = in.read("Entity", UUIDUtil.CODEC).orElse(null);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) { return super.getUpdateTag(registries); }
    private final com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour directBeltInput = com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.createNative(this);
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (directBeltInput != null) behaviours.add(directBeltInput);
    }
}
