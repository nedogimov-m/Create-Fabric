package com.simibubi.create.content.trains.track;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Authoritative source curve break/wrench operations. Real server ticks earn hardness progress; packets only hold input. */
public final class CurvedTrackServerInteraction {
    private record Prepared(TrackBlockEntity owner,TrackBlockEntity other,BezierConnection curve,TrackCurveSelection.Hit hit,BlockState state){}
    private static final class Session {
        final Player player;final ServerLevel level;final BlockPos owner,target;final BezierConnection expected;
        int segment;long lastInput,lastProgress=Long.MIN_VALUE;float progress;
        Session(Player player,Prepared prepared,int segment){this.player=player;level=(ServerLevel)player.level();owner=prepared.owner.getBlockPos();target=prepared.other.getBlockPos();expected=prepared.curve.clone();this.segment=segment;lastInput=level.getGameTime();}
    }
    private static final Map<MinecraftServer,Map<UUID,Session>> SERVERS=new WeakHashMap<>();
    private static Map<UUID,Session> sessions(Player player){return SERVERS.computeIfAbsent(player.level().getServer(),ignored->new HashMap<>());}
    private static Prepared validate(Player player,BlockPos ownerPos,BlockPos target,int segment){
        if(!(player.level() instanceof ServerLevel level)||!player.isAlive()||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,ownerPos)||!level.mayInteract(player,target))return null;
        if(!LoadedLevelAccess.isLoaded(level,ownerPos)||!LoadedLevelAccess.isLoaded(level,target))return null;
        if(!(LoadedLevelAccess.blockEntity(level,ownerPos) instanceof TrackBlockEntity owner)||!(LoadedLevelAccess.blockEntity(level,target) instanceof TrackBlockEntity other))return null;
        var curve=owner.getConnections().get(target);var reverse=other.getConnections().get(ownerPos);
        if(curve==null||!curve.primary||reverse==null||reverse.primary||curve.getMaterial()!=reverse.getMaterial()||!curve.equalsSansMaterial(reverse)||segment<0||segment>=curve.getSegmentCount()-1)return null;
        var from=player.getEyePosition();double range=player.blockInteractionRange()+1;var to=from.add(player.getLookAngle().scale(range));
        if(!curve.getBounds().contains(from)&&curve.getBounds().clip(from,to).isEmpty())return null;
        var hit=TrackCurveSelection.trace(curve,from,to);if(hit==null||Math.abs(hit.segment()-segment)>2)return null;
        try{if(!TrackCurveSelection.visible(from,hit,TrackCurveSelection.obstacle(level,from,to,player)))return null;}catch(UnloadedTrackException unavailable){return null;}
        if(!level.getWorldBorder().isWithinBounds(BlockPos.containing(hit.point()))||!level.mayInteract(player,BlockPos.containing(hit.point())))return null;
        return new Prepared(owner,other,curve,hit,owner.getBlockState());
    }
    public static boolean apply(Player player,CurvedTrackDestroyPacket packet){
        if(!(player.level() instanceof ServerLevel))return false;var map=sessions(player);var existing=map.get(player.getUUID());
        if(packet.action()==CurvedTrackDestroyPacket.Action.ABORT){if(existing==null||!existing.owner.equals(packet.pos())||!existing.target.equals(packet.targetPos()))return false;map.remove(player.getUUID());feedback(existing,0,false);return true;}
        var prepared=validate(player,packet.pos(),packet.targetPos(),packet.segment());if(prepared==null)return false;
        if(packet.action()==CurvedTrackDestroyPacket.Action.WRENCH){
            if(!player.isShiftKeyDown()||!player.getMainHandItem().is(AllItems.WRENCH))return false;
            if(existing!=null){map.remove(player.getUUID());feedback(existing,0,false);}return remove(player,prepared,true);
        }
        if(packet.action()==CurvedTrackDestroyPacket.Action.BEGIN){
            if(existing!=null)feedback(existing,0,false);var session=new Session(player,prepared,packet.segment());map.put(player.getUUID(),session);feedback(session,0,false);return true;
        }
        if(existing==null||existing.level!=player.level()||!existing.owner.equals(packet.pos())||!existing.target.equals(packet.targetPos())||existing.expected.getMaterial()!=prepared.curve.getMaterial()||!existing.expected.equalsSansMaterial(prepared.curve))return false;
        existing.segment=packet.segment();existing.lastInput=existing.level.getGameTime();return true;
    }
    public static void tick(MinecraftServer server){var map=SERVERS.get(server);if(map==null)return;for(var session:List.copyOf(map.values())){
        var player=session.player;long time=session.level.getGameTime();
        if(session.lastProgress==time)continue;session.lastProgress=time;
        var prepared=session.level==player.level()&&time-session.lastInput<=2?validate(player,session.owner,session.target,session.segment):null;
        if(prepared==null||session.expected.getMaterial()!=prepared.curve.getMaterial()||!session.expected.equalsSansMaterial(prepared.curve)){map.remove(player.getUUID());feedback(session,0,false);continue;}
        session.progress+=player.isCreative()?.125f:Math.max(0,prepared.state.getDestroyProgress(player,session.level,session.owner))/8f;
        if(session.progress>=1){map.remove(player.getUUID());boolean removed=remove(player,prepared,false);feedback(session,removed?1:0,removed);}
        else feedback(session,session.progress,false);
        if(time%4==0)player.swing(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);
    }}
    private static boolean remove(Player player,Prepared prepared,boolean wrench){
        var level=(ServerLevel)player.level();
        try{TrackCurveItems.requireMaterials(prepared.curve);TrackPropagator.preflightChanges(level,List.of(prepared.owner.getBlockPos(),prepared.other.getBlockPos()));}
        catch(UnloadedTrackException unavailable){player.sendOverlayMessage(Component.translatable("create.track.unloaded"));return false;}
        // The preflight is read-only; confirm the exact current world binding before committing either endpoint.
        if(LoadedLevelAccess.blockEntity(level,prepared.owner.getBlockPos())!=prepared.owner||LoadedLevelAccess.blockEntity(level,prepared.other.getBlockPos())!=prepared.other||prepared.owner.getConnections().get(prepared.curve.getKey())!=prepared.curve)return false;
        prepared.owner.removeConnection(prepared.other.getBlockPos());prepared.other.removeConnection(prepared.owner.getBlockPos());
        if(!player.isCreative()){if(wrench)TrackCurveItems.addItemsToPlayer(player,prepared.curve);else TrackCurveItems.spawn(level,prepared.curve);}
        TrackCurveItems.spawnDestroyParticles(level,prepared.curve);
        var sound=prepared.state.getSoundType();var at=BlockPos.containing(prepared.hit.point());if(wrench)level.playSound(null,at,AllSoundEvents.WRENCH_REMOVE,SoundSource.BLOCKS,1,level.getRandom().nextFloat()*.5f+.5f);
        level.playSound(null,at,sound.getBreakSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8f);return true;
    }
    /** Source endpoint sneak wrench: preflight all actual curves, destroy the real block once, then refund exact contents. */
    public static boolean dismantleEndpoint(Player player,UseOnContext context){
        if(!(player.level() instanceof ServerLevel level)||!player.isAlive()||!player.isShiftKeyDown()||!context.getItemInHand().is(AllItems.WRENCH)||!player.mayBuild()||player.isSpectator())return false;
        var pos=context.getClickedPos();if(!LoadedLevelAccess.isLoaded(level,pos)||!level.mayInteract(player,pos)||!player.isWithinBlockInteractionRange(pos,1))return false;
        var state=LoadedLevelAccess.blockState(level,pos);if(!(state.getBlock() instanceof TrackBlock))return false;
        var be=LoadedLevelAccess.blockEntity(level,pos);var curves=be instanceof TrackBlockEntity track?List.copyOf(track.getConnections().values()):List.<BezierConnection>of();
        var changed=new LinkedHashSet<BlockPos>();changed.add(pos);for(var curve:curves){changed.add(curve.getKey());if(!player.isCreative())TrackCurveItems.requireMaterials(curve);}
        try{TrackPropagator.preflightChanges(level,changed);}catch(UnloadedTrackException unavailable){player.sendOverlayMessage(Component.translatable("create.track.unloaded"));return false;}
        if(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level,player,pos,state,be))return false;
        // A real BEFORE listener may replace the block or edit a curve synchronously. It cannot
        // transfer this operation's paid refund to another native world owner at the same position.
        if(!LoadedLevelAccess.isLoaded(level,pos)||LoadedLevelAccess.blockState(level,pos)!=state||LoadedLevelAccess.blockEntity(level,pos)!=be)return false;
        if(be instanceof TrackBlockEntity track&&(track.getConnections().size()!=curves.size()||curves.stream().anyMatch(curve->track.getConnections().get(curve.getKey())!=curve)))return false;
        try{TrackPropagator.preflightChanges(level,changed);}catch(UnloadedTrackException unavailable){return false;}
        var drops=player.isCreative()?List.<net.minecraft.world.item.ItemStack>of():Block.getDrops(state,level,pos,be,player,context.getItemInHand());
        boolean oldCancel=be instanceof TrackBlockEntity track&&track.cancelDrops;if(be instanceof TrackBlockEntity track)track.cancelDrops=true;
        if(!level.destroyBlock(pos,false)){if(be instanceof TrackBlockEntity track)track.cancelDrops=oldCancel;return false;}
        if(!player.isCreative()){for(var curve:curves)TrackCurveItems.addItemsToPlayer(player,curve);drops.forEach(stack->player.getInventory().placeItemBackInInventory(stack,net.minecraft.util.Prediction.SERVER_ONLY));}
        PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level,player,pos,state,be);level.playSound(null,pos,AllSoundEvents.WRENCH_REMOVE,SoundSource.BLOCKS,1,.75f);return true;
    }
    private static void feedback(Session session,float progress,boolean removed){
        session.level.destroyBlockProgress(session.player.getId(),session.owner,progress<=0||removed?-1:(int)(progress*10)-1);
        if(session.player instanceof ServerPlayer player&&session.level.getServer().getPlayerList().getPlayer(player.getUUID())==player)ServerPlayNetworking.send(player,new CurvedTrackDestroyPacket.Progress(session.owner,session.target,progress,removed));
    }
    public static float progress(Player player){var session=sessions(player).get(player.getUUID());return session==null?0:session.progress;}
    public static void stop(Player player){var map=SERVERS.get(player.level().getServer());if(map==null)return;var session=map.remove(player.getUUID());if(session!=null)feedback(session,0,false);}
    public static void register(){ServerTickEvents.END_SERVER_TICK.register(CurvedTrackServerInteraction::tick);ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->stop(handler.player));ServerLifecycleEvents.SERVER_STOPPED.register(SERVERS::remove);}
    private CurvedTrackServerInteraction(){}
}
