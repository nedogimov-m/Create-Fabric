package com.simibubi.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.logistics.depot.DepotRenderer;
import com.simibubi.create.content.processing.basin.BasinRenderer;
import com.simibubi.create.content.processing.burner.BlazeBurnerRenderer;
import com.simibubi.create.foundation.model.InlineMeshModel;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class CreateClient implements ClientModInitializer {
    public static final com.simibubi.create.content.equipment.zapper.ZapperRenderHandler ZAPPER_RENDER_HANDLER=com.simibubi.create.content.equipment.EquipmentClient.ZAPPER;
  public static final com.simibubi.create.content.schematics.client.ClientSchematicLoader SCHEMATIC_SENDER =
      com.simibubi.create.content.schematics.client.SchematicsClient.LOADER;
  public static final com.simibubi.create.content.schematics.client.SchematicHandler SCHEMATIC_HANDLER =
      com.simibubi.create.content.schematics.client.SchematicsClient.HANDLER;
  public static final com.simibubi.create.content.schematics.client.SchematicAndQuillHandler SCHEMATIC_AND_QUILL_HANDLER =
      com.simibubi.create.content.schematics.client.SchematicsClient.QUILL;
  @Override
  public void onInitializeClient() {
    com.simibubi.create.infrastructure.config.ClientConfigHooks.register();
    AllKeys.register();
    com.simibubi.create.content.logistics.filter.FilterItemTooltip.setShiftDown(AllKeys::shiftDown);
    com.simibubi.create.infrastructure.gui.OpenCreateMenuButton.register();
    com.simibubi.create.infrastructure.gui.CreateMenuArtwork.register();
    net.createmod.ponder.Ponder.registerEngineFoundation();
    net.createmod.ponder.command.SimplePonderActions.register();
    com.simibubi.create.foundation.ponder.CreatePonderPlugin.register();
    com.simibubi.create.foundation.sound.SoundScapes.register();
    com.simibubi.create.content.trains.CameraDistanceModifier.register();
    com.simibubi.create.content.legacy.ChromaticCompoundColor.register();
    com.simibubi.create.content.logistics.itemHatch.ItemHatchModels.register();
    BlockEntityRenderers.register(AllBlockEntityTypes.ITEM_HATCH,com.simibubi.create.content.logistics.itemHatch.ItemHatchRenderer::new);
    com.simibubi.create.content.equipment.tool.CardboardSwordClient.register();
    com.simibubi.create.content.equipment.blueprint.BlueprintClient.register();
    com.simibubi.create.content.equipment.blueprint.BlueprintRenderer.register();
    com.simibubi.create.content.decoration.copycat.CopycatModel.register();
    com.simibubi.create.content.schematics.client.SchematicsClient.register();
    com.simibubi.create.content.equipment.EquipmentClient.register();
    net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(AllBlockEntityTypes.CUCKOO_CLOCK,com.simibubi.create.content.kinetics.clock.CuckooClockRenderer::new);
    com.simibubi.create.content.equipment.toolbox.ToolboxHandlerClient.register();
    com.simibubi.create.content.contraptions.wrench.RadialWrenchHandler.register();
    com.simibubi.create.foundation.placement.KineticPlacementPreview.register();
    com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelPlacementClient.register();
    com.simibubi.create.content.redstone.displayLink.DisplayLinkClient.register();
    com.simibubi.create.content.trains.track.TrackModel.register();
    com.simibubi.create.content.trains.track.TrackPlacementPreview.register();
    com.simibubi.create.content.trains.graph.sync.RailwayGraphClient.register();
    com.simibubi.create.content.trains.graph.TrackGraphVisualizer.register();
    com.simibubi.create.content.trains.bogey.BogeyRenderers.registerStandard();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.BOGEY,
        com.simibubi.create.content.trains.bogey.BogeyBlockEntityRenderer::new);
    com.simibubi.create.content.decoration.girder.GirderModels.register();
    com.simibubi.create.content.redstone.nixieTube.NixieTubeModel.register();
    com.simibubi.create.content.redstone.nixieTube.NixieSignalModels.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.NIXIE_TUBE,
        com.simibubi.create.content.redstone.nixieTube.NixieTubeRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PLACARD,
        com.simibubi.create.content.decoration.placard.PlacardRenderer::new);
    com.simibubi.create.content.trains.display.FlapDisplayModel.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.FLAP_DISPLAY,
        com.simibubi.create.content.trains.display.FlapDisplayRenderer::new);
    com.simibubi.create.content.trains.station.StationClient.register();
    com.simibubi.create.content.trains.schedule.ScheduleClient.register();
    com.simibubi.create.content.trains.signal.SignalClient.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.TRACK_STATION,
        com.simibubi.create.content.trains.station.StationRenderer::new);
    com.simibubi.create.content.trains.track.TrackModels.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.TRACK, com.simibubi.create.content.trains.track.TrackRenderer::new);
    com.simibubi.create.content.trains.track.TrackBlockOutline.register();
    com.simibubi.create.content.trains.track.CurvedTrackInteraction.register();
    com.simibubi.create.content.decoration.palettes.RailwayGlassCTModel.register();
    com.simibubi.create.content.redstone.diodes.BrassDiodeSettingsClient.register();
    com.simibubi.create.content.contraptions.gantry.GantryClient.register();
    com.simibubi.create.content.contraptions.elevator.ElevatorControlsHandler.register();
    net.minecraft.client.renderer.entity.EntityRenderers.register(
        AllEntityTypes.GANTRY_CONTRAPTION,
        com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.ROPE_PULLEY,
        com.simibubi.create.content.contraptions.pulley.PulleyRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.ELEVATOR_PULLEY,
        com.simibubi.create.content.contraptions.elevator.ElevatorPulleyRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.GANTRY_SHAFT,
        com.simibubi.create.content.kinetics.gantry.GantryShaftRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.GANTRY_PINION,
        com.simibubi.create.content.contraptions.gantry.GantryCarriageRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PULSE_EXTENDER,
        com.simibubi.create.content.redstone.diodes.BrassDiodeRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PULSE_REPEATER,
        com.simibubi.create.content.redstone.diodes.BrassDiodeRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PULSE_TIMER,
        com.simibubi.create.content.redstone.diodes.BrassDiodeRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.ANALOG_LEVER,
        com.simibubi.create.content.redstone.analogLever.AnalogLeverRenderer::new);

    net.minecraft.client.renderer.entity.EntityRenderers.register(
        AllEntityTypes.ORIENTED_CONTRAPTION,
        com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer::new);
    net.minecraft.client.renderer.entity.EntityRenderers.register(
        AllEntityTypes.LINEAR_CONTRAPTION,
        com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.MECHANICAL_PISTON,
        com.simibubi.create.content.contraptions.piston.MechanicalPistonRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.SEQUENCED_GEARSHIFT, KineticBlockEntityRenderer::new);
    com.simibubi.create.content.contraptions.piston.PistonSettingsClient.register();
    net.minecraft.client.gui.screens.MenuScreens.register(
        com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftMenu.TYPE,
        com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftScreen::new);
    com.simibubi.create.content.decoration.slidingDoor.DoorClient.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.WINDMILL_BEARING,
        com.simibubi.create.content.contraptions.bearing.BearingRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.CLOCKWORK_BEARING,
        com.simibubi.create.content.contraptions.bearing.BearingRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.STICKER,
        com.simibubi.create.content.contraptions.chassis.StickerRenderer::new);
    com.simibubi.create.content.contraptions.chassis.ChassisSettingsClient.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.REDSTONE_LINK,
        com.simibubi.create.content.redstone.link.LinkRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.LECTERN_CONTROLLER,
        com.simibubi.create.content.redstone.link.controller.LecternControllerRenderer::new);
    net.minecraft.client.gui.screens.MenuScreens.register(
        com.simibubi.create.content.redstone.link.controller.LinkedControllerMenu.TYPE,
        com.simibubi.create.content.redstone.link.controller.LinkedControllerScreen::new);
    com.simibubi.create.content.redstone.link.controller.LinkedControllerClientHandler.register();
    com.simibubi.create.content.redstone.link.LinkClientOverlay.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.SLIDING_DOOR,
        com.simibubi.create.content.decoration.slidingDoor.SlidingDoorRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.CONTRAPTION_CONTROLS,
        com.simibubi
                .create
                .content
                .contraptions
                .actors
                .contraptionControls
                .ContraptionControlsRenderer
            ::new);
    com.simibubi.create.content.equipment.bell.BellEffects.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PECULIAR_BELL,
        com.simibubi.create.content.equipment.bell.BellRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.HAUNTED_BELL,
        com.simibubi.create.content.equipment.bell.BellRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.DESK_BELL,
        com.simibubi.create.content.redstone.deskBell.DeskBellRenderer::new);
    var particles =
        net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance();
    particles.register(
        AllParticleTypes.SOUL,
        com.simibubi.create.content.equipment.bell.SoulParticle.Factory::new);
    particles.register(
        AllParticleTypes.SOUL_PERIMETER,
        com.simibubi.create.content.equipment.bell.SoulParticle.Factory::new);
    particles.register(
        AllParticleTypes.SOUL_EXPANDING_PERIMETER,
        com.simibubi.create.content.equipment.bell.SoulParticle.Factory::new);
    particles.register(
        AllParticleTypes.SOUL_BASE,
        com.simibubi.create.content.equipment.bell.SoulBaseParticle.Factory::new);
    AllMovementRenderers.register();
    com.simibubi.create.content.contraptions.ContraptionClient.register();
    com.simibubi.create.content.contraptions.minecart.MinecartControllerClient.register();
    com.simibubi.create.content.contraptions.minecart.CouplingHandlerClient.register();
    com.simibubi.create.content.contraptions.minecart.CouplingRenderer.register();
    com.simibubi.create.content.contraptions.mounted.CartAssemblerSettingsClient.register();
    net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(java.util.List.of(net.minecraft.client.color.block.BlockTintSources.redstone()),AllBlocks.CONTROLLER_RAIL);
    com.simibubi.create.content.contraptions.actors.seat.ContraptionPlayerPassengerRotation
        .register();
    net.minecraft.client.renderer.entity.EntityRenderers.register(
        AllEntityTypes.CONTROLLED_CONTRAPTION,
        com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.MECHANICAL_BEARING,
        com.simibubi.create.content.contraptions.bearing.BearingRenderer::new);
    com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer.register();
    com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRidingHandler.register();
    com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorOverlay.register();
    com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler.register();
    net.minecraft.client.renderer.entity.EntityRenderers.register(
        AllEntityTypes.SUPER_GLUE,
        com.simibubi.create.content.contraptions.glue.SuperGlueRenderer::new);
    com.simibubi.create.content.trains.TrainMailModels.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PACKAGE_POSTBOX,
        com.simibubi.create.content.logistics.packagePort.postbox.PostboxRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.TRACK_OBSERVER,
        com.simibubi.create.content.trains.observer.TrackObserverRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PACKAGE_FROGPORT,
        com.simibubi.create.content.logistics.packagePort.frogport.FrogportRenderer::new);
    net.minecraft.client.gui.screens.MenuScreens.register(
        com.simibubi.create.content.logistics.packagePort.PackagePortMenu.TYPE,
        com.simibubi.create.content.logistics.packagePort.PackagePortScreen::new);
    com.simibubi.create.content.equipment.hats.EntityHats.register();
    com.simibubi.create.content.logistics.stockTicker.StockKeeperPortrait.register();
    com.simibubi.create.content.logistics.stockTicker.StockTickerPaymentInspectionClient.register();
    com.simibubi.create.content.logistics.tableCloth.TableClothModel.register();
    com.simibubi.create.content.logistics.tableCloth.TableClothOverlayRenderer.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.TABLE_CLOTH,
        com.simibubi.create.content.logistics.tableCloth.TableClothRenderer::new);
    com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen.register();
    com.simibubi.create.content.logistics.stockTicker.StockTickerScreen.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.FACTORY_PANEL,
        com.simibubi.create.content.logistics.factoryBoard.FactoryPanelRenderer::new);
    net.minecraft.client.gui.screens.MenuScreens.register(
        com.simibubi.create.content.logistics.factoryBoard.FactoryPanelMenu.TYPE,
        com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen::new);
    com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PACKAGER_LINK,
        com.simibubi.create.content.logistics.packagerLink.PackagerLinkRenderer::new);
    net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
        .register(
            AllParticleTypes.WIFI,
            com.simibubi.create.content.logistics.packagerLink.WiFiParticle.Factory::new);
    com.simibubi.create.content.logistics.vault.ItemVaultModel.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.CREATIVE_CRATE,
        com.simibubi.create.content.logistics.crate.CreativeCrateRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PACKAGER,
        com.simibubi.create.content.logistics.packager.PackagerRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.REPACKAGER,
        com.simibubi.create.content.logistics.packager.PackagerRenderer::new);
    com.simibubi.create.content.logistics.box.PackageRenderer.register();
    net.minecraft.client.renderer.entity.EntityRenderers.register(
        AllEntityTypes.SEAT,
        com.simibubi.create.content.contraptions.actors.seat.SeatRenderer::new);
    net.minecraft.client.gui.screens.MenuScreens.register(
        com.simibubi.create.content.logistics.filter.PackageFilterMenu.TYPE,
        com.simibubi.create.content.logistics.filter.PackageFilterScreen::new);
    com.simibubi.create.content.logistics.filter.AttributeFilterScreen.register();
    net.minecraft.client.gui.screens.MenuScreens.register(
        com.simibubi.create.content.logistics.filter.FilterMenu.TYPE,
        com.simibubi.create.content.logistics.filter.FilterScreen::new);
    AllFluidModels.register();
    com.simibubi.create.content.processing.sequenced.SequencedAssemblyTooltip.register();
    com.simibubi.create.content.kinetics.motor.KineticSpeedSettingsClient.register();
    com.simibubi.create.foundation.blockEntity.behaviour.FilteringSettingsClient.register();
    com.simibubi.create.content.contraptions.actors.roller.RollerSettingsClient.register();
    com.simibubi.create.content.logistics.tunnel.TunnelSettingsClient.register();
    com.simibubi.create.content.logistics.tunnel.BrassTunnelModel.register();
    com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer.register();
    com.simibubi.create.content.kinetics.gauge.GaugeObservationClient.register();
    com.simibubi.create.content.decoration.steamWhistle.WhistleModel.register();
    com.simibubi.create.content.decoration.steamWhistle.WhistleBlockEntity.CLIENT_TICK =
        com.simibubi.create.content.decoration.steamWhistle.WhistleEffects::tick;
    BlockEntityRenderers.register(
        AllBlockEntityTypes.STEAM_WHISTLE,
        com.simibubi.create.content.decoration.steamWhistle.WhistleRenderer::new);
    net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
        .register(
            AllParticleTypes.AIR, com.simibubi.create.foundation.particle.AirParticle.Factory::new);
    net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
        .register(
            AllParticleTypes.AIR_FLOW,
            com.simibubi.create.content.kinetics.fan.AirFlowParticle.Factory::new);
    net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
        .register(
            AllParticleTypes.STEAM_JET,
            com.simibubi.create.content.kinetics.steamEngine.SteamJetParticle.Factory::new);
    net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
        .register(
            AllParticleTypes.CUBE, com.simibubi.create.content.trains.CubeParticle.Factory::new);
    com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity.CLIENT_TICK =
        com.simibubi.create.content.kinetics.steamEngine.SteamEngineEffects::tick;
    com.simibubi.create.content.decoration.palettes.PaletteCTModel.register();
    com.simibubi.create.content.decoration.palettes.WindowRoofCTModel.register();
    InlineMeshModel.register();
    com.simibubi.create.content.fluids.tank.FluidTankModel.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.FLUID_TANK,
        com.simibubi.create.content.fluids.tank.FluidTankRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.CREATIVE_FLUID_TANK,
        com.simibubi.create.content.fluids.tank.FluidTankRenderer::new);
    AllPartialModels.register();
    com.simibubi.create.content.equipment.symmetryWand.SymmetryWandClient.register();
    com.simibubi.create.content.kinetics.NativeKineticsClient.register();
    com.simibubi.create.content.decoration.NativeDecorationCTModel.register();
    BlockEntityRenderers.register(AllBlockEntityTypes.FLUID_PIPE,com.simibubi.create.content.decoration.bracket.BracketRenderer::new);
    com.simibubi.create.content.trains.entity.TrainClient.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.STEAM_ENGINE,
        com.simibubi.create.content.kinetics.steamEngine.SteamEngineRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.POWERED_SHAFT, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.ROTATION_SPEED_CONTROLLER, KineticBlockEntityRenderer::new);
    com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointHandler.register();
    BlockEntityRenderers.register(AllBlockEntityTypes.MECHANICAL_ARM,com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.DEPLOYER,
        com.simibubi.create.content.kinetics.deployer.DeployerRenderer::new);
    BlockEntityRenderers.register(AllBlockEntityTypes.SPEEDOMETER, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.STRESSOMETER, KineticBlockEntityRenderer::new);
    com.simibubi.create.content.kinetics.crafter.CrafterCTModel.register();
    BlockEntityRenderers.register(AllBlockEntityTypes.KINETIC, KineticBlockEntityRenderer::new);
    for (var type :
        java.util.List.of(
            AllBlockEntityTypes.BRACKETED_KINETIC,
            AllBlockEntityTypes.MOTOR,
            AllBlockEntityTypes.HAND_CRANK,
            AllBlockEntityTypes.GEARSHIFT,
            AllBlockEntityTypes.CLUTCH,
            AllBlockEntityTypes.GEARBOX,
            AllBlockEntityTypes.ADJUSTABLE_CHAIN_GEARSHIFT)) {
      BlockEntityRenderers.register(type, KineticBlockEntityRenderer::new);
    }
    BlockEntityRenderers.register(AllBlockEntityTypes.MILLSTONE, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.MECHANICAL_PRESS, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.SMART_OBSERVER,
        com.simibubi.create.content.redstone.smartObserver.SmartObserverRenderer::new);
    com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchScreen.register();
    BlockEntityRenderers.register(
        AllBlockEntityTypes.CHUTE, com.simibubi.create.content.logistics.chute.ChuteRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.SMART_CHUTE,
        com.simibubi.create.content.logistics.chute.ChuteRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.ANDESITE_TUNNEL,
        com.simibubi.create.content.logistics.tunnel.BeltTunnelRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.BRASS_TUNNEL,
        com.simibubi.create.content.logistics.tunnel.BeltTunnelRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.FUNNEL,
        com.simibubi.create.content.logistics.funnel.FunnelRenderer::new);
    BlockEntityRenderers.register(AllBlockEntityTypes.DEPOT, DepotRenderer::new);
    BlockEntityRenderers.register(AllBlockEntityTypes.WEIGHTED_EJECTOR,com.simibubi.create.content.logistics.depot.EjectorRenderer::new);
    com.simibubi.create.content.logistics.depot.EjectorTargetHandler.register();
    BlockEntityRenderers.register(AllBlockEntityTypes.WATER_WHEEL, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.LARGE_WATER_WHEEL, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.MECHANICAL_MIXER, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(AllBlockEntityTypes.BASIN, BasinRenderer::new);
    BlockEntityRenderers.register(AllBlockEntityTypes.HEATER, BlazeBurnerRenderer::new);
    BlockEntityRenderers.register(AllBlockEntityTypes.ENCASED_FAN, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.BELT, com.simibubi.create.content.kinetics.belt.BeltRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.CRUSHING_WHEEL, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.MECHANICAL_CRAFTER,
        com.simibubi.create.content.kinetics.crafter.MechanicalCrafterRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PORTABLE_STORAGE_INTERFACE,
        com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.PORTABLE_FLUID_INTERFACE,
        com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.MECHANICAL_ROLLER,
        com.simibubi.create.content.contraptions.actors.roller.RollerRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.HARVESTER,
        com.simibubi.create.content.contraptions.actors.harvester.HarvesterRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.DRILL,
        com.simibubi.create.content.kinetics.base.CuttingToolRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.SAW,
        com.simibubi.create.content.kinetics.base.CuttingToolRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.MECHANICAL_PUMP, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(AllBlockEntityTypes.FLUID_VALVE, KineticBlockEntityRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.SMART_FLUID_PIPE,
        com.simibubi.create.content.fluids.pipes.SmartFluidPipeRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.SPOUT, com.simibubi.create.content.fluids.spout.SpoutRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.ITEM_DRAIN,
        com.simibubi.create.content.fluids.drain.ItemDrainRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.HOSE_PULLEY,
        com.simibubi.create.content.fluids.hosePulley.HosePulleyRenderer::new);
    BlockEntityRenderers.register(
        AllBlockEntityTypes.STRAIGHT_PIPE,
        com.simibubi.create.content.fluids.pipes.TransparentStraightPipeRenderer::new);
    com.simibubi.create.content.fluids.PipeAttachmentModel.register();
  }
}
