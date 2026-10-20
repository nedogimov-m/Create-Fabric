package com.simibubi.create.content.contraptions.behaviour.dispenser;

import com.simibubi.create.api.contraption.dispenser.DefaultMountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedProjectileDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.OptionalMountedDispenseBehavior;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Source handlers whose offset, velocity or return-container rules differ from a static dispenser. */
public final class MountedDispenseItemBehaviors {
    private static boolean registered;
    private static final MountedDispenseBehavior SPAWN_EGG = new DefaultMountedDispenseBehavior() {
        @Override protected ItemStack execute(ItemStack stack, MovementContext context, BlockPos pos, Vec3 facing) {
            if (!(stack.getItem() instanceof SpawnEggItem) || !(context.world instanceof ServerLevel level))
                return super.execute(stack, context, pos, facing);
            var type = SpawnEggItem.getType(stack);
            if(type==null)return stack;
            var offset = BlockPos.containing(facing.x + .7, facing.y + .7, facing.z + .7);
            var target = pos.offset(offset);
            if (!LoadedLevelAccess.isLoaded(level, target) || !LoadedLevelAccess.isLoaded(level, target.below()))
                return stack;
            var bounds=type.getSpawnAABB(Vec3.atBottomCenterOf(target)).inflate(1);
            for(int x=(int)Math.floor(bounds.minX)>>4;x<=((int)Math.floor(bounds.maxX)>>4);x++)
                for(int z=(int)Math.floor(bounds.minZ)>>4;z<=((int)Math.floor(bounds.maxZ)>>4);z++)
                    if(!LoadedEntityAccess.isReady(level,x,z))return stack;
            var entity = create(type, level, stack, target, facing.y < .5);
            if (entity != null && level.tryAddFreshEntityWithPassengers(entity)) {
                entity.setDeltaMovement(context.motion.scale(2));
                if(entity instanceof Mob mob)mob.playAmbientSound();
                stack.shrink(1);
            }
            return stack;
        }
    };
    private static final MountedDispenseBehavior TNT = new DefaultMountedDispenseBehavior() {
        @Override protected ItemStack execute(ItemStack stack, MovementContext context, BlockPos pos, Vec3 facing) {
            var location = muzzle(pos, facing);
            var tnt = new PrimedTnt(context.world, location.x, location.y, location.z, null);
            tnt.push(context.motion.x, context.motion.y, context.motion.z);
            if (context.world.addFreshEntity(tnt)) {
                context.world.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED,
                    SoundSource.BLOCKS, 1, 1);
                stack.shrink(1);
            }
            return stack;
        }
    };
    private static final MountedDispenseBehavior FIREWORK = new DefaultMountedDispenseBehavior() {
        @Override protected ItemStack execute(ItemStack stack, MovementContext context, BlockPos pos, Vec3 facing) {
            var location = muzzle(pos, facing);
            var firework = new FireworkRocketEntity(context.world, stack.copyWithCount(1), location.x, location.y,
                location.z, true);
            firework.shoot(facing.x, facing.y, facing.z, .5f, 1);
            if (context.world.addFreshEntity(firework)) {
                firework.applyOnProjectileSpawned((ServerLevel)context.world,stack);
                stack.shrink(1);
            }
            return stack;
        }
        @Override protected void playSound(LevelAccessor level, BlockPos pos) {
            level.levelEvent(LevelEvent.SOUND_FIREWORK_SHOOT, pos, 0);
        }
    };
    private static final MountedDispenseBehavior FIRE_CHARGE = new DefaultMountedDispenseBehavior() {
        @Override protected ItemStack execute(ItemStack stack, MovementContext context, BlockPos pos, Vec3 facing) {
            var location = muzzle(pos, facing);
            var random = context.world.getRandom();
            var acceleration = new Vec3(random.nextGaussian() * .05 + facing.x + context.motion.x,
                random.nextGaussian() * .05 + facing.y + context.motion.y,
                random.nextGaussian() * .05 + facing.z + context.motion.z).normalize();
            var fireball = new SmallFireball(context.world, location.x, location.y, location.z, acceleration);
            fireball.setItem(stack.copyWithCount(1));
            if (context.world.addFreshEntity(fireball)) {
                fireball.applyOnProjectileSpawned((ServerLevel)context.world,stack);
                stack.shrink(1);
            }
            return stack;
        }
        @Override protected void playSound(LevelAccessor level, BlockPos pos) {
            level.levelEvent(LevelEvent.SOUND_BLAZE_FIREBALL, pos, 0);
        }
    };
    private static final MountedDispenseBehavior BUCKET = new DefaultMountedDispenseBehavior() {
        @Override protected ItemStack execute(ItemStack stack, MovementContext context, BlockPos pos, Vec3 facing) {
            var target = pos.relative(MountedDispenseBehavior.getClosestFacingDirection(facing));
            var state = LoadedLevelAccess.blockState(context.world, target);
            if (!(state.getBlock() instanceof BucketPickup pickup))
                return super.execute(stack, context, pos, facing);
            var filled = pickup.pickupBlock(null, context.world, target, state);
            // Some BucketPickup implementations decline even when the interface is present.
            if (filled.isEmpty()) return stack;
            stack.shrink(1);
            if (stack.isEmpty()) return filled;
            MountedDispenseBehavior.placeItemInInventory(filled, context, pos);
            return stack;
        }
    };
    private static final MountedDispenseBehavior POTIONS = new MountedProjectileDispenseBehavior() {
        @Override protected Projectile getProjectile(Level level, double x, double y, double z, ItemStack stack,
                                                       Direction facing) {
            return stack.is(Items.LINGERING_POTION)
                ? new ThrownLingeringPotion(level, x, y, z, stack.copyWithCount(1))
                : new ThrownSplashPotion(level, x, y, z, stack.copyWithCount(1));
        }
        @Override protected float getUncertainty() { return super.getUncertainty() * .5f; }
        @Override protected float getPower() { return super.getPower() * 1.25f; }
    };
    private static final MountedDispenseBehavior BOTTLE = new OptionalMountedDispenseBehavior() {
        @Override protected @Nullable ItemStack doExecute(ItemStack stack, MovementContext context, BlockPos pos,
                                                          Vec3 facing) {
            var target = pos.relative(MountedDispenseBehavior.getClosestFacingDirection(facing));
            var state = LoadedLevelAccess.blockState(context.world, target);
            ItemStack filled;
            if (state.getBlock() instanceof BeehiveBlock hive && state.is(BlockTags.BEEHIVES)
                && state.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
                hive.releaseBeesAndResetHoneyLevel(context.world, state, target, null,
                    BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                filled = new ItemStack(Items.HONEY_BOTTLE);
            } else if (state.getFluidState().is(FluidTags.WATER)) {
                filled = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            } else return null;
            stack.shrink(1);
            if (stack.isEmpty()) return filled;
            MountedDispenseBehavior.placeItemInInventory(filled, context, pos);
            return stack;
        }
    };

    public static synchronized void registerDefaults() {
        if (registered) return;
        registered = true;
        MountedDispenseBehavior.REGISTRY.registerProvider(item -> item instanceof SpawnEggItem ? SPAWN_EGG : null);
        MountedDispenseBehavior.REGISTRY.register(Items.TNT, TNT);
        MountedDispenseBehavior.REGISTRY.register(Items.FIREWORK_ROCKET, FIREWORK);
        MountedDispenseBehavior.REGISTRY.register(Items.FIRE_CHARGE, FIRE_CHARGE);
        MountedDispenseBehavior.REGISTRY.register(Items.BUCKET, BUCKET);
        MountedDispenseBehavior.REGISTRY.register(Items.GLASS_BOTTLE, BOTTLE);
        MountedDispenseBehavior.REGISTRY.register(Items.SPLASH_POTION, POTIONS);
        MountedDispenseBehavior.REGISTRY.register(Items.LINGERING_POTION, POTIONS);
    }

    private static Vec3 muzzle(BlockPos pos, Vec3 facing) { return Vec3.atCenterOf(pos).add(facing.scale(.7)); }
    private static <T extends Entity> T create(EntityType<T> type,ServerLevel level,ItemStack stack,BlockPos pos,boolean offset) {
        return type.create(level,EntityType.<T>createDefaultStackConfig(level,stack,null),pos,EntitySpawnReason.DISPENSER,offset,false);
    }
    private MountedDispenseItemBehaviors() {}
}
