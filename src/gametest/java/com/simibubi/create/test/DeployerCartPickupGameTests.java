package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.mounted.*;
import com.simibubi.create.content.kinetics.deployer.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Real cart pickup actions to real saved BE/owned moving actor output endpoints; authored UNRUN. */
public final class DeployerCartPickupGameTests {
    private static final BlockPos TARGET=new BlockPos(3,2,3),ACTOR=TARGET.west(2);
    private static boolean packed(ItemStack stack){return stack.has(AllDataComponents.MINECRAFT_CONTRAPTION_DATA);}
    private static long loose(GameTestHelper h){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(TARGET)).inflate(6)).stream().filter(item->packed(item.getItem())).mapToLong(item->item.getItem().getCount()).sum();}
    private static DeployerBlockEntity stationary(GameTestHelper h){
        h.setBlock(ACTOR,AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,Direction.EAST));var owner=h.getBlockEntity(ACTOR,DeployerBlockEntity.class);owner.setHeldItem(new ItemStack(AllItems.WRENCH));owner.setFilter(new ItemStack(AllItems.WRENCH));owner.getPlayer();owner.inventoryChanged();return owner;
    }
    private static void activate(DeployerBlockEntity owner){DeployerHandler.activate(owner,owner.getPlayer());}
    @GameTest public void actualStationaryCartPickupIsSavedAndExtractableFromAutomation(GameTestHelper h)throws Exception{
        var forest=MinecartNativeGameTests.forest(h);var owner=stationary(h);activate(owner);var outputs=owner.getOverflowItems();h.assertTrue(forest.cart().isRemoved()&&!forest.source().ownsPackedReceipt()&&outputs.size()==1&&packed(outputs.getFirst()),"Real source cart retires only after actual canonical saved BE output installation");
        var saved=owner.saveWithFullMetadata(h.getLevel().registryAccess());var values=saved.read("Overflow",ItemStack.CODEC.listOf()).orElseThrow();h.assertTrue(values.size()==1&&packed(values.getFirst()),"Actual native BE serializer owns exactly one complete receipt");
        try(var tx=Transaction.openOuter()){h.assertValueEqual(owner.inventory.extract(ItemVariant.of(outputs.getFirst()),1,tx),1L,"Real automation sees canonical component-preserving receipt");tx.commit();}
        h.assertTrue(owner.getOverflowItems().isEmpty()&&loose(h)==0&&owner.getHeldItem().is(AllItems.WRENCH),"One real extraction clears source output and preserves independent actual tool");h.succeed();
    }
    @GameTest public void actualStationaryOutputSurvivesFreshNativeOwnerLoadBeforeExtraction(GameTestHelper h)throws Exception{
        var forest=MinecartNativeGameTests.forest(h);var owner=stationary(h);activate(owner);var saved=owner.saveWithFullMetadata(h.getLevel().registryAccess());var actual=BlockEntity.loadStatic(owner.getBlockPos(),owner.getBlockState(),saved,h.getLevel().registryAccess());h.getLevel().removeBlockEntity(owner.getBlockPos());h.getLevel().setBlockEntity(actual);
        var restored=(DeployerBlockEntity)actual;var outputs=restored.getOverflowItems();h.assertTrue(restored!=owner&&outputs.size()==1&&packed(outputs.getFirst())&&forest.cart().isRemoved(),"Actual native BE factory/load replaces the old owner and restores one original output");
        try(var tx=Transaction.openOuter()){h.assertValueEqual(restored.inventory.extract(ItemVariant.of(outputs.getFirst()),1,tx),1L,"Fresh actual world owner automation extracts the retained complete receipt");tx.commit();}h.assertTrue(restored.getOverflowItems().isEmpty()&&loose(h)==0,"No generic avatar output or worlddrop duplicates native reload");h.succeed();
    }
    @GameTest public void actualRemovedStationaryDeployerDropsItsOneCanonicalPackedOutput(GameTestHelper h)throws Exception{
        var forest=MinecartNativeGameTests.forest(h);var owner=stationary(h);activate(owner);var original=owner.getOverflowItems().getFirst();var receipt=PackedCartData.receiptId(original);h.assertTrue(h.getLevel().destroyBlock(owner.getBlockPos(),true),"Real native world removal executes original BE preRemoveSideEffects");
        var outputs=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(ACTOR)).inflate(3),item->PackedCartData.hasReceipt(item.getItem(),receipt));h.assertTrue(outputs.size()==1&&outputs.getFirst().getItem().getCount()==1&&owner.getOverflowItems().isEmpty()&&forest.cart().isRemoved(),"Actual removal journal handoff gives one genuine native packed drop and no remaining BE source");DeployerOutputRecovery.tick(h.getLevel());h.assertValueEqual(loose(h),1L,"Real retry cannot reissue already accepted output");h.succeed();
    }
    private record Moving(ControlledContraptionEntity owner,MovementContext context,DeployerMovementBehaviour behavior,DeployerFakePlayer actor){}
    private static Moving moving(GameTestHelper h,boolean full)throws Exception{
        h.setBlock(ACTOR,AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,Direction.EAST));var be=h.getBlockEntity(ACTOR,DeployerBlockEntity.class);be.setHeldItem(new ItemStack(AllItems.WRENCH));be.setFilter(new ItemStack(AllItems.WRENCH));h.setBlock(ACTOR.south(),Blocks.BARREL);
        if(full){var barrel=h.getBlockEntity(ACTOR.south(),BarrelBlockEntity.class);for(int slot=0;slot<barrel.getContainerSize();slot++)barrel.setItem(slot,new ItemStack(Items.STONE,64));}
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(ACTOR),h.absolutePos(ACTOR.south())))),"Actual moving source glue admitted");
        var structure=new BearingContraption(false,Direction.UP);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(ACTOR.below()))&&structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Independent actual source actor/storage captured");
        var owner=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());owner.setContraption(structure);owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(ACTOR)));h.assertTrue(h.getLevel().addFreshEntity(owner),"Actual independent moving owner registered before output action");owner.tickActors();
        var context=structure.getActorAt(BlockPos.ZERO).right;var behavior=(DeployerMovementBehaviour)MovementBehaviour.REGISTRY.get(context.state);var actor=behavior.getPlayer(context);h.assertTrue(context.position!=null&&context.temporaryData==actor&&owner.hasOwnedStructure(),"Real owned actor computes its genuine work position");return new Moving(owner,context,behavior,actor);
    }
    private static long stored(Moving actor){long count=0;for(var view:actor.owner.getContraption().getStorage().getAllItems())if(view.getResource().toStack().has(AllDataComponents.MINECRAFT_CONTRAPTION_DATA))count+=view.getAmount();return count;}
    @GameTest public void actualMovingCartPickupTransfersToRealMountedStorage(GameTestHelper h)throws Exception{
        var forest=MinecartNativeGameTests.forest(h);var actor=moving(h,false);actor.behavior.visitNewPosition(actor.context,h.absolutePos(TARGET));
        h.assertTrue(forest.cart().isRemoved()&&!forest.source().ownsPackedReceipt()&&stored(actor)==1&&loose(h)==0,"Real actor pickup passes through canonical output and genuine transactional mounted storage");
        h.assertTrue(actor.actor.movingOverflow.stream().noneMatch(DeployerCartPickupGameTests::packed)&&actor.actor.getMainHandItem().is(AllItems.WRENCH),"Canonical queue drained once and original filtered tool remains");
        var saved=actor.owner.getContraption().writeNBT(h.getLevel().registryAccess(),false);h.assertTrue(saved.contains("Blocks"),"Actual owner serializes after the accepted native transfer");h.assertValueEqual(stored(actor),1L,"Serialization does not create or remove a second material output");h.succeed();
    }
    @GameTest public void actualFullMovingStoragePublishesNativePackedDropAfterClearingCanonicalQueue(GameTestHelper h)throws Exception{
        var forest=MinecartNativeGameTests.forest(h);var actor=moving(h,true);var seen=new java.util.concurrent.atomic.AtomicReference<ItemEntity>();
        try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity instanceof ItemEntity item&&packed(item.getItem()),entity->{seen.set((ItemEntity)entity);h.assertTrue(com.simibubi.create.foundation.utility.NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(entity),"Actual native output section insertion is witnessed");h.assertTrue(actor.actor.movingOverflow.stream().noneMatch(DeployerCartPickupGameTests::packed),"Actual raw actor queue revoked before native callback");var saved=actor.owner.getContraption().writeNBT(h.getLevel().registryAccess(),false);var overflow=actor.context.blockEntityData.read("Overflow",ItemStack.CODEC.listOf()).orElseThrow();h.assertTrue(overflow.stream().noneMatch(DeployerCartPickupGameTests::packed)&&saved.contains("Blocks"),"Actual source save flushes cleared canonical output before callback");})){
            actor.behavior.visitNewPosition(actor.context,h.absolutePos(TARGET));
        }h.assertTrue(seen.get()!=null&&forest.cart().isRemoved()&&loose(h)==1&&stored(actor)==0,"One complete real native output, no source/target storage duplicate");h.succeed();
    }
    @GameTest public void actualMovingQueuePersistsThroughNativeOwnerUnloadLoadAndStorageDrain(GameTestHelper h)throws Exception{
        var forest=MinecartNativeGameTests.forest(h);var actor=moving(h,false);actor.behavior.activate(actor.context,h.absolutePos(TARGET),actor.actor,DeployerBlockEntity.Mode.USE);h.assertTrue(actor.actor.movingOverflow.stream().filter(DeployerCartPickupGameTests::packed).count()==1&&forest.cart().isRemoved(),"Actual scoped action installed receipt in genuine actor queue before ordinary disposal");
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,h.getLevel().registryAccess());actor.owner.saveWithoutId(out);actor.owner.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var restored=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());restored.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),out.buildResult()));h.assertTrue(h.getLevel().addFreshEntity(restored),"Actual native entity and saved actor root reloaded");restored.tickActors();var context=restored.getContraption().getActorAt(BlockPos.ZERO).right;var behavior=(DeployerMovementBehaviour)MovementBehaviour.REGISTRY.get(context.state);var player=behavior.getPlayer(context);var live=new Moving(restored,context,behavior,player);
        behavior.visitNewPosition(context,h.absolutePos(TARGET));h.assertTrue(stored(live)==1&&loose(h)==0&&player.movingOverflow.stream().noneMatch(DeployerCartPickupGameTests::packed),"Actual restored actor inventory/queue drain delivers one complete receipt to real mounted storage");h.succeed();
    }
}
