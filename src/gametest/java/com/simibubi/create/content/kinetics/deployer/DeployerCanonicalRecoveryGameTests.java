package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import com.simibubi.create.test.CartAcceptanceTestFixtures;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real removed BE handoff and replacement of the actual native cached journal. Runtime UNRUN. */
public final class DeployerCanonicalRecoveryGameTests {
    private static final BlockPos SOURCE=new BlockPos(3,2,3);
    private static DeployerBlockEntity source(GameTestHelper h){h.setBlock(SOURCE,AllBlocks.DEPLOYER);var owner=h.getBlockEntity(SOURCE,DeployerBlockEntity.class);owner.heldItem=new ItemStack(Items.DIAMOND,7);owner.overflowItems.add(new ItemStack(Items.EMERALD,3));return owner;}
    private static CompoundTag capture(ServerLevel level){return (CompoundTag)DeployerOutputRecovery.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),level.getDataStorage().computeIfAbsent(DeployerOutputRecovery.TYPE)).getOrThrow();}
    private static void replace(ServerLevel level){var saved=capture(level);level.getDataStorage().set(DeployerOutputRecovery.TYPE,DeployerOutputRecovery.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),saved).getOrThrow());}
    private static int owned(GameTestHelper h,Item item){int amount=0;var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);for(var tag:capture(h.getLevel()).getListOrEmpty("Pending")){var entry=(CompoundTag)tag;if(!Vec3.atCenterOf(h.absolutePos(SOURCE)).equals(entry.read("Position",Vec3.CODEC).orElse(null)))continue;var loose=entry.read("Loose",ItemStack.OPTIONAL_CODEC,ops).orElse(ItemStack.EMPTY);var pending=entry.getCompoundOrEmpty("Entity").read("Item",ItemStack.OPTIONAL_CODEC,ops).orElse(ItemStack.EMPTY);if(loose.is(item))amount+=loose.getCount();if(pending.is(item))amount+=pending.getCount();}return amount;}
    private static int drops(GameTestHelper h,Item item){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(SOURCE)).inflate(7),entity->entity.getItem().is(item)).stream().mapToInt(entity->entity.getItem().getCount()).sum();}
    @GameTest public void actualCacheReplacementBetweenNativeHeadAndSectionRetainsRemovedDeployerStock(GameTestHelper h)throws Exception{
        var owner=source(h);var rejected=new AtomicReference<ItemEntity>();
        try(var observer=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity.level()==h.getLevel()&&entity.position().equals(Vec3.atCenterOf(h.absolutePos(SOURCE)))&&entity instanceof ItemEntity item&&item.getItem().is(Items.DIAMOND),entity->{h.assertTrue(NativeEntityAcceptance.isPreparedEndpointInCurrentAdd(entity),"Replacement occurs in the actual prepared native add after HEAD validation");rejected.set((ItemEntity)entity);replace(h.getLevel());})){
            h.assertTrue(h.getLevel().destroyBlock(owner.getBlockPos(),false),"Actual native BE removal runs the original source-output handoff");
        }
        h.assertTrue(rejected.get()!=null&&owner.heldItem.isEmpty()&&owner.overflowItems.isEmpty()&&h.getLevel().getEntity(rejected.get().getUUID())==null&&drops(h,Items.DIAMOND)==0&&drops(h,Items.EMERALD)==0&&owned(h,Items.DIAMOND)==7&&owned(h,Items.EMERALD)==3,"Retired actual publisher cannot insert its first output or continue its old loop; one replacement owns exactly the original paid 7+3");
        h.startSequence().thenWaitUntil(()->h.assertTrue(owned(h,Items.DIAMOND)==0&&owned(h,Items.EMERALD)==0&&drops(h,Items.DIAMOND)==7&&drops(h,Items.EMERALD)==3,"Actual registered world tick admits only replacement journal output and retires each finite receipt")).thenExecute(()->h.assertTrue(h.getLevel().getEntity(rejected.get().getUUID())!=rejected.get(),"Old prospective endpoint never becomes an independently accepted duplicate")).thenSucceed();
    }
    @GameTest public void actualEarlyCreatedReplacementStopsRetiredDeployerBeforeItsNextReceipt(GameTestHelper h)throws Exception{
        var owner=source(h);var accepted=new AtomicReference<ItemEntity>();
        try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity.level()==h.getLevel()&&entity.position().equals(Vec3.atCenterOf(h.absolutePos(SOURCE)))&&entity instanceof ItemEntity item&&item.getItem().is(Items.DIAMOND),entity->{h.assertTrue(NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(entity),"Real first output has native section custody before early callback replacement");accepted.set((ItemEntity)entity);replace(h.getLevel());})){
            h.assertTrue(h.getLevel().destroyBlock(owner.getBlockPos(),false),"Genuine original Deployer removal");
        }
        h.assertTrue(accepted.get()!=null&&h.getLevel().getEntity(accepted.get().getUUID())==accepted.get()&&drops(h,Items.DIAMOND)==7&&drops(h,Items.EMERALD)==0&&owned(h,Items.DIAMOND)==0&&owned(h,Items.EMERALD)==3,"Replacement copied no accepted stock; old delivery frame stops before the original remaining emerald receipt");
        h.startSequence().thenWaitUntil(()->h.assertTrue(owned(h,Items.EMERALD)==0&&drops(h,Items.DIAMOND)==7&&drops(h,Items.EMERALD)==3,"Current actual world journal alone publishes the original remaining stock once")).thenSucceed();
    }
}
