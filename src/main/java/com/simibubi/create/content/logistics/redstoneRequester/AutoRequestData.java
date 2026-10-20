package com.simibubi.create.content.logistics.redstoneRequester;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record AutoRequestData(PackageOrderWithCrafts encodedRequest,String encodedTargetAddress,BlockPos targetOffset,String targetDim,boolean isValid) {
    public static final Codec<AutoRequestData> CODEC=RecordCodecBuilder.create(i->i.group(
        PackageOrderWithCrafts.CODEC.fieldOf("encoded_request").forGetter(AutoRequestData::encodedRequest),
        Codec.STRING.fieldOf("encoded_target_address").forGetter(AutoRequestData::encodedTargetAddress),
        BlockPos.CODEC.fieldOf("target_offset").forGetter(AutoRequestData::targetOffset),
        Codec.STRING.fieldOf("target_dim").forGetter(AutoRequestData::targetDim),
        Codec.BOOL.fieldOf("is_valid").forGetter(AutoRequestData::isValid)).apply(i,AutoRequestData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,AutoRequestData> STREAM_CODEC=StreamCodec.composite(PackageOrderWithCrafts.STREAM_CODEC,AutoRequestData::encodedRequest,ByteBufCodecs.STRING_UTF8,AutoRequestData::encodedTargetAddress,BlockPos.STREAM_CODEC,AutoRequestData::targetOffset,ByteBufCodecs.STRING_UTF8,AutoRequestData::targetDim,ByteBufCodecs.BOOL,AutoRequestData::isValid,AutoRequestData::new);
    public AutoRequestData() { this(PackageOrderWithCrafts.empty(),"",BlockPos.ZERO,"null",false); }
    public void writeToItem(BlockPos position,ItemStack stack) { var mutable=new Mutable(this);mutable.targetOffset=position.offset(targetOffset);stack.set(AllDataComponents.AUTO_REQUEST_DATA,mutable.toImmutable()); }
    public static AutoRequestData readFromItem(Level level,Player player,BlockPos position,ItemStack stack) {
        var data=stack.get(AllDataComponents.AUTO_REQUEST_DATA);if(data==null)return null;
        var mutable=new Mutable(data);mutable.targetOffset=mutable.targetOffset.subtract(position);mutable.isValid=mutable.targetOffset.closerThan(BlockPos.ZERO,128)&&data.targetDim.equals(level.dimension().identifier().toString());
        if(player!=null)player.sendOverlayMessage(Component.translatable(mutable.isValid?"create.redstone_requester.keeper_connected":"create.redstone_requester.keeper_too_far_away").withStyle(mutable.isValid?ChatFormatting.WHITE:ChatFormatting.RED));
        return mutable.toImmutable();
    }
    public static class Mutable {
        public PackageOrderWithCrafts encodedRequest=PackageOrderWithCrafts.empty();
        public String encodedTargetAddress="",targetDim="null";
        public BlockPos targetOffset=BlockPos.ZERO;
        public boolean isValid;
        public Mutable() {}
        public Mutable(AutoRequestData data) { encodedRequest=data.encodedRequest;encodedTargetAddress=data.encodedTargetAddress;targetOffset=data.targetOffset;targetDim=data.targetDim;isValid=data.isValid; }
        public AutoRequestData toImmutable() { return new AutoRequestData(encodedRequest,encodedTargetAddress,targetOffset,targetDim,isValid); }
    }
}
