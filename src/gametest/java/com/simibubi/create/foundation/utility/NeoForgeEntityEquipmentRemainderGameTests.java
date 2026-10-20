package com.simibubi.create.foundation.utility;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
/** Source-bound duplicate remainder validation; all runtime cases UNRUN. */
public final class NeoForgeEntityEquipmentRemainderGameTests {
    private static ListTag rows(Tag... values){var rows=new ListTag();for(var value:values)rows.add(value);return rows;}
    private static CompoundTag item(String id,int count){var item=new CompoundTag();item.putString("id",id);item.putInt("count",count);return item;}
    @GameTest public void changedResidualCountOrItemCannotDisappearBehindCorrectNativeEquipment(GameTestHelper h){
        var original=new CompoundTag();original.putString("id","minecraft:pig");original.put("HandItems",rows(item("minecraft:diamond",11),new CompoundTag()));
        var nativeTag=NeoForgeVanillaEntityImport.convert(original,3955,h.getLevel().registryAccess());var duplicate=nativeTag.copy();duplicate.put("HandItems",original.get("HandItems").copy());
        h.assertTrue(NeoForgeVanillaEntityImport.finishAfterVanillaFixer(original,duplicate,3955,h.getLevel().registryAccess()).equals(nativeTag),"An exact source-bound remainder is removed only after real native gear also proves that same original eleven-item lineage");
        for(var changed:rows(item("minecraft:diamond",12),item("minecraft:emerald",11))){var corrupt=nativeTag.copy();corrupt.put("HandItems",rows(changed,new CompoundTag()));refuses(h,original,corrupt);}
        var nativeOnly=nativeTag.copy();nativeOnly.put("ArmorItems",rows(item("minecraft:diamond",11),new CompoundTag(),new CompoundTag(),new CompoundTag()));refuses(h,original,nativeOnly);h.succeed();
    }
    private static void refuses(GameTestHelper h,CompoundTag source,CompoundTag actualFixed){var before=source.copy();var after=actualFixed.copy();boolean refused=false;try{NeoForgeVanillaEntityImport.finishAfterVanillaFixer(source,actualFixed,3955,h.getLevel().registryAccess());}catch(RuntimeException expected){refused=true;}h.assertTrue(refused&&source.equals(before)&&actualFixed.equals(after),"Changed or unowned residual stock is refused without changing the original source or caller's fixed tree");}
}
