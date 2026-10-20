package com.simibubi.create.content.logistics.packagePort;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.registry.*;
import com.simibubi.create.content.kinetics.chainConveyor.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Public target registry and the source relative-position codec. Unloaded targets never acquire tickets. */
public abstract class PackagePortTarget {
    public static final Codec<PackagePortTarget> CODEC=CreateBuiltInRegistries.PACKAGE_PORT_TARGET_TYPE.byNameCodec().dispatch(PackagePortTarget::getType,PackagePortTargetType::codec);
    public static final StreamCodec<RegistryFriendlyByteBuf,PackagePortTarget> STREAM_CODEC=ByteBufCodecs.registry(CreateRegistries.PACKAGE_PORT_TARGET_TYPE).dispatch(PackagePortTarget::getType,PackagePortTargetType::streamCodec);
    public BlockPos relativePos;
    protected PackagePortTarget(BlockPos relativePos){this.relativePos=relativePos.immutable();}
    public abstract boolean export(PackagePortBlockEntity port,ItemStack item,boolean simulate);
    public abstract void register(PackagePortBlockEntity port);
    public abstract void deregister(PackagePortBlockEntity port);
    public abstract Vec3 getExactTargetLocation(PackagePortBlockEntity port);
    public abstract ItemStack getIcon();
    public abstract PackagePortTargetType getType();
    public abstract boolean valid(PackagePortBlockEntity port);
    public void setup(PackagePortBlockEntity port){}
    public static final class ChainConveyorFrogportTarget extends PackagePortTarget {
        public static final MapCodec<ChainConveyorFrogportTarget> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
                BlockPos.CODEC.fieldOf("relative_pos").forGetter(value->value.relativePos),Codec.FLOAT.fieldOf("chain_pos").forGetter(value->value.chainPos),
                BlockPos.CODEC.optionalFieldOf("connection").forGetter(value->Optional.ofNullable(value.connection)),Codec.BOOL.fieldOf("flipped").forGetter(value->value.flipped)).apply(instance,(pos,chain,connection,flipped)->new ChainConveyorFrogportTarget(pos,chain,connection.orElse(null),flipped)));
        public static final StreamCodec<RegistryFriendlyByteBuf,ChainConveyorFrogportTarget> STREAM_CODEC=StreamCodec.of((buf,value)->{
            buf.writeBlockPos(value.relativePos);buf.writeFloat(value.chainPos);buf.writeBoolean(value.connection!=null);if(value.connection!=null)buf.writeBlockPos(value.connection);buf.writeBoolean(value.flipped);
        },buf->new ChainConveyorFrogportTarget(buf.readBlockPos(),buf.readFloat(),buf.readBoolean()?buf.readBlockPos():null,buf.readBoolean()));
        public float chainPos;
        public BlockPos connection;
        public boolean flipped;
        public ChainConveyorFrogportTarget(BlockPos relativePos,float chainPos,BlockPos connection,boolean flipped){super(relativePos);this.chainPos=chainPos;this.connection=connection==null||connection.equals(BlockPos.ZERO)?null:connection.immutable();this.flipped=flipped;}
        public ChainConveyorBlockEntity conveyor(PackagePortBlockEntity port){return port.getLevel()!=null&&LoadedLevelAccess.blockEntity(port.getLevel(),port.getBlockPos().offset(relativePos)) instanceof ChainConveyorBlockEntity conveyor?conveyor:null;}
        @Override public boolean valid(PackagePortBlockEntity port){var conveyor=conveyor(port);return conveyor!=null&&Float.isFinite(chainPos)&&(connection==null?chainPos>=0&&chainPos<=360:conveyor.getConnectionStats(connection)!=null&&chainPos>=0&&chainPos<=conveyor.getConnectionStats(connection).chainLength());}
        @Override public void setup(PackagePortBlockEntity port){var conveyor=conveyor(port);if(conveyor!=null)flipped=conveyor.isReversed();}
        @Override public boolean export(PackagePortBlockEntity port,ItemStack item,boolean simulate){
            var conveyor=conveyor(port);if(!valid(port)||!conveyor.canAcceptMorePackages()||connection!=null&&(conveyor.getConnected(connection)==null||conveyor.getConnected(connection).getLoopingPackages().size()>=ChainConveyorBlockEntity.capacity()))return false;
            if(simulate)return conveyor.getSpeed()!=0;
            var parcel=new ChainConveyorPackage(chainPos,item);return connection==null?conveyor.addLoopingPackage(parcel):conveyor.addTravellingPackage(parcel,connection);
        }
        @Override public void register(PackagePortBlockEntity port){
            var conveyor=conveyor(port);if(conveyor==null||!Float.isFinite(chainPos))return;
            if(connection!=null&&conveyor.isReversed()!=flipped){
                var other=conveyor.getConnected(connection);if(other==null)return;var stats=conveyor.getConnectionStats(connection);if(stats==null)return;
                deregister(port);chainPos=stats.chainLength()-chainPos;connection=connection.multiply(-1);flipped=!flipped;relativePos=other.getBlockPos().subtract(port.getBlockPos());conveyor=other;port.notifyUpdate();
            }
            if(!valid(port))return;
            String filter=port.getFilterString();if(filter==null){conveyor.removePort(port.getBlockPos());return;}
            conveyor.registerPort(port.getBlockPos(),chainPos,connection,filter);
        }
        @Override public void deregister(PackagePortBlockEntity port){var conveyor=conveyor(port);if(conveyor!=null)conveyor.removePort(port.getBlockPos());}
        @Override public Vec3 getExactTargetLocation(PackagePortBlockEntity port){var conveyor=conveyor(port);return conveyor==null?Vec3.ZERO:conveyor.getPackagePosition(chainPos,connection);}
        @Override public ItemStack getIcon(){return new ItemStack(AllBlocks.CHAIN_CONVEYOR);}
        @Override public PackagePortTargetType getType(){return AllPackagePortTargetTypes.CHAIN_CONVEYOR;}
        public static final class Type implements PackagePortTargetType {
            @Override public MapCodec<ChainConveyorFrogportTarget> codec(){return CODEC;}
            @Override public StreamCodec<RegistryFriendlyByteBuf,ChainConveyorFrogportTarget> streamCodec(){return STREAM_CODEC;}
        }
    }
    public static final class TrainStationFrogportTarget extends PackagePortTarget {
        public static final MapCodec<TrainStationFrogportTarget> CODEC=BlockPos.CODEC.fieldOf("relative_pos").xmap(TrainStationFrogportTarget::new,value->value.relativePos);
        public static final StreamCodec<RegistryFriendlyByteBuf,TrainStationFrogportTarget> STREAM_CODEC=StreamCodec.of((buffer,value)->buffer.writeBlockPos(value.relativePos),buffer->new TrainStationFrogportTarget(buffer.readBlockPos()));
        public TrainStationFrogportTarget(BlockPos relativePos){super(relativePos);}
        private com.simibubi.create.content.trains.station.StationBlockEntity station(PackagePortBlockEntity port){return port.getLevel()!=null&&LoadedLevelAccess.blockEntity(port.getLevel(),port.getBlockPos().offset(relativePos)) instanceof com.simibubi.create.content.trains.station.StationBlockEntity actual?actual:null;}
        @Override public boolean valid(PackagePortBlockEntity port){var station=station(port);return port instanceof com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity&&station!=null&&station.getStation()!=null;}
        @Override public boolean export(PackagePortBlockEntity port,ItemStack item,boolean simulate){return false;}
        @Override public void register(PackagePortBlockEntity port){var station=station(port);if(station!=null&&port instanceof com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity box&&station.getStation()!=null)station.getStation().attachPackagePort(box);}
        @Override public void deregister(PackagePortBlockEntity port){var station=station(port);if(station!=null&&port instanceof com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity box&&station.getStation()!=null)station.getStation().removePackagePort(box);}
        @Override public Vec3 getExactTargetLocation(PackagePortBlockEntity port){return Vec3.atCenterOf(port.getBlockPos().offset(relativePos));}
        @Override public ItemStack getIcon(){return new ItemStack(AllBlocks.TRACK_STATION);}
        @Override public PackagePortTargetType getType(){return AllPackagePortTargetTypes.TRAIN_STATION;}
        public static final class Type implements PackagePortTargetType {
            @Override public MapCodec<TrainStationFrogportTarget> codec(){return CODEC;}
            @Override public StreamCodec<RegistryFriendlyByteBuf,TrainStationFrogportTarget> streamCodec(){return STREAM_CODEC;}
        }
    }

}
