package com.simibubi.create.foundation.utility;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Actual typed entity/item owner paths; every authored runtime case is UNRUN. */
public final class NeoForgeEntityScopeGameTests {
    private static final UUID ROOT=UUID.fromString("d3621925-d207-437f-a913-d7848b474d31"),CHILD=UUID.fromString("e9629bd0-c269-4c6f-a20d-fef337129aa5"),OTHER=UUID.fromString("a0829f10-64f0-4a0d-a4da-544a445619fe");
    private static ListTag rows(Tag... values){var result=new ListTag();for(var value:values)result.add(value);return result;}
    private static CompoundTag xyz(){var tag=new CompoundTag();tag.putInt("X",0);tag.putInt("Y",0);tag.putInt("Z",0);return tag;}
    private static CompoundTag originalPacked(){
        var state=AllBlocks.MINECART_ANCHOR.defaultBlockState();var sourceState=new CompoundTag();sourceState.putString("Name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());var properties=new CompoundTag();state.getValues().forEach(value->properties.putString(value.property().getName(),value.valueName()));if(!properties.isEmpty())sourceState.put("Properties",properties);
        var row=new CompoundTag();row.putLong("Pos",BlockPos.ZERO.asLong());row.putInt("State",0);var blocks=new CompoundTag();blocks.put("Palette",rows(sourceState));blocks.put("BlockList",rows(row));
        var body=new CompoundTag();body.putString("Type","create:mounted");body.putString("RotationMode","ROTATE");body.putString("InitialOrientation","EAST");body.put("Anchor",xyz());body.putBoolean("Stalled",false);body.putBoolean("BottomlessSupply",false);body.put("Blocks",blocks);
        for(String key:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions"))body.put(key,new ListTag());
        var components=new CompoundTag();components.put("create:minecart_contraption_data",body);var item=new CompoundTag();item.putString("id","create:minecart_contraption");item.putInt("count",1);item.put("components",components);return item;
    }
    private static CompoundTag template(UUID uuid){var tag=new CompoundTag();tag.putString("id","minecraft:pig");tag.store("UUID",UUIDUtil.CODEC,uuid);tag.put("HandItems",rows(originalPacked(),new CompoundTag()));return tag;}
    private static CompoundTag saved(UUID uuid){var tag=template(uuid);tag.put("ArmorItems",rows(new CompoundTag(),new CompoundTag(),new CompoundTag(),new CompoundTag()));tag.put("Pos",rows(DoubleTag.valueOf(1),DoubleTag.valueOf(128),DoubleTag.valueOf(2)));tag.put("Motion",rows(DoubleTag.valueOf(0),DoubleTag.valueOf(0),DoubleTag.valueOf(0)));tag.put("Rotation",rows(FloatTag.valueOf(0),FloatTag.valueOf(0)));tag.putFloat("FallDistance",0);tag.putShort("Fire",(short)0);tag.putShort("Air",(short)300);tag.putBoolean("OnGround",false);tag.putBoolean("Invulnerable",false);tag.putInt("PortalCooldown",0);return tag;}
    private static NeoForgeMigrationScope actualOwner(GameTestHelper h,int slot){return NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(new BlockPos(2,2,2))).item("inventory",slot);}
    private static ItemStack read(GameTestHelper h,CompoundTag tag){return ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),tag).getOrThrow();}
    private static UUID plan(ItemStack item){return UUIDUtil.CODEC.parse(NbtOps.INSTANCE,item.get(AllDataComponents.MINECRAFT_CONTRAPTION_DATA).get("NativePlanId")).getOrThrow();}
    private static ItemStack hand(GameTestHelper h,CompoundTag actual){return read(h,actual.getCompoundOrEmpty("equipment").getCompoundOrEmpty("mainhand"));}
    private static ItemStack expected(GameTestHelper h,NeoForgeMigrationScope leaf){return read(h,NeoForgeSaveCodecs.item(originalPacked(),3955,h.getLevel().registryAccess(),leaf));}
    private static CompoundTag convert(GameTestHelper h,CompoundTag source,NeoForgeMigrationScope owner){return NeoForgeVanillaEntityImport.convert(source,3955,h.getLevel().registryAccess(),owner);}
    @GameTest public void uuidShapedTemplateInheritsActualContainingOwnerAndNeverBecomesSavedWorldOwner(GameTestHelper h){
        var source=template(ROOT);var original=source.copy();var owner=actualOwner(h,0);var first=hand(h,convert(h,source,owner));
        h.assertTrue(ItemStack.matches(first,expected(h,owner.item("handitems",0))),"Actual template HandItems field and original index thread through preflight, prepare, whole DFU and postpass");
        var changed=template(OTHER);h.assertTrue(plan(first).equals(plan(hand(h,convert(h,changed,owner))))&&!plan(first).equals(plan(hand(h,convert(h,source,actualOwner(h,1)))))&&source.equals(original),"Template UUID does not override the real containing inventory slot and original source is unchanged");h.succeed();
    }
    @GameTest public void templatePassengerInheritsActualOriginalPassengerIndexEvenWithUuid(GameTestHelper h){
        var source=template(ROOT);source.put("Passengers",rows(template(CHILD)));var owner=actualOwner(h,0);var actual=convert(h,source,owner);var child=hand(h,actual.getListOrEmpty("Passengers").getCompoundOrEmpty(0));
        h.assertTrue(ItemStack.matches(child,expected(h,owner.item("passenger",0).item("handitems",0)))&&!plan(child).equals(plan(hand(h,actual))),"Actual child template uses its typed passenger path, separate from the original root hand");
        source.getListOrEmpty("Passengers").getCompoundOrEmpty(0).store("UUID",UUIDUtil.CODEC,OTHER);var changed=convert(h,source,owner);
        h.assertTrue(plan(child).equals(plan(hand(h,changed.getListOrEmpty("Passengers").getCompoundOrEmpty(0)))),"Changing only a UUID-shaped template field cannot replace inherited source ownership");h.succeed();
    }
    @GameTest public void completeSavedBoundaryDerivesRealRootAndChildEntityOwners(GameTestHelper h){
        var source=saved(ROOT);source.put("Passengers",rows(saved(CHILD)));var original=source.copy();var actual=NeoForgeSavedVanillaEntityImport.convert(source,3955,h.getLevel().registryAccess());
        h.assertTrue(ItemStack.matches(hand(h,actual),expected(h,NeoForgeMigrationScope.entity(ROOT).item("handitems",0)))&&ItemStack.matches(hand(h,actual.getListOrEmpty("Passengers").getCompoundOrEmpty(0)),expected(h,NeoForgeMigrationScope.entity(CHILD).item("handitems",0)))&&source.equals(original),"Only genuine complete saved nodes derive ownership from each actual original entity UUID");
        var missing=source.copy();missing.getListOrEmpty("Passengers").getCompoundOrEmpty(0).remove("UUID");boolean refused=false;try{NeoForgeSavedVanillaEntityImport.convert(missing,3955,h.getLevel().registryAccess());}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"A missing real saved child UUID cannot fall back to a template path");h.succeed();
    }
    @GameTest public void sparseOriginalByteSlotsAndMerchantFieldsKeepTheirOwnScopeAndRealCounts(GameTestHelper h){
        var first=originalPacked();first.putByte("Slot",(byte)26);var second=originalPacked();second.putByte("Slot",(byte)3);var source=new CompoundTag();source.putString("id","minecraft:chest_minecart");source.put("Items",rows(first,second));var owner=actualOwner(h,0);var actual=convert(h,source,owner);
        var row=actual.getListOrEmpty("Items").getCompoundOrEmpty(0).copy();h.assertTrue(row.get("Slot") instanceof ByteTag&&row.getByte("Slot").orElseThrow()==26,"Actual original sparse byte slot26 survives the whole migration");row.remove("Slot");h.assertTrue(ItemStack.matches(read(h,row),expected(h,owner.item("items",26))),"Sparse identity follows actual source slot26, independent from serialized row0");
        source.put("Items",rows(second,first));var reordered=convert(h,source,owner).getListOrEmpty("Items").getCompoundOrEmpty(1).copy();reordered.remove("Slot");h.assertTrue(ItemStack.matches(read(h,row),read(h,reordered)),"Reordering sparse source rows preserves the identity of the genuine same slot");
        var merchant=new CompoundTag();merchant.putString("id","minecraft:villager");var offer=new CompoundTag();var cost=originalPacked();cost.putInt("count",1000);offer.put("buy",cost);offer.put("sell",originalPacked());var offers=new CompoundTag();offers.put("Recipes",rows(offer));merchant.put("Offers",offers);var migrated=convert(h,merchant,owner).getCompoundOrEmpty("Offers").getListOrEmpty("Recipes").getCompoundOrEmpty(0);var buy=migrated.getCompoundOrEmpty("buy").copy();h.assertTrue(buy.getInt("count").orElseThrow()==1000,"Real source merchant cost1000 survives the count1 component probe");buy.putInt("count",1);
        h.assertTrue(ItemStack.matches(read(h,buy),expected(h,owner.item("offers",0).item("buy",0)))&&ItemStack.matches(read(h,migrated.getCompoundOrEmpty("sell")),expected(h,owner.item("offers",0).item("sell",0))),"Actual buy and sell leaves use their separate original typed offer paths");h.succeed();
    }
    @GameTest public void unscopedPackedTemplateRefusesWithoutTreatingItsUuidAsWorldOwnership(GameTestHelper h){
        var source=template(ROOT);var original=source.copy();boolean refused=false;try{NeoForgeVanillaEntityImport.convert(source,3955,h.getLevel().registryAccess());}catch(RuntimeException expected){refused=true;}
        h.assertTrue(refused&&source.equals(original),"A UUID-shaped template never supplies absent genuine containing owner context");h.succeed();
    }
}
