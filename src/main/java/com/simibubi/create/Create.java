package com.simibubi.create;

import com.simibubi.create.content.kinetics.KineticNetworks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Create implements ModInitializer {
  public static final String ID = "create";
  public static final String NAME = "Create";
  public static final com.google.gson.Gson GSON =
      new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  /** Use the random source of a local level or entity for new gameplay code. */
  @Deprecated
  public static final java.util.Random RANDOM = new java.util.Random();

  public static final com.simibubi.create.content.kinetics.TorquePropagator TORQUE_PROPAGATOR =
      new com.simibubi.create.content.kinetics.TorquePropagator();
  public static final com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler
      REDSTONE_LINK_NETWORK_HANDLER =
          com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.INSTANCE;
  public static final Logger LOGGER = LoggerFactory.getLogger(ID);

  public static Identifier asResource(String path) {
    return Identifier.fromNamespaceAndPath(ID, path);
  }

  public static net.createmod.catnip.lang.LangBuilder lang() {
    return new net.createmod.catnip.lang.LangBuilder(ID);
  }

  private static <B extends net.minecraft.world.level.block.entity.BlockEntity>
      void registerItemStorage(
          java.util.function.BiFunction<
                  B,
                  net.minecraft.core.Direction,
                  net.fabricmc.fabric.api.transfer.v1.storage.Storage<
                      net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>>
              provider,
          net.minecraft.world.level.block.entity.BlockEntityType<B> type) {
    ItemStorage.SIDED.registerForBlockEntity(
        (be, side) ->
            com.simibubi.create.foundation.item.WorldBlockEntityStorage.of(
                be,
                provider.apply(be, side),
                net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.blank()),
        type);
  }

  private static <B extends net.minecraft.world.level.block.entity.BlockEntity>
      void registerFluidStorage(
          java.util.function.BiFunction<
                  B,
                  net.minecraft.core.Direction,
                  net.fabricmc.fabric.api.transfer.v1.storage.Storage<
                      net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>>
              provider,
          net.minecraft.world.level.block.entity.BlockEntityType<B> type) {
    net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.registerForBlockEntity(
        (be, side) ->
            com.simibubi.create.foundation.item.WorldBlockEntityStorage.of(
                be,
                provider.apply(be, side),
                net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.blank()),
        type);
  }

  @Override
  public void onInitialize() {
    com.simibubi.create.foundation.data.recipe.CreateResourceConditions.register();
    com.simibubi.create.content.decoration.copycat.CopycatConfig.reload();
    com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks.register();
    AllFluids.register();
    AllMapDecorationTypes.register();
    AllBlocks.register();
    com.simibubi.create.content.trains.schedule.hat.ConductorHatState.register();
    com.simibubi.create.foundation.advancement.AllAdvancements.register();
    com.simibubi.create.foundation.advancement.AllTriggers.register();
    com.simibubi.create.content.kinetics.clock.CuckooClockAdvancements.register();
    com.simibubi.create.content.kinetics.gauge.GaugeObservationNetworking.register();
    com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.register();
    com.simibubi.create.foundation.advancement.GameplayAdvancements.register();
    com.simibubi.create.content.kinetics.NativeKinetics.register();
    com.simibubi.create.content.trains.track.AllPortalTracks.registerDefaults();
    net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance()
        .add(AllBlocks.CARDBOARD_BLOCK, 100, 20);
    net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance().add(AllBlocks.BOUND_CARDBOARD_BLOCK,100,20);
    AllItems.register();
    com.simibubi.create.impl.registry.CreateDataMapsImpl.register();
    AllStructureProcessorTypes.register();
    AllSchematicStateFilters.registerDefaults();
    AllSchematicRequirements.registerDefaults();
    com.simibubi.create.content.schematics.table.SchematicTableMenu.register();
    com.simibubi.create.content.schematics.cannon.SchematicannonMenu.register();
    com.simibubi.create.content.schematics.ServerSchematicLoader.register();
    com.simibubi.create.content.schematics.SchematicWaterUpdates.register();
    com.simibubi.create.content.schematics.packet.SchematicUploadPacket.register();
    com.simibubi.create.content.schematics.packet.SchematicSyncPacket.register();
    com.simibubi.create.content.schematics.packet.InstantSchematicPacket.register();
    com.simibubi.create.content.schematics.packet.SchematicPlacePacket.register();
    com.simibubi.create.content.equipment.clipboard.ClipboardEditPacket.register();
    com.simibubi.create.content.equipment.clipboard.ClipboardValueSettingsHandler.register();
    com.simibubi.create.content.equipment.Equipment.register();
    AllContraptionMovementSettings.registerDefaults();
    AllMovementBehaviours.register();
    com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem.register();
    com.simibubi.create.content.contraptions.behaviour.dispenser.MountedDispenseItemBehaviors
        .registerDefaults();
    com.simibubi.create.content.decoration.slidingDoor.DoorStatePacket.register();
    com.simibubi.create.content.redstone.contact.ContactChunkRevalidation.register();
    com.simibubi.create.content.equipment.bell.HauntedBellPulser.register();
    AllContraptionTypes.init();
    com.simibubi.create.content.contraptions.minecart.MinecartConfig.reload();
    com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController.register();
    // Source HIGH coupling/wrench removal runs before the cart pickup endpoint.
    com.simibubi.create.content.contraptions.minecart.MinecartCouplingItem.register();
    com.simibubi.create.content.contraptions.mounted.MinecartContraptionItem.register();
    com.simibubi.create.content.contraptions.mounted.CartAssemblerSettings.register();
    com.simibubi.create.infrastructure.command.CouplingCommand.register();
    net.createmod.ponder.command.PonderCommands.register();
    AllMountedStorageTypes.init();
    com.simibubi.create.content.contraptions.glue.SuperGlueHandler.register();
    com.simibubi.create.content.logistics.tableCloth.ShopStockPacket.register();
    com.simibubi.create.content.logistics.stockTicker.StockTickerMenu.register();
    com.simibubi.create.content.logistics.stockTicker.StockTickerPaymentInspection.register();
    com.simibubi.create.content.logistics.stockTicker.StockTickerCraftingPackets.register();
    com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler.register();
    com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterMenu.register();
    com.simibubi.create.content.logistics.factoryBoard.FactoryPanelMenu.register();
    com.simibubi.create.content.logistics.filter.FilterMenu.register();
    com.simibubi.create.content.logistics.filter.AttributeFilterMenu.register();
    com.simibubi.create.content.kinetics.motor.KineticSpeedSettings.register();
    com.simibubi.create.content.contraptions.actors.roller.RollerSettings.register();
    com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.register();
    com.simibubi.create.content.logistics.itemHatch.ItemHatchHandler.register();
    com.simibubi.create.content.equipment.tool.CardboardSwordItem.register();
    com.simibubi.create.content.equipment.TreeFertilizerItem.register();
    com.simibubi.create.content.equipment.blueprint.Blueprints.register();
    com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchMenu.register();
    com.simibubi.create.content.logistics.funnel.FunnelItem.register();
    com.simibubi.create.content.logistics.tunnel.TunnelSettings.register();
    AllDataComponents.register();
    com.simibubi.create.content.legacy.LegacyMaterials.register();
        com.simibubi.create.content.trains.schedule.ScheduleNetworking.register();
    com.simibubi.create.content.trains.entity.CarriageSyncData.register();
    AllEntityTypes.register();
    com.simibubi.create.content.trains.entity.CarriagePlayerReferences.register();
    com.simibubi.create.content.trains.TrainAdvancements.register();
    com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes.init();
    com.simibubi.create.content.kinetics.mechanicalArm.ArmAdvancements.register();
    com.simibubi.create.content.kinetics.mechanicalArm.ArmNetworking.register();
    com.simibubi.create.content.logistics.depot.EjectorNetworking.register();
    com.simibubi.create.content.contraptions.actors.trainControls.ControlsServerHandler.register();
    com.simibubi.create.content.contraptions.ContraptionNetworking.register();
    com.simibubi.create.content.logistics.filter.PackageFilterMenu.register();
    com.simibubi.create.content.logistics.item.filter.attribute.AllItemAttributeTypes.init();
    AllFluids.registerStorage();
    com.simibubi.create.content.fluids.tank.BoilerHeaters.registerDefaults();
    AllBlockEntityTypes.register();
    AllSchematicNbt.registerDefaults();
    registerItemStorage((table,side)->table.inventory.storage,AllBlockEntityTypes.SCHEMATIC_TABLE);
    registerItemStorage((cannon,side)->cannon.inventory.storage,AllBlockEntityTypes.SCHEMATICANNON);
    com.simibubi.create.content.equipment.toolbox.ToolboxNetworking.register();
    com.simibubi.create.content.contraptions.wrench.RadialWrenchMenuSubmitPacket.register();
    com.simibubi.create.content.equipment.symmetryWand.SymmetryWandNetworking.register();
    registerItemStorage((toolbox,side)->toolbox.inventory,AllBlockEntityTypes.TOOLBOX);
    AllDisplaySources.register();
    AllDisplayTargets.register();
    com.simibubi.create.content.redstone.displayLink.DisplayLinkMenu.register();
    ServerTickEvents.END_LEVEL_TICK.register(
        com.simibubi.create.content.trains.track.TrackConnectionCleanup::tick);
    com.simibubi.create.content.trains.track.CurvedTrackDestroyPacket.register();
    com.simibubi.create.content.contraptions.piston.PistonSettings.register();
    com.simibubi.create.content.redstone.diodes.BrassDiodeSettings.register();
    com.simibubi.create.content.contraptions.gantry.GantryContraptionUpdatePacket.register();
    com.simibubi.create.content.contraptions.elevator.ElevatorNetworking.register();
    com.simibubi.create.content.trains.station.StationNetworking.register();
    com.simibubi.create.content.trains.signal.SignalEdgePoints.register();
    com.simibubi.create.content.trains.observer.ObserverEdgePoints.register();
    com.simibubi.create.content.trains.TrainManager.register();
    com.simibubi.create.content.trains.entity.TrainRelocationPacket.register();
    com.simibubi.create.content.logistics.packagePort.postbox.PostboxPlacement.register();
    com.simibubi.create.content.trains.signal.SignalMenu.register();
    com.simibubi.create.content.trains.track.TrackPortalCleanup.register();
    com.simibubi.create.content.trains.RailwayGraphManager.register();
    com.simibubi.create.content.trains.graph.sync.RailwayGraphSync.register();
    com.simibubi.create.content.trains.signal.SignalEdgeGroupSync.register();
    ServerTickEvents.END_LEVEL_TICK.register(
        com.simibubi.create.content.contraptions.pulley.PulleyMirrorRestoration::tick);
    com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftMenu.register();
    ServerTickEvents.END_LEVEL_TICK.register(
        com.simibubi.create.content.contraptions.DeferredContraptionRestoration::tick);
    ServerTickEvents.END_LEVEL_TICK.register(
        com.simibubi.create.content.contraptions.ContraptionPassengerHandoff::tick);
    com.simibubi.create.content.contraptions.ContraptionPassengerHandoff.register();
    com.simibubi.create.content.contraptions.chassis.ChassisSettings.register();
    REDSTONE_LINK_NETWORK_HANDLER.register();
    com.simibubi.create.content.redstone.link.LinkHandler.register();
    com.simibubi.create.content.redstone.link.controller.LinkedControllerPackets.register();
    com.simibubi.create.content.redstone.link.controller.LinkedControllerMenu.register();
    registerItemStorage(
        (endpoint, side) -> endpoint.storage(), AllBlockEntityTypes.PORTABLE_STORAGE_INTERFACE);
    registerFluidStorage(
        (endpoint, side) -> endpoint.storage(), AllBlockEntityTypes.PORTABLE_FLUID_INTERFACE);
    com.simibubi.create.content.logistics.packagePort.AllPackagePortTargetTypes.register();
    com.simibubi.create.content.logistics.packagePort.PackagePortMenu.register();
    registerItemStorage((port, side) -> port.itemHandler, AllBlockEntityTypes.PACKAGE_FROGPORT);
    registerItemStorage((port, side) -> port.itemHandler, AllBlockEntityTypes.PACKAGE_POSTBOX);
    com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteraction.register();
    com.simibubi.create.content.kinetics.chainConveyor.ServerChainConveyorHandler.register();
    com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionHandler.register();
    registerItemStorage(
        (ticker, side) -> ticker.receivedPayments.automation, AllBlockEntityTypes.STOCK_TICKER);
    com.simibubi.create.content.logistics.packager.AllInventoryIdentifiers.registerDefaults();
    registerItemStorage((vault, side) -> vault.storage(), AllBlockEntityTypes.ITEM_VAULT);
    registerItemStorage((crate, side) -> crate.inv, AllBlockEntityTypes.CREATIVE_CRATE);
    com.simibubi.create.content.logistics.packager.AllUnpackingHandlers.registerDefaults();
    registerItemStorage((packager, side) -> packager.inventory, AllBlockEntityTypes.PACKAGER);
    registerItemStorage((packager, side) -> packager.inventory, AllBlockEntityTypes.REPACKAGER);
    AllRecipeTypes.register();
    com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe.register();
    com.simibubi.create.content.fluids.potion.BrewingReagentIngredient.register();
    com.simibubi.create.content.processing.recipe.BlockTagIngredient.register();
    com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity.registerAttachments();
    com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes.init();
    com.simibubi.create.content.kinetics.fan.processing.FanProcessing.register();
    com.simibubi.create.content.kinetics.fan.processing.HauntingHorseConversion.register();
    AllSoundEvents.register();
    AllParticleTypes.register();
    AllBlockSpoutingBehaviours.registerDefaults();
    AllOpenPipeEffectHandlers.registerDefaults();
    registerFluidStorage(
        (spout, side) -> side == net.minecraft.core.Direction.DOWN ? null : spout.tank,
        AllBlockEntityTypes.SPOUT);
    registerFluidStorage(
        (drain, side) -> side == net.minecraft.core.Direction.UP ? null : drain.fluidStorage,
        AllBlockEntityTypes.ITEM_DRAIN);
    registerItemStorage(
        (drain, side) -> side != null && side.getAxis().isHorizontal() ? drain.storage(side) : null,
        AllBlockEntityTypes.ITEM_DRAIN);
    registerFluidStorage(
        (pulley, side) ->
            side == null
                    || com.simibubi.create.content.fluids.hosePulley.HosePulleyBlock.hasPipeTowards(
                        pulley.getBlockState(), side)
                ? pulley.storage
                : null,
        AllBlockEntityTypes.HOSE_PULLEY);
    com.simibubi.create.infrastructure.worldgen.AllPlacedFeatures.register();
    registerItemStorage((chute, side) -> chute.itemHandler, AllBlockEntityTypes.CHUTE);
    registerItemStorage((chute, side) -> chute.itemHandler, AllBlockEntityTypes.SMART_CHUTE);
    registerItemStorage(
        (tunnel, side) -> tunnel.storage(side), AllBlockEntityTypes.ANDESITE_TUNNEL);
    registerItemStorage((tunnel, side) -> tunnel.storage(side), AllBlockEntityTypes.BRASS_TUNNEL);
    registerItemStorage((funnel, side) -> funnel.directInput, AllBlockEntityTypes.FUNNEL);
    registerItemStorage((mill, side) -> mill.inventory.automation, AllBlockEntityTypes.MILLSTONE);
    registerItemStorage((depot, side) -> depot.inventory.automation, AllBlockEntityTypes.DEPOT);
    registerItemStorage((station, side) -> station.depotBehaviour.itemHandler, AllBlockEntityTypes.TRACK_STATION);
    registerItemStorage((ejector, side) -> ejector.depotBehaviour.itemHandler, AllBlockEntityTypes.WEIGHTED_EJECTOR);
    registerItemStorage((deployer, side) -> deployer.inventory, AllBlockEntityTypes.DEPLOYER);
    registerItemStorage((basin, side) -> basin.inventory.automation, AllBlockEntityTypes.BASIN);
    registerItemStorage((belt, side) -> belt.storage(side), AllBlockEntityTypes.BELT);
    registerItemStorage(
        (crusher, side) -> crusher.inventory, AllBlockEntityTypes.CRUSHING_WHEEL_CONTROLLER);
    registerItemStorage(
        (crafter, side) -> crafter.storage(), AllBlockEntityTypes.MECHANICAL_CRAFTER);
    registerItemStorage((saw, side) -> saw.storage(side), AllBlockEntityTypes.SAW);
    registerFluidStorage((basin, side) -> basin.fluids.automation, AllBlockEntityTypes.BASIN);
    registerFluidStorage((tank, side) -> tank.storage(), AllBlockEntityTypes.FLUID_TANK);
    registerFluidStorage((tank, side) -> tank.storage(), AllBlockEntityTypes.CREATIVE_FLUID_TANK);
    ServerTickEvents.END_LEVEL_TICK.register(KineticNetworks::tick);
    ServerTickEvents.END_LEVEL_TICK.register(com.simibubi.create.content.kinetics.deployer.DeployerOutputRecovery::tick);
    ServerTickEvents.END_LEVEL_TICK.register(com.simibubi.create.content.kinetics.press.PressingWorldOutputRecovery::tick);
    ServerTickEvents.END_LEVEL_TICK.register(com.simibubi.create.content.processing.basin.BasinOverflowRecovery::tick);
    ServerTickEvents.END_LEVEL_TICK.register(
        com.simibubi.create.content.contraptions.ContraptionHandler::tick);
    ServerTickEvents.END_SERVER_TICK.register(
        server ->
            com.simibubi
                .create
                .content
                .logistics
                .packagerLink
                .GlobalLogisticsManager
                .get(server)
                .tick());
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register(
        (level, chunk, previous, next) -> {
          boolean loaded = next.isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL);
          if (loaded == previous.isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)) return;
          // FULL can be reported inside its own conversion future; defer reads until it has
          // returned.
          for (var entity : java.util.List.copyOf(chunk.getBlockEntities().values()))
            if (entity
                instanceof
                com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity link) {
              var logistics =
                  com.simibubi.create.content.logistics.packagerLink.GlobalLogisticsManager.get(
                      level);
              if (loaded) logistics.scheduleInitialization(link);
              else {
                logistics.cancelInitialization(link);
                link.behaviour.unload();
              }
            }
        });
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
        com.simibubi.create.content.logistics.packagerLink.GlobalLogisticsManager::unload);
    ServerLevelEvents.LOAD.register((server, level) -> TORQUE_PROPAGATOR.onLoadWorld(level));
    ServerLevelEvents.UNLOAD.register((server, level) -> KineticNetworks.unload(level));
    com.simibubi.create.infrastructure.config.AllConfigs.register();
    com.simibubi.create.foundation.utility.ServerSpeedProvider.register();
    LOGGER.info("Create Fabric 26.3 development port initialized");
  }
}
