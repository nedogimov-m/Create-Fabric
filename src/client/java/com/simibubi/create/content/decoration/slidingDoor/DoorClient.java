package com.simibubi.create.content.decoration.slidingDoor;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.VirtualRenderWorld;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public final class DoorClient {
    private record Key(UUID owner,BlockPos pos){}
    private record Pending(DoorStatePacket packet,long expires){}
    private static final Map<Key,Pending> PENDING=new LinkedHashMap<>();
    private static ClientLevel level;
    private static long ticks;
    private static void ensureWorld(ClientLevel current){if(level!=current){level=current;ticks=0;PENDING.clear();}}
    public static void register(){
        SlidingDoorModels.register();
        ClientPlayNetworking.registerGlobalReceiver(DoorStatePacket.TYPE,(packet,context)->{ensureWorld(context.client().level);if(level==null)return;if(PENDING.size()>=1024)PENDING.remove(PENDING.keySet().iterator().next());PENDING.put(new Key(packet.uuid(),packet.pos()),new Pending(packet,ticks+200));apply();});
        ClientTickEvents.END_CLIENT_TICK.register(client->{ensureWorld(client.level);ticks++;apply();});
    }
    private static void apply(){
        if(level==null)return;
        for(var iterator=PENDING.values().iterator();iterator.hasNext();){var pending=iterator.next();var packet=pending.packet;if(pending.expires<ticks){iterator.remove();continue;}
            if(!(level.getEntity(packet.entityId()) instanceof AbstractContraptionEntity owner)||!owner.getUUID().equals(packet.uuid())||owner.getContraption()==null)continue;
            var changed=SlidingDoorMovementBehaviour.setDoorOpen(owner.getContraption(),packet.pos(),packet.open(),false);
            if(owner.getClientRenderState() instanceof VirtualRenderWorld view)for(var pos:changed)view.setCapturedBlockState(pos,owner.getContraption().getBlocks().get(pos).state());
            iterator.remove();
        }
    }
    private DoorClient(){}
}
