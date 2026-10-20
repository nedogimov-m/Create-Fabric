package com.simibubi.create.content.kinetics.crank;

import java.util.function.BiConsumer;
import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Source short click/hold value board routed through real native input and protected server receivers. */
public final class ValveHandleSettings {
    public static BiConsumer<BlockPos,InteractionHand> CLIENT_START = (pos,hand) -> {};
    public record SetAngle(BlockPos pos,int angle) implements CustomPacketPayload {
        public static final Type<SetAngle> TYPE = new Type<>(Create.asResource("valve_handle_angle"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetAngle> CODEC = CustomPacketPayload.codec(
                (packet,buf) -> { buf.writeBlockPos(packet.pos); buf.writeVarInt(packet.angle); },
                buf -> new SetAngle(buf.readBlockPos(),buf.readVarInt()));
        @Override public Type<SetAngle> type() { return TYPE; }
    }
    public record Activate(BlockPos pos,InteractionHand hand) implements CustomPacketPayload {
        public static final Type<Activate> TYPE = new Type<>(Create.asResource("valve_handle_activate"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Activate> CODEC = CustomPacketPayload.codec(
                (packet,buf) -> { buf.writeBlockPos(packet.pos); buf.writeEnum(packet.hand); },
                buf -> new Activate(buf.readBlockPos(),buf.readEnum(InteractionHand.class)));
        @Override public Type<Activate> type() { return TYPE; }
    }
    public static Vec3 offset(Direction face) { return new Vec3(.5-face.getStepX()*3.5/16,.5-face.getStepY()*3.5/16,.5-face.getStepZ()*3.5/16); }
    public static boolean testHit(BlockState state,BlockHitResult hit) {
        return state.getBlock() instanceof ValveHandleBlock && hit.getDirection() == state.getValue(ValveHandleBlock.FACING)
                && hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())).distanceTo(offset(hit.getDirection())) < 1d/3;
    }
    private static ValveHandleBlockEntity editable(Level level,Player player,BlockPos pos) {
        if (player.isSpectator() || !player.mayBuild() || !player.isWithinBlockInteractionRange(pos,1)
                || !level.mayInteract(player,pos) || !LoadedLevelAccess.isLoaded(level,pos)) return null;
        return LoadedLevelAccess.blockEntity(level,pos) instanceof ValveHandleBlockEntity valve ? valve : null;
    }
    public static boolean apply(Level level,Player player,SetAngle packet) {
        var valve = editable(level,player,packet.pos);
        if (valve == null || !valve.showValue() || packet.angle == 0 || packet.angle < -180 || packet.angle > 180) return false;
        valve.setAngleInput(packet.angle); return true;
    }
    public static boolean apply(Level level,Player player,Activate packet) {
        var valve = editable(level,player,packet.pos);
        return valve != null && ((ValveHandleBlock)valve.getBlockState().getBlock()).clicked(level,packet.pos,valve.getBlockState(),player,packet.hand);
    }
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(SetAngle.TYPE,SetAngle.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Activate.TYPE,Activate.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetAngle.TYPE,(packet,context) -> apply(context.player().level(),context.player(),packet));
        ServerPlayNetworking.registerGlobalReceiver(Activate.TYPE,(packet,context) -> apply(context.player().level(),context.player(),packet));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit) -> {
            var pos = hit.getBlockPos(); var state = LoadedLevelAccess.blockState(level,pos);
            if (!(state.getBlock() instanceof ValveHandleBlock block) || !player.mayBuild() || player.isSpectator()
                    || !level.mayInteract(player,pos) || !(LoadedLevelAccess.blockEntity(level,pos) instanceof ValveHandleBlockEntity valve)) return InteractionResult.PASS;
            var held = player.getItemInHand(hand);
            if (player.isShiftKeyDown() && held.is(AllItems.WRENCH)) return InteractionResult.PASS;
            if (!player.isShiftKeyDown() && testHit(state,hit) && !held.has(net.minecraft.core.component.DataComponents.DYE)
                    && !held.is(AllItems.WRENCH) && valve.showValue()) {
                if (level.isClientSide()) CLIENT_START.accept(pos.immutable(),hand);
                return InteractionResult.SUCCESS;
            }
            // Source RightClickBlock override also handles held items and reverse (shift) clicks.
            return block.clicked(level,pos,state,player,hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }
    private ValveHandleSettings() {}
}
