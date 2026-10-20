package com.simibubi.create.content.logistics.box;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.logistics.chute.ChuteBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;

public class PackageEntity extends LivingEntity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<ItemStack> BOX = net.minecraft.network.syncher.SynchedEntityData.defineId(PackageEntity.class, net.minecraft.network.syncher.EntityDataSerializers.ITEM_STACK);
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder data) {super.defineSynchedData(data);data.define(BOX, ItemStack.EMPTY);}
    @Override public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key) {super.onSyncedDataUpdated(key);if(key==BOX){box=entityData.get(BOX);refreshDimensions();}}


    private @Nullable Entity originalEntity;
    public ItemStack box;

    public int insertionDelay;

    public Vec3 vec2 = Vec3.ZERO, vec3 = Vec3.ZERO;

    public WeakReference<@Nullable Player> tossedBy = new WeakReference<>(null);

    public PackageEntity(EntityType<? extends PackageEntity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        box = ItemStack.EMPTY;
        setYRot(random.nextFloat() * 360.0F);
        setYHeadRot(getYRot());
        yRotO = getYRot();
        insertionDelay = 30;
    }

    public PackageEntity(Level worldIn, double x, double y, double z) {
        this(AllEntityTypes.PACKAGE, worldIn);
        setPos(x, y, z);
        refreshDimensions();
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        super.move(type, movement);
        if (movement.lengthSqr() >= 0.01f) {
            needsSync = true;
        }
    }

    public static PackageEntity fromDroppedItem(Level world, Entity originalEntity, ItemStack itemstack) {
        PackageEntity packageEntity = new PackageEntity(AllEntityTypes.PACKAGE, world);

        Vec3 position = originalEntity.position();
        packageEntity.setPos(position);
        packageEntity.setBox(itemstack);
        packageEntity.setDeltaMovement(originalEntity.getDeltaMovement().scale(1.5f));
        packageEntity.originalEntity = originalEntity;

        if (world != null && !world.isClientSide()) {
            if (ChuteBlock.isChute(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(world, BlockPos.containing(
                position.x,
                position.y + 0.5f,
                position.z
            )))) {
                packageEntity.setYRot((int) packageEntity.getYRot() / 90 * 90);
            }
        }

        return packageEntity;
    }

    public static PackageEntity fromItemStack(Level world, Vec3 position, ItemStack itemstack) {
        PackageEntity packageEntity = new PackageEntity(AllEntityTypes.PACKAGE, world);
        packageEntity.setPos(position);
        packageEntity.setBox(itemstack);
        return packageEntity;
    }

    @Override
    public ItemStack getPickResult() {
        return box.copy();
    }

    public static AttributeSupplier.Builder createPackageAttributes() {
        return createLivingAttributes().add(Attributes.MAX_HEALTH, 5.0f).add(Attributes.MOVEMENT_SPEED, 1.0f);
    }

    @Override
    public MoveSimulationType getMoveSimulationType() {return MoveSimulationType.SERVER_AND_CLIENT;}

    @Override
    public boolean isEffectiveAi() {
        return true;
    }

    // 26.3 predicts movement and reconciles interpolation through the native entity simulator.
    @Override
    public void lerpMotion(Vec3 clientVelocity) {
        setDeltaMovement(getDeltaMovement().add(clientVelocity).scale(0.5f));
    }

    public String getAddress() {
        return box.get(AllDataComponents.PACKAGE_ADDRESS);
    }

    @Override
    public void tick() {
        if (firstTick) {
            verifyInitialEntity();
            originalEntity = null;
        }

        insertionDelay = Math.min(insertionDelay + 1, 30);
        super.tick();

        if (!PackageItem.isPackage(box)) {
            discard();
        }
    }

    /*
     * Forge created package entities even when an ItemEntity is spawned as 'fake'.
     * See: GiveCommand#giveItem. This method discards the package if it originated
     * from such a fake item
     */
    protected void verifyInitialEntity() {
        if (!(originalEntity instanceof ItemEntity itemEntity)) {
            return;
        }
        if (((com.simibubi.create.foundation.mixin.ItemEntityPickupAccessor)itemEntity).create$pickupDelay()==32767) discard();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (box == null) {
            return super.getDefaultDimensions(pose);
        }
        return EntityDimensions.fixed(PackageItem.getWidth(box), PackageItem.getHeight(box));
    }

    public ItemStack getBox() {
        return box;
    }

    public static boolean centerPackage(Entity entity, Vec3 target) {
        if (!(entity instanceof PackageEntity packageEntity)) {
            return true;
        }
        return packageEntity.decreaseInsertionTimer(target);
    }

    public boolean decreaseInsertionTimer(@Nullable Vec3 targetSpot) {
        if (targetSpot != null) {
            setDeltaMovement(getDeltaMovement().scale(0.75f).multiply(1, 0.25f, 1));
            Vec3 pos = position().add(targetSpot.subtract(position()).scale(0.2f));
            setPos(pos.x, pos.y, pos.z);
            float yawTarget = (int) getYRot() / 90 * 90;
            setYRot(net.minecraft.util.Mth.rotLerp(0.5f, getYRot(), yawTarget));
        }
        insertionDelay = Math.max(insertionDelay - 3, 0);
        return insertionDelay == 0;
    }

    public void setBox(ItemStack box) {
        this.box = box.copyWithCount(1);
        entityData.set(BOX, this.box);
        refreshDimensions();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canCollideWith(Entity pEntity) {
        return pEntity instanceof PackageEntity && pEntity.getBoundingBox().maxY < getBoundingBox().minY + 0.125f;
    }

    @Override public boolean canBeCollidedWith(Entity entity) {return false;}

    @Override
    public InteractionResult interact(Player pPlayer, InteractionHand pHand, Vec3 location) {
        if (isRemoved() || box.isEmpty()) return InteractionResult.PASS;
        if (!pPlayer.getItemInHand(pHand).isEmpty()) {
            return super.interact(pPlayer, pHand, location);
        }
        if (pPlayer.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        pPlayer.setItemInHand(pHand, box);
        level().playSound(
            null,
            blockPosition(),
            SoundEvents.ITEM_PICKUP,
            SoundSource.PLAYERS,
            0.2f,
            0.75f + level().getRandom().nextFloat()
        );
        remove(RemovalReason.DISCARDED);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void push(Entity entityIn) {
        boolean isOtherPackage = entityIn instanceof PackageEntity;

        if (!isOtherPackage && tossedBy.get() != null) {
            tossedBy = new WeakReference<>(null); // no nudging
        }

        if (isOtherPackage) {
            if (entityIn.getBoundingBox().minY < getBoundingBox().maxY) {
                super.push(entityIn);
            }
        } else if (entityIn.getBoundingBox().minY <= getBoundingBox().minY) {
            super.push(entityIn);
        }
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity entity) {
        return position().add(0, entity.getDimensions(getPose()).height(), 0);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return super.getPassengerAttachmentPoint(entity, dimensions, partialTick).add(0, 2 / 16.0f, 0);
    }

    @Override
    protected void onInsideBlock(BlockState state) {
        super.onInsideBlock(state);
        if (!isAlive()) {
            return;
        }
        if (state.getBlock() == Blocks.WATER || state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(
            BlockStateProperties.WATERLOGGED)) {
            if (!level().isClientSide()) {destroy(damageSources().drown());remove(RemovalReason.KILLED);}
        }
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (level().isClientSide() || !isAlive()) {
            return false;
        }

        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            remove(RemovalReason.KILLED);
            return false;
        }

        if (!box.canBeHurtBy(source)) return false;

        if (source.equals(damageSources().inWall()) && (isPassenger() || insertionDelay < 20)) {
            return false;
        }

        if (source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }

        if (isInvulnerableTo((ServerLevel) level(), source)) {
            return false;
        }

        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            destroy(source);
            remove(RemovalReason.KILLED);
            return false;
        }

        if (source.is(DamageTypeTags.IS_FIRE)) {
            if (isOnFire()) {
                takeDamage(source, 0.15F);
            } else {
                setRemainingFireTicks(100); // 5 seconds
            }
            return false;
        }

        boolean shotCanPierce;
        if (source.getDirectEntity() instanceof AbstractArrow persistentProjectileEntity) {
            shotCanPierce = persistentProjectileEntity.getPierceLevel() > 0;
        } else {
            shotCanPierce = false;
        }

        if (source.getEntity() instanceof Player player && !player.getAbilities().mayBuild) {
            return false;
        }

        destroy(source);
        remove(RemovalReason.KILLED);
        return shotCanPierce;
    }

    private void takeDamage(DamageSource source, float amount) {
        float hp = getHealth();
        hp = hp - amount;
        if (hp <= 0.5F) {
            destroy(source);
            remove(RemovalReason.KILLED);
        } else {
            setHealth(hp);
        }
    }

    private void destroy(DamageSource source) {
        if (level() instanceof ServerLevel server) PackageItem.pop(server, getBoundingBox().getCenter(), box);
        if (level() instanceof ServerLevel serverLevel) {
            dropAllDeathLoot(serverLevel, source);

        }
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource pDamageSource) {
        super.dropAllDeathLoot(level, pDamageSource);
        for (ItemStack itemstack : PackageItem.getContents(box)) {
            PackageItem.spawnEgg(level, itemstack, blockPosition(), null);
            if (!itemstack.isEmpty()) level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), itemstack));
        }
    }

    private com.simibubi.create.foundation.utility.NeoForgePackageHistory originalPackageHistory;
    private void restoreSavedBox(ItemStack actualSaved){box=actualSaved.copy();entityData.set(BOX,box);refreshDimensions();}
    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        restoreSavedBox(view.read("Box", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        originalPackageHistory=view.read(com.simibubi.create.foundation.utility.NeoForgePackageHistory.KEY,com.simibubi.create.foundation.utility.NeoForgePackageHistory.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        if(originalPackageHistory!=null)view.store(com.simibubi.create.foundation.utility.NeoForgePackageHistory.KEY,com.simibubi.create.foundation.utility.NeoForgePackageHistory.CODEC,originalPackageHistory);
        if (!box.isEmpty()) {
            view.store("Box", ItemStack.CODEC, box);
        }
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot pSlot) {
        if (pSlot == EquipmentSlot.MAINHAND) {
            return getBox();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot pSlot, ItemStack pStack) {
        if (pSlot == EquipmentSlot.MAINHAND) {
            setBox(pStack);
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public InteractionHand getUsedItemHand() {
        return InteractionHand.MAIN_HAND;
    }

    @Override
    public float getVoicePitch() {
        return 1.5f;
    }

    @Override
    public Fallsounds getFallSounds() {
        return new Fallsounds(SoundEvents.CHISELED_BOOKSHELF_FALL, SoundEvents.CHISELED_BOOKSHELF_FALL);
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return box != null && !box.isEmpty() && !box.canBeHurtBy(damageSources().onFire()) || super.fireImmune();
    }
}
