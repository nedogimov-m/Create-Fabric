package com.simibubi.create;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.actors.seat.SeatMovementBehaviour;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.simibubi.create.content.kinetics.saw.SawMovementBehaviour;

public final class AllMovementBehaviours {
  public static void register() {
    MovementBehaviour.REGISTRY.register(
        AllBlocks.TRAIN_CONTROLS,
        new com.simibubi
            .create
            .content
            .contraptions
            .actors
            .trainControls
            .ControlsMovementBehaviour());
    com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.register(
        AllBlocks.TRAIN_CONTROLS,
        new com.simibubi
            .create
            .content
            .contraptions
            .actors
            .trainControls
            .ControlsInteractionBehaviour());
    com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.register(
        AllBlocks.BLAZE_BURNER,
        new com.simibubi.create.api.behaviour.interaction.ConductorBlockInteractionBehavior
            .BlazeBurner());
    MovementBehaviour.REGISTRY.register(
        AllBlocks.MECHANICAL_BEARING,
        new com.simibubi.create.content.contraptions.bearing.StabilizedBearingMovementBehaviour());
    var doors =
        new com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour();
    var doorInteraction =
        new com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovingInteraction();
    for (var door :
        java.util.List.of(
            AllBlocks.ANDESITE_DOOR,
            AllBlocks.BRASS_DOOR,
            AllBlocks.COPPER_DOOR,
            AllBlocks.TRAIN_DOOR,
            AllBlocks.FRAMED_GLASS_DOOR)) {
      MovementBehaviour.REGISTRY.register(door, doors);
      com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.register(
          door, doorInteraction);
    }
    MovementBehaviour.REGISTRY.register(
        net.minecraft.world.level.block.Blocks.DROPPER,
        new com.simibubi.create.content.contraptions.behaviour.dispenser
            .DropperMovementBehaviour());
    MovementBehaviour.REGISTRY.register(
        net.minecraft.world.level.block.Blocks.DISPENSER,
        new com.simibubi.create.content.contraptions.behaviour.dispenser
            .DispenserMovementBehaviour());
    MovementBehaviour.REGISTRY.register(
        AllBlocks.CONTRAPTION_CONTROLS,
        new com.simibubi
            .create
            .content
            .contraptions
            .actors
            .contraptionControls
            .ContraptionControlsMovement());
    com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.register(
        AllBlocks.CONTRAPTION_CONTROLS,
        new com.simibubi
            .create
            .content
            .contraptions
            .actors
            .contraptionControls
            .ContraptionControlsMovingInteraction());
    var bells = new com.simibubi.create.content.contraptions.behaviour.BellMovementBehaviour();
    MovementBehaviour.REGISTRY.register(net.minecraft.world.level.block.Blocks.BELL, bells);
    MovementBehaviour.REGISTRY.register(AllBlocks.PECULIAR_BELL, bells);
    MovementBehaviour.REGISTRY.register(AllBlocks.DESK_BELL, bells);
    MovementBehaviour.REGISTRY.register(
        AllBlocks.HAUNTED_BELL,
        new com.simibubi.create.content.equipment.bell.HauntedBellMovementBehaviour());
    var campfires =
        new com.simibubi.create.content.contraptions.behaviour.CampfireMovementBehaviour();
    MovementBehaviour.REGISTRY.register(net.minecraft.world.level.block.Blocks.CAMPFIRE, campfires);
    MovementBehaviour.REGISTRY.register(
        net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE, campfires);
    MovementBehaviour.REGISTRY.register(
        AllBlocks.REDSTONE_CONTACT,
        new com.simibubi.create.content.redstone.contact.ContactMovementBehaviour());
    MovementBehaviour.REGISTRY.register(
        AllBlocks.ANDESITE_FUNNEL,
        com.simibubi.create.content.logistics.funnel.FunnelMovementBehaviour.andesite());
    MovementBehaviour.REGISTRY.register(
        AllBlocks.BRASS_FUNNEL,
        com.simibubi.create.content.logistics.funnel.FunnelMovementBehaviour.brass());
    MovementBehaviour.REGISTRY.register(
        AllBlocks.BASIN, new com.simibubi.create.content.processing.basin.BasinMovementBehaviour());
    MovementBehaviour.REGISTRY.register(
        AllBlocks.BLAZE_BURNER,
        new com.simibubi.create.content.processing.burner.BlazeBurnerMovementBehaviour());
    MovementBehaviour.REGISTRY.register(
        AllBlocks.FLUID_TANK,
        new com.simibubi.create.content.fluids.tank.FluidTankMovementBehavior());
    MovementBehaviour.REGISTRY.register(
        AllBlocks.MECHANICAL_ROLLER,
        new com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour());
    var interfaces =
        new com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement();
    MovementBehaviour.REGISTRY.register(AllBlocks.PORTABLE_STORAGE_INTERFACE, interfaces);
    MovementBehaviour.REGISTRY.register(AllBlocks.PORTABLE_FLUID_INTERFACE, interfaces);
    MovementBehaviour.REGISTRY.register(
        AllBlocks.MECHANICAL_HARVESTER,
        new com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour());
    MovementBehaviour.REGISTRY.register(
        AllBlocks.MECHANICAL_PLOUGH,
        new com.simibubi.create.content.contraptions.actors.plough.PloughMovementBehaviour());
    MovementBehaviour.REGISTRY.register(AllBlocks.MECHANICAL_DRILL, new DrillMovementBehaviour());
    MovementBehaviour.REGISTRY.register(AllBlocks.MECHANICAL_SAW, new SawMovementBehaviour());
    MovementBehaviour.REGISTRY.register(
        AllBlocks.DEPLOYER,
        new com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour());
    com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.register(
        AllBlocks.DEPLOYER,
        new com.simibubi.create.content.kinetics.deployer.DeployerMovingInteraction());
    var seats = new SeatMovementBehaviour();
    AllBlocks.SEATS.values().forEach(block -> MovementBehaviour.REGISTRY.register(block, seats));
  }

  private AllMovementBehaviours() {}
}
