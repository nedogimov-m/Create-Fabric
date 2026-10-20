package com.simibubi.create.test;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsInputPacket;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;

/** Test-only observations of actual native tick/packet boundaries, never packets or owners fabricated by a test. */
public final class TrainVehiclePacketProbe {
    private static volatile UUID watched;
    private static final AtomicInteger clientTicks=new AtomicInteger(),localAuthorityTicks=new AtomicInteger(),
        clientVehicle=new AtomicInteger(),serverVehicle=new AtomicInteger(),resets=new AtomicInteger(),
        clientControls=new AtomicInteger(),serverControls=new AtomicInteger(),rotations=new AtomicInteger();
    public record Snapshot(int ticks,int localAuthorityTicks,int clientVehicle,int serverVehicle,int resets,int clientControls,int serverControls,int rotations){}
    public static void watch(UUID body){watched=null;for(var count:new AtomicInteger[]{clientTicks,localAuthorityTicks,clientVehicle,serverVehicle,resets,clientControls,serverControls,rotations})count.set(0);watched=body;}
    public static void clear(){watched=null;}
    private static boolean riding(Minecraft client){return watched!=null&&client.player!=null&&client.player.getRootVehicle().getUUID().equals(watched);}
    public static void tick(){var client=Minecraft.getInstance();if(!riding(client))return;clientTicks.incrementAndGet();if(client.player.getRootVehicle().isLocalInstanceAuthoritative())localAuthorityTicks.incrementAndGet();}
    public static void sent(Packet<?> packet){
        if(watched==null||!(packet instanceof ServerboundMoveVehiclePacket||packet instanceof ServerboundMovePlayerPacket.Rot||packet instanceof ServerboundCustomPayloadPacket))return;
        var client=Minecraft.getInstance();
        if(riding(client)){if(packet instanceof ServerboundMoveVehiclePacket)clientVehicle.incrementAndGet();if(packet instanceof ServerboundMovePlayerPacket.Rot)rotations.incrementAndGet();}
        if(packet instanceof ServerboundCustomPayloadPacket custom&&custom.payload() instanceof ControlsInputPacket controls&&controls.body().equals(watched))clientControls.incrementAndGet();
    }
    public static void vehicleReceived(ServerPlayer player){if(watched!=null&&player.level().getServer().isSameThread()&&player.getRootVehicle().getUUID().equals(watched))serverVehicle.incrementAndGet();}
    public static void controlsReceived(ServerPlayer player,ControlsInputPacket packet){if(watched!=null&&player.level().getServer().isSameThread()&&packet.body().equals(watched))serverControls.incrementAndGet();}
    public static void resetReceived(){var client=Minecraft.getInstance();if(client.isSameThread()&&riding(client))resets.incrementAndGet();}
    public static Snapshot snapshot(){return new Snapshot(clientTicks.get(),localAuthorityTicks.get(),clientVehicle.get(),serverVehicle.get(),resets.get(),clientControls.get(),serverControls.get(),rotations.get());}
    public static void assertTrain(){var s=snapshot();if(s.ticks<20||s.localAuthorityTicks!=s.ticks||s.clientVehicle!=0||s.serverVehicle!=0||s.resets!=0||s.clientControls<1||s.serverControls<1||s.rotations<1)throw new AssertionError("Actual native Train must retain local interpolation and real control/player packets without vanilla coordinate/reset traffic: "+s);}
    public static void assertVanillaVehicle(){var s=snapshot();if(s.ticks<10||s.localAuthorityTicks!=s.ticks||s.clientVehicle<1||s.serverVehicle<1||s.rotations<1)throw new AssertionError("Genuine vanilla controlling passenger must retain native vehicle movement sending: "+s);}
    private TrainVehiclePacketProbe(){}
}
