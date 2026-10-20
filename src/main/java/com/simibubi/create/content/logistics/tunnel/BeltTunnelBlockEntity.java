package com.simibubi.create.content.logistics.tunnel;

import com.mojang.serialization.Codec;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public class BeltTunnelBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    public static final class Flap {float previous=.25f,current=.25f;public float value(float partial){return previous+(current-previous)*partial;}}
    public final Map<Direction,Flap> flaps=new EnumMap<>(Direction.class);
    public final Set<Direction> sides=EnumSet.noneOf(Direction.class);
    private final long[] sequences=new long[6];
    private int inwardMask;
    private boolean initialized,received;
    public BeltTunnelBlockEntity(BlockPos pos,BlockState state){this(AllBlockEntityTypes.ANDESITE_TUNNEL,pos,state);}
    protected BeltTunnelBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public BeltBlockEntity belt(){return level==null?null:BeltHelper.getSegmentBE(level,worldPosition.below());}
    public Storage<ItemVariant> storage(Direction side){var belt=belt();return belt==null?null:belt.storage(Direction.UP);}
    public void changed(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    public void updateTunnelConnections(){
        if(level==null)return;
        var oldSides=EnumSet.copyOf(sides.isEmpty()?EnumSet.noneOf(Direction.class):sides);var oldFlaps=EnumSet.noneOf(Direction.class);oldFlaps.addAll(flaps.keySet());sides.clear();
        var state=getBlockState();if(!(state.getBlock() instanceof BeltTunnelBlock))return;
        var axis=state.getValue(BeltTunnelBlock.HORIZONTAL_AXIS);var left=Direction.get(Direction.AxisDirection.POSITIVE,axis).getClockWise();var shape=state.getValue(BeltTunnelBlock.SHAPE);
        var visible=EnumSet.noneOf(Direction.class);
        for(var side:Direction.Plane.HORIZONTAL){
            if(side.getAxis()!=axis&&!(shape==BeltTunnelBlock.Shape.CROSS||shape==BeltTunnelBlock.Shape.T_LEFT&&side==left||shape==BeltTunnelBlock.Shape.T_RIGHT&&side==left.getOpposite()))continue;
            sides.add(side);var next=LoadedLevelAccess.blockState(level,worldPosition.relative(side));
            if(next.getBlock() instanceof BeltTunnelBlock||next.getBlock() instanceof BeltFunnelBlock&&next.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.EXTENDED&&next.getValue(BeltFunnelBlock.HORIZONTAL_FACING)==side.getOpposite())continue;
            visible.add(side);flaps.computeIfAbsent(side,key->new Flap());
        }
        flaps.keySet().retainAll(visible);
        if(!oldSides.equals(sides)||!oldFlaps.equals(visible))changed();
    }
    public void flap(Direction side,boolean inward){
        if(!flaps.containsKey(side))return;
        sequences[side.ordinal()]++;if(inward)inwardMask|=1<<side.ordinal();else inwardMask&=~(1<<side.ordinal());
        var flap=flaps.get(side);flap.previous=flap.current=inward?-1:1;changed();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,BeltTunnelBlockEntity tunnel){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, tunnel)) {
            if (!createTick.shouldTick()) return;

        tunnel.flaps.values().forEach(flap->{flap.previous=flap.current;flap.current*=.95f;});
        if(level.isClientSide()&&!tunnel.isVirtual())return;
        if(!tunnel.initialized||level.getGameTime()%20==0){tunnel.initialized=true;tunnel.updateTunnelConnections();}
        if(tunnel instanceof BrassTunnelBlockEntity brass)brass.tickDistribution();
    
        }
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){
        super.write(out, clientPacket);out.store("Flaps",Codec.INT.listOf(),flaps.keySet().stream().map(Direction::ordinal).toList());out.store("Sides",Codec.INT.listOf(),sides.stream().map(Direction::ordinal).toList());out.store("FlapSequences",Codec.LONG.listOf(),Arrays.stream(sequences).boxed().toList());out.putInt("InwardFlaps",inwardMask);
    }
    @Override protected void read(ValueInput in, boolean clientPacket){
        var visible=EnumSet.noneOf(Direction.class);for(int ordinal:in.read("Flaps",Codec.INT.listOf()).orElse(List.of()))if(ordinal>=0&&ordinal<6)visible.add(Direction.values()[ordinal]);flaps.keySet().retainAll(visible);visible.forEach(side->flaps.computeIfAbsent(side,key->new Flap()));sides.clear();for(int ordinal:in.read("Sides",Codec.INT.listOf()).orElse(visible.stream().map(Direction::ordinal).toList()))if(ordinal>=0&&ordinal<6)sides.add(Direction.values()[ordinal]);
        var sequence=in.read("FlapSequences",Codec.LONG.listOf()).orElse(List.of());inwardMask=in.getIntOr("InwardFlaps",0);
        for(var side:Direction.values()){int index=side.ordinal();long value=index<sequence.size()?sequence.get(index):0;if(received&&level!=null&&level.isClientSide()&&value!=sequences[index]&&flaps.containsKey(side)){var flap=flaps.get(side);flap.previous=flap.current=(inwardMask&(1<<index))!=0?-1:1;}sequences[index]=value;}received=true;
    
        super.read(in, clientPacket);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
}
