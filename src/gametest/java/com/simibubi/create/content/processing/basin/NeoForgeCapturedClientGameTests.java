package com.simibubi.create.content.processing.basin;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.bell.PeculiarBellBlockEntity;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.foundation.utility.NeoForgeCapturedBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.Arrays;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Genuine separate server Data and client event/view shapes; actual native factories/protocol, no fake level. UNRUN. */
public final class NeoForgeCapturedClientGameTests {
    private static final int VERSION=NeoForgeSavedDataImport.SOURCE_DATA_VERSION;
    private static final BlockPos P=new BlockPos(3,4,3);
    private static CompoundTag item(String name,int count){var tag=new CompoundTag();tag.putString("id",name);tag.putInt("count",count);return tag;}
    private static CompoundTag full(String type){var tag=new CompoundTag();tag.putString("id","create:"+type);return tag;}
    private static CompoundTag additional(CompoundTag tag){var result=tag.copy();for(String field:new String[]{"id","x","y","z"})result.remove(field);return result;}
    private static CompoundTag row(CompoundTag data,CompoundTag client){var row=new CompoundTag();row.put("Data",data);row.put("UpdateTag",client);return row;}
    private static CompoundTag convert(GameTestHelper h,BlockState state,CompoundTag source){
        var before=source.copy();var result=NeoForgeCapturedBlockEntityImport.convert(source,P,state,VERSION,h.getLevel().registryAccess(),NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(BlockPos.ZERO)));h.assertTrue(before.equals(source),"Original full owner and distinct client snapshot remain unchanged");return result;
    }
    private static ItemStack stack(GameTestHelper h,CompoundTag tag,String key){return ItemStack.OPTIONAL_CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),tag.get(key)).getOrThrow();}
    private static CompoundTag handler(int size){var tag=new CompoundTag();tag.putInt("Size",size);tag.put("Items",new ListTag());return tag;}
    private static CompoundTag lerped(){var tag=new CompoundTag();tag.putFloat("Speed",0);tag.putFloat("Target",0);tag.putFloat("Value",0);return tag;}
    private static ListTag tanks(){var list=new ListTag();for(int index=0;index<2;index++){var row=new CompoundTag();row.put("TankContent",new CompoundTag());row.put("Level",lerped());list.add(row);}return list;}
    private static CompoundTag basin(){var source=full("basin");source.put("InputItems",handler(9));source.put("OutputItems",handler(9));source.put("InputTanks",tanks());source.put("OutputTanks",tanks());source.put("Overflow",new ListTag());source.put("FluidOverflow",new ListTag());source.put("DisabledSpoutput",new ListTag());return source;}

    @GameTest public void distinctOriginalCrankSnapshotRetainsItsOwnCounterSpeedAndDirectionWithoutOverwritingServerData(GameTestHelper h){
        var data=full("hand_crank");data.putInt("InUse",77);data.putBoolean("Backwards",true);data.putFloat("Speed",64);var client=additional(data);client.putInt("InUse",14);client.putBoolean("Backwards",false);client.putFloat("Speed",128);
        var state=AllBlocks.HAND_CRANK.defaultBlockState();var result=convert(h,state,row(data,client));var owner=(HandCrankBlockEntity)BlockEntity.loadStatic(P,state,result.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());var view=new HandCrankBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());
        h.assertTrue(owner.inUse==77&&owner.backwards&&view.inUse==14&&!view.backwards&&result.getCompoundOrEmpty("UpdateTag").getFloatOr("Speed",0)==128,"Actual native full owner retains77/backwards while the separate source client view retains14/forward and its explicit128 speed");h.succeed();
    }

    @GameTest public void capturedDeployerClientHandAndParticleAreTypedViewsRatherThanASecondPlayerInventory(GameTestHelper h){
        var data=full("deployer");data.putString("Mode","USE");data.putString("State","EXPANDING");data.putInt("Timer",17);data.putBoolean("Powered",false);var inventory=new ListTag();var original=item("minecraft:stone",5);original.putByte("Slot",(byte)0);inventory.add(original);data.put("Inventory",inventory);data.put("HeldItem",item("minecraft:stone",5));data.put("Overflow",new ListTag());
        var client=additional(data);var old=(CompoundTag)client.getListOrEmpty("Inventory").getFirst();old.putInt("count",2);client.put("HeldItem",item("minecraft:stone",2));client.putBoolean("Fistbump",true);client.putFloat("Reach",.5f);var particle=item("minecraft:diamond",1);var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\"Original particle exemplar\"}");particle.put("components",components);client.put("Particle",particle);
        var state=AllBlocks.DEPLOYER.defaultBlockState();var result=convert(h,state,row(data,client));var server=result.getCompoundOrEmpty("Data");var update=result.getCompoundOrEmpty("UpdateTag");var owner=(DeployerBlockEntity)BlockEntity.loadStatic(P,state,server,h.getLevel().registryAccess());
        h.assertTrue(owner.getHeldItem().getCount()==5&&stack(h,update,"HeldItem").getCount()==2&&update.getBooleanOr("Fistbump",false)&&update.getFloatOr("Reach",0)==.5f,"Only server Data owns source hand5; the separate explicit client hand2/pose remains a visual snapshot");
        h.assertTrue(!update.contains("PlayerInventory")&&!update.contains("Inventory")&&!update.contains("Overflow")&&stack(h,update,"Particle").get(DataComponents.CUSTOM_NAME).getString().equals("Original particle exemplar"),"Actual native Deployer update protocol strips private inventory/overflow; typed source particle item remains an event exemplar");h.succeed();
    }

    @GameTest public void originalBellDirectionEventMapsToTheActualNativeRingProtocolWithoutInventingASequence(GameTestHelper h){
        var data=full("peculiar_bell");var client=new CompoundTag();client.putString("Ringing","EAST");var state=AllBlocks.PECULIAR_BELL.defaultBlockState();var result=convert(h,state,row(data,client));var update=result.getCompoundOrEmpty("UpdateTag");var view=new PeculiarBellBlockEntity(P,state);view.readClient(update,h.getLevel().registryAccess());
        h.assertTrue(view.isRinging&&view.ringingTicks==0&&view.ringDirection==Direction.EAST&&result.getCompoundOrEmpty("Data").get("Ringing")==null&&update.getLongOr("RingSequence",0)==0,"Genuine source Ringing direction is exactly a new ring at tick0, separate from full saved owner and any unstored native sequence");h.succeed();
    }

    @GameTest public void actualOriginalPoweredShaftCaptureReplacesOnlyItsStateAndClientFactoryWhileRetainingTypedFullMetadata(GameTestHelper h){
        var data=full("powered_shaft");data.putInt("Direction",1);data.putFloat("Speed",128);var client=new CompoundTag();client.putFloat("Speed",0);client.putBoolean("NeedsSpeedUpdate",true);var state=AllBlocks.SHAFT.defaultBlockState();var result=convert(h,state,row(data,client));
        h.assertTrue(result.getCompoundOrEmpty("Data").getStringOr("id","").equals("create:simple_kinetic")&&result.getCompoundOrEmpty("Data").getFloatOr("Speed",0)==128&&result.getCompoundOrEmpty("UpdateTag").getFloatOr("Speed",-1)==0,"Pinned source capture retained full powered-shaft NBT but created a fresh bracketed shaft for its client snapshot; only the exact source-proven replacement is accepted");h.succeed();
    }

    @GameTest public void clientChainParcelKeepsItsProvidedTransientNetIdAndSeparateFullMaterialIdentity(GameTestHelper h){
        var data=full("chain_conveyor");data.putFloat("Speed",64);var connections=new ListTag();connections.add(BlockPos.CODEC.encodeStart(NbtOps.INSTANCE,new BlockPos(4,0,0)).getOrThrow());data.put("Connections",connections);var packages=new ListTag();var parcel=new CompoundTag();parcel.putFloat("Position",30);parcel.put("Item",item("create:cardboard_package_12x12",1));packages.add(parcel);data.put("LoopingPackages",packages);data.put("TravellingPackages",new ListTag());
        var client=additional(data);((CompoundTag)client.getListOrEmpty("LoopingPackages").getFirst()).putInt("NetID",123);var effect=new CompoundTag();effect.putInt("X",4);effect.putInt("Y",0);effect.putInt("Z",0);client.put("DestroyEffect",effect);var result=convert(h,AllBlocks.CHAIN_CONVEYOR.defaultBlockState(),row(data,client));var material=(CompoundTag)result.getCompoundOrEmpty("Data").getListOrEmpty("LoopingPackages").getFirst();var view=(CompoundTag)result.getCompoundOrEmpty("UpdateTag").getListOrEmpty("LoopingPackages").getFirst();
        h.assertTrue(view.getIntOr("NetID",0)==123&&view.get("Identity").equals(material.get("Identity"))&&BlockPos.CODEC.parse(NbtOps.INSTANCE,result.getCompoundOrEmpty("UpdateTag").get("DestroyEffect")).getOrThrow().equals(new BlockPos(4,0,0)),"Actual source client integer123 is preserved; native material identity remains the separately scoped full parcel identity, not a fabricated source entity UUID");h.succeed();
    }

    @GameTest public void originalBasinItemAndFluidEventsHydrateOnlyActualNativeVisualCachesAndNeverFluidStock(GameTestHelper h){
        var data=basin();var client=additional(data);var items=new ListTag();items.add(item("minecraft:diamond",1));client.put("VisualizedItems",items);var fluids=new ListTag();var fluid=new CompoundTag();fluid.putString("id","minecraft:water");fluid.putInt("amount",250);fluids.add(fluid);client.put("VisualizedFluids",fluids);var state=AllBlocks.BASIN.defaultBlockState();var result=convert(h,state,row(data,client));var view=new BasinBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());
        h.assertTrue(view.visualizedOutputItems.size()==1&&view.visualizedOutputFluids.size()==1&&view.visualizedOutputFluids.getFirst().getValue().getAmount()==250L*81&&view.fluids.allTanks().stream().allMatch(tank->tank.getAmount()==0)&&view.allItems().stream().allMatch(slot->slot.getAmount()==0),"Genuine source visual item/fluid events populate only10-tick display references; no diamond or20250 fluid droplets become a storage owner");
        var packet=view.getUpdateTag(h.getLevel().registryAccess());h.assertTrue(packet.getListOrEmpty("VisualizedFluids").size()==1&&view.visualizedOutputItems.isEmpty()&&view.visualizedOutputFluids.isEmpty(),"Actual native event writer emits and retires one cache snapshot, retaining no material receipt");h.succeed();
    }

    @GameTest public void originalNixieComputerSignalRetainsAllFourteenActualSourceBytesThroughNativeClientDecodeAndWrite(GameTestHelper h){
        var data=full("nixie_tube");data.putInt("RedstoneStrength",7);var client=additional(data);client.putInt("RedstoneStrength",9);byte[] signal={63,20,0,6,2,3,4,0,10,63,1,0,2,1};client.putByteArray("ComputerSignal",signal);var state=AllBlocks.ORANGE_NIXIE_TUBE.defaultBlockState();var result=convert(h,state,row(data,client));var view=new NixieTubeBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());
        h.assertTrue(view.computerSignal!=null&&Arrays.equals(view.computerSignal.encode(),signal)&&view.getRedstoneStrength()==9&&result.getCompoundOrEmpty("Data").getIntOr("RedstoneStrength",0)==7&&Arrays.equals(view.getUpdateTag(h.getLevel().registryAccess()).getByteArray("ComputerSignal").orElseThrow(),signal),"Actual source two7-byte displays retain RGB/blink/glow fields and distinct counter9; server Data stays7 and no optional addon is used to synthesize the cached signal");h.succeed();
    }

    @GameTest public void malformedOrUnauditedClientFieldsFailWithoutChangingTheSourceOwner(GameTestHelper h){
        var data=full("nixie_tube");data.putInt("RedstoneStrength",7);var client=additional(data);client.putByteArray("ComputerSignal",new byte[13]);var source=row(data,client);var before=source.copy();boolean rejected=false;try{convert(h,AllBlocks.ORANGE_NIXIE_TUBE.defaultBlockState(),source);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&source.equals(before),"Malformed source computer cache cannot be silently dropped or mutate the real full owner");
        client=additional(data);client.putBoolean("InventedPaid",true);source=row(data,client);before=source.copy();rejected=false;try{convert(h,AllBlocks.ORANGE_NIXIE_TUBE.defaultBlockState(),source);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&source.equals(before),"Unknown client field is refused without treating the tag as a full material owner");h.succeed();
    }
}
