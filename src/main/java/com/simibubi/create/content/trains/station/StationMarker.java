package com.simibubi.create.content.trains.station;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllMapDecorationTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.saveddata.maps.MapDecoration;

/** Exact source station/target identity; modern labels remain real native typed components. */
public final class StationMarker {
    private static final Codec<BlockPos> ORIGINAL_POSITION=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("X").forGetter(BlockPos::getX),Codec.INT.fieldOf("Y").forGetter(BlockPos::getY),Codec.INT.fieldOf("Z").forGetter(BlockPos::getZ)).apply(i,BlockPos::new));
    private static final Codec<BlockPos> POSITION=Codec.withAlternative(BlockPos.CODEC,ORIGINAL_POSITION);
    private static final Codec<Component> ORIGINAL_NAME=new Codec<>(){
        @Override public <T> DataResult<Pair<Component,T>> decode(DynamicOps<T> ops,T input){
            return ops.getStringValue(input).flatMap(ignored->{
                T fixed=DataFixers.getDataFixer().update(References.TEXT_COMPONENT,new Dynamic<>(ops,input),3955,SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
                return ComponentSerialization.CODEC.parse(ops,fixed).map(component->Pair.of(component,ops.empty()));
            });
        }
        @Override public <T> DataResult<T> encode(Component component,DynamicOps<T> ops,T prefix){return ComponentSerialization.CODEC.encode(component,ops,prefix);}
    };
    private static final Codec<StationMarker> CURRENT=RecordCodecBuilder.create(i->i.group(POSITION.fieldOf("source").forGetter(StationMarker::getSource),POSITION.fieldOf("target").forGetter(StationMarker::getTarget),ComponentSerialization.CODEC.fieldOf("label").forGetter(StationMarker::getName)).apply(i,StationMarker::new));
    private static final Codec<StationMarker> ORIGINAL=RecordCodecBuilder.create(i->i.group(ORIGINAL_POSITION.fieldOf("source").forGetter(StationMarker::getSource),ORIGINAL_POSITION.fieldOf("target").forGetter(StationMarker::getTarget),ORIGINAL_NAME.fieldOf("name").forGetter(StationMarker::getName)).apply(i,StationMarker::new));
    public static final Codec<StationMarker> CODEC=Codec.withAlternative(CURRENT,ORIGINAL);
    private final BlockPos source,target;
    private final Component name;
    public StationMarker(BlockPos source,BlockPos target,Component name){this.source=Objects.requireNonNull(source).immutable();this.target=Objects.requireNonNull(target).immutable();this.name=Objects.requireNonNull(name).copy();}
    public static StationMarker fromWorld(BlockGetter level,BlockPos pos){
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof StationBlockEntity station)||station.getStation()==null)return null;
        return new StationMarker(pos,station.targeting().getPositionForMapMarker(),Component.literal(station.getStation().name()));
    }
    public static StationMarker load(CompoundTag tag,HolderLookup.Provider registries){return CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),tag).getOrThrow();}
    public CompoundTag save(HolderLookup.Provider registries){return (CompoundTag)CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),this).getOrThrow();}
    public BlockPos getSource(){return source;}
    public BlockPos getTarget(){return target;}
    public Component getName(){return name;}
    public String getId(){return "create:station-"+target.getX()+","+target.getY()+","+target.getZ();}
    @Override public boolean equals(Object other){return this==other||other instanceof StationMarker marker&&target.equals(marker.target)&&name.equals(marker.name);}
    @Override public int hashCode(){return Objects.hash(target,name);}
    public static MapDecoration createStationDecoration(byte x,byte y,Optional<Component> name){return new MapDecoration(AllMapDecorationTypes.STATION_MAP_DECORATION,x,y,(byte)0,name);}
}
