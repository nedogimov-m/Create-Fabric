package com.simibubi.create.foundation.blockEntity.behaviour.filtering;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Shared server authority for filter samples and the held-use amount board. */
public final class FilteringSettings {
    public interface ClientStart {void start(BlockPos pos,Direction face,InteractionHand hand);}
    public static ClientStart CLIENT_START=(pos,face,hand)->{};
    /** Amount zero is a short interaction; positive amounts change the board settings. */
    public record SetFilter(BlockPos pos,Direction face,InteractionHand hand,int amount,boolean upTo) implements CustomPacketPayload {
        public static final Type<SetFilter> TYPE=new Type<>(Create.asResource("filter_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetFilter> CODEC=CustomPacketPayload.codec((packet,buffer)->{buffer.writeBlockPos(packet.pos);buffer.writeEnum(packet.face);buffer.writeEnum(packet.hand);buffer.writeVarInt(packet.amount);buffer.writeBoolean(packet.upTo);},buffer->new SetFilter(buffer.readBlockPos(),buffer.readEnum(Direction.class),buffer.readEnum(InteractionHand.class),buffer.readVarInt(),buffer.readBoolean()));
        @Override public Type<SetFilter> type(){return TYPE;}
    }
    public static FilteringBehaviour resolve(Level level,BlockPos pos){var entity=LoadedLevelAccess.blockEntity(level,pos);if(entity instanceof com.simibubi.create.content.trains.observer.TrackObserverBlockEntity observer)return observer.filtering;if(entity instanceof com.simibubi.create.content.logistics.tableCloth.TableClothBlockEntity cloth)return cloth.isShop()?cloth.priceTag:null;if(entity instanceof com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity crate)return crate.filtering;if(entity instanceof com.simibubi.create.content.logistics.itemHatch.ItemHatchBlockEntity hatch)return hatch.filtering;if(entity instanceof FunnelBlockEntity funnel&&funnel.supportsFiltering())return funnel.filtering;if(entity instanceof com.simibubi.create.content.logistics.chute.SmartChuteBlockEntity chute)return chute.filtering;if(entity instanceof com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity sensor)return sensor.filtering;if(entity instanceof com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity observer)return observer.filtering;if(entity instanceof com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity roller)return roller.filtering;return com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(entity,FilteringBehaviour.TYPE);}
    public static boolean activeSide(net.minecraft.world.level.block.state.BlockState state,Direction side){
        if(state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock){
            var face=state.getValue(com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock.FACE);
            return side==(face==net.minecraft.world.level.block.state.properties.AttachFace.WALL?state.getValue(com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock.FACING):face==net.minecraft.world.level.block.state.properties.AttachFace.FLOOR?Direction.UP:Direction.DOWN);
        }
        if(state.getBlock() instanceof com.simibubi.create.content.kinetics.deployer.DeployerBlock deployer)return deployer.filterSide(state,side);
        if(state.getBlock() instanceof com.simibubi.create.content.kinetics.saw.SawBlock)return state.getValue(com.simibubi.create.content.kinetics.saw.SawBlock.FACING)==Direction.UP&&side==Direction.UP;
        if(state.getBlock() instanceof com.simibubi.create.content.processing.basin.BasinBlock)return side.getAxis().isHorizontal();
if(state.getBlock() instanceof com.simibubi.create.content.logistics.itemHatch.ItemHatchBlock)return true;if(state.getBlock() instanceof com.simibubi.create.content.trains.observer.TrackObserverBlock)return side==Direction.UP;if(state.getBlock() instanceof com.simibubi.create.content.contraptions.actors.roller.RollerBlock)return side==Direction.UP;if(state.getBlock() instanceof com.simibubi.create.content.logistics.tableCloth.TableClothBlock)return true;if(state.getBlock() instanceof com.simibubi.create.content.logistics.crate.CreativeCrateBlock)return side==Direction.UP;if(state.getBlock() instanceof com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock)return com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock.FILTER_SLOT.activeSide(state,side);if(state.getBlock() instanceof com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock)return com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock.FILTER_SLOT.activeSide(state,side);return state.getBlock() instanceof com.simibubi.create.content.logistics.chute.SmartChuteBlock?side.getAxis().isHorizontal():FunnelFilterSlotPositioning.activeSide(state,side);}
    public static net.minecraft.world.phys.Vec3 processingFilterPosition(net.minecraft.world.level.block.state.BlockState state,Direction side){
        if(state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock)return com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock.filterPosition(state);
        if(state.getBlock() instanceof com.simibubi.create.content.kinetics.deployer.DeployerBlock)return com.simibubi.create.content.kinetics.deployer.DeployerBlock.filterPosition(state,side);
        if(state.getBlock() instanceof com.simibubi.create.content.processing.basin.BasinBlock)return new net.minecraft.world.phys.Vec3(.5+side.getStepX()*.5,.75,.5+side.getStepZ()*.5);
        if(state.getBlock() instanceof com.simibubi.create.content.kinetics.saw.SawBlock){
            double offset=state.getValue(com.simibubi.create.content.kinetics.saw.SawBlock.FLIPPED)?-3/16d:3/16d;
            return state.getValue(com.simibubi.create.content.kinetics.saw.SawBlock.AXIS_ALONG_FIRST_COORDINATE)?new net.minecraft.world.phys.Vec3(.5+offset,12.5/16d,.5):new net.minecraft.world.phys.Vec3(.5,12.5/16d,.5+offset);
        }
        return null;
    }
    public static boolean testHit(net.minecraft.world.level.block.state.BlockState state,BlockPos pos,BlockHitResult hit){
        var processing=processingFilterPosition(state,hit.getDirection());
        if(processing!=null)return activeSide(state,hit.getDirection())&&processing.distanceTo(hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos)))<.25;
if(state.getBlock() instanceof com.simibubi.create.content.logistics.itemHatch.ItemHatchBlock)return com.simibubi.create.content.logistics.itemHatch.HatchFilterSlot.testHit(state,pos,hit);if(state.getBlock() instanceof com.simibubi.create.content.trains.observer.TrackObserverBlock)return com.simibubi.create.content.trains.observer.TrackObserverBlock.testFilterHit(pos,hit);if(state.getBlock() instanceof com.simibubi.create.content.contraptions.actors.roller.RollerBlock)return com.simibubi.create.content.contraptions.actors.roller.RollerBlock.testPanelHit(state,pos,hit,true);if(state.getBlock() instanceof com.simibubi.create.content.logistics.crate.CreativeCrateBlock)return com.simibubi.create.content.logistics.crate.CreativeCrateBlock.testFilterHit(pos,hit);if(state.getBlock() instanceof com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock)return com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock.FILTER_SLOT.testHit(state,pos,hit);if(state.getBlock() instanceof com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock)return com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock.FILTER_SLOT.testHit(state,pos,hit);if(state.getBlock() instanceof com.simibubi.create.content.logistics.chute.SmartChuteBlock)return activeSide(state,hit.getDirection())&&com.simibubi.create.content.logistics.chute.SmartChuteBlock.filterPosition(hit.getDirection()).distanceTo(hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos)))<.25;return state.getBlock() instanceof AbstractFunnelBlock&&FunnelFilterSlotPositioning.testHit(state,pos,hit);}
    public static boolean testHit(Level level,net.minecraft.world.level.block.state.BlockState state,BlockPos pos,BlockHitResult hit){return LoadedLevelAccess.blockEntity(level,pos) instanceof com.simibubi.create.content.logistics.tableCloth.TableClothBlockEntity cloth?cloth.targetsPriceTag(hit):testHit(state,pos,hit);}
    public static InteractionResult interact(Level level,Player player,InteractionHand hand,BlockHitResult hit){
        var pos=hit.getBlockPos();var state=LoadedLevelAccess.blockState(level,pos);var filtering=resolve(level,pos);
        if(filtering==null||!filtering.mayInteract(player)||!testHit(level,state,pos,hit)||!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos)||player.getItemInHand(hand).is(AllItems.WRENCH))return InteractionResult.PASS;
        if(filtering.isCountVisible()){if(level.isClientSide())CLIENT_START.start(pos.immutable(),hit.getDirection(),hand);}else if(!level.isClientSide())filtering.applyPlayerSample(player,hand);
        return InteractionResult.SUCCESS;
    }
    public static boolean apply(Level level,Player player,SetFilter packet){
        if(packet.amount<0||player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(packet.pos,1)||!level.mayInteract(player,packet.pos)||!level.isLoaded(packet.pos)||!activeSide(LoadedLevelAccess.blockState(level,packet.pos),packet.face))return false;
        var filtering=resolve(level,packet.pos);if(filtering==null||!filtering.mayInteract(player))return false;
        if(LoadedLevelAccess.blockState(level,packet.pos).getBlock() instanceof com.simibubi.create.content.contraptions.actors.roller.RollerBlock&&!com.simibubi.create.content.contraptions.actors.roller.RollerSettings.targetsPanel(level,player,packet.pos,true))return false;
        if(packet.amount==0)return filtering.applyPlayerSample(player,packet.hand);
        if(!filtering.isCountVisible()||packet.amount>filtering.getMaxStackSize())return false;
        filtering.setAmount(packet.amount,packet.upTo);return true;
    }
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(SetFilter.TYPE,SetFilter.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetFilter.TYPE,(packet,context)->apply(context.player().level(),context.player(),packet));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->interact(level,player,hand,hit));
    }
    private FilteringSettings(){}
}
