package com.simibubi.create.content.equipment.zapper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
public abstract class ConfigureZapperPacket implements CustomPacketPayload {
    protected final InteractionHand hand;
    protected final PlacementPatterns pattern;
    public ConfigureZapperPacket(InteractionHand hand,PlacementPatterns pattern){this.hand=java.util.Objects.requireNonNull(hand);this.pattern=java.util.Objects.requireNonNull(pattern);}
    public void handle(ServerPlayer player){if(!player.isSpectator()&&player.getItemInHand(hand).getItem() instanceof ZapperItem)configureZapper(player.getItemInHand(hand));}
    public abstract void configureZapper(ItemStack stack);
}
