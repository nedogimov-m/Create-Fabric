package com.simibubi.create.test;
import com.simibubi.create.*;
import com.simibubi.create.content.equipment.clipboard.*;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import com.simibubi.create.foundation.item.ItemSampleEquality;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
/** Explicit registered native NBT item locations; all3 cases UNRUN. */
public final class NeoForgeNativeSamplePathsGameTests {
    private static ListTag rows(Tag... values){var result=new ListTag();for(var value:values)result.add(value);return result;}
    private static CompoundTag xyz(){var tag=new CompoundTag();tag.putInt("X",0);tag.putInt("Y",0);tag.putInt("Z",0);return tag;}
    private static CompoundTag originalPacked(){
        var state=AllBlocks.MINECART_ANCHOR.defaultBlockState();var sourceState=new CompoundTag();sourceState.putString("Name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());var properties=new CompoundTag();state.getValues().forEach(value->properties.putString(value.property().getName(),value.valueName()));if(!properties.isEmpty())sourceState.put("Properties",properties);
        var row=new CompoundTag();row.putLong("Pos",BlockPos.ZERO.asLong());row.putInt("State",0);var blocks=new CompoundTag();blocks.put("Palette",rows(sourceState));blocks.put("BlockList",rows(row));
        var body=new CompoundTag();body.putString("Type","create:mounted");body.putString("RotationMode","ROTATE");body.putString("InitialOrientation","EAST");body.put("Anchor",xyz());body.putBoolean("Stalled",false);body.putBoolean("BottomlessSupply",false);body.put("Blocks",blocks);
        for(String key:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions"))body.put(key,new ListTag());
        var components=new CompoundTag();components.put("create:minecart_contraption_data",body);var item=new CompoundTag();item.putString("id","create:minecart_contraption");item.putInt("count",1);item.put("components",components);return item;
    }

    private static ItemStack migrate(GameTestHelper h,CompoundTag source,int index){return ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),NeoForgeSaveCodecs.item(source,3955,h.getLevel().registryAccess(),NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(new BlockPos(2,2,2))).item("inventory",index))).getOrThrow();}
    private static CompoundTag encoded(GameTestHelper h,ItemStack actual){return (CompoundTag)ItemStack.CODEC.encodeStart(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),actual).getOrThrow();}
    private static ItemStack clipboard(GameTestHelper h,ItemStack packed,boolean opaque){var copied=new CompoundTag();var filter=new CompoundTag();filter.putInt("Row",1);filter.putInt("Value",11);filter.put("Filter",encoded(h,packed));copied.put("Filtering",filter);var frequencies=new CompoundTag();frequencies.put("First",encoded(h,packed));frequencies.put("Last",encoded(h,packed));copied.put("Frequencies",frequencies);if(opaque)copied.put("ThirdPartyOpaque",encoded(h,packed));var result=new ItemStack(AllBlocks.CLIPBOARD);result.set(AllDataComponents.CLIPBOARD_CONTENT,new ClipboardContent(ClipboardType.WRITTEN,List.of(),false).setCopiedValues(copied));return result;}
    @GameTest public void copiedFilterAndFrequenciesNormalizeOnlyTheirActualTypedSampleFields(GameTestHelper h){
        var first=clipboard(h,migrate(h,originalPacked(),0),false);var second=clipboard(h,migrate(h,originalPacked(),1),false);
        h.assertTrue(!ItemStack.isSameItemSameComponents(first,second)&&ItemSampleEquality.same(first,second)&&ItemSampleEquality.hash(first)==ItemSampleEquality.hash(second),"Native copied Filtering.Filter and Frequencies.First/Last compare genuine same original samples across their separate containing owners");
        var content=second.get(AllDataComponents.CLIPBOARD_CONTENT);var changed=content.copiedValues().orElseThrow().copy();changed.getCompoundOrEmpty("Filtering").putInt("Value",12);second.set(AllDataComponents.CLIPBOARD_CONTENT,content.setCopiedValues(changed));h.assertTrue(!ItemSampleEquality.same(first,second),"Actual filtering count remains significant");
        h.assertTrue(!ItemSampleEquality.same(clipboard(h,migrate(h,originalPacked(),0),true),clipboard(h,migrate(h,originalPacked(),1),true)),"An unknown compound containing item-shaped data stays exact; it is never scanned as a sample owner");h.succeed();
    }
    private static CompoundTag owningItem(String id,String component,Tag value){var result=new CompoundTag();result.putString("id",id);result.putInt("count",1);var components=new CompoundTag();components.put(component,value);result.put("components",components);return result;}
    private static CompoundTag entity(String type){var entity=new CompoundTag();entity.putString("id",type);entity.put("HandItems",rows(originalPacked(),new CompoundTag()));return entity;}
    private static void assertValue(GameTestHelper h,CompoundTag source){var original=source.copy();var first=migrate(h,source,0);var second=migrate(h,source,1);h.assertTrue(!ItemStack.isSameItemSameComponents(first,second)&&ItemSampleEquality.same(first,second)&&ItemSampleEquality.hash(first)==ItemSampleEquality.hash(second)&&source.equals(original),"Actual native typed holder keeps raw scoped identities while comparing the same genuine source item leaves by value");}
    @GameTest public void nativeEntityDataAndBeeOccupantsUseTheirActualTypedItemLeaves(GameTestHelper h){
        assertValue(h,owningItem("minecraft:pig_spawn_egg","minecraft:entity_data",entity("minecraft:pig")));
        var bee=new CompoundTag();bee.put("entity_data",entity("minecraft:bee"));bee.putInt("ticks_in_hive",5);bee.putInt("min_ticks_in_hive",20);assertValue(h,owningItem("minecraft:beehive","minecraft:bees",rows(bee)));h.succeed();
    }
    @GameTest public void nativeVanillaBlockEntityContainerMatchesActualSparseItemsWithoutScanningUnknownState(GameTestHelper h){
        var chest=new CompoundTag();chest.putString("id","minecraft:chest");var stock=originalPacked();stock.putByte("Slot",(byte)26);chest.put("Items",rows(stock));assertValue(h,owningItem("minecraft:chest","minecraft:block_entity_data",chest));h.succeed();
    }
}
