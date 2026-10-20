package com.simibubi.create.content.trains.entity;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Validate every literal source identity, supported native role and current parent edge before capture. */
public final class CarriagePassengerPreflight {
  private record Visit(Entity entity, int depth) {}

  public static Map<BlockPos, Entity> validate(
      Level level, CarriageContraption body, BlockPos offset) throws AssemblyException {
    if (!(level instanceof ServerLevel server))
      throw new IllegalStateException(
          "Carriage passenger preflight requires the actual server world");
    var identities = new HashSet<UUID>();
    var roots = new LinkedHashMap<BlockPos, Entity>();
    for (var local : body.getSeats()) {
      var source = body.anchor.offset(local).offset(offset);
      LoadedEntityAccess.requireReady(server, source);
      for (var seat : server.getEntitiesOfClass(SeatEntity.class, new AABB(source))) {
        if (seat.isRemoved() || server.getEntity(seat.getUUID()) != seat)
          throw AssemblyException.entitiesNotReady(source);
        var root = seat.getFirstPassenger();
        if (root == null) continue;
        if (roots.putIfAbsent(local, root) != null)
          throw AssemblyException.entitiesNotReady(source);
        var pending = new ArrayDeque<Visit>();
        pending.add(new Visit(root, 0));
        while (!pending.isEmpty()) {
          var visit = pending.removeFirst();
          var actual = visit.entity();
          if (visit.depth() > 64
              || identities.size() >= 1024
              || !identities.add(actual.getUUID())
              || actual.level() != server
              || actual.isRemoved()
              || server.getEntity(actual.getUUID()) != actual)
            throw AssemblyException.entitiesNotReady(source);
          LoadedEntityAccess.requireReady(server,actual.blockPosition());
          if(actual instanceof Player){if(!(actual instanceof ServerPlayer player)||server.getServer().getPlayerList().getPlayer(player.getUUID())!=player||!actual.getPassengers().isEmpty())throw AssemblyException.entitiesNotReady(source);}
          else if(!actual.getType().canSerialize())throw AssemblyException.entitiesNotReady(source);
          for(var child:actual.getPassengers())if(child.getVehicle()!=actual)throw AssemblyException.entitiesNotReady(source);
          for (var child : actual.getPassengers())
            pending.addLast(new Visit(child, visit.depth() + 1));
        }
      }
    }
    return Map.copyOf(roots);
  }

  private CarriagePassengerPreflight() {}
}
