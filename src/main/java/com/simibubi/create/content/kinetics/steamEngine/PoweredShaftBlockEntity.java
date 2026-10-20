package com.simibubi.create.content.kinetics.steamEngine;

import com.simibubi.create.*;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public class PoweredShaftBlockEntity extends com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity {
    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){return false;}
    public boolean addToEngineTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){return super.addToGoggleTooltip(tooltip,sneaking);}
    public BlockPos enginePos;
    public net.minecraft.world.level.block.Block capacityKey;
    public float engineEfficiency;
    public int movementDirection=1,initialTicks=3;
    public PoweredShaftBlockEntity(BlockPos pos,BlockState state){this(AllBlockEntityTypes.POWERED_SHAFT,pos,state);}
    public PoweredShaftBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public boolean isPoweredBy(BlockPos engine){return worldPosition.subtract(engine).equals(enginePos);}
    public boolean canBePoweredBy(BlockPos engine){return initialTicks==0&&(enginePos==null||isPoweredBy(engine));}
    public void update(BlockPos engine,int direction,float efficiency){
        if(!canBePoweredBy(engine))return;
        enginePos=worldPosition.subtract(engine);efficiency=Float.isFinite(efficiency)?Math.clamp(efficiency,0,1):0;direction=Integer.signum(direction);
        var nextCapacityKey=level==null?AllBlocks.STEAM_ENGINE:com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,engine).getBlock();
        if(engineEfficiency==efficiency&&movementDirection==direction&&capacityKey==nextCapacityKey)return;
        capacityKey=nextCapacityKey;engineEfficiency=efficiency;movementDirection=direction;changed();
    }
    public void remove(BlockPos engine){if(!isPoweredBy(engine))return;enginePos=null;capacityKey=null;engineEfficiency=0;movementDirection=0;changed();}
    public void followNetworkDirection(int direction){
        direction=Integer.signum(direction);if(direction==0||movementDirection==direction)return;
        movementDirection=direction;
        if(level!=null&&enginePos!=null&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,worldPosition.subtract(enginePos)) instanceof SteamEngineBlockEntity engine)engine.reverse();
        setChanged();sync();
    }
    /** Native steam generators are reversible; a rigid driven source chooses their direction. */
    public void followDrivenDirection(float conveyedSpeed) {
        if (conveyedSpeed == 0 || getGeneratedSpeed() == 0 || Math.signum(conveyedSpeed) == Math.signum(getGeneratedSpeed())) return;
        float previous = getSpeed();
        followNetworkDirection(conveyedSpeed > 0 ? 1 : -1);
        setSpeed(Math.copySign(Math.abs(getTheoreticalSpeed()), conveyedSpeed));
        onSpeedChanged(previous);
    }
    @Override public void applyNewSpeed(float previousSpeed,float generatedSpeed) {
        if (hasSource() && previousSpeed != 0 && generatedSpeed != 0) {
            followDrivenDirection(previousSpeed);
            generatedSpeed = Math.copySign(Math.abs(generatedSpeed), previousSpeed);
        }
        super.applyNewSpeed(previousSpeed, generatedSpeed);
    }
    private void changed(){updateGeneratedRotation();}
    private int speedModifier(){return 1+(engineEfficiency>=1?3:Math.min(2,(int)Math.floor(engineEfficiency*4)));}
    @Override public float getGeneratedSpeed(){
        if(level instanceof ServerLevel server&&(enginePos==null||!server.shouldTickBlocksAt(worldPosition)||!server.shouldTickBlocksAt(worldPosition.subtract(enginePos))))return 0;
        return getCombinedCapacity()>0?movementDirection*16f*speedModifier():0;
    }
    private float getCombinedCapacity(){return capacityKey==null?0:(float)(engineEfficiency*BlockStressValues.getCapacity(capacityKey));}
    @Override public double getAddedStressCapacity(){return getCombinedCapacity()/speedModifier();}
    @Override public int getRotationAngleOffset(Direction.Axis axis){return super.getRotationAngleOffset(axis)+(axis.choose(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ())%2==0?180:0);}
    @Override public void tick(){if(level==null){super.tick();return;}tick(level,worldPosition,getBlockState(),this);}
    public static void tick(Level level,BlockPos pos,BlockState state,PoweredShaftBlockEntity shaft){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, shaft)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,shaft);if(shaft.initialTicks>0)shaft.initialTicks--;
        if(!level.isClientSide()&&level.getGameTime()%20==0&&!PoweredShaftBlock.stillValid(state,level,pos))level.scheduleTick(pos,com.simibubi.create.AllBlocks.POWERED_SHAFT,1);
        if(level.isClientSide()||shaft.enginePos==null)return;
        var enginePos=pos.subtract(shaft.enginePos);
        // A vanished or unloaded owner must never leave a live generator behind.
        if(!level.isLoaded(enginePos)||!level.shouldTickBlocksAt(enginePos)||!(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,enginePos) instanceof SteamEngineBlockEntity engine)||!SteamEngineBlock.getShaftPos(engine.getBlockState(),enginePos).equals(pos))shaft.remove(enginePos);
    
        }
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("Direction",movementDirection);out.putInt("Warmup",initialTicks);if(enginePos!=null){out.store("EnginePos",BlockPos.CODEC,enginePos);out.putFloat("EnginePower",engineEfficiency);out.putString("EngineType",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(capacityKey==null?AllBlocks.STEAM_ENGINE:capacityKey).toString());}}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);movementDirection=Integer.signum(in.getIntOr("Direction",1));initialTicks=Math.max(0,in.getIntOr("Warmup",3));enginePos=in.read("EnginePos",BlockPos.CODEC).orElse(null);float power=in.getFloatOr("EnginePower",0);engineEfficiency=enginePos==null||!Float.isFinite(power)?0:Math.max(0,power);
        var key=net.minecraft.resources.Identifier.tryParse(in.getStringOr("EngineType","create:steam_engine"));
        capacityKey=enginePos==null||key==null?null:net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
    }
}
