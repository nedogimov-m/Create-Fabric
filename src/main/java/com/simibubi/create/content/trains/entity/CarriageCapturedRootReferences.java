package com.simibubi.create.content.trains.entity;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.*;

/** Unadopted original roots stay world/PlayerData-owned: retain only exact UUID/seat intent. */
public final class CarriageCapturedRootReferences {
    /** Ownership metadata may be absent, but a malformed present field has no usable partial value. */
    static <A> MapCodec<Optional<A>> strictOptional(Codec<A> codec,String name){
        var field=codec.optionalFieldOf(name);
        return new MapCodec<>(){
            @Override public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops,MapLike<T> input){var decoded=field.decode(ops,input);var error=decoded.error();return error.isPresent()?DataResult.error(error.get().messageSupplier(),decoded.lifecycle()):decoded;}
            @Override public <T> RecordBuilder<T> encode(Optional<A> value,DynamicOps<T> ops,RecordBuilder<T> prefix){return field.encode(value,ops,prefix);}
            @Override public <T> Stream<T> keys(DynamicOps<T> ops){return field.keys(ops);}
        };
    }
    private record Root(UUID id,int seat){
        static final Codec<Root> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Root").forGetter(Root::id),Codec.intRange(0,Integer.MAX_VALUE).fieldOf("Seat").forGetter(Root::seat)).apply(i,Root::new));
    }
    private final CarriageContraptionEntity front;private final Map<UUID,Integer> pending=new LinkedHashMap<>();private boolean adopting;
    public CarriageCapturedRootReferences(CarriageContraptionEntity front){this.front=front;}
    public void captureInitials(){
        if(front.getContraption()==null)return;for(var entry:front.getContraption().getInitialPassengers().entrySet()){
            var original=entry.getValue();var reason=original.getRemovalReason();if(reason!=null&&reason.shouldDestroy()){pending.remove(original.getUUID());continue;}int seat=front.getContraption().getSeats().indexOf(entry.getKey());if(seat<0)throw new IllegalStateException("Original captured root has no actual seat");if(original.getVehicle()==front&&Objects.equals(front.getContraption().getSeatMapping().get(original.getUUID()),seat)){pending.remove(original.getUUID());continue;}var previous=pending.putIfAbsent(original.getUUID(),seat);if(previous!=null&&previous!=seat)throw new IllegalStateException("One original captured root cannot claim two native seats");
        }
        pruneAccepted();
    }
    public void pruneAccepted(){if(!(front.level() instanceof ServerLevel level))return;for(var entry:List.copyOf(pending.entrySet())){var actual=level.getEntity(entry.getKey());if(actual!=null&&actual.getVehicle()==front&&Objects.equals(front.getContraption().getSeatMapping().get(entry.getKey()),entry.getValue()))pending.remove(entry.getKey(),entry.getValue());}}
    public boolean adopt(){
        if(front.level().isClientSide())return true;if(adopting)return pending.isEmpty();if(!(front.level() instanceof ServerLevel level)||!CarriagePlayerReferenceTransitions.frontCurrent(front))return false;adopting=true;
        try{captureInitials();for(var entry:List.copyOf(pending.entrySet())){var actual=level.getEntity(entry.getKey());if(actual==null||actual.isRemoved()||!actual.isAlive()||!LoadedEntityAccess.isReady(level,actual.blockPosition()))continue;if(actual instanceof ServerPlayer player&&level.getServer().getPlayerList().getPlayer(player.getUUID())!=player)continue;if(actual.getVehicle()!=null&&actual.getVehicle()!=front&&!(actual.getVehicle() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity))continue;if(front.addSittingPassenger(actual,entry.getValue()))pending.remove(entry.getKey(),entry.getValue());}return pending.isEmpty();}finally{adopting=false;}
    }
    public void write(ValueOutput output){captureInitials();var values=pending.entrySet().stream().map(e->new Root(e.getKey(),e.getValue())).toList();if(!values.isEmpty())output.store("PendingCapturedRoots",Root.CODEC.listOf(),values);}
    public void read(ValueInput input){pending.clear();var seats=new HashSet<Integer>();var roots=input.read(strictOptional(Root.CODEC.listOf(),"PendingCapturedRoots")).orElseThrow(()->new IllegalArgumentException("Malformed native original-root reference field")).orElse(List.of());if(roots.size()>CarriagePassengerGraph.MAX_NODES)throw new IllegalArgumentException("Persisted original root intent bound exceeded");for(var root:roots)if(pending.putIfAbsent(root.id(),root.seat())!=null||!seats.add(root.seat()))throw new IllegalArgumentException("Duplicate persisted original root/seat intent");}
    public void validateSeats(){if(pending.isEmpty())return;if(front.getContraption()==null)throw new IllegalStateException("Captured root references require actual native body");for(int seat:pending.values())if(seat<0||seat>=front.getContraption().getSeats().size())throw new IllegalArgumentException("Persisted original root seat outside actual body");}
    Map<UUID,Integer> persisted(){return Map.copyOf(pending);}
    public boolean empty(){pruneAccepted();return pending.isEmpty();}
    /** No NPC payload/cargo is owned here; world restoration receives only UUID/seat intent. */
    public Map<UUID,Integer> pending(){captureInitials();return Map.copyOf(pending);}
    public void clearWorldTransferred(){pending.clear();}
}
