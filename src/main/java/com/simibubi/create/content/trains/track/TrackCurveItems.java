package com.simibubi.create.content.trains.track;

import com.simibubi.create.Create;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/** One physical curve is refunded when either endpoint is broken, before its reciprocal is forgotten. */
final class TrackCurveItems {
    static void requireMaterials(BezierConnection curve){if(curve.hasGirder&&BuiltInRegistries.ITEM.getOptional(Create.asResource("metal_girder")).isEmpty())throw new IllegalStateException("Preserved source girder curves require the real metal_girder registration before world interaction");}
    static void spawn(ServerLevel level,BezierConnection curve){
        if(!level.getGameRules().get(GameRules.BLOCK_DROPS))return;
        var girder=curve.hasGirder?BuiltInRegistries.ITEM.getOptional(Create.asResource("metal_girder")).orElseThrow(()->new IllegalStateException("Preserved source girder curve requires the real metal_girder registration before world interaction")):null;
        int cost=TrackCurveCosts.getTrackItemCost(curve),segments=curve.getSegmentCount();
        for(int item=0;item<cost;item++){
            int sample=Math.min(segments-1,cost==curve.getTrackItemCost()?item*2:(int)((long)item*segments/cost));var point=curve.getPosition(curve.getSegmentT(sample)).add(0,3/16d,0);spawn(level,point,curve.getMaterial().asStack());
            if(curve.hasGirder)for(int i=0;i<2;i++)spawn(level,point,new ItemStack(girder));
        }
    }
    static void addItemsToPlayer(net.minecraft.world.entity.player.Player player,BezierConnection curve){
        requireMaterials(curve);int tracks=TrackCurveCosts.getTrackItemCost(curve);while(tracks>0){int count=Math.min(64,tracks);player.getInventory().placeItemBackInInventory(curve.getMaterial().asStack(count),net.minecraft.util.Prediction.SERVER_ONLY);tracks-=count;}
        int girders=TrackCurveCosts.getGirderItemCost(curve);var item=curve.hasGirder?BuiltInRegistries.ITEM.getOptional(Create.asResource("metal_girder")).orElseThrow():null;while(girders>0){int count=Math.min(64,girders);player.getInventory().placeItemBackInInventory(new ItemStack(item,count),net.minecraft.util.Prediction.SERVER_ONLY);girders-=count;}
    }
    static void spawnDestroyParticles(ServerLevel level,BezierConnection curve){
        var track=new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,curve.getMaterial().getBlock().defaultBlockState());
        var girder=curve.hasGirder?new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,com.simibubi.create.AllBlocks.METAL_GIRDER.defaultBlockState()):null;var origin=Vec3.atLowerCornerOf(curve.bePositions.getFirst());
        for(var segment:curve)for(int side:new int[]{-1,1}){var point=segment.position.add(segment.normal.scale(14/16f*side)).add(origin);level.sendParticles(track,point.x,point.y,point.z,1,0,0,0,0);if(girder!=null)level.sendParticles(girder,point.x,point.y-.5,point.z,1,0,0,0,0);}
    }
    private static void spawn(ServerLevel level,Vec3 point,ItemStack stack){var entity=new ItemEntity(level,point.x,point.y,point.z,stack);entity.setDefaultPickUpDelay();level.addFreshEntity(entity);}
    private TrackCurveItems(){}
}
