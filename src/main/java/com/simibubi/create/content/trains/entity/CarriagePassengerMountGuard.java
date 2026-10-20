package com.simibubi.create.content.trains.entity;

import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Supported native trees have serializable NPC nodes and literal current PlayerList leaves. */
public final class CarriagePassengerMountGuard {
    private record Visit(Entity entity,int depth){}
    public static boolean mayAdopt(Entity root){
        if(!(root.level() instanceof ServerLevel level))return root.level().isClientSide();
        var pending=new ArrayDeque<Visit>();var identities=new HashSet<UUID>();pending.add(new Visit(root,0));
        while(!pending.isEmpty()){
            var visit=pending.removeFirst();var actual=visit.entity();
            if(visit.depth()>CarriagePassengerGraph.MAX_DEPTH||identities.size()>=CarriagePassengerGraph.MAX_NODES||!identities.add(actual.getUUID())||actual.level()!=level||actual.isRemoved()||level.getEntity(actual.getUUID())!=actual||!LoadedEntityAccess.isReady(level,actual.blockPosition()))return false;
            if(actual instanceof Player){if(!(actual instanceof ServerPlayer player)||level.getServer().getPlayerList().getPlayer(player.getUUID())!=player||!actual.getPassengers().isEmpty())return false;}
            else if(!actual.getType().canSerialize())return false;
            for(var child:actual.getPassengers()){if(child.getVehicle()!=actual)return false;pending.addLast(new Visit(child,visit.depth()+1));}
        }
        return true;
    }
    public static boolean mayMount(Entity passenger,Entity parent){
        if(passenger.level().isClientSide()||parent==null)return true;
        var front=parent.getRootVehicle() instanceof CarriageContraptionEntity carrier?carrier:null;
        if(!CarriageNpcMaterialOwners.permitsMove(passenger,parent,front))return false;
        if(front==null)return true;
        return CarriagePlayerReferenceTransitions.frontCurrent(front)&&LoadedEntityAccess.isReady(front.level(),front.blockPosition())&&CarriagePlayerReferenceTransitions.hasPreparedSeat(front,parent==front?passenger:rootBelow(parent,front))&&!(parent instanceof Player)&&mayAdopt(passenger);
    }
    private static Entity rootBelow(Entity actual,CarriageContraptionEntity front){for(int depth=0;depth<=CarriagePassengerGraph.MAX_DEPTH;depth++){if(actual.getVehicle()==front)return actual;if(actual.getVehicle()==null)return front;actual=actual.getVehicle();}return front;}
    private CarriagePassengerMountGuard(){}
}
