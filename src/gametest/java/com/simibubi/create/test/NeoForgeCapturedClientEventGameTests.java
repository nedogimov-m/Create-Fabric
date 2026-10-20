package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.redstone.deskBell.DeskBellBlockEntity;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.utility.NeoForgeCapturedBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Source3955 client events through actual native factories, readers, writers and finite stock. UNRUN. */
public final class NeoForgeCapturedClientEventGameTests {
    private static final BlockPos P=new BlockPos(3,4,3);
    private static CompoundTag item(String id,int amount){var tag=new CompoundTag();tag.putString("id",id);tag.putInt("count",amount);return tag;}
    private static CompoundTag full(String type){var tag=new CompoundTag();tag.putString("id","create:"+type);return tag;}
    private static CompoundTag additional(CompoundTag tag){var result=tag.copy();for(String key:new String[]{"id","x","y","z"})result.remove(key);return result;}
    private static CompoundTag handler(int size,int slot,CompoundTag item){var tag=new CompoundTag();tag.putInt("Size",size);var items=new ListTag();if(item!=null){var row=item.copy();row.putInt("Slot",slot);items.add(row);}tag.put("Items",items);return tag;}
    private static CompoundTag pos(int x,int y,int z){var tag=new CompoundTag();tag.putInt("X",x);tag.putInt("Y",y);tag.putInt("Z",z);return tag;}
    private static CompoundTag station(){var tag=full("track_station");tag.store("Id",UUIDUtil.CODEC,UUID.fromString("39ac25ab-d435-4f15-8767-4d158fc57b11"));tag.put("TargetTrack",pos(0,-1,2));tag.putBoolean("TargetDirection",true);tag.putBoolean("Ortho",true);tag.putString("DoorControl","WEST");tag.putInt("FailedCarriageIndex",-1);tag.putInt("PrevTrainColor",7);tag.put("OutputBuffer",handler(8,0,null));return tag;}
    private static CompoundTag exception(String text){var tag=new CompoundTag();tag.putString("Component","{\"text\":\""+text+"\"}");tag.putLong("Position",new BlockPos(-4,80,29).asLong());return tag;}
    private static CompoundTag convert(GameTestHelper h,BlockState state,CompoundTag data,CompoundTag client){var row=new CompoundTag();row.put("Data",data);row.put("UpdateTag",client);var before=row.copy();var result=NeoForgeCapturedBlockEntityImport.convert(row,P,state,NeoForgeSavedDataImport.SOURCE_DATA_VERSION,h.getLevel().registryAccess(),NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(P)));h.assertTrue(before.equals(row),"Actual original full owner and separate client packet are unchanged");return result;}
    private static CompoundTag spout(){
        var tag=full("spout");tag.putInt("ProcessingTicks",-1);var tanks=new ListTag();var tank=new CompoundTag();var content=new CompoundTag();var fluid=new CompoundTag();fluid.putString("id","minecraft:water");fluid.putInt("amount",250);content.put("Fluid",fluid);tank.put("TankContent",content);var level=new CompoundTag();level.putFloat("Speed",0);level.putFloat("Target",.25f);level.putFloat("Value",.25f);tank.put("Level",level);tanks.add(tank);tag.put("Tanks",tanks);return tag;
    }

    @GameTest public void stationClientPresenceUsesItsActualNativeDtoAndKeepsDiagnosticsAndServerOwnerSeparate(GameTestHelper h){
        var data=station();data.put("LastException",exception("Server diagnostic"));var client=additional(data);client.put("LastException",exception("Client diagnostic"));var imminent=UUID.fromString("79812d4a-7827-4d58-8049-69ebc1593d54");client.store("ImminentTrain",UUIDUtil.CODEC,imminent);for(String key:new String[]{"TrainPresent","TrainCanDisassemble","TrainBackwards","TrainHasSchedule","TrainHasAutoSchedule"})client.putBoolean(key,true);
        var state=AllBlocks.TRACK_STATION.defaultBlockState();var result=convert(h,state,data,client);var update=result.getCompoundOrEmpty("UpdateTag");var view=new StationBlockEntity(P,state);view.readClient(update,h.getLevel().registryAccess());var owner=(StationBlockEntity)BlockEntity.loadStatic(P,state,result.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());var presence=view.clientTrainPresence();
        h.assertTrue(imminent.equals(presence.imminentTrain())&&presence.present()&&presence.canDisassemble()&&presence.backwards()&&presence.hasSchedule()&&presence.hasAutoSchedule()&&view.getStation()==null&&!view.stationModeEntryPending(),"Supplied original presence identifier/flags hydrate only the genuine native display DTO without adopting a train/point or authorizing station entry");
        h.assertTrue(!update.contains("ImminentTrain")&&!update.contains("TrainPresent")&&update.getCompoundOrEmpty("StationData").read("ImminentTrain",UUIDUtil.CODEC).orElseThrow().equals(imminent)&&!update.contains("AssemblyEntryIntent")&&!update.contains("DisassembledTrainId")&&owner.clientTrainPresence().imminentTrain()==null,"Source event fields are mapped under actual StationData; private native operation identifiers stay absent and full disk owner gains no presence");
        h.assertTrue(owner.trainAssemblyFailure().component.getString().equals("Server diagnostic")&&view.trainAssemblyFailure().component.getString().equals("Client diagnostic")&&view.getUpdateTag(h.getLevel().registryAccess()).getCompoundOrEmpty("StationData").read("ImminentTrain",UUIDUtil.CODEC).orElseThrow().equals(imminent),"Actual no-level native read/write uses its supplied real registries for distinct client diagnostics and retains the genuine presence view");h.succeed();
    }

    @GameTest public void aLaterActualStationClientSnapshotClearsPresenceWithoutInventingAnImminentIdentifier(GameTestHelper h){
        var data=station();var client=additional(data);client.store("ImminentTrain",UUIDUtil.CODEC,UUID.fromString("79812d4a-7827-4d58-8049-69ebc1593d54"));client.putBoolean("TrainPresent",true);var state=AllBlocks.TRACK_STATION.defaultBlockState();var view=new StationBlockEntity(P,state);view.readClient(convert(h,state,data,client).getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());h.assertTrue(view.clientTrainPresence().present(),"Earlier actual source presence was accepted");
        client=additional(data);client.putInt("PrevTrainColor",8);view.readClient(convert(h,state,data,client).getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());h.assertTrue(view.clientTrainPresence().equals(StationBlockEntity.ClientTrainPresence.EMPTY)&&!view.getUpdateTag(h.getLevel().registryAccess()).getCompoundOrEmpty("StationData").contains("ImminentTrain"),"Explicit absence in a later genuine packet clears all presence flags and emits no invented source Train UUID");h.succeed();
    }

    @GameTest public void repeatedOriginalDeskBellDingEventsSurviveNativeClientProtocolWithoutFabricatingSequenceOrServerTimer(GameTestHelper h){
        var data=full("desk_bell");var client=new CompoundTag();client.putBoolean("Ding",true);var state=AllBlocks.DESK_BELL.defaultBlockState();var result=convert(h,state,data,client);var view=new DeskBellBlockEntity(P,state);
        for(int event=0;event<2;event++){view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());var first=view.getUpdateTag(h.getLevel().registryAccess());h.assertTrue(first.getBooleanOr("Ding",false)&&!view.getUpdateTag(h.getLevel().registryAccess()).contains("Ding")&&first.getLongOr("DingSequence",0)==0&&view.getPressTimer()==0,"Each genuine source Ding is one queued native event even with the same absent sequence; server press time is never reconstructed from it");}
        h.assertTrue(!result.getCompoundOrEmpty("Data").contains("Ding"),"Full original disk owner did not acquire a client event");h.succeed();
    }

    @GameTest public void actualPostboxParticleEventRetiresSeparatelyFromTheSingleTailMailOwner(GameTestHelper h){
        var data=full("package_postbox");data.put("Inventory",handler(18,17,item("create:cardboard_package_12x12",1)));data.putString("AddressFilter","Original Market");data.putBoolean("AcceptsPackages",true);var client=additional(data);client.putBoolean("Particles",true);client.putInt("OpenCount",2);var state=AllBlocks.POSTBOXES.values().iterator().next().defaultBlockState();var result=convert(h,state,data,client);var view=new PostboxBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());var owner=(PostboxBlockEntity)BlockEntity.loadStatic(P,state,result.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());
        var first=view.getUpdateTag(h.getLevel().registryAccess());h.assertTrue(first.getBooleanOr("Particles",false)&&first.getIntOr("OpenCount",0)==2&&!view.getUpdateTag(h.getLevel().registryAccess()).contains("Particles")&&owner.inventory.getItem(17).getCount()==1&&owner.inventory.getItem(0).isEmpty()&&!result.getCompoundOrEmpty("Data").contains("Particles"),"Real native one-shot particle cache and exact client opener count leave the full tail mail parcel in its only server inventory owner");h.succeed();
    }

    @GameTest public void originalFrogportOpenerSnapshotSurvivesARealNativeClientReadBeforeLevelAssignment(GameTestHelper h){
        var data=full("package_frogport");data.put("Inventory",handler(18,17,item("create:cardboard_package_12x12",1)));data.putString("AddressFilter","Original Market");data.putBoolean("AcceptsPackages",true);data.putFloat("PlacedYaw",42);var client=additional(data);client.putInt("OpenCount",3);var state=AllBlocks.PACKAGE_FROGPORT.defaultBlockState();var result=convert(h,state,data,client);var view=new FrogportBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());var owner=(FrogportBlockEntity)BlockEntity.loadStatic(P,state,result.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());
        h.assertTrue(view.getUpdateTag(h.getLevel().registryAccess()).getIntOr("OpenCount",0)==3&&owner.getUpdateTag(h.getLevel().registryAccess()).getIntOr("OpenCount",-1)==0&&owner.inventory.getItem(17).getCount()==1&&!view.isAnimationInProgress(),"Actual clientPacket discriminator preserves3 real display openers before a level exists; full reload resets viewer state and gains no incoming parcel animation");h.succeed();
    }

    @GameTest public void originalSpoutSplashIsOnlyOneClientEventAndNeverACraftedOutputOrFluidPayment(GameTestHelper h){
        var data=spout();var client=additional(data);client.putBoolean("Splash",true);var state=AllBlocks.SPOUT.defaultBlockState();var result=convert(h,state,data,client);var view=new SpoutBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());var owner=(SpoutBlockEntity)BlockEntity.loadStatic(P,state,result.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());var first=view.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(first.getBooleanOr("Splash",false)&&!view.getUpdateTag(h.getLevel().registryAccess()).contains("Splash")&&owner.tank.getAmount()==250L*81&&owner.processingTicks==-1&&!result.getCompoundOrEmpty("Data").contains("Pending")&&!result.getCompoundOrEmpty("Data").contains("Splash"),"The genuine source splash is a retired visual event;250mB remains real saved fluid with no invented paid output or restarted job");h.succeed();
    }

    @GameTest public void sawPlayEventUsesTypedItemComponentsAndNeverBecomesFiniteInputOrOutput(GameTestHelper h){
        var data=full("saw");var inventory=handler(32,0,item("minecraft:diamond",13));inventory.putFloat("ProcessingTime",7.25f);inventory.putFloat("RecipeTime",55.5f);inventory.putBoolean("AppliedRecipe",false);data.put("Inventory",inventory);data.putInt("RecipeIndex",3);var client=additional(data);var event=item("minecraft:emerald",1);var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\"Original saw event\"}");event.put("components",components);client.put("PlayEvent",event);var state=AllBlocks.MECHANICAL_SAW.defaultBlockState().setValue(SawBlock.FACING,Direction.UP);var result=convert(h,state,data,client);var view=new SawBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());var owner=(SawBlockEntity)BlockEntity.loadStatic(P,state,result.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());var first=view.getUpdateTag(h.getLevel().registryAccess());var exemplar=ItemStack.OPTIONAL_CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),first.get("PlayEvent")).getOrThrow();
        h.assertTrue(exemplar.get(DataComponents.CUSTOM_NAME).getString().equals("Original saw event")&&!view.getUpdateTag(h.getLevel().registryAccess()).contains("PlayEvent")&&owner.inventory.input().getCount()==13&&owner.inventory.outputs().isEmpty()&&!result.getCompoundOrEmpty("Data").contains("PlayEvent"),"Typed source event exemplar retires through the real native protocol without becoming an emerald stock owner, duplicating13 input diamonds or inventing processed output");h.succeed();
    }

    @GameTest public void malformedOriginalPresenceAndFalseTrueOnlyEventMarkersRefuseWithoutSourceMutation(GameTestHelper h){
        var data=station();var client=additional(data);client.putBoolean("TrainPresent",true);var beforeData=data.copy();var beforeClient=client.copy();boolean rejected=false;try{convert(h,AllBlocks.TRACK_STATION.defaultBlockState(),data,client);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&data.equals(beforeData)&&client.equals(beforeClient),"Train flags without their actual supplied identifier refuse before publication");
        data=full("desk_bell");client=new CompoundTag();client.putBoolean("Ding",false);beforeData=data.copy();beforeClient=client.copy();rejected=false;try{convert(h,AllBlocks.DESK_BELL.defaultBlockState(),data,client);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&data.equals(beforeData)&&client.equals(beforeClient),"Original event writer only emits true Ding markers; malformed false does not silently start an animation");h.succeed();
    }
}
