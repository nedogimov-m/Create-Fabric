package com.simibubi.create.test;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.dispenser.DefaultMountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedProjectileDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.OptionalMountedDispenseBehavior;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.behaviour.dispenser.DispenserMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.dispenser.DropperMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.dispenser.MountedDispenseExecution;
import com.simibubi.create.content.contraptions.behaviour.dispenser.storage.DispenserMountedStorage;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.foundation.item.MountedItemInventory;
import com.simibubi.create.foundation.mixin.accessor.DispenserBlockAccessor;
import com.simibubi.create.foundation.mixin.accessor.ProjectileDispenseBehaviorAccessor;
import com.simibubi.create.impl.contraption.dispenser.DispenserBehaviorConverter;
import java.util.HashSet;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real world actions and transactions; no simulated world effects or implementation-copy tests. */
public class DispenserGameTests {
    private static final BlockPos P = new BlockPos(4, 5, 4);
    private static final DropperMovementBehaviour DROPPER = new DropperMovementBehaviour();
    private static final DispenserMovementBehaviour DISPENSER = new DispenserMovementBehaviour();

    private static MovementContext context(GameTestHelper h) {
        var contraption = new BearingContraption(false, Direction.UP);
        var manager = contraption.getStorage();
        manager.reset();
        manager.addStorage(new DispenserMountedStorage(new MountedItemInventory(9)), BlockPos.ZERO);
        manager.addStorage(new SimpleMountedStorage(new MountedItemInventory(27)), BlockPos.ZERO.east());
        manager.initialize();
        manager.setOwned(true);
        return new MovementContext(h.getLevel(), new StructureBlockInfo(BlockPos.ZERO,
            Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST), new CompoundTag()),
            contraption);
    }
    private static MountedItemStorage local(MovementContext context) { return context.getItemStorage(); }
    private static MountedItemStorage shared(MovementContext context) {
        return context.contraption.getStorage().getAllItemStorages().get(BlockPos.ZERO.east());
    }
    private static long count(MovementContext context, Item item) {
        long count = 0;
        for (var view : context.contraption.getStorage().getAllItems())
            if (view.getResource().isOf(item)) count += view.getAmount();
        return count;
    }
    private static List<ItemEntity> drops(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(P)).inflate(6));
    }
    private static List<Projectile> projectiles(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(Projectile.class, new AABB(h.absolutePos(P)).inflate(6));
    }
    private static void visit(GameTestHelper h, MovementContext context, boolean dispenser) {
        (dispenser ? DISPENSER : DROPPER).visitNewPosition(context, h.absolutePos(P));
    }
    private static ItemStack named(Item item, int count) {
        var stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("A specific sample"));
        return stack;
    }
    private static void fill(MountedItemStorage storage, Item item) {
        for (int slot = 0; slot < storage.getSlotCount(); slot++)
            storage.setStackInSlot(slot, new ItemStack(item, item.getDefaultMaxStackSize()));
    }
    private static MountedDispenseBehavior behaviour(ItemStack stack) {
        var result = MountedDispenseBehavior.REGISTRY.get(stack.getItem());
        return result == null ? DefaultMountedDispenseBehavior.INSTANCE : result;
    }

    @GameTest public void bothVanillaBlocksHaveTheirSeparateSourceActors(GameTestHelper h) {
        h.assertTrue(MovementBehaviour.REGISTRY.get(Blocks.DROPPER) instanceof DropperMovementBehaviour,
            "Dropper registered");
        h.assertTrue(MovementBehaviour.REGISTRY.get(Blocks.DISPENSER) instanceof DispenserMovementBehaviour,
            "Dispenser registered");
        h.succeed();
    }
    @GameTest public void lastStackableSampleCannotDispenseWithoutRefill(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, named(Items.DIAMOND, 1));
        visit(h, context, false);
        h.assertTrue(local(context).getStackInSlot(0).getCount() == 1 && drops(h).isEmpty(),
            "Last sample reserved, no world action"); h.succeed();
    }
    @GameTest public void refillMovesOnlyTheExactComponentVariantAndDispensesOne(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, named(Items.DIAMOND, 1));
        shared(context).setStackInSlot(0, new ItemStack(Items.DIAMOND, 21));
        shared(context).setStackInSlot(1, named(Items.DIAMOND, 7));
        visit(h, context, false);
        h.assertTrue(local(context).getStackInSlot(0).getCount() == 7 && count(context, Items.DIAMOND) == 21,
            "Exact refill committed, other components retained");
        h.assertTrue(drops(h).size() == 1 && drops(h).getFirst().getItem().getCount() == 1
            && drops(h).getFirst().getItem().has(DataComponents.CUSTOM_NAME), "Only one named item drops");
        h.succeed();
    }
    @GameTest public void allNearlyEmptySlotsRefillBeforeRandomChoice(GameTestHelper h) {
        var context = context(h);
        for (int slot = 0; slot < 5; slot++) local(context).setStackInSlot(slot, new ItemStack(Items.DIAMOND));
        shared(context).setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
        shared(context).setStackInSlot(1, new ItemStack(Items.DIAMOND, 64));
        shared(context).setStackInSlot(2, new ItemStack(Items.DIAMOND, 64));
        int chosen = DropperMovementBehaviour.getSlot(local(context), RandomSource.create(71),
            context.contraption.getStorage().getAllItems());
        h.assertTrue(chosen >= 0 && chosen < 4, "Only topped-up slots eligible");
        h.assertTrue(local(context).getStackInSlot(0).getCount() == 64
            && local(context).getStackInSlot(1).getCount() == 64
            && local(context).getStackInSlot(2).getCount() == 64
            && local(context).getStackInSlot(3).getCount() == 4
            && local(context).getStackInSlot(4).getCount() == 1, "Refill all five slots in order");
        h.assertValueEqual(count(context, Items.DIAMOND), 0L, "All 192 cargo moved, no duplication"); h.succeed();
    }
    @GameTest public void stackSizeComponentLimitsRefillWithoutMixingTheDefaultVariant(GameTestHelper h) {
        var context=context(h);var sample=named(Items.DIAMOND,1);sample.set(DataComponents.MAX_STACK_SIZE,16);
        local(context).setStackInSlot(0,sample);shared(context).setStackInSlot(0,sample.copyWithCount(16));
        shared(context).setStackInSlot(1,named(Items.DIAMOND,11));visit(h,context,false);
        h.assertTrue(local(context).getStackInSlot(0).getCount()==15
            &&local(context).getStackInSlot(0).getMaxStackSize()==16
            &&shared(context).getStackInSlot(0).getCount()==1&&shared(context).getStackInSlot(1).getCount()==11,
            "Component-defined max16 reserves one and leaves another variant untouched");
        h.assertTrue(drops(h).size()==1&&drops(h).getFirst().getItem().getMaxStackSize()==16,"Dropped component variant intact");h.succeed();
    }
    @GameTest public void failedPrivateTopoffInsertionAbortsSharedExtraction(GameTestHelper h) {
        var contents=new MountedItemInventory(9);contents.setStack(0,new ItemStack(Items.DIAMOND));
        var local=new DispenserMountedStorage(contents){
            @Override public net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage<ItemVariant> getSlot(int index){
                var actual=super.getSlot(index);
                return new net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage<>(){
                    @Override public boolean isResourceBlank(){return actual.isResourceBlank();}
                    @Override public ItemVariant getResource(){return actual.getResource();}
                    @Override public long getAmount(){return actual.getAmount();}
                    @Override public long getCapacity(){return actual.getCapacity();}
                    @Override public long insert(ItemVariant item,long amount,net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction){return 0;}
                    @Override public long extract(ItemVariant item,long amount,net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction){return actual.extract(item,amount,transaction);}
                };
            }
        };
        var cargo=new MountedItemInventory(2);cargo.setStack(0,new ItemStack(Items.DIAMOND,9));
        boolean refused=false;
        try{DropperMovementBehaviour.getSlot(local,RandomSource.create(71),cargo);}
        catch(IllegalStateException expected){refused=true;}
        h.assertTrue(refused&&cargo.getStack(0).getCount()==9&&local.getStackInSlot(0).getCount()==1,
            "A rejected private insertion rolls back the common inventory in the same transaction");h.succeed();
    }
    @GameTest public void randomChoiceIncludesEveryEligibleSlotBeyondTheFirstTwo(GameTestHelper h) {
        var context = context(h);
        for (int slot = 0; slot < 9; slot++) local(context).setStackInSlot(slot, new ItemStack(Items.DIAMOND, 2));
        var random = RandomSource.create(419); var seen = new HashSet<Integer>();
        for (int attempt = 0; attempt < 300; attempt++)
            seen.add(DropperMovementBehaviour.getSlot(local(context), random,
                context.contraption.getStorage().getAllItems()));
        h.assertValueEqual(seen.size(), 9, "Every slot participates in selection"); h.succeed();
    }
    @GameTest public void unstackableLastItemCanDispenseWithoutRefill(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(8, named(Items.IRON_PICKAXE, 1));
        visit(h, context, false);
        h.assertTrue(local(context).getStackInSlot(8).isEmpty() && drops(h).size() == 1
            && drops(h).getFirst().getItem().has(DataComponents.CUSTOM_NAME), "Unstackable items are not samples");
        h.succeed();
    }
    @GameTest public void dispenserPrivateSlotsCannotRefillFromEachOther(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, new ItemStack(Items.DIAMOND));
        local(context).setStackInSlot(8, new ItemStack(Items.DIAMOND, 2)); visit(h, context, false);
        h.assertTrue(local(context).getStackInSlot(0).getCount() == 1
            && local(context).getStackInSlot(8).getCount() == 1 && count(context, Items.DIAMOND) == 0,
            "Private ammunition stays outside shared cargo"); h.succeed();
    }
    @GameTest public void defaultDropIncludesContraptionMotionAndExactOneItem(GameTestHelper h) {
        var context = context(h); context.motion = new Vec3(2, 3, 4);
        local(context).setStackInSlot(0, named(Items.DIAMOND, 3)); visit(h, context, false);
        var entity = drops(h).getFirst(); var relative = entity.getDeltaMovement().subtract(context.motion);
        h.assertTrue(relative.x > 0 && relative.x < .7 && Math.abs(relative.y) < .3 && Math.abs(relative.z) < .3,
            "Source spread added to actor velocity");
        h.assertTrue(entity.getItem().getCount() == 1 && local(context).getStackInSlot(0).getCount() == 2,
            "Consume one"); h.succeed();
    }
    @GameTest public void neighborContainerReceivesOneForEveryFacing(GameTestHelper h) {
        var context = context(h);
        for (var facing : Direction.values()) {
            context.state = context.state.setValue(DispenserBlock.FACING, facing);
            h.setBlock(P.relative(facing), Blocks.BARREL);
            local(context).setStackInSlot(0, new ItemStack(Items.ARROW, 3)); visit(h, context, true);
            h.assertTrue(h.getBlockEntity(P.relative(facing), BarrelBlockEntity.class).getItem(0).getCount() == 1
                && local(context).getStackInSlot(0).getCount() == 2, "Cardinal container insertion");
            h.setBlock(P.relative(facing), Blocks.AIR);
        }
        h.assertTrue(projectiles(h).isEmpty(), "Container insertion takes priority over projectile action"); h.succeed();
    }
    @GameTest public void partiallyRotatedDispenserUsesClosestContainerSide(GameTestHelper h) {
        var context = context(h); context.rotation = vector -> vector.yRot((float) Math.toRadians(65));
        var facing = MountedDispenseBehavior.getClosestFacingDirection(MountedDispenseBehavior.getDispenserNormal(context));
        h.assertTrue(facing != Direction.EAST, "Fixture really rotates away from original axis");
        h.setBlock(P.relative(facing), Blocks.BARREL); local(context).setStackInSlot(0, new ItemStack(Items.ARROW, 2));
        visit(h, context, true);
        h.assertTrue(h.getBlockEntity(P.relative(facing), BarrelBlockEntity.class).getItem(0).is(Items.ARROW)
            && projectiles(h).isEmpty(), "Nearest transformed facing selects inventory"); h.succeed();
    }
    @GameTest public void fullNeighborContainerSuppressesProjectileAndKeepsOriginalCount(GameTestHelper h) {
        var context = context(h); h.setBlock(P.east(), Blocks.BARREL);
        var target = h.getBlockEntity(P.east(), BarrelBlockEntity.class);
        for (int slot = 0; slot < target.getContainerSize(); slot++) target.setItem(slot, new ItemStack(Items.DIRT, 64));
        local(context).setStackInSlot(0, new ItemStack(Items.ARROW, 3)); visit(h, context, true);
        h.assertTrue(local(context).getStackInSlot(0).getCount() == 3 && projectiles(h).isEmpty() && drops(h).isEmpty(),
            "Occupied insertion path must not fall through"); h.succeed();
    }
    @GameTest public void inventoryMinecartAtTargetReceivesInsteadOfFiring(GameTestHelper h) {
        var context = context(h); var cart = h.spawn(EntityTypes.CHEST_MINECART, P.east());
        local(context).setStackInSlot(0, new ItemStack(Items.ARROW, 2)); visit(h, context, true);
        h.assertTrue(cart.getItem(0).is(Items.ARROW) && local(context).getStackInSlot(0).getCount() == 1
            && projectiles(h).isEmpty(), "Native inventory entity query"); h.succeed();
    }
    @GameTest public void worldActionsRejectAnOpenTransferTransaction(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, new ItemStack(Items.ARROW, 2));
        boolean refused = false;
        try (var transaction = Transaction.openOuter()) {
            try { visit(h, context, true); } catch (IllegalStateException expected) { refused = true; }
        }
        h.assertTrue(refused && local(context).getStackInSlot(0).getCount() == 2 && projectiles(h).isEmpty(),
            "Aborted transactions cannot publish a projectile"); h.succeed();
    }
    @GameTest public void unknownChunkNeverGeneratesTerrainOrConsumesAmmunition(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, new ItemStack(Items.ARROW, 2));
        var far = new BlockPos(2100000, 70, 2100000); DISPENSER.visitNewPosition(context, far);
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null
            && local(context).getStackInSlot(0).getCount() == 2, "No tickets, no world action"); h.succeed();
    }
    @GameTest public void staleStorageHandleCannotDispenseAfterOwnershipEnds(GameTestHelper h) {
        var context = context(h); var held = local(context); held.setStackInSlot(0, new ItemStack(Items.ARROW, 2));
        context.contraption.getStorage().setOwned(false); visit(h, context, true);
        h.assertTrue(held.getStackInSlot(0).isEmpty() && projectiles(h).isEmpty(), "Old handle cannot fire"); h.succeed();
    }
    @GameTest public void projectileUsesTransformedNormalPowerAndActorMotion(GameTestHelper h) {
        var context = context(h); context.motion = new Vec3(.3, .1, -.2);
        context.rotation = vector -> vector.yRot(.5f);
        var stack = named(Items.ARROW, 2);
        var behaviour = new MountedProjectileDispenseBehavior() {
            @Override protected Projectile getProjectile(Level level, double x, double y, double z, ItemStack item,
                                                          Direction facing) {
                var projectile = EntityTypes.ARROW.create(level, net.minecraft.world.entity.EntitySpawnReason.DISPENSER);
                projectile.setPos(x, y, z); return projectile;
            }
            @Override protected float getPower() { return 2.7f; }
            @Override protected float getUncertainty() { return 0; }
        };
        behaviour.dispense(stack, context, h.absolutePos(P));
        var projectile = projectiles(h).getFirst();
        var normal = MountedDispenseBehavior.getDispenserNormal(context);
        h.assertTrue(projectile.getDeltaMovement().distanceTo(normal.scale(2.7f).add(context.motion)) < .00001,
            "Custom power plus inherited motion");
        h.assertTrue(projectile.position().distanceTo(Vec3.atCenterOf(h.absolutePos(P)).add(normal.scale(.7))) < .00001,
            "Source .7 muzzle offset"); h.assertValueEqual(stack.getCount(), 1, "One projectile"); h.succeed();
    }
    @GameTest public void vanillaProjectileConversionKeepsItsActualRegisteredConfig(GameTestHelper h) {
        var original = new ProjectileDispenseBehavior(Items.SNOWBALL);
        var config = ((ProjectileDispenseBehaviorAccessor) original).create$getDispenseConfig();
        h.assertTrue(config.power() > 0 && config.uncertainty() >= 0, "Native accessor config");
        var context = context(h); var stack = new ItemStack(Items.SNOWBALL, 2);
        MountedProjectileDispenseBehavior.of(original).dispense(stack, context, h.absolutePos(P));
        h.assertTrue(projectiles(h).getFirst().getType() == EntityTypes.SNOWBALL && stack.getCount() == 1,
            "Native ProjectileItem construction, one item"); h.succeed();
    }
    @GameTest public void splashAndLingeringUseSeparateNativeClassesAndKeepComponents(GameTestHelper h) {
        var context = context(h);
        for (var item : List.of(Items.SPLASH_POTION, Items.LINGERING_POTION)) {
            var stack = named(item, 1); behaviour(stack).dispense(stack, context, h.absolutePos(P));
        }
        var projectiles = projectiles(h);
        h.assertTrue(projectiles.stream().anyMatch(entity -> entity instanceof ThrownSplashPotion potion
            && potion.getItem().has(DataComponents.CUSTOM_NAME)), "Splash entity with components");
        h.assertTrue(projectiles.stream().anyMatch(entity -> entity instanceof ThrownLingeringPotion potion
            && potion.getItem().has(DataComponents.CUSTOM_NAME)), "Lingering entity with components"); h.succeed();
    }
    @GameTest public void emptyBucketCollectsWaterAndRetainsTheLastEmptySample(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, new ItemStack(Items.BUCKET, 2));
        h.setBlock(P.east(), Blocks.WATER); visit(h, context, true);
        h.assertTrue(local(context).getStackInSlot(0).is(Items.BUCKET)
            && local(context).getStackInSlot(0).getCount() == 1 && h.getBlockState(P.east()).isAir(),
            "Water source collected, one empty bucket reserved");
        h.assertTrue(local(context).getStackInSlot(1).is(Items.WATER_BUCKET) && drops(h).isEmpty(),
            "Filled remainder enters private slot"); h.succeed();
    }
    @GameTest public void flowingWaterPickupFailureDoesNotConsumeEmptyBucket(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, new ItemStack(Items.BUCKET, 2));
        h.setBlock(P.east(), Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3));
        visit(h, context, true);
        h.assertTrue(local(context).getStackInSlot(0).getCount() == 2 && local(context).getStackInSlot(1).isEmpty()
            && !h.getBlockState(P.east()).isAir(), "Declined BucketPickup preserves original stack"); h.succeed();
    }
    @GameTest public void bottleCollectsWaterAndFullHiveWithoutLosingReservedInput(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, new ItemStack(Items.GLASS_BOTTLE, 3));
        h.setBlock(P.east(), Blocks.WATER); visit(h, context, true);
        h.assertTrue(local(context).getStackInSlot(1).is(Items.POTION)
            && local(context).getStackInSlot(0).getCount() == 2, "Water bottle in second slot");
        h.setBlock(P.east(), Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
        // The returned unstackable water bottle is itself a valid future random slot.
        // Select the glass-bottle handler explicitly when testing the second interaction.
        MountedDispenseExecution.dispense(local(context),0,behaviour(local(context).getStackInSlot(0)),
            context,h.absolutePos(P));
        h.assertTrue(local(context).getStackInSlot(2).is(Items.HONEY_BOTTLE)
            && local(context).getStackInSlot(0).getCount() == 1
            && h.getBlockState(P.east()).getValue(BeehiveBlock.HONEY_LEVEL) == 0, "Honey drained once"); h.succeed();
    }
    @GameTest public void rejectedBottleActionPreservesAllBottles(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, new ItemStack(Items.GLASS_BOTTLE, 4));
        h.setBlock(P.east(), Blocks.STONE); visit(h, context, true);
        h.assertTrue(local(context).getStackInSlot(0).getCount() == 4 && drops(h).isEmpty(),
            "Optional failure cannot consume or drop"); h.succeed();
    }
    @GameTest public void partialPrivateAndSharedInsertionDropsOnlyTheRemainingAmount(GameTestHelper h) {
        var context = context(h); fill(local(context), Items.DIRT); fill(shared(context), Items.DIRT);
        local(context).setStackInSlot(0, new ItemStack(Items.DIAMOND, 62));
        shared(context).setStackInSlot(0, new ItemStack(Items.DIAMOND, 61));
        MountedDispenseBehavior.placeItemInInventory(new ItemStack(Items.DIAMOND, 9), context, h.absolutePos(P));
        h.assertTrue(local(context).getStackInSlot(0).getCount() == 64
            && shared(context).getStackInSlot(0).getCount() == 64, "Two plus three inserted");
        h.assertTrue(drops(h).size() == 1 && drops(h).getFirst().getItem().getCount() == 4,
            "Only four overflow, never the original seven remainder"); h.succeed();
    }
    @GameTest public void helperCannotOverwriteTheSelectedReturnSlot(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));
        MountedDispenseExecution.dispense(local(context), 0, (stack, ctx, pos) -> {
            stack.shrink(1);
            MountedDispenseBehavior.placeItemInInventory(new ItemStack(Items.EMERALD, 7), ctx, pos);
            return stack;
        }, context, h.absolutePos(P));
        h.assertTrue(local(context).getStackInSlot(0).is(Items.DIAMOND)
            && local(context).getStackInSlot(0).getCount() == 1
            && local(context).getStackInSlot(1).is(Items.EMERALD)
            && local(context).getStackInSlot(1).getCount() == 7, "Result and returned container both survive"); h.succeed();
    }
    @GameTest public void reentrantActorCannotDispenseTheSameUncommittedSlotTwice(GameTestHelper h) {
        var context=context(h);local(context).setStackInSlot(0,new ItemStack(Items.DIAMOND,3));
        var refused=new java.util.concurrent.atomic.AtomicBoolean();
        MountedDispenseExecution.dispense(local(context),0,(stack,ctx,pos)->{
            try {MountedDispenseExecution.dispense(local(ctx),0,DefaultMountedDispenseBehavior.INSTANCE,ctx,pos);}
            catch(IllegalStateException expected){refused.set(true);}
            return DefaultMountedDispenseBehavior.INSTANCE.dispense(stack,ctx,pos);
        },context,h.absolutePos(P));
        h.assertTrue(refused.get()&&local(context).getStackInSlot(0).getCount()==2&&drops(h).size()==1,
            "One world action and one consumption across nested same-slot request");h.succeed();
    }
    @GameTest public void unavailableOverflowSurvivesPrivateSaveAndFailedRestoreThenDropsExactlyOnce(GameTestHelper h)throws Exception {
        h.setBlock(P,Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.EAST));
        var world=h.getBlockEntity(P,DispenserBlockEntity.class);
        for(int slot=0;slot<9;slot++)world.setItem(slot,new ItemStack(Items.DIRT,64));
        var structure=new BearingContraption(false,Direction.UP);
        h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(P.below()))&&structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Take full private inventory");
        var owner=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());
        owner.setContraption(structure);owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(P)));
        var context=structure.getActorAt(BlockPos.ZERO).right;
        var far=new BlockPos(2200000,70,2200000);
        MountedDispenseBehavior.placeItemInInventory(named(Items.DIAMOND,9),context,far);
        h.assertTrue(MountedDispenseExecution.hasPendingRemainders(context)&&drops(h).isEmpty()
            &&h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Unavailable overflow has durable ownership, no chunk load");
        var privateSave=structure.writeNBT(h.getLevel().registryAccess(),false);
        var clientCopy=Contraption.fromNBT(h.getLevel(),structure.writeNBT(h.getLevel().registryAccess(),true),true);
        h.assertFalse(clientCopy.getActorAt(BlockPos.ZERO).right.blockEntityData.contains(MountedDispenseExecution.PENDING_REMAINDERS),"Private overflow components do not enter tracking snapshot");
        h.assertTrue(drops(h).isEmpty(),"Saving cannot spawn a pending remainder");
        var restored=Contraption.fromNBT(h.getLevel(),privateSave,false);
        var restoredContext=restored.getActorAt(BlockPos.ZERO).right;
        h.assertFalse(MountedDispenseExecution.prepareDisassembly(restoredContext,far),"Unavailable pending remainder blocks restoration preflight");
        boolean refused=false;
        try{restored.tryAddBlocksToWorld(h.getLevel(),new StructureTransform(far,0,0,0));}
        catch(AssemblyException expected){refused=true;}
        h.assertTrue(refused&&restored.phase()==Contraption.Phase.REMOVED
            &&restored.getStorage().ownsContents()&&MountedDispenseExecution.hasPendingRemainders(restoredContext),"Failed restore leaves all queued ownership intact");
        h.assertTrue(restored.tryAddBlocksToWorld(h.getLevel(),new StructureTransform(h.absolutePos(P),0,0,0)),"Restore once ready");
        h.assertTrue(!MountedDispenseExecution.hasPendingRemainders(restoredContext)&&drops(h).size()==1
            &&drops(h).getFirst().getItem().getCount()==9&&drops(h).getFirst().getItem().has(DataComponents.CUSTOM_NAME),"Exactly nine component-bearing queued items materialize once");
        h.assertTrue(h.getBlockEntity(P,DispenserBlockEntity.class).getItem(0).getCount()==64
            &&!restored.getStorage().ownsContents(),"World receives full private slots and old owner expires");
        h.succeed();
    }
    @GameTest public void reentrantWorldCallbackCannotRestoreWhileSelectedStackIsDetached(GameTestHelper h)throws Exception {
        h.setBlock(P,Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.EAST));
        h.getBlockEntity(P,DispenserBlockEntity.class).setItem(0,new ItemStack(Items.DIAMOND,3));
        var structure=new BearingContraption(false,Direction.UP);
        h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(P.below()))&&structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Capture real source");
        var owner=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());
        owner.setContraption(structure);owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(P)));
        var context=structure.getActorAt(BlockPos.ZERO).right;
        var refused=new java.util.concurrent.atomic.AtomicBoolean();
        MountedDispenseExecution.dispense(local(context),0,(stack,ctx,pos)->{
            try{structure.tryAddBlocksToWorld(h.getLevel(),new StructureTransform(h.absolutePos(P),0,0,0));}
            catch(AssemblyException expected){refused.set(expected.canRetry());}
            h.assertTrue(h.getBlockState(P).isAir()&&structure.getStorage().ownsContents(),"Reentrant restore cannot take detached result ownership");
            return DefaultMountedDispenseBehavior.INSTANCE.dispense(stack,ctx,pos);
        },context,h.absolutePos(P));
        h.assertTrue(refused.get()&&local(context).getStackInSlot(0).getCount()==2&&drops(h).size()==1,"Outer action commits once after retryable restore refusal");
        h.assertTrue(structure.tryAddBlocksToWorld(h.getLevel(),new StructureTransform(h.absolutePos(P),0,0,0))
            &&h.getBlockEntity(P,DispenserBlockEntity.class).getItem(0).getCount()==2,"Subsequent restoration owns exact final result");
        h.succeed();
    }
    @GameTest public void tntFireworkAndFireChargeConsumeOneAndKeepComponents(GameTestHelper h) {
        var context = context(h); context.motion = new Vec3(.8, .2, -.3);
        for (var item : List.of(Items.TNT, Items.FIREWORK_ROCKET, Items.FIRE_CHARGE)) {
            var stack = named(item, 2); behaviour(stack).dispense(stack, context, h.absolutePos(P));
            h.assertValueEqual(stack.getCount(), 1, "One special item consumed");
        }
        h.assertTrue(h.getLevel().getEntitiesOfClass(PrimedTnt.class, new AABB(h.absolutePos(P)).inflate(3)).size() == 1,
            "One primed TNT");
        h.assertTrue(projectiles(h).stream().anyMatch(entity -> entity instanceof FireworkRocketEntity firework
            && firework.getItem().has(DataComponents.CUSTOM_NAME)), "Firework components retained");
        h.assertTrue(projectiles(h).stream().anyMatch(entity -> entity instanceof SmallFireball fireball
            && fireball.getItem().has(DataComponents.CUSTOM_NAME)), "Fire charge components retained"); h.succeed();
    }
    @GameTest public void spawnEggUsesEntityComponentsAndTwiceActorVelocity(GameTestHelper h) {
        var context = context(h); context.motion = new Vec3(.3, .1, .2);
        var stack = named(Items.COW_SPAWN_EGG, 2); behaviour(stack).dispense(stack, context, h.absolutePos(P));
        var cows = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class,
            new AABB(h.absolutePos(P)).inflate(4));
        h.assertTrue(cows.size() == 1 && cows.getFirst().getDeltaMovement().distanceTo(context.motion.scale(2)) < .00001
            && cows.getFirst().hasCustomName() && stack.getCount() == 1, "Configured spawn with source motion"); h.succeed();
    }
    @GameTest public void eggWithoutEntityDataCannotCrashOrConsume(GameTestHelper h) {
        var context = context(h); var stack = new ItemStack(Items.COW_SPAWN_EGG, 2);
        stack.remove(DataComponents.ENTITY_DATA); behaviour(stack).dispense(stack, context, h.absolutePos(P));
        h.assertValueEqual(stack.getCount(), 2, "Invalid egg component declines safely"); h.succeed();
    }
    @GameTest public void nativeFallbackHasARealInventoryAndDoesNotLoseInsertedRemainders(GameTestHelper h) {
        var context = context(h); local(context).setStackInSlot(0, new ItemStack(Items.APPLE, 2));
        var previous = DispenserBlock.DISPENSER_REGISTRY.put(Items.APPLE, new DefaultDispenseItemBehavior() {
            @Override protected ItemStack execute(BlockSource source, ItemStack stack) {
                source.blockEntity().setItem(8, named(Items.EMERALD, 3));
                return consumeWithRemainder(source, stack, new ItemStack(Items.BUCKET));
            }
        });
        try {
            visit(h, context, true);
            h.assertTrue(local(context).getStackInSlot(0).getCount() == 1
                && local(context).getStackInSlot(1).is(Items.BUCKET)
                && local(context).getStackInSlot(8).getCount() == 3
                && local(context).getStackInSlot(8).has(DataComponents.CUSTOM_NAME),
                "Native input, inventory edits and returned container all survive");
        } finally {
            if (previous == null) DispenserBlock.DISPENSER_REGISTRY.remove(Items.APPLE);
            else DispenserBlock.DISPENSER_REGISTRY.put(Items.APPLE, previous);
            MountedDispenseBehavior.REGISTRY.invalidate();
        }
        h.succeed();
    }
    @GameTest public void nativeResolverObservesActualStackComponentsInsteadOfFirstItemLookup(GameTestHelper h) {
        var plain = new ItemStack(Items.STICK); var equipped = new ItemStack(Items.STICK);
        equipped.set(DataComponents.EQUIPPABLE, new ItemStack(Items.IRON_HELMET).get(DataComponents.EQUIPPABLE));
        var accessor = (DispenserBlockAccessor) Blocks.DISPENSER;
        h.assertTrue(accessor.create$callGetDispenseMethod(h.getLevel(), plain).getClass() == DefaultDispenseItemBehavior.class,
            "Plain item is default");
        h.assertTrue(accessor.create$callGetDispenseMethod(h.getLevel(), equipped).getClass() != DefaultDispenseItemBehavior.class,
            "Same item with EQUIPPABLE resolves actual native default");
        h.assertTrue(DispenserBehaviorConverter.INSTANCE.get(Items.STICK) != null,
            "Provider does not cache a default-stack null result");
        var context=context(h);var stand=h.spawn(EntityTypes.ARMOR_STAND,P.east());
        var cached=MountedDispenseBehavior.REGISTRY.get(Items.STICK);
        cached.dispense(plain.copyWithCount(2),context,h.absolutePos(P));
        var input=equipped.copyWithCount(2);cached.dispense(input,context,h.absolutePos(P));
        h.assertTrue(stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.STICK)
            &&input.getCount()==1,"Same cached mounted entry resolves actual component behavior");
        h.succeed();
    }
    @GameTest public void nestedOptionalActionsKeepTheirOwnSuccessSounds(GameTestHelper h) {
        var context=context(h);var events=new java.util.ArrayList<Integer>();
        var optional=new OptionalMountedDispenseBehavior(){
            private int depth;
            @Override protected ItemStack doExecute(ItemStack stack,MovementContext ctx,BlockPos pos,Vec3 facing){
                if(depth++==0){try{dispense(new ItemStack(Items.DIAMOND,2),ctx,pos.north(2));return null;}finally{depth--;}}
                depth--;stack.shrink(1);return stack;
            }
            @Override protected void playSound(net.minecraft.world.level.LevelAccessor level,BlockPos pos){
                var capture=(net.minecraft.world.level.LevelAccessor)java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),new Class<?>[]{net.minecraft.world.level.LevelAccessor.class},
                    (proxy,method,args)->{if(method.getName().equals("levelEvent")){events.add((Integer)args[0]);return null;}
                        throw new AssertionError("Unexpected sound query "+method);});
                super.playSound(capture,pos);
            }
        };
        var input=new ItemStack(Items.DIAMOND,2);optional.dispense(input,context,h.absolutePos(P));
        h.assertTrue(input.getCount()==2&&events.equals(List.of(
            net.minecraft.world.level.block.LevelEvent.SOUND_DISPENSER_DISPENSE,
            net.minecraft.world.level.block.LevelEvent.SOUND_DISPENSER_FAIL)),"Inner success cannot overwrite outer refusal");
        h.succeed();
    }
    @GameTest public void realCaptureSaveReloadAndDisassemblyKeepExactPrivateAmmunition(GameTestHelper h) throws Exception {
        h.setBlock(P, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        h.getBlockEntity(P, DispenserBlockEntity.class).setItem(4, named(Items.ARROW, 7));
        var structure = new BearingContraption(false, Direction.UP);
        h.assertTrue(structure.assemble(h.getLevel(), h.absolutePos(P.below())), "Capture dispenser");
        h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(), BlockPos.ZERO), "Take ownership");
        var owner = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, h.getLevel());
        owner.setContraption(structure); owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(P))); owner.tickActors();
        var context = structure.getActorAt(BlockPos.ZERO).right;
        visit(h, context, true);
        var saved = structure.writeNBT(h.getLevel().registryAccess(), false);
        var restored = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, h.getLevel());
        restored.setContraption(Contraption.fromNBT(h.getLevel(), saved, false)); restored.setPos(owner.position());
        var storage = restored.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO);
        h.assertTrue(storage.getStackInSlot(4).getCount() == 6 && count(context, Items.ARROW) == 0,
            "Exact private storage serialized after firing");
        h.assertTrue(restored.disassemble(), "Disassemble exact saved owner");
        var world = h.getBlockEntity(P, DispenserBlockEntity.class).getItem(4);
        h.assertTrue(world.getCount() == 6 && world.has(DataComponents.CUSTOM_NAME)
            && !storage.isAvailable(), "World takes ownership once, old handles expire"); h.succeed();
    }
}
