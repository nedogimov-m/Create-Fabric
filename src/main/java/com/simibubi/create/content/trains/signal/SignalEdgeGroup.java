package com.simibubi.create.content.trains.signal;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.entity.Train;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

/** Real source traffic group: exact Train membership, boundary reservations and intersecting closure. */
public final class SignalEdgeGroup {
    private record Intersection(UUID key,UUID value){static final Codec<Intersection> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Key").forGetter(Intersection::key),UUIDUtil.CODEC.fieldOf("Value").forGetter(Intersection::value)).apply(i,Intersection::new));}
    public static final Codec<SignalEdgeGroup> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Id").forGetter(group->group.id),EdgeGroupColor.CODEC.optionalFieldOf("Color",EdgeGroupColor.YELLOW).forGetter(group->group.color),Intersection.CODEC.listOf().optionalFieldOf("Connected",List.of()).forGetter(group->group.intersecting.entrySet().stream().map(entry->new Intersection(entry.getKey(),entry.getValue())).toList()),Codec.BOOL.optionalFieldOf("Fallback",false).forGetter(group->group.fallbackGroup)).apply(i,(id,color,intersections,fallback)->{var group=new SignalEdgeGroup(id);group.color=color;group.fallbackGroup=fallback;for(var intersection:intersections)if(group.intersecting.putIfAbsent(intersection.key,intersection.value)!=null)throw new IllegalArgumentException("Duplicate actual signal intersection identity");return group;}));
    public final UUID id;
    public EdgeGroupColor color=EdgeGroupColor.getDefault();
    public final Set<Train> trains=Collections.newSetFromMap(new IdentityHashMap<>());
    public SignalBoundary reserved;
    public final Map<UUID,UUID> intersecting=new LinkedHashMap<>();
    public final Set<SignalEdgeGroup> intersectingResolved=Collections.newSetFromMap(new IdentityHashMap<>());
    public final Set<UUID> adjacent=new LinkedHashSet<>();
    public boolean fallbackGroup;
    public boolean topologyReady=true;
    private TrainManager manager;
    public SignalEdgeGroup(UUID id){this.id=Objects.requireNonNull(id);}
    public SignalEdgeGroup asFallback(){fallbackGroup=true;return this;}
    public void bind(TrainManager manager){if(this.manager!=null&&manager!=null&&this.manager!=manager)throw new IllegalStateException("Traffic group belongs to another actual native server");this.manager=manager;intersectingResolved.clear();}
    public TrainManager manager(){return manager;}
    public boolean hasResolvedTopology(){resolve();return intersectingResolved.stream().allMatch(group->group.topologyReady);}
    public boolean isOccupiedUnless(Train train){resolve();for(var group:intersectingResolved)if(!group.topologyReady||group.reserved!=null&&(group.manager==null||!group.manager.reservedBy(group,train))||group.trains.size()>1||!group.trains.contains(train)&&!group.trains.isEmpty())return true;return false;}
    public boolean isOccupiedUnless(SignalBoundary boundary){resolve();for(var group:intersectingResolved)if(!group.topologyReady||!group.trains.isEmpty()||group.reserved!=null&&group.reserved!=boundary)return true;return false;}
    private void resolve(){if(intersectingResolved.isEmpty())walkIntersecting(intersectingResolved::add);}
    public void putIntersection(UUID intersectionId,UUID targetGroup){Objects.requireNonNull(intersectionId);Objects.requireNonNull(targetGroup);if(Objects.equals(intersecting.get(intersectionId),targetGroup))return;walkIntersecting(group->group.intersectingResolved.clear());intersecting.put(intersectionId,targetGroup);walkIntersecting(group->group.intersectingResolved.clear());resolveColor();dirty();}
    public void removeIntersection(UUID intersectionId){walkIntersecting(group->group.intersectingResolved.clear());var removed=intersecting.remove(intersectionId);if(manager!=null){var other=manager.signalEdgeGroups().get(removed);if(other!=null){other.walkIntersecting(group->group.intersectingResolved.clear());other.intersecting.remove(intersectionId);other.resolveColor();}}resolveColor();dirty();}
    public void putAdjacent(UUID id){if(adjacent.add(Objects.requireNonNull(id)))dirty();}
    public void removeAdjacent(UUID id){if(adjacent.remove(id))dirty();}
    public void resolveColor(){resolve();int mask=0;if(manager!=null)for(var group:intersectingResolved)for(var adjacentId:group.adjacent){var other=manager.signalEdgeGroups().get(adjacentId);if(other!=null&&!intersectingResolved.contains(other))mask=other.color.strikeFrom(mask);}var next=EdgeGroupColor.findNextAvailable(mask);if(next==color)return;walkIntersecting(group->{group.color=next;group.dirty();});}
    public void walkIntersecting(Consumer<SignalEdgeGroup> action){var visited=Collections.newSetFromMap(new IdentityHashMap<SignalEdgeGroup,Boolean>());var queue=new ArrayDeque<SignalEdgeGroup>();queue.add(this);while(!queue.isEmpty()){var group=queue.removeFirst();if(!visited.add(group))continue;action.accept(group);if(group.manager!=null)for(var id:group.intersecting.values()){var next=group.manager.signalEdgeGroups().get(id);if(next!=null)queue.addLast(next);}}}
    private void dirty(){if(manager!=null)manager.markDirty();}
    public CompoundTag write(){return (CompoundTag)CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,this).getOrThrow();}
    public static SignalEdgeGroup read(CompoundTag tag){return CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,tag).getOrThrow();}
}
