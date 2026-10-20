package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.mounted.*;
import com.simibubi.create.content.equipment.clipboard.*;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packager.repackager.PackageRepackageHelper;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.stockTicker.*;
import com.simibubi.create.foundation.item.ItemSampleEquality;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.apache.commons.lang3.mutable.MutableBoolean;

/** Exact physical variants remain distinct while actual original source samples retain their value semantics. All cases UNRUN. */
public final class NeoForgePackedValueSemanticsGameTests {
    private static ListTag rows(Tag... values){var result=new ListTag();for(var value:values)result.add(value);return result;}
    private static CompoundTag xyz(){var tag=new CompoundTag();tag.putInt("X",0);tag.putInt("Y",0);tag.putInt("Z",0);return tag;}
    private static CompoundTag originalPacked(){
        var state=AllBlocks.MINECART_ANCHOR.defaultBlockState();var sourceState=new CompoundTag();sourceState.putString("Name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());var properties=new CompoundTag();state.getValues().forEach(value->properties.putString(value.property().getName(),value.valueName()));if(!properties.isEmpty())sourceState.put("Properties",properties);
        var row=new CompoundTag();row.putLong("Pos",BlockPos.ZERO.asLong());row.putInt("State",0);var blocks=new CompoundTag();blocks.put("Palette",rows(sourceState));blocks.put("BlockList",rows(row));
        var body=new CompoundTag();body.putString("Type","create:mounted");body.putString("RotationMode","ROTATE");body.putString("InitialOrientation","EAST");body.put("Anchor",xyz());body.putBoolean("Stalled",false);body.putBoolean("BottomlessSupply",false);body.put("Blocks",blocks);
        for(String key:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions"))body.put(key,new ListTag());
        var components=new CompoundTag();components.put("create:minecart_contraption_data",body);var item=new CompoundTag();item.putString("id","create:minecart_contraption");item.putInt("count",1);item.put("components",components);return item;
    }

    private static ItemStack migrated(GameTestHelper h,int slot){return migrated(h,originalPacked(),slot);}
    private static ItemStack migrated(GameTestHelper h,CompoundTag source,int slot){var scope=NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(new BlockPos(2,2,2))).item("inventory",slot);return ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),NeoForgeSaveCodecs.item(source,3955,h.getLevel().registryAccess(),scope)).getOrThrow();}
    private static UUID plan(ItemStack actual){return PackedCartData.receiptId(actual);}
    @GameTest public void originalValueMatchesAcrossRealOwnersWhileNativePhysicalIdentityRemainsExact(GameTestHelper h){
        var source=originalPacked();var old=source.copy();var first=migrated(h,source,0);var second=migrated(h,source,1);
        h.assertTrue(!plan(first).equals(plan(second))&&!ItemStack.isSameItemSameComponents(first,second)&&!ItemVariant.of(first).equals(ItemVariant.of(second)),"Distinct actual source inventory slots retain distinct exact native plan/component/transfer identities");
        var keys=new HashMap<ItemSampleEquality.Key,Integer>();keys.put(ItemSampleEquality.key(first),11);
        h.assertTrue(ItemSampleEquality.same(first,second)&&ItemSampleEquality.hash(first)==ItemSampleEquality.hash(second)&&keys.get(ItemSampleEquality.key(second))==11&&source.equals(old),"Only sample comparisons share the unchanged genuine original packed value and have consistent map hashes");
        var different=source.copy();different.getCompoundOrEmpty("components").getCompoundOrEmpty("create:minecart_contraption_data").putString("InitialOrientation","WEST");h.assertTrue(!ItemSampleEquality.same(first,migrated(h,different,2)),"Actual original orientation remains part of sample value");
        var chest=source.copy();chest.putString("id","create:chest_minecart_contraption");h.assertTrue(!ItemSampleEquality.same(first,migrated(h,chest,3)),"Actual enclosing registered item kind remains part of sample identity");h.succeed();
    }
    @GameTest public void typedSourceValueIgnoresOnlyCompoundKeyInsertionOrderAndRetainsNumericTypesAndListOrder(GameTestHelper h){
        var first=new CompoundTag();first.putInt("count",11);first.put("rows",rows(IntTag.valueOf(1),IntTag.valueOf(2)));var reversed=new CompoundTag();reversed.put("rows",first.get("rows").copy());reversed.putInt("count",11);
        h.assertTrue(NeoForgePackedValueSemantics.originalValue(first).equals(NeoForgePackedValueSemantics.originalValue(reversed)),"Compound insertion order does not create a different genuine source value");
        reversed.putByte("count",(byte)11);h.assertTrue(!NeoForgePackedValueSemantics.originalValue(first).equals(NeoForgePackedValueSemantics.originalValue(reversed)),"Original byte11 cannot equal original int11");reversed=first.copy();reversed.put("rows",rows(IntTag.valueOf(2),IntTag.valueOf(1)));h.assertTrue(!NeoForgePackedValueSemantics.originalValue(first).equals(NeoForgePackedValueSemantics.originalValue(reversed)),"Actual list order remains significant");h.succeed();
    }
    @GameTest public void laterNativePlanEditInvalidatesHistoricalSemanticKeyWithoutRebindingIt(GameTestHelper h){
        var original=migrated(h,0);var edited=migrated(h,1);var actual=edited.get(AllDataComponents.MINECRAFT_CONTRAPTION_DATA).copy();actual.getCompoundOrEmpty("Contraption").putBoolean("Stalled",true);edited.set(AllDataComponents.MINECRAFT_CONTRAPTION_DATA,actual);
        h.assertTrue(NeoForgePackedValueSemantics.validOriginalValue(actual)==null&&!ItemSampleEquality.same(original,edited)&&ItemSampleEquality.same(edited,edited.copy()),"A real native body edit falls back to exact current value and cannot keep an obsolete historical match");
        var parsed=LegacyPackedCartPlan.parse(edited).encode();h.assertTrue(!parsed.contains(NeoForgePackedValueSemantics.SOURCE_VALUE)&&!parsed.contains(NeoForgePackedValueSemantics.NATIVE_IMAGE),"Native parse/re-encode never blesses a stale source key against changed body state");h.succeed();
    }
    @GameTest public void realFilterSummaryPromiseAndCraftingRequestCompareOriginalSampleValues(GameTestHelper h){
        var physical=migrated(h,0);var sample=migrated(h,1);h.assertTrue(FilterItemStack.of(sample).test(h.getLevel(),physical,true),"Actual component-sensitive filter accepts the same original value from its true physical source owner");
        var summary=new InventorySummary();summary.add(physical,7);summary.add(sample,4);h.assertTrue(summary.getCountOf(physical)==11&&summary.getCountOf(sample)==11&&summary.getStacks().size()==1,"Telemetry and request summary aggregate genuine same original values");
        var queue=new RequestPromiseQueue(()->{});queue.add(new RequestPromise(new BigItemStack(sample,11)));queue.itemEnteredSystem(physical,7);h.assertTrue(queue.getTotalPromisedAndRemoveExpired(physical,-1)==4,"Actual arriving physical variant satisfies seven of eleven semantically matching promised samples");
        var order=new PackageOrderWithCrafts(new PackageOrder(List.of(new BigItemStack(physical,2))),List.of(new PackageOrderWithCrafts.CraftingEntry(new PackageOrder(List.of(new BigItemStack(sample,1))),2)));
        h.assertTrue(order.orderedStacksMatchOrderedRecipes()&&new BigItemStack(physical,11).equals(new BigItemStack(sample,11))&&new BigItemStack(physical,11).hashCode()==new BigItemStack(sample,11).hashCode(),"Actual order recipe map and counted sample wrapper agree on equality and hash");h.succeed();
    }
    private static ItemStack clipboard(ItemStack packed){
        var filter=new ItemStack(AllItems.FILTER);filter.set(AllDataComponents.FILTER_ITEMS,ItemContainerContents.fromItems(List.of(packed)));filter.set(AllDataComponents.FILTER_ITEMS_RESPECT_NBT,true);
        var text=Component.literal("Real sample").withStyle(style->style.withBold(true).withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromStack(filter))));var entry=new ClipboardEntry(false,text).displayItem(filter,11);var clipboard=new ItemStack(AllBlocks.CLIPBOARD);clipboard.set(AllDataComponents.CLIPBOARD_CONTENT,new ClipboardContent(ClipboardType.WRITTEN,List.of(List.of(entry)),false));clipboard.set(DataComponents.CUSTOM_NAME,text);return clipboard;
    }
    @GameTest public void typedNestedClipboardIconsAndRichItemHoversKeepSampleMeaningWithoutNormalizingRealComponents(GameTestHelper h){
        var first=clipboard(migrated(h,0));var second=clipboard(migrated(h,1));var old=first.copy();
        h.assertTrue(!ItemStack.isSameItemSameComponents(first,second)&&ItemSampleEquality.same(first,second)&&ItemSampleEquality.hash(first)==ItemSampleEquality.hash(second)&&ItemStack.matches(first,old),"Explicit clipboard icon/filter/rich-hover paths compare original source values while retaining the exact physical components");
        second.set(DataComponents.CUSTOM_NAME,Component.literal("Different sample"));h.assertTrue(!ItemSampleEquality.same(first,second),"Ordinary native component differences remain significant");h.succeed();
    }
    @GameTest public void actualPackagerExtractsAndRollsBackRealVariantsChosenBySemanticRequest(GameTestHelper h){
        var position=new BlockPos(3,2,3);h.setBlock(position.west(),Blocks.CHEST);h.setBlock(position,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.EAST));var chest=h.getBlockEntity(position.west(),ChestBlockEntity.class);var first=migrated(h,0);var second=migrated(h,1);var sample=migrated(h,2);var firstValue=PackedCartData.detachedItemCopy(first);var secondValue=PackedCartData.detachedItemCopy(second);var sourceIds=Set.of(plan(first),plan(second));var sampleId=plan(sample);chest.setItem(0,first);chest.setItem(1,second);var packager=h.getBlockEntity(position,PackagerBlockEntity.class);
        try(var preview=Transaction.openOuter()){var requests=new ArrayList<>(List.of(PackagingRequest.create(sample,2,"",0,new MutableBoolean(true),0,47,null)));h.assertTrue(packager.fulfillRequests(requests,preview)&&requests.isEmpty(),"Actual source views with distinct raw components satisfy one semantic request inside its real transaction");}
        h.assertTrue(ItemStack.matches(chest.getItem(0),firstValue)&&ItemStack.matches(chest.getItem(1),secondValue)&&packager.heldBox.isEmpty()&&packager.queuedExitingPackages.isEmpty(),"Native transaction rollback restores both exact physical source items and every package owner");
        try(var committed=Transaction.openOuter()){var requests=new ArrayList<>(List.of(PackagingRequest.create(sample,2,"",0,new MutableBoolean(true),0,48,null)));h.assertTrue(packager.fulfillRequests(requests,committed),"Actual retry extracts its actual view variants");committed.commit();}
        var outputs=new ArrayList<BigItemStack>();if(!packager.heldBox.isEmpty())outputs.add(new BigItemStack(packager.heldBox,1));outputs.addAll(packager.queuedExitingPackages);var ids=new HashSet<UUID>();int total=0;
        for(var box:outputs)for(var actual:PackageItem.getContents(box.stack))if(!actual.isEmpty()){ids.add(plan(actual));total+=actual.getCount()*box.count;}
        h.assertTrue(chest.isEmpty()&&total==2&&ids.equals(sourceIds)&&!ids.contains(sampleId),"Committed native bulky-item packages retain exactly the two actual physical plan identities across held and queued owners, never copies of the request sample");h.succeed();
    }
    private static final class RecipeRepacker extends PackageRepackageHelper {
        List<BigItemStack> craft(InventorySummary actual,PackageOrderWithCrafts order){return repackBasedOnRecipes(actual,order,"",RandomSource.create(7));}
    }
    @GameTest public void recipeRepackerConsumesActualPhysicalEntriesWithoutCloningItsGhostPattern(GameTestHelper h){
        var first=migrated(h,0);var second=migrated(h,1);var sample=migrated(h,2);var stock=InventorySummary.physical();stock.add(first,1);stock.add(second,1);
        var context=new PackageOrderWithCrafts(PackageOrder.empty(),List.of(new PackageOrderWithCrafts.CraftingEntry(new PackageOrder(List.of(new BigItemStack(sample,1))),2)));var boxes=new RecipeRepacker().craft(stock,context);var ids=new HashSet<UUID>();int count=0;
        for(var box:boxes)for(var actual:PackageItem.getContents(box.stack))if(!actual.isEmpty()){ids.add(plan(actual));count+=actual.getCount()*box.count;}
        h.assertTrue(stock.getCountOf(sample)==0&&count==2&&ids.equals(Set.of(plan(first),plan(second)))&&!ids.contains(plan(sample)),"Both real physical source entries are consumed once and retain their exact plan IDs in actual recipe packages");h.succeed();
    }
}
