package com.simibubi.create.foundation.utility;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Value comparison metadata only. No digest is an entity identity, receipt or ownership proof. */
public final class NeoForgePackedValueSemantics {
    public static final String SOURCE_VALUE="LegacySourceValue",NATIVE_IMAGE="LegacyNativeImage";
    private static final String DOMAIN="create:original_packed_value/v1";

    /** Exact original typed value; containing source-owner coordinates are deliberately absent. */
    public static String originalValue(CompoundTag originalBody){return digest(originalBody);}

    public static void bind(CompoundTag actualNativePlan,String actualOriginalValue){
        if(!validDigest(actualOriginalValue))throw new IllegalArgumentException("Malformed original packed semantic value");
        actualNativePlan.remove(SOURCE_VALUE);actualNativePlan.remove(NATIVE_IMAGE);
        actualNativePlan.putString(SOURCE_VALUE,actualOriginalValue);
        actualNativePlan.putString(NATIVE_IMAGE,image(actualNativePlan));
    }

    /** A later edit to this particular native image invalidates the historical comparison key. */
    public static String validOriginalValue(CompoundTag actualNativePlan){
        if(actualNativePlan.getIntOr("CartItemSchema",0)!=2||actualNativePlan.getIntOr("OriginalDataVersion",0)!=3955)return null;
        if(!(actualNativePlan.get(SOURCE_VALUE) instanceof StringTag source)||!(actualNativePlan.get(NATIVE_IMAGE) instanceof StringTag bound)
                ||!validDigest(source.value())||!validDigest(bound.value()))return null;
        return bound.value().equals(image(actualNativePlan))?source.value():null;
    }

    private static String image(CompoundTag actual){var copy=actual.copy();copy.remove(SOURCE_VALUE);copy.remove(NATIVE_IMAGE);return digest(copy);}
    private static boolean validDigest(String value){return value!=null&&value.matches("[0-9a-f]{64}");}
    private static String digest(Tag actual){
        try{
            var digest=MessageDigest.getInstance("SHA-256");
            try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))){
                text(out,DOMAIN);write(out,actual,0);
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(IOException|NoSuchAlgorithmException impossible){throw new IllegalStateException("Native typed packed value digest failed",impossible);}
    }
    private static void text(DataOutputStream out,String text)throws IOException{var bytes=text.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static void write(DataOutputStream out,Tag actual,int depth)throws IOException{
        if(depth>256)throw new IllegalArgumentException("Packed semantic value exceeds native nesting bounds");
        out.writeByte(actual.getId());
        if(actual instanceof CompoundTag compound){
            var keys=new TreeSet<>(compound.keySet());out.writeInt(keys.size());
            for(var key:keys){text(out,key);write(out,compound.get(key),depth+1);}
        }else if(actual instanceof ListTag list){out.writeInt(list.size());for(var child:list)write(out,child,depth+1);}
        else if(actual instanceof StringTag string)text(out,string.value());
        else actual.write(out);
    }
    private NeoForgePackedValueSemantics(){}
}
