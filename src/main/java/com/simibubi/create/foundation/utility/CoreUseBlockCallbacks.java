package com.simibubi.create.foundation.utility;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Core interaction actions keep their normal order and phases; mirror protection is a real Fabric probe. */
public final class CoreUseBlockCallbacks {
    private record Probe(Player player,Level level,InteractionHand hand,BlockPos target,BlockHitResult hit){}
    private static final ThreadLocal<Probe> PROBE=new ThreadLocal<>();
    private static UseBlockCallback action(UseBlockCallback callback){
        return (player,level,hand,hit)->{
            var probe=PROBE.get();
            if(probe!=null&&probe.player==player&&probe.level==level&&probe.hand==hand&&probe.hit==hit
                &&probe.target.equals(hit.getBlockPos()))return InteractionResult.PASS;
            return callback.interact(player,level,hand,hit);
        };
    }
    public static void register(UseBlockCallback callback){UseBlockCallback.EVENT.register(action(callback));}
    public static void register(Identifier phase,UseBlockCallback callback){UseBlockCallback.EVENT.register(phase,action(callback));}
    /** External Fabric callbacks still receive the exact real player, level, hand and target. */
    public static InteractionResult protection(Player player,Level level,InteractionHand hand,BlockHitResult hit){
        var previous=PROBE.get();PROBE.set(new Probe(player,level,hand,hit.getBlockPos().immutable(),hit));
        try{return UseBlockCallback.EVENT.invoker().interact(player,level,hand,hit);}
        finally{if(previous==null)PROBE.remove();else PROBE.set(previous);}
    }
    private CoreUseBlockCallbacks(){}
}
