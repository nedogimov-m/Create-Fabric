package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.foundation.utility.NeoForgeBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeMovementContextImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;

/** Real pinned serialized owners through actual native BEs, storage transactions and save/reload. */
public final class NeoForgeProcessingMigrationGameTests {
    private static CompoundTag item(String id, int count) {
        var result = new CompoundTag(); result.putString("id", id); result.putInt("count", count);
        var components = new CompoundTag(); var custom = new CompoundTag(); custom.putString("owner", "genuine original processing stock");
        components.put("minecraft:custom_data", custom); result.put("components", components); return result;
    }
    private static CompoundTag handler(int size) { var tag = new CompoundTag(); tag.putInt("Size", size); tag.put("Items", new ListTag()); return tag; }
    private static void slot(CompoundTag handler, int index, String id, int amount) {
        var item = item(id, amount); item.putInt("Slot", index); ((ListTag)handler.get("Items")).add(item);
    }
    private static CompoundTag base(String type) { var tag = new CompoundTag(); tag.putString("id", "create:" + type); return tag; }
    private static CompoundTag fluid(int amount) {
        var result = new CompoundTag(); result.putString("id", "minecraft:water"); result.putInt("amount", amount); return result;
    }
    private static ListTag tanks(int amount) {
        var list = new ListTag(); var tank = new CompoundTag(); var content = new CompoundTag(); if (amount != 0) content.put("Fluid", fluid(amount)); tank.put("TankContent", content);
        tank.put("Level", new CompoundTag()); list.add(tank); var second = new CompoundTag(); second.put("TankContent", new CompoundTag()); second.put("Level", new CompoundTag()); list.add(second); return list;
    }
    private static CompoundTag basinSource() {
        var source = base("basin"); source.put("InputItems", handler(9)); source.put("OutputItems", handler(9));
        source.put("InputTanks", tanks(0)); source.put("OutputTanks", tanks(0)); source.put("Overflow", new ListTag());
        source.put("FluidOverflow", new ListTag()); source.put("DisabledSpoutput", new ListTag()); return source;
    }
    private static CompoundTag convert(GameTestHelper h, CompoundTag source) {
        return NeoForgeBlockEntityImport.convert(source, 3955, h.getLevel().registryAccess(),
            NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(), h.absolutePos(new BlockPos(2,2,2))));
    }
    private static void load(GameTestHelper h, BlockEntity be, CompoundTag tag) {
        var problems = new ProblemReporter.Collector();
        be.loadWithComponents(TagValueInput.create(problems, h.getLevel().registryAccess(), tag));
        h.assertTrue(problems.isEmpty(), "Native typed BE decoder accepted every migrated field: " + problems.getReport());
    }
    private static CompoundTag sawSource(boolean applied) {
        var source = base("saw"); var inventory = handler(32);
        slot(inventory, applied ? 31 : 0, "minecraft:diamond", applied ? 17 : 13);
        inventory.putFloat("ProcessingTime", 7.25f); inventory.putFloat("RecipeTime", 55.5f); inventory.putBoolean("AppliedRecipe", applied);
        source.put("Inventory", inventory); source.putInt("RecipeIndex", 3); return source;
    }
    @GameTest public void genuineBasinNinthSlotsAndPaidOverflowSurviveNativeReloadAndPartialSpoutTransfer(GameTestHelper h) {
        var source = basinSource(); slot(source.getCompoundOrEmpty("InputItems"), 8, "minecraft:emerald", 3);
        for (int i=0;i<9;i++) slot(source.getCompoundOrEmpty("OutputItems"), i, "minecraft:cobblestone", 64);
        source.put("InputTanks", tanks(250)); source.put("OutputTanks", tanks(125));
        ((ListTag)source.get("Overflow")).add(item("minecraft:diamond", 11));
        ((ListTag)source.get("FluidOverflow")).add(fluid(100)); var unchanged = source.copy();
        var data = convert(h, source); var pos = new BlockPos(2,2,2);
        h.setBlock(pos, AllBlocks.BASIN.defaultBlockState().setValue(BasinBlock.FACING, Direction.EAST));
        h.setBlock(pos.east().below(), AllBlocks.BASIN);
        var basin = h.getBlockEntity(pos, BasinBlockEntity.class); var target = h.getBlockEntity(pos.east().below(), BasinBlockEntity.class);
        load(h, basin, data);
        try (Transaction tx=Transaction.openOuter()) {
            target.inventory.automation.insert(ItemVariant.of(basin.spoutBuffer.items().getFirst()), 60, tx);
            target.fluids.automation.insert(FluidVariant.of(Fluids.WATER), 76950, tx); tx.commit();
        }
        h.assertTrue(basin.inventory.inputs.get(8).getAmount()==3 && basin.inventory.outputs.get(8).getAmount()==64
            && basin.fluids.inputs.getFirst().getAmount()==20250 && basin.fluids.outputs.getFirst().getAmount()==10125
            && !basin.canContinueProcessing() && source.equals(unchanged), "Ninth input/output slots, both tanks, already-paid queues and source bytes retained");
        basin.spoutBuffer.flush();
        h.assertTrue(basin.spoutBuffer.items().size()==1 && basin.spoutBuffer.items().getFirst().getCount()==7
            && target.inventory.inputs.getFirst().getAmount()==64 && basin.spoutBuffer.fluids().getFirst().amount()==8100
            && target.fluids.inputs.getFirst().getAmount()==76950, "Actual native item target accepted4, seven stayed owned; partial fluid insertion rolled back completely");
        var saved=basin.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=new BasinBlockEntity(basin.getBlockPos(),basin.getBlockState());load(h,restored,saved);
        h.assertTrue(restored.spoutBuffer.items().getFirst().getCount()==7 && restored.spoutBuffer.fluids().getFirst().amount()==8100
            && restored.inventory.outputs.get(8).getAmount()==64, "Real native re-save/reload keeps remaining queue separately from all full output slots");h.succeed();
    }
    @GameTest public void genuineMillstoneRetainsInputIdentityAndInFlightTimerThroughNativeSave(GameTestHelper h) {
        var source=base("millstone");source.put("InputInventory",handler(1));source.put("OutputInventory",handler(9));source.putInt("Timer",37);
        slot(source.getCompoundOrEmpty("InputInventory"),0,"minecraft:wheat",5);slot(source.getCompoundOrEmpty("OutputInventory"),8,"minecraft:diamond",9);
        var data=convert(h,source);var be=new MillstoneBlockEntity(BlockPos.ZERO,AllBlocks.MILLSTONE.defaultBlockState());load(h,be,data);
        var saved=be.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(be.getProcessingTimer()==37 && be.inventory.inputs.getFirst().getAmount()==5 && be.inventory.outputs.get(8).getAmount()==9
            && saved.getCompoundOrEmpty("Processing").equals(data.getCompoundOrEmpty("Processing")),"Actual native timer/processing identity agree, preventing first-tick reset; ninth output retained");h.succeed();
    }
    @GameTest public void genuineUpToFilterKeepsItsSavedCountWhenSampleHasLowerStackLimit(GameTestHelper h) {
        var source=basinSource();source.put("Filter",item("create:filter",1));source.putInt("FilterAmount",64);source.putBoolean("UpTo",true);
        var basin=new BasinBlockEntity(BlockPos.ZERO,AllBlocks.BASIN.defaultBlockState());load(h,basin,convert(h,source));
        h.assertTrue(basin.getFilter().getFilter().getCount()==1 && basin.getFilter().getAmount()==64 && basin.getFilter().upTo,
            "Pinned setFilter does not clamp existing upTo count when a smaller-stack sample is selected; physical filter remains one owned item");h.succeed();
    }
    @GameTest public void genuineAppliedSawKeepsThirtySecondSlotAsPaidOutputWithoutAnotherRecipeRoll(GameTestHelper h) {
        var source=sawSource(true);var unchanged=source.copy();var be=new SawBlockEntity(BlockPos.ZERO,AllBlocks.MECHANICAL_SAW.defaultBlockState().setValue(SawBlock.FACING,Direction.UP));
        load(h,be,convert(h,source));var saved=be.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(be.inventory.input().isEmpty() && be.inventory.outputs().size()==1 && be.inventory.outputs().getFirst().getCount()==17
            && be.inventory.appliedRecipe && be.inventory.remainingTime==7.25f && source.equals(unchanged)
            && !saved.getCompoundOrEmpty("Inventory").getBooleanOr("SourceRecipeIndex",false),"Already-applied actual source slot31 stays output, never new input or unpaid roll");h.succeed();
    }
    @GameTest public void genuineUnappliedSawKeepsExactTimerAndSourceIndexWithoutInventingRecipeId(GameTestHelper h) {
        var be=new SawBlockEntity(BlockPos.ZERO,AllBlocks.MECHANICAL_SAW.defaultBlockState().setValue(SawBlock.FACING,Direction.UP));load(h,be,convert(h,sawSource(false)));
        var saved=be.saveWithFullMetadata(h.getLevel().registryAccess()).getCompoundOrEmpty("Inventory");
        h.assertTrue(be.inventory.input().getCount()==13 && be.inventory.outputs().isEmpty() && !be.inventory.appliedRecipe
            && be.inventory.remainingTime==7.25f && be.inventory.recipeDuration==55.5f && be.inventory.recipeIndex==3
            && saved.getBooleanOr("SourceRecipeIndex",false) && !saved.contains("Recipe"),"Source index is retained as a selector; no original recipe ID existed to guess");h.succeed();
    }
    @GameTest public void malformedOriginalProcessingCannotBeClampedOrLoseSparseStock(GameTestHelper h) {
        var duplicate=basinSource();slot(duplicate.getCompoundOrEmpty("OutputItems"),8,"minecraft:diamond",1);slot(duplicate.getCompoundOrEmpty("OutputItems"),8,"minecraft:emerald",1);
        var wrongSize=basinSource();wrongSize.getCompoundOrEmpty("OutputItems").putInt("Size",8);
        var tooMuchFluid=basinSource();tooMuchFluid.put("InputTanks",tanks(1001));
        var saw=sawSource(false);saw.getCompoundOrEmpty("Inventory").putFloat("ProcessingTime",Float.NaN);
        var wrongOutput=sawSource(false);slot(wrongOutput.getCompoundOrEmpty("Inventory"),31,"minecraft:emerald",2);
        var tooManyItems=basinSource();slot(tooManyItems.getCompoundOrEmpty("InputItems"),0,"minecraft:diamond",128);
        for(var source:java.util.List.of(duplicate,wrongSize,tooMuchFluid,saw,wrongOutput,tooManyItems)){
            var unchanged=source.copy();boolean rejected=false;try{convert(h,source);}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected&&source.equals(unchanged),"Invalid source is refused before any native publication or in-place mutation");
        }h.succeed();
    }
    private static ListTag vector(double x,double y,double z){var result=new ListTag();result.add(DoubleTag.valueOf(x));result.add(DoubleTag.valueOf(y));result.add(DoubleTag.valueOf(z));return result;}
    private static CompoundTag xyz(int x,int y,int z){var result=new CompoundTag();result.putInt("X",x);result.putInt("Y",y);result.putInt("Z",z);return result;}
    private static CompoundTag actor(){var result=new CompoundTag();result.put("Pos",xyz(1,2,3));result.put("Motion",vector(.1,0,0));result.put("RelativeMotion",vector(.1,0,0));result.putBoolean("Stall",false);result.putBoolean("FirstMovement",false);result.put("Data",new CompoundTag());return result;}
    @GameTest public void actualSeatActorAndScopedTransportIdentityKeepTypedPositionsAndSourceOwner(GameTestHelper h){
        var source=actor();source.getCompoundOrEmpty("Data").putInt("SeatIndex",0);var unchanged=source.copy();
        var state=AllBlocks.SEATS.get(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState();
        var nativeRow=NeoForgeMovementContextImport.convert(state,source,3955,h.getLevel().registryAccess());
        var id=UUID.fromString("91ad83fa-4976-4599-a523-c63f7e869180");
        var first=NeoForgeMigrationScope.train(id,0).block(new BlockPos(1,2,3)).nativeTransportIdentity("held",0);
        h.assertTrue(nativeRow.read("Pos",BlockPos.CODEC).orElseThrow().equals(new BlockPos(1,2,3))&&source.equals(unchanged)
            && first.equals(NeoForgeMigrationScope.train(id,0).block(new BlockPos(1,2,3)).nativeTransportIdentity("held",0))
            && !first.equals(NeoForgeMigrationScope.train(id,1).block(new BlockPos(1,2,3)).nativeTransportIdentity("held",0)),"Actual original local position/seat index retained; NEW native transport IDs depend on actual containing owner");h.succeed();
    }
    @GameTest public void genuineVanillaChestItemsUseTypedFixerAndNativeSlotsWithoutComponentLoss(GameTestHelper h) {
        var source=new CompoundTag();source.putString("id","minecraft:chest");var contents=new ListTag();
        var original=item("minecraft:diamond",11);original.putByte("Slot",(byte)26);contents.add(original);source.put("Items",contents);
        var unchanged=source.copy();var nativeTag=NeoForgeBlockEntityImport.convert(source,3955,h.getLevel().registryAccess());
        var pos=new BlockPos(2,2,2);h.setBlock(pos,Blocks.CHEST);var chest=h.getBlockEntity(pos,net.minecraft.world.level.block.entity.ChestBlockEntity.class);load(h,chest,nativeTag);
        h.assertTrue(chest.getItem(26).getCount()==11 && chest.getItem(26).has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
            && source.equals(unchanged),"Actual source vanilla Slot BYTE26 retains all eleven component-bearing items through native factory decode");
        var malformed=source.copy();malformed.getListOrEmpty("Items").add(original.copy());boolean rejected=false;
        try{NeoForgeBlockEntityImport.convert(malformed,3955,h.getLevel().registryAccess());}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected,"Duplicate physical vanilla slot refuses before publication");h.succeed();
    }
    @GameTest(maxTicks=60) public void paidBasinOverflowSurvivesActualNativeRefusalAndSourceRemoval(GameTestHelper h) throws Exception {
        var pos=new BlockPos(2,2,2);h.setBlock(pos,AllBlocks.BASIN);var basin=h.getBlockEntity(pos,BasinBlockEntity.class);
        var source=basinSource();((ListTag)source.get("Overflow")).add(item("minecraft:diamond",11));load(h,basin,convert(h,source));
        var point=net.minecraft.world.phys.Vec3.atCenterOf(basin.getBlockPos());
        var blocker=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),point.x,point.y,point.z,new ItemStack(Items.STICK));
        h.assertTrue(h.getLevel().addFreshEntity(blocker),"Actual native blocker admitted");
        try(var refusal=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().is(Items.DIAMOND),entity->entity.setUUID(blocker.getUUID()))) {
            h.getLevel().destroyBlock(basin.getBlockPos(),false);
        }
        var data=h.getLevel().getDataStorage().computeIfAbsent(com.simibubi.create.content.processing.basin.BasinOverflowRecovery.TYPE);
        var ops=h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var serialized=com.simibubi.create.content.processing.basin.BasinOverflowRecovery.CODEC.encodeStart(ops,data).getOrThrow();
        var restored=com.simibubi.create.content.processing.basin.BasinOverflowRecovery.CODEC.parse(ops,serialized).getOrThrow();
        h.assertTrue(basin.spoutBuffer.isEmpty() && basin.isRemoved() && serialized.equals(com.simibubi.create.content.processing.basin.BasinOverflowRecovery.CODEC.encodeStart(ops,restored).getOrThrow()),
            "Source revoked once; rejected exact native endpoint lives in codec-valid saved recovery after real block removal");
        blocker.discard();
        h.runAfterDelay(20,()->{
            long amount=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(basin.getBlockPos()).inflate(3)).stream()
                .filter(entity->entity.getItem().is(Items.DIAMOND)).mapToLong(entity->entity.getItem().getCount()).sum();
            var saved=(CompoundTag)com.simibubi.create.content.processing.basin.BasinOverflowRecovery.CODEC.encodeStart(ops,data).getOrThrow();
            h.assertTrue(amount==11 && saved.getListOrEmpty("Pending").isEmpty(),"Actual recovery retry emits exactly eleven paid items after refusal ends, with no second BE owner or refund");h.succeed();
        });
    }

}
