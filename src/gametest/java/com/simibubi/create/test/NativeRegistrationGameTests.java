package com.simibubi.create.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.material.Fluids;

/** Observes genuine test-mod/native bootstrap events and actual native association values. UNRUN. */
public final class NativeRegistrationGameTests {
    @GameTest public void callbacksBeforeOwnerBeforeEntryAndAfterNativePublication(GameTestHelper h){
        h.assertTrue(NativeRegistrationFixtures.bootstrapCompleted,"Actual test mod must finish its real bootstrap");
        h.assertTrue(BuiltInRegistries.REGISTRY.getValue(NativeRegistrationFixtures.KEY.identifier())==NativeRegistrationFixtures.registry,"Actual native root registry owns the fixture");
        h.assertTrue(NativeRegistrationFixtures.registry.getValue(NativeRegistrationFixtures.EARLY_ID)==NativeRegistrationFixtures.early&&NativeRegistrationFixtures.observedEarly.get()==NativeRegistrationFixtures.early,"Before-owner callback sees actual native registered entry");
        h.assertTrue(NativeRegistrationFixtures.beforeOwner.get()==1&&NativeRegistrationFixtures.beforeEntry.get()==1&&NativeRegistrationFixtures.afterEntry.get()==1&&NativeRegistrationFixtures.directOwner.get()==1,"Actual before/after/direct callbacks each execute exactly once");
        h.assertTrue(NativeRegistrationFixtures.DIRECT_ASSOCIATION.get(Blocks.STONE)==NativeRegistrationFixtures.early,"Actual direct callback publishes actual SimpleRegistry association");h.succeed();
    }
    @GameTest public void builderPublishesActualSingleMultiAndTagAssociations(GameTestHelper h){
        var value=NativeRegistrationFixtures.built;
        h.assertTrue(value!=null&&NativeRegistrationFixtures.registry.getValue(NativeRegistrationFixtures.BUILDER_ID)==value&&NativeRegistrationFixtures.builder.get()==value,"Builder supplies the actual native bound entry");
        h.assertTrue(NativeRegistrationFixtures.factoryCalls.get()==1&&NativeRegistrationFixtures.publishedCallbacks.get()==1&&NativeRegistrationFixtures.lateCallbacks.get()==1&&NativeRegistrationFixtures.reentrantBuilderRejections.get()==1,"Real factory and source/late callbacks run once");
        h.assertTrue(NativeRegistrationFixtures.BY_BLOCK.get(Blocks.COBBLESTONE).equals(List.of(value,value)),"Source Multi associations preserve both real registrations");
        h.assertTrue(NativeRegistrationFixtures.BY_ENTITY.get(EntityTypes.PIG)==value&&NativeRegistrationFixtures.BY_BE.get(BlockEntityTypes.BARREL)==value&&NativeRegistrationFixtures.BY_FLUID.get(Fluids.WATER)==value,"All native source direct association kinds publish the same genuine entry");
        h.assertTrue(NativeRegistrationFixtures.BY_TAG.get(Blocks.STONE)==NativeRegistrationFixtures.tagBuilt&&NativeRegistrationFixtures.BY_TAG.get(Blocks.DIRT)==null,"Source tag association follows actual loaded vanilla tags");h.succeed();
    }
    @GameTest public void missingIdsAndDuplicateNativeOwnersNeverFabricatePublication(GameTestHelper h){
        h.assertTrue(NativeRegistrationFixtures.missingDefaultedCallback.get()==0,"Missing native BLOCK ID must not publish default AIR");
        h.assertTrue(NativeRegistrationFixtures.duplicateBuilderRejections.get()==1&&NativeRegistrationFixtures.factoryCalls.get()==1,"Repeated builder registration rejects without recreating an entry");
        h.assertTrue(NativeRegistrationFixtures.duplicateOwnerRejections.get()==1&&NativeRegistrationFixtures.registry.getValue(NativeRegistrationFixtures.BUILDER_ID)==NativeRegistrationFixtures.built,"Conflicting native owner cannot steal callback custody");
        h.assertTrue(NativeRegistrationFixtures.unsupportedAssociations.get()==1&&NativeRegistrationFixtures.unpublishedGetRejections.get()==1,"Source missing-association and unpublished-entry guards execute");
        h.assertTrue("actual-parent".equals(NativeRegistrationFixtures.actualParent),"Native build registers and returns actual parent");h.succeed();
    }
}
