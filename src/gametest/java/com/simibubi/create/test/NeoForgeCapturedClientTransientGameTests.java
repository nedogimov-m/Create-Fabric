package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.foundation.utility.NeoForgeCapturedBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Genuine source transient client views through native factories/readers/writers; real client effects UNRUN. */
public final class NeoForgeCapturedClientTransientGameTests {
    private static final BlockPos P=new BlockPos(3,4,3);
    private static CompoundTag full(String type){var tag=new CompoundTag();tag.putString("id","create:"+type);return tag;}
    private static CompoundTag additional(CompoundTag source){var tag=source.copy();for(String key:new String[]{"id","x","y","z"})tag.remove(key);return tag;}
    private static CompoundTag item(String id,int amount){var tag=new CompoundTag();tag.putString("id",id);tag.putInt("count",amount);return tag;}
    private static CompoundTag oldPos(int x,int y,int z){var tag=new CompoundTag();tag.putInt("X",x);tag.putInt("Y",y);tag.putInt("Z",z);return tag;}
    private static CompoundTag convert(GameTestHelper h,BlockState state,CompoundTag source,CompoundTag client){var row=new CompoundTag();row.put("Data",source);row.put("UpdateTag",client);var before=row.copy();var result=NeoForgeCapturedBlockEntityImport.convert(row,P,state,NeoForgeSavedDataImport.SOURCE_DATA_VERSION,h.getLevel().registryAccess(),NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(P)));h.assertTrue(row.equals(before),"Real original full owner and separate client packet are unchanged");return result;}
    private static CompoundTag tank(int millibuckets){var tag=full("fluid_tank");tag.putInt("Size",1);tag.putInt("Height",1);tag.putBoolean("Window",true);tag.putInt("Luminosity",0);var fluid=new CompoundTag();fluid.putString("id","minecraft:water");fluid.putInt("amount",millibuckets);var content=new CompoundTag();content.put("Fluid",fluid);tag.put("TankContent",content);var boiler=new CompoundTag();boiler.putFloat("Supply",0);boiler.putInt("ActiveHeat",0);boiler.putBoolean("PassiveHeat",false);boiler.putInt("Engines",0);boiler.putInt("Whistles",0);boiler.putBoolean("Update",false);tag.put("Boiler",boiler);return tag;}

    @GameTest public void pressParticleReferencesWaitForActualClientLevelAndRetireThroughTheRealNativeEventWriter(GameTestHelper h){
        var data=full("mechanical_press");data.putBoolean("Running",false);data.putBoolean("Finished",false);data.putInt("Mode",0);data.putInt("Ticks",0);var client=additional(data);var samples=new ListTag();samples.add(item("minecraft:diamond",1));samples.add(item("minecraft:emerald",2));client.put("ParticleItems",samples);var state=AllBlocks.MECHANICAL_PRESS.defaultBlockState();var result=convert(h,state,data,client);var view=new MechanicalPressBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());var owner=(MechanicalPressBlockEntity)BlockEntity.loadStatic(P,state,result.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());
        h.assertTrue(view.getPressingBehaviour().particleItems.size()==2&&view.getPressingBehaviour().particleItems.get(1).getCount()==2&&owner.getPressingBehaviour().particleItems.isEmpty()&&!owner.getPressingBehaviour().hasAppliedImpact(),"Two actual source particle exemplars survive client read before setLevel without becoming a paid pressing impact or server stock");var packet=view.getUpdateTag(h.getLevel().registryAccess());h.assertTrue(packet.getListOrEmpty("ParticleItems").size()==2&&view.getPressingBehaviour().particleItems.isEmpty(),"Actual native client event writer retires the same two references once; no fake Level is required");h.succeed();
    }

    @GameTest public void originalTankForceFluidLevelSnapsItsActualVisualStateWithoutChangingTheSeparateFiniteServerOwner(GameTestHelper h){
        var data=tank(250);var first=additional(data);first.putBoolean("ForceFluidLevel",true);var state=AllBlocks.FLUID_TANK.defaultBlockState();var converted=convert(h,state,data,first);var view=new FluidTankBlockEntity(P,state);view.readClient(converted.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());float previous=view.getFluidLevel(1);
        var second=additional(tank(750));second.putBoolean("ForceFluidLevel",true);second.putBoolean("LazySync",true);converted=convert(h,state,data,second);view.readClient(converted.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());var owner=(FluidTankBlockEntity)BlockEntity.loadStatic(P,state,converted.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());
        h.assertTrue(view.getTankInventory().getAmount()==750L*81&&view.getFluidLevel(0)==view.getFillState()&&view.getFluidLevel(1)==view.getFillState()&&view.getFluidLevel(1)>previous&&owner.getTankInventory().getAmount()==250L*81,"Genuine force cue snaps both native interpolation endpoints to the explicit client750mB view; full server Data retains exactly250mB");h.assertTrue(converted.getCompoundOrEmpty("UpdateTag").getBooleanOr("LazySync",false)&&!converted.getCompoundOrEmpty("Data").contains("ForceFluidLevel"),"Real lazy chase cue remains only in the client protocol; no material or forced server receipt is synthesized");h.succeed();
    }

    @GameTest public void chainDestroyEventAndAllSignedOriginalNetIdsSurviveNativeClientReloadWithoutBecomingMaterialReceipts(GameTestHelper h){
        for(int supplied:new int[]{123,0,Integer.MIN_VALUE}){
            var data=full("chain_conveyor");var connections=new ListTag();connections.add(BlockPos.CODEC.encodeStart(NbtOps.INSTANCE,new BlockPos(4,0,0)).getOrThrow());data.put("Connections",connections);var loops=new ListTag();var parcel=new CompoundTag();parcel.putFloat("Position",30);parcel.put("Item",item("create:cardboard_package_12x12",1));loops.add(parcel);data.put("LoopingPackages",loops);data.put("TravellingPackages",new ListTag());var client=additional(data);((CompoundTag)client.getListOrEmpty("LoopingPackages").getFirst()).putInt("NetID",supplied);client.put("DestroyEffect",oldPos(4,0,0));var state=AllBlocks.CHAIN_CONVEYOR.defaultBlockState();var result=convert(h,state,data,client);var view=new ChainConveyorBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());var owner=(ChainConveyorBlockEntity)BlockEntity.loadStatic(P,state,result.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());
            h.assertTrue(view.getLoopingPackages().getFirst().netId==supplied&&view.getLoopingPackages().getFirst().identity.equals(owner.getLoopingPackages().getFirst().identity)&&owner.getLoopingPackages().size()==1&&owner.getLoopingPackages().getFirst().item.getCount()==1,"Actual supplied client int, including reachable original AtomicInteger overflow/zero, remains exact and separate from native scoped parcel material identity");var packet=view.getUpdateTag(h.getLevel().registryAccess());h.assertTrue(BlockPos.CODEC.parse(NbtOps.INSTANCE,packet.get("DestroyEffect")).getOrThrow().equals(new BlockPos(4,0,0))&&!view.getUpdateTag(h.getLevel().registryAccess()).contains("DestroyEffect")&&!result.getCompoundOrEmpty("Data").contains("DestroyEffect"),"One genuine source chain destruction cue survives pre-Level read and retires only its visual cache; no refund or lost parcel receipt is invented");
        }h.succeed();
    }

    @GameTest public void sourceForceMovementForAllThreeActuatorsWaitsForAnActualMatchingBodyAndNeverFabricatesItsIdentity(GameTestHelper h){
        String[] types={"mechanical_piston","rope_pulley","elevator_pulley"};Block[] blocks={AllBlocks.MECHANICAL_PISTON,AllBlocks.ROPE_PULLEY,AllBlocks.ELEVATOR_PULLEY};
        for(int index=0;index<types.length;index++){
            var data=full(types[index]);if(index<2)data.putInt("ScrollValue",0);data.putBoolean("Running",false);data.putBoolean("Waiting",false);data.putFloat("Offset",1);if(index==0)data.putInt("ExtensionLength",4);else data.putInt("InitialOffset",0);var client=additional(data);client.putFloat("Offset",3);client.putBoolean("ForceMovement",true);if(index==2)client.putInt("ClientTarget",4);var state=blocks[index].defaultBlockState();var result=convert(h,state,data,client);var view=(LinearActuatorBlockEntity)((EntityBlock)state.getBlock()).newBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());var owner=(LinearActuatorBlockEntity)BlockEntity.loadStatic(P,state,result.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());var packet=view.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(view.offset==3&&owner.offset==1&&view.getMovedContraption()==null&&packet.getBooleanOr("ForceMovement",false)&&!packet.contains("MovedEntity")&&!result.getCompoundOrEmpty("Data").contains("ForceMovement")&&!result.getCompoundOrEmpty("Data").contains("MovedEntity"),"Actual source force cue for "+types[index]+" retains explicit client offset3 while full owner stays1; it waits for a genuine matching body rather than inventing an unstored source entity UUID");h.assertTrue(!view.getUpdateTag(h.getLevel().registryAccess()).contains("ForceMovement"),"Native client event writer retires only the queued force cue");
        }h.succeed();
    }
}
