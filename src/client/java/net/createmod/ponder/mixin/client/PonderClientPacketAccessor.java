package net.createmod.ponder.mixin.client;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ClientPacketListener.class)
public interface PonderClientPacketAccessor {
  @Accessor("serverChunkRadius") int ponder$chunkRadius();
}
