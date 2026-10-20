package com.simibubi.create.content.kinetics.chainConveyor;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Source skyhook path, shared by server validation and client prediction. */
public final class ChainConveyorRidingState {
    public static final TagKey<Item> CHAIN_RIDEABLE=TagKey.create(Registries.ITEM,Create.asResource("chain_rideable"));
    public BlockPos anchor,connection;
    public float position;
    public boolean reversed;
    public int catchingUp=20;
    public UUID edgeId;
    public ChainConveyorRidingState(BlockPos anchor,BlockPos connection,float position,boolean reversed){this.anchor=anchor.immutable();this.connection=connection==null||connection.equals(BlockPos.ZERO)?null:connection.immutable();this.position=position;this.reversed=reversed;}
    public static boolean eligible(Player player){return player.isAlive()&&!player.isRemoved()&&!player.isSpectator()&&player.mayBuild()&&!player.isPassenger()&&!player.isSleeping()&&!player.isShiftKeyDown()&&player.isHolding(stack->stack.is(CHAIN_RIDEABLE));}
    public ChainConveyorBlockEntity conveyor(Level level){return LoadedLevelAccess.blockEntity(level,anchor) instanceof ChainConveyorBlockEntity conveyor&&!conveyor.isRemoved()?conveyor:null;}
    public ChainConveyorRidingState copy(){var copy=new ChainConveyorRidingState(anchor,connection,position,reversed);copy.catchingUp=catchingUp;copy.edgeId=edgeId;return copy;}
    public boolean advance(Level level,Vec3 look){return advance(level,look,1);}
    /** Client prediction scales travel; server session validation always advances native time. */
    public boolean advance(Level level,Vec3 look,float serverSpeed){
        var conveyor=conveyor(level);if(conveyor==null||!Float.isFinite(position))return false;
        float speed=conveyor.getSpeed()*(level.isClientSide()?serverSpeed:1);
        if(connection!=null){
            var stats=conveyor.getConnectionStats(connection);if(stats==null||edgeId!=null&&!edgeId.equals(conveyor.getConnectionId(connection)))return false;
            if(edgeId==null)edgeId=conveyor.getConnectionId(connection);
            if(reversed!=conveyor.isReversed()){
                var other=conveyor.getConnected(connection);if(other==null)return false;
                position=Math.max(0,stats.chainLength()-position);anchor=other.getBlockPos();connection=connection.multiply(-1);edgeId=other.getConnectionId(connection);reversed=conveyor.isReversed();return true;
            }
            position=Math.min(stats.chainLength(),position+Math.abs(speed/360f));
            if(position>=stats.chainLength()){
                var other=conveyor.getConnected(connection);if(other!=null){position=ChainConveyorBlockEntity.wrapAngle(stats.tangentAngle()+180+70*(reversed?-1:1));anchor=other.getBlockPos();connection=null;edgeId=null;}
            }
            return true;
        }
        reversed=conveyor.isReversed();float previous=position;position=ChainConveyorBlockEntity.wrapAngle(position+speed/(Mth.PI*1.5f));
        BlockPos selected=null;double nearest=Double.POSITIVE_INFINITY;
        for(var offset:conveyor.getConnections()){double difference=Vec3.atLowerCornerOf(offset).normalize().distanceToSqr(look.normalize());if(difference>nearest)continue;nearest=difference;selected=offset;}
        if(selected!=null&&ChainConveyorBlockEntity.loopThresholdCrossed(position,previous,conveyor.getConnectionStats(selected).tangentAngle(),reversed)){connection=selected;edgeId=conveyor.getConnectionId(selected);position=0;}
        return true;
    }
    public Vec3 target(Level level){
        var conveyor=conveyor(level);if(conveyor==null)return null;
        if(connection!=null)return conveyor.getConnectionStats(connection)==null?null:conveyor.getPackagePosition(position,connection);
        double angle=Math.toRadians(position);return Vec3.atBottomCenterOf(anchor).add(Math.sin(angle),.25,Math.cos(angle));
    }
    public static Vec3 grip(Player player){return player.position().add(0,player.getBoundingBox().getYsize()+.5*player.getScale(),0);}
    public boolean inReach(Player player,Vec3 target){var delta=target.subtract(grip(player));return catchingUp>0||delta.length()<=3&&delta.y>=-1;}
    public static Vec3 spring(Vec3 velocity,Vec3 difference){return velocity.scale(.75).add(difference.scale(.25));}
}
