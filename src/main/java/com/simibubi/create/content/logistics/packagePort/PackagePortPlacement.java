package com.simibubi.create.content.logistics.packagePort;

import com.simibubi.create.content.kinetics.chainConveyor.*;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Placement uses the actual server-validated chain selection, never a client-supplied destination. */
public final class PackagePortPlacement {
    private record Selection(ResourceKey<Level> dimension,BlockPos anchor,BlockPos connection,float position,boolean reversed){}
    private static final WeakHashMap<Player,Selection> SELECTED=new WeakHashMap<>();
    public static void select(Player player,ChainConveyorBlockEntity conveyor,ChainConveyorInteraction.Selection hit){SELECTED.put(player,new Selection(player.level().dimension(),conveyor.getBlockPos(),hit.connection().equals(BlockPos.ZERO)?null:hit.connection(),hit.position(),conveyor.isReversed()));}
    public static boolean placed(Player player,FrogportBlockEntity port){
        var selected=SELECTED.remove(player);if(selected==null||selected.dimension!=player.level().dimension()||!(LoadedLevelAccess.blockEntity(player.level(),selected.anchor) instanceof ChainConveyorBlockEntity conveyor))return false;
        var connection=selected.connection;float position=selected.position;
        if(connection!=null&&conveyor.isReversed()!=selected.reversed){var other=conveyor.getConnected(connection);if(other==null)return false;position=conveyor.getConnectionStats(connection).chainLength()-position;connection=connection.multiply(-1);conveyor=other;}
        if(!port.setTarget(new PackagePortTarget.ChainConveyorFrogportTarget(conveyor.getBlockPos().subtract(port.getBlockPos()),position,connection,conveyor.isReversed())))return false;
        PackagePortMenu.open(player,port);return true;
    }
    private PackagePortPlacement(){}
}
