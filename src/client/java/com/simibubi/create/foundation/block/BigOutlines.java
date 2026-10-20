package com.simibubi.create.foundation.block;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;

public final class BigOutlines {
    public static void pick(Minecraft client,float partial){
        if(client.level==null||!(client.getCameraEntity() instanceof LocalPlayer player))return;
        var origin=player.getEyePosition(partial);double range=player.blockInteractionRange()+1;var target=origin.add(player.getViewVector(partial).scale(range));
        double nearest=client.hitResult==null||client.hitResult.getType()==HitResult.Type.MISS?range*range:client.hitResult.getLocation().distanceToSqr(origin)+.000001;
        var seen=new HashSet<BlockPos>();BlockHitResult best=null;
        int steps=(int)Math.ceil(range*4);for(int i=0;i<=steps;i++){
            var cell=BlockPos.containing(origin.lerp(target,i/(double)steps));
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
                var pos=cell.offset(x,y,z);if(!seen.add(pos))continue;var state=client.level.getBlockState(pos);if(!(state.getBlock() instanceof IHaveBigOutline))continue;
                var hit=state.getInteractionShape(client.level,pos).clip(origin,target,pos);if(hit==null)continue;double distance=origin.distanceToSqr(hit.getLocation());if(distance>=nearest)continue;nearest=distance;
                var local=hit.getLocation().subtract(Vec3.atCenterOf(pos));var point=Vec3.atCenterOf(pos).add(Math.clamp(local.x,-1,1),Math.clamp(local.y,-1,1),Math.clamp(local.z,-1,1));best=new BlockHitResult(point,hit.getDirection(),pos,hit.isInside());
            }
        }
        if(best!=null){client.hitResult=best;client.crosshairPickEntity=null;}
    }
    private BigOutlines(){}
}
