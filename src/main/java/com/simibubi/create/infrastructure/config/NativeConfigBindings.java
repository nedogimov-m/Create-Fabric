package com.simibubi.create.infrastructure.config;

import com.simibubi.create.content.contraptions.ContraptionSettings;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlock;
import com.simibubi.create.content.contraptions.chassis.ChassisSettings;
import com.simibubi.create.content.contraptions.minecart.MinecartConfig;
import com.simibubi.create.content.contraptions.pulley.PulleySettings;
import com.simibubi.create.content.decoration.copycat.CopycatConfig;
import com.simibubi.create.content.equipment.EquipmentConfig;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandConfig;
import com.simibubi.create.content.equipment.toolbox.ToolboxConfig;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmConfig;
import com.simibubi.create.content.logistics.depot.EjectorConfig;
import com.simibubi.create.content.schematics.SchematicConfig;
import com.simibubi.create.content.trains.TrainConfig;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Actual native consumers share one validated source snapshot; no alternate inventory/world is created. */
final class NativeConfigBindings {
 @SuppressWarnings("unchecked") private static <T>T value(Map<String,Object> snapshot,NativeConfigBase.CValue<T> definition){return (T)Objects.requireNonNull(snapshot.get(definition.key()),definition.key());}
 private static float number(Map<String,Object> snapshot,NativeConfigBase.ConfigFloat definition){return value(snapshot,definition).floatValue();}
 private record NativeValues(EquipmentConfig.Values equipment,TrainConfig.Values trains,SchematicConfig.Values schematics,EjectorConfig.Values ejector,int toolbox,int arm,int symmetry,int placement,int coupling,boolean pickup,boolean containers){}
 private static NativeValues prepare(CServer config,Map<String,Object> state){
  var e=config.equipment;var t=config.trains;var s=config.schematics;var k=config.kinetics;
  var equipment=new EquipmentConfig.Values(value(state,e.airInBacktank),value(state,e.enchantedBacktankCapacity),value(state,e.maxExtendoGripActions),value(state,e.maxPotatoCannonShots));
  var trains=new TrainConfig.Values(value(state,t.trainsCauseDamage),value(state,t.maxTrackPlacementLength),value(state,t.maxAssemblyLength),value(state,t.maxBogeyCount),number(state,t.manualTrainSpeedModifier),number(state,t.trainTopSpeed),number(state,t.trainTurningTopSpeed),number(state,t.trainAcceleration),number(state,t.poweredTrainTopSpeed),number(state,t.poweredTrainTurningTopSpeed),number(state,t.poweredTrainAcceleration));
  var schematics=new SchematicConfig.Values(value(state,s.creativePrintIncludesAir),value(state,s.maxSchematics),value(state,s.maxTotalSchematicSize),value(state,s.maxSchematicPacketSize),value(state,s.schematicIdleTimeout),value(state,s.schematicannonDelay),value(state,s.schematicannonShotsPerGunpowder));
  var ejector=new EjectorConfig.Values(value(state,k.maxEjectorDistance),value(state,k.ejectorScanInterval));
  return new NativeValues(equipment,trains,schematics,ejector,value(state,e.toolboxRange),value(state,config.logistics.mechanicalArmRange),value(state,e.maxSymmetryWandRange),value(state,e.placementAssistRange),value(state,k.maxCartCouplingLength),value(state,k.survivalContraptionPickup),value(state,k.minecartContraptionInContainers));
 }
 static Runnable prepareServer(CServer config,Map<String,Object> state){
  var nativeValues=prepare(config,state);var k=config.kinetics;
  int linkRange=value(state,config.logistics.linkRange);
  int blocks=value(state,k.maxBlocksMoved),poles=value(state,k.maxPistonPoles),sails=value(state,k.minimumWindmillSails),sailsPerRPM=value(state,k.windmillSailsPerRPM),psi=value(state,config.logistics.psiTimeout),roller=value(state,k.rollerFillDepth),chassis=value(state,k.maxChassisRange),rope=value(state,k.maxRopeLength);
  var spawner=value(state,k.spawnerMovement);var amethyst=value(state,k.amethystMovement);var obsidian=value(state,k.obsidianMovement);var deepslate=value(state,k.reinforcedDeepslateMovement);
  boolean noDrop=value(state,k.noDropWhenContraptionReplaceBlocks),stabilise=value(state,k.stabiliseStableContraptions),storeItems=value(state,k.moveItemsToStorage),replant=value(state,k.harvesterReplants),partial=value(state,k.harvestPartiallyGrown);
  return ()->{
   EquipmentConfig.installServer(nativeValues.equipment);TrainConfig.installServer(nativeValues.trains);SchematicConfig.installServer(nativeValues.schematics);EjectorConfig.installServer(nativeValues.ejector);ToolboxConfig.installServer(nativeValues.toolbox);ArmConfig.installServer(nativeValues.arm);SymmetryWandConfig.installServer(nativeValues.symmetry);CopycatConfig.installServer(nativeValues.placement);MinecartConfig.installServer(nativeValues.coupling,nativeValues.pickup,nativeValues.containers);
   ContraptionSettings.maxBlocksMoved=blocks;ContraptionSettings.maxPistonPoles=poles;ContraptionSettings.minimumWindmillSails=sails;ContraptionSettings.windmillSailsPerRPM=sailsPerRPM;ContraptionSettings.psiTimeout=psi;ContraptionSettings.rollerFillDepth=roller;
   ContraptionSettings.spawnerMovement=spawner;ContraptionSettings.amethystMovement=amethyst;ContraptionSettings.obsidianMovement=obsidian;ContraptionSettings.reinforcedDeepslateMovement=deepslate;ContraptionSettings.noDropWhenContraptionReplaceBlocks=noDrop;ContraptionSettings.stabiliseStableContraptions=stabilise;ContraptionSettings.moveItemsToStorage=storeItems;ContraptionSettings.harvesterReplants=replant;ContraptionSettings.harvestPartiallyGrown=partial;
   com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.INSTANCE.setRange(linkRange);
   ChassisSettings.maxRange=chassis;PulleySettings.maxRopeLength=rope;RollerBlock.placementAssistRange=nativeValues.placement;
  };
 }
 static Runnable prepareRemoteServer(CServer config,Map<String,Object> state){var nativeValues=prepare(config,state);int chassis=value(state,config.kinetics.maxChassisRange);return ()->{EquipmentConfig.installClient(nativeValues.equipment);TrainConfig.installClient(nativeValues.trains);SchematicConfig.installClient(nativeValues.schematics);EjectorConfig.installClient(nativeValues.ejector);ToolboxConfig.installClient(nativeValues.toolbox);ArmConfig.installClient(nativeValues.arm);ChassisSettings.installClientMaximum(chassis);};}
 static Runnable prepareLocalClient(CClient config,Map<String,Object> state){boolean rotate=value(state,config.rotateWhenSeated);return ()->ContraptionSettings.rotateWhenSeated=rotate;}
 static void clearRemoteServer(){EquipmentConfig.clearClient();TrainConfig.clearClient();SchematicConfig.clearClient();EjectorConfig.clearClient();ToolboxConfig.clearClient();ArmConfig.clearClient();ChassisSettings.clearClientMaximum();}
 static void invalidateLoadedKinetics(MinecraftServer server){for(var level:server.getAllLevels())KineticNetworks.refreshStressProviders(level);}
 private NativeConfigBindings(){}
}
