package com.simibubi.create.test;

import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.TicketStorage;

/** Read-only snapshots of the genuine native ticket storage; never obtains a chunk or adds a ticket. */
public final class SchematicBoundaryFixtures {
    @SuppressWarnings("unchecked") public static Map<Long,List<String>> tickets(ServerLevel level){
        try{
            var field=ServerChunkCache.class.getDeclaredField("ticketStorage");field.setAccessible(true);
            var storage=(TicketStorage)field.get(level.getChunkSource());var tickets=TicketStorage.class.getDeclaredField("tickets");tickets.setAccessible(true);
            var actual=(it.unimi.dsi.fastutil.longs.Long2ObjectMap<List<Ticket>>)tickets.get(storage);var copy=new TreeMap<Long,List<String>>();
            for(var entry:actual.long2ObjectEntrySet())copy.put(entry.getLongKey(),entry.getValue().stream().map(Ticket::toString).toList());return copy;
        }catch(ReflectiveOperationException failure){throw new AssertionError("Actual native ticket storage API changed",failure);}
    }
    private SchematicBoundaryFixtures(){}
}
