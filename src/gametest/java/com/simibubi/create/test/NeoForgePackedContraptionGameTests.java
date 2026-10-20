package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.content.contraptions.mounted.*;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Genuine source3955 body and actual native dispenser/admission/custody cases; runtime UNRUN. */
public final class NeoForgePackedContraptionGameTests {
    private static final BlockPos P=new BlockPos(3,2,3),CHEST=new BlockPos(1,1,0);
    private static ListTag rows(Tag... values){var result=new ListTag();for(var value:values)result.add(value);return result;}
    private static CompoundTag xyz(BlockPos p){var tag=new CompoundTag();tag.putInt("X",p.getX());tag.putInt("Y",p.getY());tag.putInt("Z",p.getZ());return tag;}
    private static CompoundTag sourceState(BlockState state){
        var tag=new CompoundTag();tag.putString("Name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());var properties=new CompoundTag();
        state.getValues().forEach(entry->properties.putString(entry.property().getName(),entry.valueName()));if(!properties.isEmpty())tag.put("Properties",properties);return tag;
    }
    private static CompoundTag diamonds(){var tag=new CompoundTag();tag.putString("id","minecraft:diamond");tag.putInt("count",11);var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\"Original eleven\"}");tag.put("components",components);return tag;}
    private static CompoundTag body(){
        var tag=new CompoundTag();tag.putString("Type","create:mounted");tag.putString("RotationMode","ROTATE");tag.putString("InitialOrientation","EAST");tag.put("Anchor",xyz(BlockPos.ZERO));tag.putBoolean("Stalled",false);tag.putBoolean("BottomlessSupply",false);
        var palette=rows(sourceState(AllBlocks.MINECART_ANCHOR.defaultBlockState()),sourceState(Blocks.CHEST.defaultBlockState()));
        var anchor=new CompoundTag();anchor.putLong("Pos",BlockPos.ZERO.asLong());anchor.putInt("State",0);
        var chest=new CompoundTag();chest.putLong("Pos",CHEST.asLong());chest.putInt("State",1);var be=new CompoundTag();be.putString("id","minecraft:chest");var sourceItem=diamonds();sourceItem.putByte("Slot",(byte)26);be.put("Items",rows(sourceItem));chest.put("Data",be);chest.put("UpdateTag",new CompoundTag());
        var blocks=new CompoundTag();blocks.put("Palette",palette);blocks.put("BlockList",rows(anchor,chest));tag.put("Blocks",blocks);
        for(String key:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions"))tag.put(key,new ListTag());
        var slots=new CompoundTag();var sparse=new CompoundTag();sparse.put("26",diamonds());slots.put("items",sparse);slots.putInt("size",27);var storage=new CompoundTag();storage.putString("type","create:chest");storage.put("value",slots);var row=new CompoundTag();row.put("pos",xyz(CHEST));row.put("storage",storage);tag.put("items",rows(row));return tag;
    }
    private static NeoForgeMigrationScope scope(GameTestHelper h,BlockPos source,int slot){return NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(source)).item("inventory",slot);}
    private static ItemStack original(GameTestHelper h,NativeCartKind kind,BlockPos position,int slot){
        var actual=new ItemStack(kind.item());actual.set(AllDataComponents.MINECRAFT_CONTRAPTION_DATA,NeoForgeContraptionEntityImport.packed(body(),3955,h.getLevel().registryAccess(),scope(h,position,slot)));return actual;
    }
    private static DispenserBlockEntity dispenser(GameTestHelper h,BlockPos position,ItemStack actual){
        h.setBlock(position.below(),Blocks.STONE);h.setBlock(position,Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.EAST));h.setBlock(position.east().below(),Blocks.STONE);h.setBlock(position.east(),Blocks.RAIL);
        var owner=h.getBlockEntity(position,DispenserBlockEntity.class);owner.setItem(0,actual);return owner;
    }
    private static void dispense(GameTestHelper h,BlockPos position,DispenserBlockEntity owner){
        var source=new BlockSource(h.getLevel(),h.absolutePos(position),h.getBlockState(position),owner);var actual=owner.getItem(0);
        var remaining=DispenserBlock.DISPENSER_REGISTRY.get(actual.getItem()).dispense(source,actual);owner.setItem(0,remaining);
    }
    private static long stock(OrientedContraptionEntity root){long count=0;for(var view:root.getContraption().getStorage().getAllItems())if(!view.isResourceBlank()&&view.getResource().isOf(Items.DIAMOND))count+=view.getAmount();return count;}
    private static AbstractMinecart placed(GameTestHelper h,BlockPos position){var carts=h.getLevel().getEntitiesOfClass(AbstractMinecart.class,new AABB(h.absolutePos(position.east())).inflate(1));h.assertTrue(carts.size()==1,"Exactly one actual native primary cart is admitted");return carts.getFirst();}
    @GameTest public void originalBodyBecomesExplicitPlanWithoutInventingMissingHistoricalCartState(GameTestHelper h){
        var source=body();var before=source.copy();var owner=scope(h,P,0);var actual=NeoForgeContraptionEntityImport.packed(source,3955,h.getLevel().registryAccess(),owner);
        h.assertTrue(source.equals(before)&&actual.getInt("CartItemSchema").orElseThrow()==2&&actual.getInt("OriginalDataVersion").orElseThrow()==3955
            &&!actual.contains("BaseCarts")&&!actual.contains("CustodyReceipt")&&!actual.contains("UUID")&&!actual.contains("NativeState"),"Original structure is retained in an explicit new native plan without fabricated original cart/cargo/paid receipt");
        h.assertTrue(actual.get("NativePlanId").equals(NeoForgeContraptionEntityImport.packed(source,3955,h.getLevel().registryAccess(),owner).get("NativePlanId"))
            &&!actual.get("NativePlanId").equals(NeoForgeContraptionEntityImport.packed(source,3955,h.getLevel().registryAccess(),scope(h,P,1)).get("NativePlanId")),"New native plan identity derives from the real containing source owner and actual slot");h.succeed();
    }
    @GameTest public void realDispenserPlacesActualSourceRideableChestAndFurnaceKindsWithOneMountedStockOwner(GameTestHelper h){
        int index=0;for(var kind:NativeCartKind.values()){
            var position=P.south(index++*4);var actual=original(h,kind,position,0);var id=PackedCartData.receiptId(actual);var owner=dispenser(h,position,actual);dispense(h,position,owner);
            var cart=placed(h,position);var controller=MinecartController.get(cart);var root=h.getLevel().getEntity(controller.getMountedRoot());
            h.assertTrue(NativeCartKind.of(cart)==kind&&owner.getItem(0).isEmpty()&&!controller.ownsPackedReceipt()&&root instanceof OrientedContraptionEntity,
                "Actual enclosing item selects the genuine native factory and one original physical item is consumed");
            var body=(OrientedContraptionEntity)root;h.assertTrue(body.hasOwnedStructure()&&body.getVehicle()==cart&&stock(body)==11&&body.getInitialOrientation()==Direction.EAST,
                "The actual admitted root owns precisely eleven original source diamonds and is mounted on its real primary");
            if(cart instanceof MinecartChest chest)h.assertTrue(chest.getItemStacks().stream().allMatch(ItemStack::isEmpty)&&chest.getContainerLootTable()==null,"New chest base retains its genuine native fresh inventory, separate from mounted source chest");
            if(cart instanceof MinecartFurnace furnace)h.assertTrue(((com.simibubi.create.foundation.mixin.accessor.MinecartFurnaceAccessor)furnace).create$getFuel()==0&&furnace.push.equals(Vec3.ZERO),"New furnace state comes from the actual native fresh cart");
            h.assertTrue(!cart.getUUID().equals(id)&&!body.getUUID().equals(id),"Native factories supply actual entity UUIDs independently from a new migrated value identity");
        }h.succeed();
    }
    @GameTest public void genuineNativeUuidRefusalLeavesActualCanonicalDispenserItemForLaterRetry(GameTestHelper h)throws Exception{
        var actual=original(h,NativeCartKind.RIDEABLE,P,0);var value=PackedCartData.detachedItemCopy(actual);var owner=dispenser(h,P,actual);
        var existing=NativeCartKind.RIDEABLE.createEmpty(h.getLevel(),Vec3.atBottomCenterOf(h.absolutePos(P.west(3))));h.assertTrue(h.getLevel().addFreshEntity(existing),"Actual existing native UUID owner admitted for a genuine duplicate-UUID refusal");
        try(var refusal=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity instanceof AbstractMinecart,entity->entity.setUUID(existing.getUUID()))){dispense(h,P,owner);}
        h.assertTrue(owner.getItem(0)==actual&&ItemStack.matches(actual,value)&&h.getLevel().getEntity(existing.getUUID())==existing,"Actual native rejection retains one exact source item and its complete original plan");
        dispense(h,P,owner);var cart=placed(h,P);var root=(OrientedContraptionEntity)h.getLevel().getEntity(MinecartController.get(cart).getMountedRoot());h.assertTrue(owner.getItem(0).isEmpty()&&root.hasOwnedStructure()&&stock(root)==11,"Later real admission consumes that same retained original once");h.succeed();
    }
    @GameTest public void acceptedPrimaryRetainsOriginalPlanWhenActualRootIsRejectedAndDeployRetryUsesSameNativeBase(GameTestHelper h)throws Exception{
        var actual=original(h,NativeCartKind.CHEST,P,0);var owner=dispenser(h,P,actual);var primary=new AtomicReference<AbstractMinecart>();
        try(var observed=CartAcceptanceTestFixtures.beforeCreated(entity->entity instanceof AbstractMinecart,entity->primary.set((AbstractMinecart)entity));
            var refusal=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity instanceof OrientedContraptionEntity,entity->entity.setUUID(primary.get().getUUID()))){dispense(h,P,owner);}
        var cart=primary.get();var controller=MinecartController.get(cart);h.assertTrue(owner.getItem(0).isEmpty()&&controller.ownsPackedReceipt(actual)&&controller.getMountedRoot()==null,"Accepted actual primary owns the one original plan after real root rejection; payer is not recreated");
        var result=CartForestTransfer.deploy(cart,Direction.SOUTH);var root=result==null?null:h.getLevel().getEntity(result.actualRoot());
        h.assertTrue(root instanceof OrientedContraptionEntity body&&body.getVehicle()==cart&&body.hasOwnedStructure()&&stock(body)==11&&!controller.ownsPackedReceipt(),"Retry uses that same real accepted base and transfers the original body stock once");h.succeed();
    }
    @GameTest public void originalPackedUnknownStateAndUnownedChildReferencesRefuseWithoutChangingOriginal(GameTestHelper h){
        var unknown=body();unknown.putString("FutureOwnedData","preserve");refuses(h,unknown);
        var missingBody=body();var child=new CompoundTag();child.store("Id",UUIDUtil.CODEC,UUID.fromString("447dc86d-7447-4687-bd06-f3fe506ad705"));child.put("Location",xyz(BlockPos.ZERO));missingBody.put("SubContraptions",rows(child));refuses(h,missingBody);
        var passenger=body();var row=new CompoundTag();row.store("Id",UUIDUtil.CODEC,UUID.fromString("6c565414-7764-40c8-a321-5349f2cebd43"));row.putInt("Seat",0);passenger.put("Passengers",rows(row));refuses(h,passenger);h.succeed();
    }
    private static void refuses(GameTestHelper h,CompoundTag source){var old=source.copy();boolean refused=false;try{NeoForgeContraptionEntityImport.packed(source,3955,h.getLevel().registryAccess(),scope(h,P,0));}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused&&source.equals(old),"Unknown material/child owner is refused without changing original source");}
    @GameTest public void nativeCapturedPaletteContractRetainsDefaultStatesThroughActualInactiveDecoder(GameTestHelper h){
        var actual=original(h,NativeCartKind.CHEST,P,0);var structure=LegacyPackedCartPlan.parse(actual).structure();
        var palette=structure.getCompoundOrEmpty("Blocks").getListOrEmpty("Palette");
        h.assertTrue(palette.size()==2&&palette.stream().allMatch(value->value instanceof CompoundTag),
            "Actual native captured palette reader requires compound states, including real default states whose standalone BlockState codec compactly encodes strings");
        var proposal=Contraption.fromPackedItemNBT(h.getLevel(),structure);
        h.assertTrue(proposal.getBlocks().size()==2&&proposal.getBlocks().get(BlockPos.ZERO).state().is(AllBlocks.MINECART_ANCHOR)
            &&proposal.getBlocks().get(CHEST).state().is(Blocks.CHEST)&&!proposal.getStorage().ownsContents(),
            "Actual native inactive decoder retains every real source default-state block without admitting a second stock owner");h.succeed();
    }

    private static CompoundTag sourcePackedItem(CompoundTag originalBody) {
        var source = new CompoundTag(); source.putString("id", "create:minecart_contraption"); source.putInt("count", 1);
        var components = new CompoundTag(); components.put("create:minecart_contraption_data", originalBody); source.put("components", components); return source;
    }
    @GameTest public void nestedOriginalPackedStockCrossesBothRealMountedOwnersExactlyOnce(GameTestHelper h) {
        var inner = sourcePackedItem(body()); var outer = body(); var original = outer.copy();
        var physical = outer.getListOrEmpty("items").getCompoundOrEmpty(0).getCompoundOrEmpty("storage").getCompoundOrEmpty("value").getCompoundOrEmpty("items"); physical.put("26", inner.copy());
        var mirror = inner.copy(); mirror.putByte("Slot", (byte) 26);
        outer.getCompoundOrEmpty("Blocks").getListOrEmpty("BlockList").getCompoundOrEmpty(1).getCompoundOrEmpty("Data").put("Items", rows(mirror));
        original = outer.copy(); var actual = new ItemStack(NativeCartKind.RIDEABLE.item());
        actual.set(AllDataComponents.MINECRAFT_CONTRAPTION_DATA, NeoForgeContraptionEntityImport.packed(outer, 3955, h.getLevel().registryAccess(), scope(h, P, 0)));
        var owner = dispenser(h, P, actual); dispense(h, P, owner); var cart = placed(h, P);
        var root = (OrientedContraptionEntity) h.getLevel().getEntity(MinecartController.get(cart).getMountedRoot());
        var storage = root.getContraption().getStorage().getAllItems(); ItemStack nested = ItemStack.EMPTY; long plans = 0;
        for (var view : storage) if (!view.isResourceBlank()) {
            h.assertTrue(view.getResource().isOf(NativeCartKind.RIDEABLE.item()), "Outer active mounted storage owns only its real original inner packed item");
            plans += view.getAmount();
            if (nested.isEmpty()) try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                var variant = view.getResource(); h.assertTrue(view.extract(variant, 1, tx) == 1, "Extract the exact real native inner packed variant once"); nested = variant.toStack(); tx.commit();
            }
        }
        h.assertTrue(plans == 1 && !nested.isEmpty() && storage.iterator().hasNext() && stock(root) == 0 && outer.equals(original), "Captured inventory mirror adds no second inner item or diamond owner and original source stays unchanged");
        var secondPos = P.south(12); var second = dispenser(h, secondPos, nested); dispense(h, secondPos, second);
        var innerCart = placed(h, secondPos); var innerRoot = (OrientedContraptionEntity) h.getLevel().getEntity(MinecartController.get(innerCart).getMountedRoot());
        h.assertTrue(second.getItem(0).isEmpty() && innerRoot.hasOwnedStructure() && stock(innerRoot) == 11 && stock(root) == 0,
            "The actual extracted nested plan deploys precisely eleven original diamonds under one admitted inner owner"); h.succeed();
    }
    @GameTest public void sparsePackedContainerSlotsRemainDistinctPhysicalValuesAndEqualOriginalSamples(GameTestHelper h) {
        var source = new CompoundTag(); source.putString("id", "minecraft:shulker_box"); source.putInt("count", 1);
        var first = new CompoundTag(); first.putInt("slot", 26); first.put("item", sourcePackedItem(body()));
        var second = new CompoundTag(); second.putInt("slot", 3); second.put("item", sourcePackedItem(body()));
        var components = new CompoundTag(); components.put("minecraft:container", rows(first, second)); source.put("components", components); var original = source.copy();
        var encoded = NeoForgeSaveCodecs.item(source, 3955, h.getLevel().registryAccess(), scope(h, P, 0));
        var actual = ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), encoded).getOrThrow();
        var slots = NonNullList.withSize(27, ItemStack.EMPTY); actual.get(net.minecraft.core.component.DataComponents.CONTAINER).copyInto(slots);
        h.assertTrue(!slots.get(26).isEmpty() && !slots.get(3).isEmpty() && !ItemStack.matches(slots.get(26), slots.get(3))
            && com.simibubi.create.foundation.item.ItemSampleEquality.same(slots.get(26), slots.get(3)) && source.equals(original),
            "Genuine sparse slots survive containing vanilla DFU with separate native physical plan identities and the same original filter meaning");
        components.put("minecraft:container", rows(second, first));
        var reordered = ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), NeoForgeSaveCodecs.item(source, 3955, h.getLevel().registryAccess(), scope(h, P, 0))).getOrThrow();
        var reorderedSlots = NonNullList.withSize(27, ItemStack.EMPTY); reordered.get(net.minecraft.core.component.DataComponents.CONTAINER).copyInto(reorderedSlots);
        h.assertTrue(ItemStack.matches(slots.get(26), reorderedSlots.get(26)) && ItemStack.matches(slots.get(3), reorderedSlots.get(3)), "Reordering original sparse rows never changes the actual source slot identities"); h.succeed();
    }
    @GameTest public void realVanillaChestReloadRetainsScopedPackedItemsAtTheirActualOriginalByteSlots(GameTestHelper h) {
        var source = new CompoundTag(); source.putString("id", "minecraft:chest"); var inner = sourcePackedItem(body()); inner.putByte("Slot", (byte) 26); source.put("Items", rows(inner)); var original = source.copy();
        var encoded = NeoForgeBlockEntityImport.convert(source, 3955, h.getLevel().registryAccess(), scope(h, P, 0));
        var state = Blocks.CHEST.defaultBlockState(); var actual = new ChestBlockEntity(h.absolutePos(P), state); actual.setLevel(h.getLevel());
        var problems = new net.minecraft.util.ProblemReporter.Collector(); actual.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(problems, h.getLevel().registryAccess(), encoded));
        h.assertTrue(problems.isEmpty() && !actual.getItem(26).isEmpty() && actual.getItem(0).isEmpty(), "Actual vanilla native chest reader retains genuine byte slot26");
        var saved = actual.saveWithFullMetadata(h.getLevel().registryAccess()); var reloaded = new ChestBlockEntity(h.absolutePos(P), state); reloaded.setLevel(h.getLevel());
        reloaded.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(problems, h.getLevel().registryAccess(), saved));
        h.assertTrue(problems.isEmpty() && ItemStack.matches(actual.getItem(26), reloaded.getItem(26)) && source.equals(original), "Actual native save and fresh chest factory preserve the nested packed plan and untouched source"); h.succeed();
    }

}
