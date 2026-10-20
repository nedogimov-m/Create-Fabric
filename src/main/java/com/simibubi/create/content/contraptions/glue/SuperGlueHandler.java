package com.simibubi.create.content.contraptions.glue;

import com.simibubi.create.*;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import java.util.HashSet;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;

public final class SuperGlueHandler {
    public record Feedback(String key) implements CustomPacketPayload {
        public static final Type<Feedback> TYPE=new Type<>(Create.asResource("glue_selection_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Feedback> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Feedback::key,Feedback::new);
        @Override public Type<Feedback> type(){return TYPE;}
    }
    private static boolean eligible(Player player){return !player.level().isClientSide()&&player.isAlive()&&!player.isSpectator()&&player.mayBuild()&&player.getMainHandItem().getItem() instanceof SuperGlueItem;}
    public static boolean select(Player player,SuperGlueSelectionPacket packet){
        if(!eligible(player)||!player.isWithinBlockInteractionRange(packet.to(),2)||!packet.to().closerThan(packet.from(),25))return false;
        var group=SuperGlueSelectionHelper.searchGlueGroup(player.level(),packet.from(),packet.to(),false);
        if(group==null||!group.contains(packet.to()))return false;
        if(!SuperGlueSelectionHelper.collectGlueFromInventory(player,1,true))return false;
        var glue=new SuperGlueEntity(player.level(),SuperGlueEntity.span(packet.from(),packet.to()));
        if(!player.level().addFreshEntity(glue))return false;
        if(!SuperGlueSelectionHelper.collectGlueFromInventory(player,1,false)){glue.discard();return false;}
        glue.playPlaceSound();glue.spawnParticles();
        if(!(player instanceof FakePlayer))AllAdvancements.SUPER_GLUE.awardTo(player);
        return true;
    }
    public static boolean remove(Player player,SuperGlueRemovalPacket packet){
        if(!eligible(player)||!(player.level().getEntity(packet.entityId()) instanceof SuperGlueEntity glue)||player.distanceToSqr(glue.position())>32*32)return false;
        var from=player.getEyePosition();var hit=glue.getBoundingBox().clip(from,from.add(player.getLookAngle().scale(player.blockInteractionRange()+1)));
        if(hit.isEmpty())return false;
        player.level().playSound(null,BlockPos.containing(hit.get()),AllSoundEvents.SLIME_ADDED,SoundSource.BLOCKS,.5f,.5f);glue.spawnParticles();glue.discard();return true;
    }
    private static boolean offhand(Player player){return player!=null&&player.mayBuild()&&!player.getMainHandItem().is(AllItems.WRENCH)&&player.getOffhandItem().getItem() instanceof SuperGlueItem;}
    private record PlacementView(Level level,BlockPos excluded) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos){return pos.equals(excluded)?Blocks.AIR.defaultBlockState():LoadedLevelAccess.blockState(level,pos);}
        @Override public BlockEntity getBlockEntity(BlockPos pos){return pos.equals(excluded)?null:LoadedLevelAccess.blockEntity(level,pos);}
        @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinY(){return level.getMinY();}
    }
    private static BlockHitResult placementRay(BlockPlaceContext context){var player=context.getPlayer();var start=player.getEyePosition();return new PlacementView(context.getLevel(),context.getClickedPos()).clip(new ClipContext(start,start.add(player.getLookAngle().scale(player.blockInteractionRange())),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));}
    public static boolean canPlace(BlockPlaceContext context){
        if(com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem.isAssistedPlacement(context)||!offhand(context.getPlayer()))return true;
        var ray=placementRay(context);return ray.getType()==HitResult.Type.MISS||ray.getBlockPos().relative(ray.getDirection()).equals(context.getClickedPos());
    }
    public static void onBlockPlaced(BlockPlaceContext context){
        var level=context.getLevel();var player=context.getPlayer();if(level.isClientSide()||player==null)return;
        var placed=context.getClickedPos();var cached=new HashSet<SuperGlueEntity>();
        for(var direction:Direction.values())if(SuperGlueEntity.isGlued(level,placed,direction,cached)&&BlockMovementChecks.isMovementNecessary(LoadedLevelAccess.blockState(level,placed.relative(direction)),level,placed.relative(direction)))SuperGlueItem.spawnParticles(level,placed,direction,true);
        if(com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem.isAssistedPlacement(context)||!offhand(player))return;
        var ray=placementRay(context);if(ray.getType()==HitResult.Type.MISS||!ray.getBlockPos().relative(ray.getDirection()).equals(placed))return;
        if(SuperGlueEntity.isGlued(level,ray.getBlockPos(),ray.getDirection(),null)||!SuperGlueEntity.isValidFace(level,ray.getBlockPos(),ray.getDirection()))return;
        var glue=new SuperGlueEntity(level,SuperGlueEntity.span(ray.getBlockPos(),placed));
        if(level.addFreshEntity(glue)){if(!player.getAbilities().instabuild)player.getOffhandItem().hurtAndBreak(1,player,EquipmentSlot.OFFHAND);glue.playPlaceSound();glue.spawnParticles();}
    }
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(SuperGlueSelectionPacket.TYPE,SuperGlueSelectionPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SuperGlueRemovalPacket.TYPE,SuperGlueRemovalPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Feedback.TYPE,Feedback.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SuperGlueSelectionPacket.TYPE,(packet,context)->{boolean success=select(context.player(),packet);ServerPlayNetworking.send(context.player(),new Feedback(success?"success":"cannot_reach"));});
        ServerPlayNetworking.registerGlobalReceiver(SuperGlueRemovalPacket.TYPE,(packet,context)->remove(context.player(),packet));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{
            if(!(player.getItemInHand(hand).getItem() instanceof SuperGlueItem))return InteractionResult.PASS;
            var state=LoadedLevelAccess.blockState(level,hit.getBlockPos());
            if(state.getBlock() instanceof AbstractChassisBlock chassis&&chassis.handlesGlueUse(state,hit.getDirection()))return InteractionResult.PASS;
            return InteractionResult.SUCCESS;
        });
    }
    private SuperGlueHandler(){}
}
