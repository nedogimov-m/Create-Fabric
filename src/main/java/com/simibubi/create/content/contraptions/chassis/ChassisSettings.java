package com.simibubi.create.content.contraptions.chassis;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Native wrench value board. Packet target/group selection is recomputed on the server. */
public final class ChassisSettings {
    /** Source CKinetics default; root must bind this to the common server setting when that framework is ported. */
    public static volatile int maxRange=16;
    private static volatile int clientMaximum=16;
    public static int maximumForCurrentSide(){return !com.simibubi.create.infrastructure.config.AllConfigs.isServerThread()&&com.simibubi.create.infrastructure.config.AllConfigs.store(com.simibubi.create.infrastructure.config.ConfigScope.SERVER).hasRemote()?clientMaximum:maxRange;}
    public static void installClientMaximum(int value){if(value<1)throw new IllegalArgumentException("Source chassis minimum1");clientMaximum=value;var actual=com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxChassisRange;com.simibubi.create.infrastructure.config.AllConfigs.installRemoteValues(java.util.Map.of(actual.key(),value));}
    public static void clearClientMaximum(){clientMaximum=16;var actual=com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxChassisRange;com.simibubi.create.infrastructure.config.AllConfigs.clearRemoteValues(java.util.List.of(actual.key()));}
    public static BiConsumer<BlockPos,Direction> CLIENT_START=(pos,face)->{};
    public record RangeLimit(int maximum)implements CustomPacketPayload{
        public static final Type<RangeLimit> TYPE=new Type<>(Create.asResource("chassis_range_limit"));
        public static final StreamCodec<RegistryFriendlyByteBuf,RangeLimit> CODEC=CustomPacketPayload.codec((packet,buf)->buf.writeVarInt(packet.maximum),buf->new RangeLimit(buf.readVarInt()));
        @Override public Type<RangeLimit> type(){return TYPE;}
    }
    public record SetRange(BlockPos pos,int value,boolean bulk)implements CustomPacketPayload{
        public SetRange{pos=pos.immutable();}
        public static final Type<SetRange> TYPE=new Type<>(Create.asResource("chassis_range"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetRange> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeBlockPos(packet.pos);buf.writeVarInt(packet.value);buf.writeBoolean(packet.bulk);},buf->new SetRange(buf.readBlockPos(),buf.readVarInt(),buf.readBoolean()));
        @Override public Type<SetRange> type(){return TYPE;}
    }
    public static boolean panelHit(BlockPos pos,BlockHitResult hit){
        var offset=Vec3.atCenterOf(pos).add(hit.getDirection().getUnitVec3().scale(.5-1/32d));
        return hit.getLocation().distanceTo(offset)<.25;
    }
    public static boolean targetsPanel(Level level,Player player,BlockPos pos){
        var eye=player.getEyePosition();var end=eye.add(player.getViewVector(1).scale(player.blockInteractionRange()+1));
        var hit=ContraptionWorld.loadedWorld(level).clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos)&&panelHit(pos,hit);
    }
    public static boolean apply(Level level,Player player,SetRange packet){
        if(level.isClientSide()||packet.value<1||packet.value>Math.max(1,maxRange)||player.isSpectator()||!player.mayBuild()||!player.getMainHandItem().is(AllItems.WRENCH)||!player.isWithinBlockInteractionRange(packet.pos,1)||!level.mayInteract(player,packet.pos)||!targetsPanel(level,player,packet.pos)||!(LoadedLevelAccess.blockEntity(level,packet.pos) instanceof ChassisBlockEntity chassis))return false;
        List<ChassisBlockEntity> group;
        try{group=packet.bulk?chassis.collectChassisGroup():List.of(chassis);}catch(AssemblyException unavailable){return false;}
        for(var member:group)if(member.isRemoved()||member.getLevel()!=level||!level.mayInteract(player,member.getBlockPos())||LoadedLevelAccess.blockEntity(level,member.getBlockPos())!=member)return false;
        for(var member:group)member.setRange(packet.value);return true;
    }
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(SetRange.TYPE,SetRange.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RangeLimit.TYPE,RangeLimit.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->ServerPlayNetworking.send(handler.player,new RangeLimit(Math.max(1,maxRange))));
        ServerPlayNetworking.registerGlobalReceiver(SetRange.TYPE,(packet,context)->apply(context.player().level(),context.player(),packet));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{
            if(hand!=InteractionHand.MAIN_HAND||player.isShiftKeyDown()||player.isSpectator()||!player.mayBuild()||!player.getMainHandItem().is(AllItems.WRENCH)||!level.mayInteract(player,hit.getBlockPos())||!panelHit(hit.getBlockPos(),hit)||!(LoadedLevelAccess.blockEntity(level,hit.getBlockPos()) instanceof ChassisBlockEntity))return InteractionResult.PASS;
            if(level.isClientSide())CLIENT_START.accept(hit.getBlockPos().immutable(),hit.getDirection());return InteractionResult.SUCCESS;
        });
    }
    private ChassisSettings(){}
}
