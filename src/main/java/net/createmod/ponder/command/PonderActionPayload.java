// Ponder/Catnip 1.0.82 actions, MIT; bounded typed Fabric26.3 clientbound transport.
package net.createmod.ponder.command;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Only the four Ponder actions cross the server-to-client boundary. */
public record PonderActionPayload(Action action,@Nullable Identifier scene) implements CustomPacketPayload {
    public enum Action { INDEX,TAGS,SCENE,RELOAD }
    public static final Type<PonderActionPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("ponder","action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PonderActionPayload> CODEC=CustomPacketPayload.codec((packet,buffer)->{
        buffer.writeEnum(packet.action());
        if(packet.action()==Action.SCENE)Identifier.STREAM_CODEC.encode(buffer,packet.scene());
    },buffer->{var action=buffer.readEnum(Action.class);return new PonderActionPayload(action,action==Action.SCENE?Identifier.STREAM_CODEC.decode(buffer):null);});
    private static boolean registered;
    public PonderActionPayload {
        java.util.Objects.requireNonNull(action,"action");
        if((action==Action.SCENE)!=(scene!=null))throw new IllegalArgumentException("Only SCENE carries a Ponder component identifier");
    }
    public static PonderActionPayload open(String value){
        if(value.equals("index")||value.equals("ponder:index"))return new PonderActionPayload(Action.INDEX,null);
        if(value.equals("ponder:tags"))return new PonderActionPayload(Action.TAGS,null);
        return new PonderActionPayload(Action.SCENE,Identifier.parse(value));
    }
    public static PonderActionPayload reload(){return new PonderActionPayload(Action.RELOAD,null);}
    @Override public Type<PonderActionPayload> type(){return TYPE;}
    public static void register(){if(registered)return;registered=true;PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);}
}
