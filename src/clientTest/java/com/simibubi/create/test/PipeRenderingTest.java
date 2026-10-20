package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.pipes.*;
import com.simibubi.create.content.fluids.pump.*;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.content.kinetics.base.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import java.util.concurrent.atomic.*;

final class PipeRenderingTest {
    private static final java.util.List<String> REMAINING_SCENES=java.util.List.of(
        "GaugeRenderingTest",
        "LitBurnerRenderingTest",
        "SpeedControllerRenderingTest",
        "DeployerRenderingTest",
        "FilterRenderingTest",
        "AttributeFilterRenderingTest",
        "FunnelRenderingTest",
        "TunnelRenderingTest",
        "ChuteRenderingTest",
        "ObserverRenderingTest",
        "PackageRenderingTest",
        "PackagerRenderingTest",
        "StockLinkRenderingTest",
        "FactoryPanelRenderingTest",
        "RedstoneRequesterRenderingTest",
        "SeatRenderingTest",
        "StockTickerRenderingTest",
        "TableClothRenderingTest",
        "KeeperDisplayRenderingTest",
        "ChainConveyorRenderingTest",
        "FrogportRenderingTest",
        "ChainRidingRenderingTest",
        "SuperGlueRenderingTest",
        "ContraptionAssemblyRenderingTest",
        "MechanicalBearingRenderingTest",
        "ContraptionCollisionRenderingTest",
        "ContraptionActorRenderingTest",
        "MovingDeployerRenderingTest",
        "FarmingActorRenderingTest",
        "PortableInterfaceRenderingTest",
        "RollerRenderingTest",
        "ContainerActorRenderingTest",
        "ContactRenderingTest",
        "BellRenderingTest",
        "EntityReadinessRenderingTest",
        "ControlsRenderingTest",
        "DoorRenderingTest",
        "TrackRenderingTest",
        "DispenserRenderingTest",
        "StabilizedBearingRenderingTest",
        "PistonRenderingTest",
        "SequencedGearshiftRenderingTest",
        "RailwayGlassRenderingTest",
        "PulleyRenderingTest",
        "ElevatorRenderingTest",
        "GantryRenderingTest",
        "SimpleRedstoneRenderingTest",
        "PulseTimerRenderingTest",
        "ThresholdSwitchRenderingTest",
        "TrackSmoothingRenderingTest",
        "TrackPavingRenderingTest",
        "PortalTrackRenderingTest",
        "PortalGenerationRenderingTest",
        "FlapDisplayRenderingTest",
        "DisplayLinkRenderingTest",
        "CuckooClockRenderingTest",
        "GaugeObservedRenderingTest",
        "NixiePlacardRenderingTest",
        "NixieComputerSignalRenderingTest",
        "GirderRenderingTest",
        "NativeKineticsRenderingTest",
        "DecorationRenderingTest",
        "BogeyRenderingTest",
        "StationRenderingTest",
        "ArmRenderingTest",
        "WorldshaperRenderingTest",
        "SymmetryWandRenderingTest",
        "EquipmentRenderingTest",
        "EjectorRenderingTest",
        "ArmorRenderingTest",
        "CopycatRenderingTest",
        "ScheduleRenderingTest",
        "ToolboxRenderingTest",
        "SchematicsRenderingTest",
        "TrainRenderingTest",
        "SignalRenderingTest",
        "TrackCurveRenderingTest",
        "WindmillClockworkRenderingTest",
        "ClockworkReservationRenderingTest",
        "ChassisRenderingTest",
        "RedstoneLinkRenderingTest",
        "VaultRenderingTest");

    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        if(Boolean.getBoolean("create.test.onlyGaugeObserved")){GaugeObservedRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyCuckooClocks")){CuckooClockRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyArmor")){ArmorRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyWorldshaper")){WorldshaperRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlySymmetryWand")){SymmetryWandRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyCopycats")){CopycatRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyDecoration21")){DecorationRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyNativeKinetics")){NativeKineticsRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlySchematics")){SchematicsRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyToolboxes")){ToolboxRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyEjectors")){EjectorRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyEquipment")){EquipmentRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyArms")){ArmRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlySchedules")){ScheduleRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyDisplayLink")){DisplayLinkRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyThresholdSwitch")){ThresholdSwitchRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyFlapDisplay")){FlapDisplayRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyNixiePlacard")){NixiePlacardRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyNixieComputer")){NixieComputerSignalRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyGirders")){GirderRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyTrains")){TrainRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyStations")){StationRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlySignals")){SignalRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyBogeys")){BogeyRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyTracks")){TrackRenderingTest.run(context,world);return;}
        var server=world.getServer();server.runCommand("gamemode spectator @p");
        if(Boolean.getBoolean("create.test.onlyTrackSmoothing")){TrackSmoothingRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyTrackPaving")){TrackPavingRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyPortalTracks")){PortalTrackRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyPortalGeneration")){PortalGenerationRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyTrackCurves")){
            // Reproduce the aggregate's hidden-HUD entry and verify the scene restores it.
            context.runOnClient(client->{if(!client.gui.hud.isHidden())client.gui.hud.toggle();});
            TrackCurveRenderingTest.run(context,world);
            context.runOnClient(client->{if(!client.gui.hud.isHidden())throw new AssertionError("Curve scene failed to restore the original hidden HUD");});
            return;
        }
        if(Boolean.getBoolean("create.test.onlyPipes"))context.runOnClient(client->client.gui.hud.toggle());
        if(Boolean.getBoolean("create.test.onlyRemainingScenes")){
            var firstScene=System.getProperty("create.test.remainingSceneStart","GaugeRenderingTest");
            int firstIndex=REMAINING_SCENES.indexOf(firstScene);
            if(firstIndex<0)throw new IllegalArgumentException("Unknown create.test.remainingSceneStart: "+firstScene);
            // The first ordinary tail fixture creates its own scene in these real columns.
            server.runCommand("tp @p 608 7 12");
            context.waitFor(client->client.player.isSpectator()&&Math.abs(client.player.getX()-608.5)<0.1&&Math.abs(client.player.getZ()-12.5)<0.1
                &&client.level.isLoaded(new BlockPos(594,3,1))&&client.level.isLoaded(new BlockPos(608,4,12))&&client.level.isLoaded(new BlockPos(626,4,12)),500);
            world.getConnection().waitForChunksRender();
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var player=level.players().getFirst();
                if(!player.isSpectator()||Math.abs(player.getX()-608.5)>=0.1||Math.abs(player.getZ()-12.5)>=0.1)throw new AssertionError("Focused remaining scenes require the actual spectator teleport before fixture creation");
                for(int x:new int[]{594,608,626})for(int z:new int[]{1,12})if(!com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level,new BlockPos(x,3,z)))throw new AssertionError("Focused remaining scenes require actual loaded Gauge scene columns before native block-creation commands");
            });
            runRemainingScenes(context,world,firstIndex);return;
        }
        if(Boolean.getBoolean("create.test.onlyChainConveyors")){ChainConveyorRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyFrogports")){FrogportRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyChainRiding")){ChainRidingRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyGlue")){SuperGlueRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyContraptions")){ContraptionAssemblyRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyBearings")){MechanicalBearingRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyMovingDeployers")){MovingDeployerRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyFarmingActors")){FarmingActorRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyPortableInterfaces")){PortableInterfaceRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyRollers")){RollerRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyContainerActors")){ContainerActorRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyContacts")){ContactRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyBells")){BellRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyEntityReadiness")){EntityReadinessRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyControls")){ControlsRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyDoors")){DoorRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyDispensers")){DispenserRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyStabilizedBearings")){StabilizedBearingRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyPistons")){PistonRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyRailwayGlass")){RailwayGlassRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyPulleys")){PulleyRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyElevators")){ElevatorRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyGantries")){GantryRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlySimpleRedstone")){SimpleRedstoneRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyPulseTimer")){PulseTimerRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlySequencedGearshift")){SequencedGearshiftRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyWindmillClockwork")){WindmillClockworkRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyClockworkReservations")){ClockworkReservationRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyChassis")){ChassisRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyRedstoneLinks")){RedstoneLinkRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyMountedFixtures")){ContraptionAssemblyRenderingTest.run(context,world);ContainerActorRenderingTest.run(context,world);ContactRenderingTest.run(context,world);BellRenderingTest.run(context,world);EntityReadinessRenderingTest.run(context,world);ControlsRenderingTest.run(context,world);DoorRenderingTest.run(context,world);DispenserRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyActors")){ContraptionActorRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyContraptionCollisions")){ContraptionCollisionRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyFluidControls")){FluidControlRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlySpouts")){SpoutRenderingTest.run(context,world);DrainRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyHosePulleys")){HosePulleyRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyCustomFluids")){CustomFluidRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlySteamEngines")){SteamEngineRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyWhistles")){WhistleRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyGoggles")){GogglesRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyGauges")){GaugeRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyLitBurner")){LitBurnerRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlySpeedControllers")){SpeedControllerRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyDeployers")){DeployerRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyFilters")){FilterRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyObservers")){ObserverRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyPackages")){PackageRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyPackagers")){PackagerRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyVaults")){VaultRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyStockLinks")){StockLinkRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyFactoryPulseSequence")){StockLinkRenderingTest.run(context,world);FactoryPanelRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyFactoryPanels")){FactoryPanelRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyRequesters")){RedstoneRequesterRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlySeats")){SeatRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyKeeperDisplays")){StockTickerRenderingTest.run(context,world);TableClothRenderingTest.run(context,world);KeeperDisplayRenderingTest.run(context,world);ChainConveyorRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyTableCloths")){TableClothRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyStockTickers")){StockTickerRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyChutes")){ChuteRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyTunnels")){TunnelRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyFunnels")){FunnelRenderingTest.run(context,world);return;}
        if(Boolean.getBoolean("create.test.onlyAttributeFilters")){AttributeFilterRenderingTest.run(context,world);return;}
        server.runCommand("tp @p 181 6 12");
        context.waitFor(client->client.player.getX()>180&&client.level.isLoaded(new BlockPos(185,4,5)),500);
        server.runCommand("fill 173 3 0 191 3 15 minecraft:smooth_quartz");
        server.runCommand("fill 176 4 5 176 5 5 create:fluid_tank");server.runCommand("fill 185 4 5 185 5 5 create:fluid_tank");
        for(int x=177;x<185;x++)if(x!=180)server.runCommand("setblock "+x+" 4 5 "+(x==181||x==182?"create:glass_fluid_pipe[axis=x]":"create:fluid_pipe[east=true,west=true]"));
        server.runCommand("setblock 180 4 5 create:mechanical_pump[facing=east]");
        server.runCommand("setblock 180 5 5 create:cogwheel[axis=x]");server.runCommand("setblock 179 5 5 create:creative_motor[facing=east]");
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(176,4,5)) instanceof FluidTankBlockEntity tank&&tank.getHeight()==2);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();try(var tx=Transaction.openOuter()){((FluidTankBlockEntity)level.getBlockEntity(new BlockPos(176,4,5))).storage().insert(FluidVariant.of(Fluids.WATER),12*FluidConstants.BUCKET,tx);tx.commit();}});
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(185,4,5)) instanceof FluidTankBlockEntity tank&&tank.getTankInventory().getFluidAmount()>0,500);
        context.getInput().lookAt(new BlockPos(181,4,5));world.getConnection().waitForChunksRender();context.waitTicks(8);
        context.runOnClient(client->{
            var pos=new BlockPos(181,4,5);var pipe=(StraightPipeBlockEntity)client.level.getBlockEntity(pos);
            var extracted=client.getBlockEntityRenderDispatcher().tryExtractRenderState(pipe,0.5f,null,false);
            if(!(extracted instanceof TransparentStraightPipeRenderer.State state)||state.streams.isEmpty()||state.streams.stream().anyMatch(stream->stream.mesh().size()==0))throw new AssertionError("Glass pipe fluid mesh missing");
            var pump=client.level.getBlockEntity(new BlockPos(180,4,5));var rendered=client.getBlockEntityRenderDispatcher().tryExtractRenderState(pump,0.5f,null,false);
            if(!(rendered instanceof KineticRenderState pumpState)||pumpState.model.isEmpty())throw new AssertionError("Pump cog missing");
            var block=client.level.getBlockState(pos);var model=client.getModelManager().getBlockStateModelSet().get(block);var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();
            model.emitQuads(mesh.emitter(),(net.minecraft.client.renderer.block.BlockAndTintGetter)client.level,pos,block,net.minecraft.util.RandomSource.create(42),face->false);if(mesh.immutableCopy().size()==0)throw new AssertionError("Pipe attachment model empty");
        });
        context.takeScreenshot("create-vulkan-pump-and-glass-pipes");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(AllBlocks.COPPER_CASING));var pos=new BlockPos(183,4,5);level.getBlockState(pos).useItemOn(player.getMainHandItem(),level,player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false));player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);});
        context.waitFor(client->client.level.getBlockState(new BlockPos(183,4,5)).is(AllBlocks.ENCASED_FLUID_PIPE));world.getConnection().waitForChunksRender();context.waitTicks(5);context.takeScreenshot("create-vulkan-encased-pipe");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var pos=new BlockPos(180,4,5);level.setBlockAndUpdate(pos,level.getBlockState(pos).setValue(PumpBlock.FACING,Direction.WEST));});
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(181,4,5)) instanceof StraightPipeBlockEntity pipe&&pipe.transport().getFlow(Direction.WEST)!=null&&pipe.transport().getFlow(Direction.WEST).inbound==false,400);
        context.waitTicks(10);context.takeScreenshot("create-vulkan-pump-reversed-arrow");
        FluidControlRenderingTest.run(context,world);
        SpoutRenderingTest.run(context,world);
        DrainRenderingTest.run(context,world);
        HosePulleyRenderingTest.run(context,world);
        CustomFluidRenderingTest.run(context,world);
        SteamEngineRenderingTest.run(context,world);
        WhistleRenderingTest.run(context,world);
        GogglesRenderingTest.run(context,world);
        runRemainingScenes(context,world,0);
    }

    /** The unchanged ordinary post-goggles tail, shared by focused and complete runs. */
    private static void runRemainingScenes(ClientGameTestContext context,TestSingleplayerContext world,int firstIndex){
        var server=world.getServer();
        if(firstIndex<=0)GaugeRenderingTest.run(context,world);
        if(firstIndex<=1)LitBurnerRenderingTest.run(context,world);
        if(firstIndex<=2)SpeedControllerRenderingTest.run(context,world);
        if(firstIndex<=3)DeployerRenderingTest.run(context,world);
        if(firstIndex<=4)FilterRenderingTest.run(context,world);
        if(firstIndex<=5)AttributeFilterRenderingTest.run(context,world);
        if(firstIndex<=6)FunnelRenderingTest.run(context,world);
        if(firstIndex<=7)TunnelRenderingTest.run(context,world);
        if(firstIndex<=8)ChuteRenderingTest.run(context,world);
        if(firstIndex<=9)ObserverRenderingTest.run(context,world);
        if(firstIndex<=10)PackageRenderingTest.run(context,world);
        if(firstIndex<=11)PackagerRenderingTest.run(context,world);
        if(firstIndex<=12)StockLinkRenderingTest.run(context,world);
        if(firstIndex<=13)FactoryPanelRenderingTest.run(context,world);
        if(Boolean.getBoolean("create.test.onlyFactoryPulsePrefix"))return;
        if(firstIndex<=14)RedstoneRequesterRenderingTest.run(context,world);
        if(firstIndex<=15)SeatRenderingTest.run(context,world);
        if(firstIndex<=16)StockTickerRenderingTest.run(context,world);
        if(firstIndex<=17)TableClothRenderingTest.run(context,world);
        if(firstIndex<=18)KeeperDisplayRenderingTest.run(context,world);
        if(firstIndex<=19)ChainConveyorRenderingTest.run(context,world);
        if(firstIndex<=20)FrogportRenderingTest.run(context,world);
        if(firstIndex<=21)ChainRidingRenderingTest.run(context,world);
        if(firstIndex<=22)SuperGlueRenderingTest.run(context,world);
        if(firstIndex<=23)ContraptionAssemblyRenderingTest.run(context,world);
        if(firstIndex<=24)MechanicalBearingRenderingTest.run(context,world);
        if(firstIndex<=25)ContraptionCollisionRenderingTest.run(context,world);
        if(firstIndex<=26)ContraptionActorRenderingTest.run(context,world);
        if(firstIndex<=27)MovingDeployerRenderingTest.run(context,world);
        if(firstIndex<=28)FarmingActorRenderingTest.run(context,world);
        if(firstIndex<=29)PortableInterfaceRenderingTest.run(context,world);
        if(firstIndex<=30)RollerRenderingTest.run(context,world);
        if(firstIndex<=31)ContainerActorRenderingTest.run(context,world);
        if(firstIndex<=32)ContactRenderingTest.run(context,world);
        if(firstIndex<=33)BellRenderingTest.run(context,world);
        if(firstIndex<=34)EntityReadinessRenderingTest.run(context,world);
        if(firstIndex<=35)ControlsRenderingTest.run(context,world);
        if(firstIndex<=36)DoorRenderingTest.run(context,world);
        if(firstIndex<=37)TrackRenderingTest.run(context,world);
        if(firstIndex<=38)DispenserRenderingTest.run(context,world);
        if(firstIndex<=39)StabilizedBearingRenderingTest.run(context,world);
        if(firstIndex<=40)PistonRenderingTest.run(context,world);
        if(firstIndex<=41)SequencedGearshiftRenderingTest.run(context,world);
        if(firstIndex<=42)RailwayGlassRenderingTest.run(context,world);
        if(firstIndex<=43)PulleyRenderingTest.run(context,world);
        if(firstIndex<=44)ElevatorRenderingTest.run(context,world);
        if(firstIndex<=45)GantryRenderingTest.run(context,world);
        if(firstIndex<=46)SimpleRedstoneRenderingTest.run(context,world);
        if(firstIndex<=47)PulseTimerRenderingTest.run(context,world);
        if(firstIndex<=48)ThresholdSwitchRenderingTest.run(context,world);
        if(firstIndex<=49)TrackSmoothingRenderingTest.run(context,world);
        if(firstIndex<=50)TrackPavingRenderingTest.run(context,world);
        if(firstIndex<=51)PortalTrackRenderingTest.run(context,world);
        if(firstIndex<=52)PortalGenerationRenderingTest.run(context,world);
        if(firstIndex<=53)FlapDisplayRenderingTest.run(context,world);
        if(firstIndex<=54)DisplayLinkRenderingTest.run(context,world);
        if(firstIndex<=55)CuckooClockRenderingTest.run(context,world);
        if(firstIndex<=56)GaugeObservedRenderingTest.run(context,world);
        if(firstIndex<=57)NixiePlacardRenderingTest.run(context,world);
        if(firstIndex<=58)NixieComputerSignalRenderingTest.run(context,world);
        if(firstIndex<=59)GirderRenderingTest.run(context,world);
        if(firstIndex<=60)NativeKineticsRenderingTest.run(context,world);
        if(firstIndex<=61)DecorationRenderingTest.run(context,world);
        if(firstIndex<=62)BogeyRenderingTest.run(context,world);
        if(firstIndex<=63)StationRenderingTest.run(context,world);
        if(firstIndex<=64)ArmRenderingTest.run(context,world);
        if(firstIndex<=65)WorldshaperRenderingTest.run(context,world);
        if(firstIndex<=66)SymmetryWandRenderingTest.run(context,world);
        if(firstIndex<=67)EquipmentRenderingTest.run(context,world);
        if(firstIndex<=68)EjectorRenderingTest.run(context,world);
        if(firstIndex<=69)ArmorRenderingTest.run(context,world);
        if(firstIndex<=70)CopycatRenderingTest.run(context,world);
        if(firstIndex<=71)ScheduleRenderingTest.run(context,world);
        if(firstIndex<=72)ToolboxRenderingTest.run(context,world);
        if(firstIndex<=73)SchematicsRenderingTest.run(context,world);
        if(firstIndex<=74)TrainRenderingTest.run(context,world);
        if(firstIndex<=75)SignalRenderingTest.run(context,world);
        if(firstIndex<=76)TrackCurveRenderingTest.run(context,world);
        if(firstIndex<=77)WindmillClockworkRenderingTest.run(context,world);
        if(firstIndex<=78)ClockworkReservationRenderingTest.run(context,world);
        if(firstIndex<=79)ChassisRenderingTest.run(context,world);
        if(firstIndex<=80)RedstoneLinkRenderingTest.run(context,world);
        if(firstIndex<=81)VaultRenderingTest.run(context,world);
        // A long series leaves both end chunks ticking while the central chunk unloads.
        server.runCommand("tp @p 199 8 27");context.waitFor(client->Math.abs(client.player.getX()-199.5)<0.1&&client.level.isLoaded(new BlockPos(158,4,20))&&client.level.isLoaded(new BlockPos(240,4,20)),500);
        server.runCommand("fill 156 3 18 242 3 22 minecraft:smooth_quartz");
        server.runCommand("setblock 158 4 20 create:creative_fluid_tank");server.runCommand("fill 240 4 20 240 35 20 create:fluid_tank");
        for(int x=159;x<=239;x++){
            if((x-159)%16==0){server.runCommand("setblock "+x+" 4 20 create:mechanical_pump[facing=east]");server.runCommand("setblock "+x+" 5 20 create:cogwheel[axis=x]");server.runCommand("setblock "+(x-1)+" 5 20 create:creative_motor[facing=east]");}
            else server.runCommand("setblock "+x+" 4 20 create:fluid_pipe[east=true,west=true]");
        }
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(int x=158;x<=238;x+=16){var motor=(KineticBlockEntity)level.getBlockEntity(new BlockPos(x,5,20));for(int i=0;i<4;i++)motor.adjustGeneratedSpeed(false);}var tank=(CreativeFluidTankBlockEntity)level.getBlockEntity(new BlockPos(158,4,20));((CreativeFluidTankBlockEntity.CreativeSmartFluidTank)tank.getTankInventory()).setContainedFluid(new com.simibubi.create.foundation.fluid.FluidStack(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET));});
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(240,4,20)) instanceof FluidTankBlockEntity tank&&tank.getTankInventory().getFluidAmount()>0,700);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(int x:new int[]{9,15})level.getChunkSource().addTicket(new net.minecraft.server.level.Ticket(net.minecraft.server.level.TicketType.FORCED,32),new net.minecraft.world.level.ChunkPos(x,1));});server.runCommand("tp @p 5000 10 5000");
        var unloaded=new AtomicBoolean();for(int attempt=0;attempt<120&&!unloaded.get();attempt++){context.waitTicks(5);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();unloaded.set(!level.isLoaded(new BlockPos(200,4,20)));});}
        if(!unloaded.get()){server.runOnServer(ignored->Create.LOGGER.error("Middle chunk ticket status: {}, tickets: {}",world.getConnection().getServerLevel().getChunkSource().getChunkDebugData(new net.minecraft.world.level.ChunkPos(12,1)),world.getConnection().getServerLevel().getDataStorage().computeIfAbsent(net.minecraft.world.level.TicketStorage.TYPE).getTickets(new net.minecraft.world.level.ChunkPos(12,1).pack())));throw new AssertionError("Middle pipe chunk did not unload");}
        var before=new AtomicLong();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(!level.isLoaded(new BlockPos(158,4,20))||!level.isLoaded(new BlockPos(240,4,20)))throw new AssertionError("Endpoint chunks unexpectedly unloaded");before.set(((FluidTankBlockEntity)level.getBlockEntity(new BlockPos(240,4,20))).getTankInventory().getFluidAmount());});
        context.waitTicks(30);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(((FluidTankBlockEntity)level.getBlockEntity(new BlockPos(240,4,20))).getTankInventory().getFluidAmount()!=before.get())throw new AssertionError("Fluid teleported through unloaded middle chunk");});
        server.runCommand("tp @p 199 8 27");context.waitFor(client->client.player.getX()<300&&client.level.getBlockEntity(new BlockPos(200,4,20)) instanceof FluidPipeBlockEntity,500);context.waitTicks(100);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(((FluidTankBlockEntity)level.getBlockEntity(new BlockPos(240,4,20))).getTankInventory().getFluidAmount()<=before.get())throw new AssertionError("Pipe flow did not resume after chunk load");});
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(int x:new int[]{9,15})level.getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.FORCED,new net.minecraft.world.level.ChunkPos(x,1),1);});
        context.getInput().lookAt(new BlockPos(200,4,20));world.getConnection().waitForChunksRender();context.takeScreenshot("create-vulkan-pump-chain-reloaded");
    }
}
