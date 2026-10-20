package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.AllDataComponents;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Native UUID identifies the actual packed value or migrated plan; never a fabricated original entity/paid receipt. */
public final class PackedCartData {
    public static UUID receiptId(ItemStack actual){
        var data=actual.get(AllDataComponents.MINECRAFT_CONTRAPTION_DATA);
        if(LegacyPackedCartPlan.isPlan(actual))return LegacyPackedCartPlan.parse(actual).nativePlanId();
        if(data==null||data.getIntOr("CartItemSchema",0)!=1)throw new IllegalArgumentException("Actual complete native packed-cart schema required");
        return data.read("CustodyReceipt",UUIDUtil.CODEC).orElseThrow(()->new IllegalArgumentException("Missing packed receipt value identity"));
    }
    public static boolean hasReceipt(ItemStack actual,UUID expected){
        if(actual.isEmpty()||actual.getCount()!=1)return false;
        try{return receiptId(actual).equals(expected);}catch(IllegalArgumentException invalid){return false;}
    }
    /** Native components normally are immutable; directly stored CompoundTag components require an actual copy. */
    public static ItemStack detachedItemCopy(ItemStack actual){
        var result=actual.copy();for(var component:actual.getComponents())copyMutableTag(result,component);return result;
    }
    @SuppressWarnings("unchecked")
    private static <T> void copyMutableTag(ItemStack target,net.minecraft.core.component.TypedDataComponent<T> component){
        if(component.value() instanceof CompoundTag tag)target.set(component.type(),(T)tag.copy());
        else if(component.value() instanceof net.minecraft.network.chat.Component text)target.set(component.type(),(T)text.copy());
    }
    public static ItemStack detachedOne(ItemStack actual){var result=detachedItemCopy(actual);result.setCount(1);return result;}
    private PackedCartData(){}
}
