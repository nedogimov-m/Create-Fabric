package com.simibubi.create.content.kinetics.chainConveyor;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.box.PackageItem;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

public final class ChainConveyorInteractionHandler {
    private static final WeakHashMap<Level,Map<BlockPos,WeakReference<ChainConveyorBlockEntity>>> LOADED=new WeakHashMap<>();
    private static boolean suppressAttack;
    public static void track(ChainConveyorBlockEntity conveyor) { LOADED.computeIfAbsent(conveyor.getLevel(),ignored->new HashMap<>()).put(conveyor.getBlockPos(),new WeakReference<>(conveyor)); }
    public static List<ChainConveyorBlockEntity> loaded(Minecraft client) {
        var entries=LOADED.computeIfAbsent(client.level,ignored->new HashMap<>());var result=new ArrayList<ChainConveyorBlockEntity>();
        entries.values().removeIf(ref->{var be=ref.get();if(be==null||be.isRemoved())return true;result.add(be);return false;});return result;
    }
    public static boolean onUse(Minecraft client) {
        if(client.player==null||client.level==null||client.player.isSpectator())return false;
        boolean rideable=client.player.isHolding(stack->stack.is(ChainConveyorRidingState.CHAIN_RIDEABLE));
        boolean disconnect=client.player.isShiftKeyDown()&&rideable;
        boolean ride=rideable&&!client.player.isShiftKeyDown()&&ChainConveyorRidingState.eligible(client.player);
        boolean selectPort=client.player.getMainHandItem().is(com.simibubi.create.AllBlocks.PACKAGE_FROGPORT.asItem())&&!client.player.isShiftKeyDown();
        if(!disconnect&&!ride&&!selectPort&&(!PackageItem.isPackage(client.player.getMainHandItem())||client.player.isShiftKeyDown()))return false;
        var from=client.player.getEyePosition();double range=client.player.blockInteractionRange()+1;var to=from.add(client.player.getLookAngle().scale(range));
        double obstacleDistance=client.hitResult==null||client.hitResult.getType()==HitResult.Type.MISS?range*range:from.distanceToSqr(client.hitResult.getLocation())+.04;
        double bestDistance=range*range;
        ChainConveyorBlockEntity best=null;ChainConveyorInteraction.Selection selection=null;
        for(var conveyor:loaded(client)){
            var hit=ChainConveyorInteraction.trace(conveyor,from,to);if(hit==null||disconnect&&hit.connection().equals(BlockPos.ZERO))continue;
            double distance=from.distanceToSqr(hit.hit());if(distance>bestDistance)continue;
            if(distance>obstacleDistance&&!(client.hitResult instanceof BlockHitResult block&&block.getBlockPos().equals(conveyor.getBlockPos())))continue;
            bestDistance=distance;best=conveyor;selection=hit;
        }
        if(best==null)return false;
        ClientPlayNetworking.send(new ChainConveyorInteraction.Packet(best.getBlockPos(),selection.connection(),selection.position(),disconnect?ChainConveyorInteraction.DISCONNECT:ride?ChainConveyorInteraction.RIDE:selectPort?ChainConveyorInteraction.SELECT_PORT:ChainConveyorInteraction.ATTACH,0));
        if(ride)ChainConveyorRidingHandler.embark(best,selection);
        if(selectPort&&!ride){ChainConveyorOverlay.selectPort(best.getPackagePosition(selection.position(),selection.connection()));client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("create.package_port.valid"));}return true;
    }
    public static boolean onAttack(Minecraft client) {
        if(client.player==null||client.level==null||client.player.isSpectator())return false;
        var from=client.player.getEyePosition();double range=client.player.blockInteractionRange()+1;var to=from.add(client.player.getLookAngle().scale(range));
        double nearest=client.hitResult==null||client.hitResult.getType()==HitResult.Type.MISS?range*range:from.distanceToSqr(client.hitResult.getLocation())+.04;
        ChainConveyorBlockEntity best=null;ChainConveyorPackage selected=null;
        for(var conveyor:loaded(client)){
            var parcels=new ArrayList<>(conveyor.getLoopingPackages());conveyor.getTravellingPackages().values().forEach(parcels::addAll);
            for(var parcel:parcels){if(parcel.worldPosition==null)continue;var hit=ChainConveyorInteraction.packageBounds(parcel,.15).clip(from,to).orElse(null);if(hit==null)continue;double distance=from.distanceToSqr(hit);if(distance>=nearest)continue;nearest=distance;best=conveyor;selected=parcel;}
        }
        if(best==null)return false;
        ClientPlayNetworking.send(new ChainConveyorInteraction.Packet(best.getBlockPos(),BlockPos.ZERO,0,ChainConveyorInteraction.TAKE,selected.netId));suppressAttack=true;return true;
    }
    public static boolean suppressAttack(Minecraft client) { if(!client.options.keyAttack.isDown())suppressAttack=false;return suppressAttack; }
    private ChainConveyorInteractionHandler(){}
}
