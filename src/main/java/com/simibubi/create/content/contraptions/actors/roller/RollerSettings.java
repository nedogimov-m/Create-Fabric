package com.simibubi.create.content.contraptions.actors.roller;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.ContraptionWorld;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Native held-use mode board; server validates the actual upper value-box ray. */
public final class RollerSettings {
    public static Consumer<BlockPos> CLIENT_START = pos -> {};
    public record SetMode(BlockPos pos, int value) implements CustomPacketPayload {
        public static final Type<SetMode> TYPE = new Type<>(Create.asResource("roller_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetMode> CODEC = CustomPacketPayload.codec((packet, buffer) -> {
            buffer.writeBlockPos(packet.pos); buffer.writeVarInt(packet.value);
        }, buffer -> new SetMode(buffer.readBlockPos(), buffer.readVarInt()));
        @Override public Type<SetMode> type() { return TYPE; }
    }
    public static boolean apply(Level level, Player player, SetMode packet) {
        if (packet.value < 0 || packet.value >= RollerBlockEntity.RollingMode.values().length || player.isSpectator() || !player.mayBuild()
            || !player.isWithinBlockInteractionRange(packet.pos, 1) || !level.mayInteract(player, packet.pos)
            || !(LoadedLevelAccess.blockEntity(level, packet.pos) instanceof RollerBlockEntity roller)) return false;
        if (!targetsPanel(level, player, packet.pos, false)) return false;
        roller.setMode(RollerBlockEntity.RollingMode.values()[packet.value]); return true;
    }
    public static boolean targetsPanel(Level level, Player player, BlockPos pos, boolean filter) {
        var state = LoadedLevelAccess.blockState(level, pos);
        if (!(state.getBlock() instanceof RollerBlock)) return false;
        var start = player.getEyePosition(); var end = start.add(player.getViewVector(1).scale(player.blockInteractionRange() + 1));
        var hit = ContraptionWorld.loadedWorld(level).clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && RollerBlock.testPanelHit(state, pos, hit, filter);
    }
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(SetMode.TYPE, SetMode.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetMode.TYPE, (packet, context) -> apply(context.player().level(), context.player(), packet));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || player.isSpectator() || !player.mayBuild()
                || player.getMainHandItem().is(AllItems.WRENCH) || !level.mayInteract(player, hit.getBlockPos())) return InteractionResult.PASS;
            var state = LoadedLevelAccess.blockState(level, hit.getBlockPos());
            if (!(state.getBlock() instanceof RollerBlock) || !RollerBlock.testPanelHit(state, hit.getBlockPos(), hit, false)) return InteractionResult.PASS;
            if (level.isClientSide()) CLIENT_START.accept(hit.getBlockPos().immutable()); return InteractionResult.SUCCESS;
        });
    }
    private RollerSettings() {}
}
