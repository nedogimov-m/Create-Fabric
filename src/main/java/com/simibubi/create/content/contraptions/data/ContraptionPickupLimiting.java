package com.simibubi.create.content.contraptions.data;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/** Source native read-side NBT accounting and packet headroom; absent optional limit mods do not invent a larger allowance. */
public final class ContraptionPickupLimiting {
    public static final int NBT_LIMIT=2_097_152,BUFFER=20_000,LIMIT=NBT_LIMIT-BUFFER;
    public static boolean isTooLargeForPickup(Tag data){return accountedSize(data)>LIMIT;}
    public static long accountedSize(Tag data){
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{buffer.writeNbt(data);var account=NbtAccounter.unlimitedHeap();buffer.readNbt(account);return account.getUsage();}
        finally{buffer.release();}
    }
    private ContraptionPickupLimiting(){}
}
