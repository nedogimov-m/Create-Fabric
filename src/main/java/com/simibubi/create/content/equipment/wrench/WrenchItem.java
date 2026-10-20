package com.simibubi.create.content.equipment.wrench;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelStructuralBlock;
import com.simibubi.create.content.logistics.depot.DepotBlock;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class WrenchItem extends Item {
  public WrenchItem(Properties properties) {
    super(properties.stacksTo(1));
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    var level = context.getLevel();
    var player = context.getPlayer();
    if (player == null || !player.mayBuild() || !level.mayInteract(player, context.getClickedPos()))
      return InteractionResult.PASS;
    var clicked = context.getClickedPos();
    var clickedState = level.getBlockState(clicked);
    var pos =
        clickedState.getBlock() instanceof WaterWheelStructuralBlock
            ? WaterWheelStructuralBlock.master(level, clicked, clickedState)
            : clicked;
    if (pos == null) return InteractionResult.PASS;
    var state = level.getBlockState(pos);
    if(!player.isShiftKeyDown()){
      var bracket=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(level,pos,com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour.TYPE);
      if(bracket!=null&&bracket.isBracketPresent()){
        if(level.isClientSide())return InteractionResult.SUCCESS;
        var removed=bracket.removeBracket(false);
        if(removed!=null&&!player.isCreative())player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(removed.getBlock()),Prediction.SERVER_ONLY);
        if(state.is(com.simibubi.create.AllBlocks.FLUID_PIPE)){
          var axis=com.simibubi.create.content.fluids.FluidPropagator.getStraightPipeAxis(state);
          var next=com.simibubi.create.AllBlocks.FLUID_PIPE.updateBlockState(state,axis==null?Direction.UP:Direction.get(Direction.AxisDirection.POSITIVE,axis),null,level,pos);
          if(next!=state)level.setBlockAndUpdate(pos,next);
        }
        return InteractionResult.SUCCESS;
      }
    }
    if(state.getBlock() instanceof com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock assembler)
      return player.isShiftKeyDown()?assembler.onSneakWrenched(state,context):assembler.onWrenched(state,context);
    if(!player.isShiftKeyDown()&&state.getBlock() instanceof com.simibubi.create.content.redstone.rail.ControllerRailBlock rail)
      return rail.onWrenched(state,context);
    if (state.getBlock() instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock copycat)
      return player.isShiftKeyDown() ? copycat.onSneakWrenched(state, context) : copycat.onWrenched(state, context);
    if (!player.isShiftKeyDown() && state.getBlock() instanceof com.simibubi.create.content.redstone.RoseQuartzLampBlock lamp)
      return lamp.onWrenched(state, context);
    if (state.getBlock() instanceof com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock cog)
      return player.isShiftKeyDown() ? cog.onSneakWrenched(state, context) : cog.onWrenched(state, context);
    if (player.isShiftKeyDown() && state.getBlock() instanceof com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock shaft)
      return shaft.onSneakWrenched(state, context);
    if (!player.isShiftKeyDown() && state.getBlock() instanceof com.simibubi.create.content.kinetics.fan.NozzleBlock)
      return InteractionResult.FAIL;
    if (state.getBlock() instanceof com.simibubi.create.content.trains.track.TrackBlock track)
      return player.isShiftKeyDown()
          ? track.onSneakWrenched(state, context)
          : track.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock door)
      return door.onWrenched(state, context);
    if (state.getBlock()
            instanceof com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlock
        && !player.isShiftKeyDown()) return InteractionResult.PASS;
    if (state.getBlock()
        instanceof com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock panel)
      return panel.onWrenched(context);
    if (state.is(com.simibubi.create.AllBlocks.STEAM_WHISTLE_EXTENSION)) {
      if (player.isShiftKeyDown()) {
        if (context.getClickLocation().y >= pos.getY() + 0.5
            && state.getValue(
                    com.simibubi.create.content.decoration.steamWhistle.WhistleExtenderBlock.SHAPE)
                != com.simibubi
                    .create
                    .content
                    .decoration
                    .steamWhistle
                    .WhistleExtenderBlock
                    .WhistleExtenderShape
                    .SINGLE) {
          if (!level.isClientSide())
            level.setBlockAndUpdate(
                pos,
                state.setValue(
                    com.simibubi.create.content.decoration.steamWhistle.WhistleExtenderBlock.SHAPE,
                    com.simibubi
                        .create
                        .content
                        .decoration
                        .steamWhistle
                        .WhistleExtenderBlock
                        .WhistleExtenderShape
                        .SINGLE));
          return InteractionResult.SUCCESS;
        }
      } else {
        var root =
            com.simibubi.create.content.decoration.steamWhistle.WhistleExtenderBlock.findRoot(
                level, pos);
        if (root == null
            || !com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level, root)
                .is(com.simibubi.create.AllBlocks.STEAM_WHISTLE)) return InteractionResult.PASS;
        return useOn(
            new UseOnContext(
                player,
                context.getHand(),
                new net.minecraft.world.phys.BlockHitResult(
                    context.getClickLocation(),
                    context.getClickedFace(),
                    root,
                    context.isInside())));
      }
    }
    if (state.getBlock() instanceof com.simibubi.create.content.fluids.drain.ItemDrainBlock
        && !player.isShiftKeyDown()) return InteractionResult.PASS;
    if (!player.isShiftKeyDown()
        && state.getBlock()
            instanceof
            com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock
                sequencer) return sequencer.onWrenched(state, context);
    if (!player.isShiftKeyDown()
        && state.getBlock()
            instanceof com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock
        && state.getValue(
                com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.STATE)
            != com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.PistonState
                .RETRACTED) return InteractionResult.PASS;
    if (!player.isShiftKeyDown()
        && state.getBlock()
            instanceof
            com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock chassis)
      return chassis.onWrenched(state, context);
    if (!player.isShiftKeyDown()
        && state.getBlock()
            instanceof com.simibubi.create.content.redstone.link.RedstoneLinkBlock link)
      return link.onWrenched(state, context);
    if (!player.isShiftKeyDown()
        && state.getBlock() instanceof com.simibubi.create.content.trains.signal.SignalBlock signal)
      return signal.onWrenched(state, context);
    if (!player.isShiftKeyDown()
        && state.getBlock()
            instanceof
            com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock sensor)
      return sensor.onWrenched(state, context);
    if (!(state.getBlock() instanceof com.simibubi.create.content.redstone.rail.ControllerRailBlock
        || state.getBlock() instanceof com.simibubi.create.foundation.block.WrenchableDirectionalBlock
        || state.getBlock() instanceof com.simibubi.create.content.decoration.TrainTrapdoorBlock
        || state.getBlock() instanceof com.simibubi.create.content.decoration.MetalLadderBlock
        || state.getBlock() instanceof com.simibubi.create.content.decoration.MetalScaffoldingBlock
        || player.isShiftKeyDown() && (state.is(com.simibubi.create.AllBlocks.ANDESITE_BARS) || state.is(com.simibubi.create.AllBlocks.BRASS_BARS) || state.is(com.simibubi.create.AllBlocks.COPPER_BARS))
        || state.getBlock() instanceof com.simibubi.create.content.redstone.RoseQuartzLampBlock
        || state.getBlock() instanceof com.simibubi.create.content.trains.observer.TrackObserverBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock
        || state.getBlock() instanceof com.simibubi.create.content.trains.signal.SignalBlock
        || state.getBlock() instanceof com.simibubi.create.content.trains.station.StationBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.trains.bogey.AbstractBogeyBlock<?>
        || state.getBlock() instanceof com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock
        || state.getBlock() instanceof com.simibubi.create.content.decoration.placard.PlacardBlock
        || state.getBlock() instanceof com.simibubi.create.content.decoration.girder.GirderBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.contraptions.piston.PistonExtensionPoleBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock
        || state.getBlock() instanceof com.simibubi.create.content.contraptions.chassis.StickerBlock
        || state.getBlock() instanceof com.simibubi.create.content.contraptions.bearing.SailBlock
        || state.getBlock() instanceof com.simibubi.create.content.redstone.link.RedstoneLinkBlock
        || state.getBlock() instanceof com.simibubi.create.content.redstone.deskBell.DeskBellBlock
        || state.getBlock() instanceof com.simibubi.create.content.decoration.encasing.CasingBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.redstone.diodes.AbstractDiodeBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.redstone.contact.RedstoneContactBlock
        || state.getBlock()
            instanceof
            com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.contraptions.actors.AttachedActorBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.logistics.tableCloth.TableClothBlock
        || state.getBlock()
            instanceof
            com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock
        || state.getBlock() instanceof com.simibubi.create.content.logistics.vault.ItemVaultBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.logistics.crate.CreativeCrateBlock
        || state.getBlock() instanceof com.simibubi.create.content.logistics.packager.PackagerBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.logistics.chute.AbstractChuteBlock
        || state.getBlock() instanceof com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock
        || state.getBlock() instanceof com.simibubi.create.content.kinetics.fan.NozzleBlock
        || state.getBlock() instanceof IRotate
        || state.getBlock()
            instanceof com.simibubi.create.content.decoration.steamWhistle.WhistleBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.decoration.steamWhistle.WhistleExtenderBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock
        || state.getBlock() instanceof com.simibubi.create.content.logistics.itemHatch.ItemHatchBlock
        || state.getBlock() instanceof DepotBlock
        || state.getBlock() instanceof BasinBlock
        || state.getBlock() instanceof BlazeBurnerBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock
        || state.getBlock() instanceof com.simibubi.create.content.fluids.spout.SpoutBlock
        || state.getBlock() instanceof com.simibubi.create.content.fluids.drain.ItemDrainBlock
        || state.getBlock() instanceof com.simibubi.create.content.fluids.tank.FluidTankBlock
        || state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock
        || state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.AxisPipeBlock
        || state.getBlock()
            instanceof com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock))
      return InteractionResult.PASS;
    if (player.isShiftKeyDown()) {
      if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
      if (!PlayerBlockBreakEvents.BEFORE
          .invoker()
          .beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos))) {
        return InteractionResult.FAIL;
      }
      if (com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.isPiston(state)
          || state.getBlock()
              instanceof com.simibubi.create.content.contraptions.piston.PistonExtensionPoleBlock) {
        if (!com.simibubi.create.content.contraptions.piston.PistonStructure.canBreak(
            level, pos, state)) return InteractionResult.FAIL;
        com.simibubi.create.content.contraptions.piston.PistonStructure.destroyConnectedParts(
            level, pos, state, player);
      }
      if (!player.isCreative()) {
        Block.getDrops(
                state, server, pos, level.getBlockEntity(pos), player, context.getItemInHand())
            .forEach(
                stack ->
                    player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY));
      }
      var removedEntity = level.getBlockEntity(pos);
      if (removedEntity
          instanceof
          com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity conveyor)
        conveyor.refundChains(player);
      level.destroyBlock(pos, false);
      PlayerBlockBreakEvents.AFTER
          .invoker()
          .afterBlockBreak(level, player, pos, state, removedEntity);
      level.playSound(
          null,
          pos,
          AllSoundEvents.WRENCH_REMOVE,
          net.minecraft.sounds.SoundSource.BLOCKS,
          1,
          0.75f);
      level.playSound(
          null,
          pos,
          AllSoundEvents.WRENCH_REMOVE_HIT,
          net.minecraft.sounds.SoundSource.BLOCKS,
          1,
          0.75f);
      return InteractionResult.SUCCESS;
    }
    if (state.getBlock() instanceof com.simibubi.create.content.fluids.tank.FluidTankBlock) {
      if (!level.isClientSide()
          && level.getBlockEntity(pos)
              instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank)
        tank.toggleWindows();
      return InteractionResult.SUCCESS;
    }
    if (state.getBlock() instanceof com.simibubi.create.content.fluids.pump.PumpBlock) {
      if (!level.isClientSide())
        level.setBlockAndUpdate(
            pos,
            state.setValue(
                com.simibubi.create.content.fluids.pump.PumpBlock.FACING,
                state
                    .getValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING)
                    .getOpposite()));
      return InteractionResult.SUCCESS;
    }
    if (state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock
        || state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.AxisPipeBlock) {
      if (level.isClientSide()) return InteractionResult.SUCCESS;
      var old = com.simibubi.create.content.fluids.FluidPropagator.getPipe(level, pos);
      var flows = old == null ? null : old.interfaces;
      BlockState next;
      if (state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.AxisPipeBlock) {
        next =
            com.simibubi.create.AllBlocks.FLUID_PIPE.getAxisState(
                state.getValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS));
      } else if (state.getBlock()
          instanceof com.simibubi.create.content.fluids.pipes.EncasedPipeBlock) {
        next = com.simibubi.create.AllBlocks.FLUID_PIPE.defaultBlockState();
        for (var side : Direction.values())
          next =
              next.setValue(
                  com.simibubi.create.content.fluids.pipes.FluidPipeBlock.PROPERTY_BY_DIRECTION.get(
                      side),
                  state.getValue(
                      com.simibubi.create.content.fluids.pipes.FluidPipeBlock.PROPERTY_BY_DIRECTION
                          .get(side)));
      } else {
        var axis = com.simibubi.create.content.fluids.FluidPropagator.getStraightPipeAxis(state);
        if (axis == null) {
          var local =
              context
                  .getClickLocation()
                  .subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
          axis =
              java.util.Arrays.stream(Direction.Axis.values())
                  .filter(a -> a != context.getClickedFace().getAxis())
                  .max(
                      java.util.Comparator.comparingDouble(
                          a -> Math.abs(a.choose(local.x, local.y, local.z))))
                  .orElse(Direction.Axis.Y);
        }
        if (axis == context.getClickedFace().getAxis()) return InteractionResult.PASS;
        if(flows!=null&&flows.values().stream().anyMatch(connection->connection!=null&&connection.hasFlow()))com.simibubi.create.foundation.advancement.GameplayAdvancements.award(player,"glass_pipe");
        next =
            com.simibubi.create.AllBlocks.GLASS_FLUID_PIPE
                .defaultBlockState()
                .setValue(com.simibubi.create.content.fluids.pipes.AxisPipeBlock.AXIS, axis);
      }
      next =
          next.setValue(
              BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
      level.setBlockAndUpdate(pos, next);
      var pipe = com.simibubi.create.content.fluids.FluidPropagator.getPipe(level, pos);
      if (pipe != null && flows != null) {
        pipe.interfaces = flows;
        pipe.wipePressure();
      }
      return InteractionResult.SUCCESS;
    }
    if (state.getBlock()
        instanceof com.simibubi.create.content.decoration.encasing.CasingBlock casing)
      return casing.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.redstone.diodes.AbstractDiodeBlock diode)
      return diode.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.redstone.contact.RedstoneContactBlock contact)
      return contact.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.logistics.vault.ItemVaultBlock vault)
      return vault.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock observer)
      return observer.onWrenched(state, context);
    if (state.getBlock() instanceof com.simibubi.create.content.logistics.chute.ChuteBlock chute)
      return chute.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock tunnel)
      return tunnel.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock funnel)
      return funnel.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.contraptions.actors.AttachedActorBlock)
      return InteractionResult.FAIL;
    if (state.getBlock()
        instanceof com.simibubi.create.content.trains.bogey.AbstractBogeyBlock<?> bogey)
      return bogey.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock nixie)
      return nixie.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.decoration.placard.PlacardBlock placard)
      return placard.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock girder)
      return girder.onWrenched(state, context);
    if (state.getBlock()
        instanceof com.simibubi.create.content.decoration.girder.GirderBlock girder)
      return girder.onWrenched(state, context);
    if (state.getBlock() instanceof LargeWaterWheelBlock) return InteractionResult.PASS;
    if (state.getBlock() instanceof com.simibubi.create.content.kinetics.deployer.DeployerBlock
        && com.simibubi.create.content.kinetics.deployer.DeployerBlock.onHand(
            state, pos, context.getClickLocation())) {
      if (!level.isClientSide()
          && level.getBlockEntity(pos)
              instanceof com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity deployer)
        deployer.changeMode();
      return InteractionResult.SUCCESS;
    }
    if (state.getBlock()
        instanceof com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock crafter)
      return crafter.onWrenched(context);
    if (state.getBlock() instanceof com.simibubi.create.content.kinetics.belt.BeltBlock belt) {
      var slicing =
          com.simibubi.create.content.kinetics.belt.BeltSlicer.useWrench(
              level,
              pos,
              player,
              new net.minecraft.world.phys.BlockHitResult(
                  context.getClickLocation(), context.getClickedFace(), pos, context.isInside()));
      return slicing == InteractionResult.PASS
          ? belt.onWrenched(level, pos, state, player)
          : slicing;
    }
    if (state.getBlock() instanceof BasinBlock) {
      if (!level.isClientSide()
          && level.getBlockEntity(pos)
              instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity basin)
        basin.onWrenched(context.getClickedFace());
      return InteractionResult.SUCCESS;
    }
    if (!level.isClientSide()
        && level.getBlockEntity(pos)
            instanceof
            com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity clockwork
        && clockwork.isRunning()
        && !clockwork.disassemble()) return InteractionResult.FAIL;
    BlockState rotated = rotate(state, context.getClickedFace());
    if (!level.isClientSide()
        && level.getBlockEntity(pos)
            instanceof
            com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity bearing
        && bearing.isRunning()
        && !bearing.disassemble()) return InteractionResult.FAIL;
    if (!rotated.canSurvive(level, pos)) return InteractionResult.PASS;
    if (!level.isClientSide()) {
      level.setBlock(pos, Block.updateFromNeighbourShapes(rotated, level, pos), Block.UPDATE_ALL);
      level.playSound(
          null,
          pos,
          AllSoundEvents.WRENCH_ROTATE,
          net.minecraft.sounds.SoundSource.BLOCKS,
          0.25f,
          1.25f);
    }
    return InteractionResult.SUCCESS;
  }

  public static BlockState rotate(BlockState state, Direction face) {
    if (face.getAxis().isVertical()
        && (state.getBlock() instanceof com.simibubi.create.content.decoration.TrainTrapdoorBlock
            || state.getBlock() instanceof com.simibubi.create.content.decoration.MetalLadderBlock
            || state.getBlock() instanceof com.simibubi.create.content.logistics.itemHatch.ItemHatchBlock))
      return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
          state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise());
    if (face.getAxis().isVertical()
        && state.getBlock()
            instanceof com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock)
      return state.setValue(
          BlockStateProperties.HORIZONTAL_FACING,
          state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise());
    if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) && face.getAxis().isVertical())
      return state.cycle(BlockStateProperties.HORIZONTAL_AXIS);
    if (state.getBlock()
        instanceof com.simibubi.create.content.decoration.steamWhistle.WhistleBlock)
      return state.cycle(com.simibubi.create.content.decoration.steamWhistle.WhistleBlock.SIZE);
    if (state.getBlock()
            instanceof com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock
        && face.getAxis().isVertical())
      return state.cycle(
          net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
    if (state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock)
      return com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock.wrenchRotation(
          state, face);
    if (face.getAxis().isVertical()
        && state.getBlock()
            instanceof com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock)
      return state.setValue(
          com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock.FACING,
          state
              .getValue(
                  com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock.FACING)
              .getClockWise());
    if (face.getAxis().isVertical()
        && state.getBlock()
            instanceof com.simibubi.create.content.kinetics.base.HorizontalKineticBlock)
      return state.setValue(
          BlockStateProperties.HORIZONTAL_FACING,
          state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise());
    if (state.getBlock()
        instanceof com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock) {
      var facing = state.getValue(BlockStateProperties.FACING);
      var next = state;
      if (facing.getAxis() == face.getAxis())
        return state.cycle(
            com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock
                .AXIS_ALONG_FIRST_COORDINATE);
      next = next.setValue(BlockStateProperties.FACING, facing.getClockWise(face.getAxis()));
      if (face.getAxis().isVertical())
        next =
            next.cycle(
                com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock
                    .AXIS_ALONG_FIRST_COORDINATE);
      return next;
    }
    if (state.getBlock() instanceof com.simibubi.create.content.kinetics.saw.SawBlock) {
      return com.simibubi.create.content.kinetics.saw.SawBlock.wrenchRotation(state, face);
    }
    if (state.hasProperty(BlockStateProperties.AXIS)) {
      Direction normal =
          Direction.get(
              Direction.AxisDirection.POSITIVE, state.getValue(BlockStateProperties.AXIS));
      if (normal.getAxis() != face.getAxis()) {
        return state.setValue(
            BlockStateProperties.AXIS, normal.getClockWise(face.getAxis()).getAxis());
      }
    } else if (state.hasProperty(BlockStateProperties.FACING)) {
      Direction facing = state.getValue(BlockStateProperties.FACING);
      if (facing.getAxis() != face.getAxis())
        return state.setValue(BlockStateProperties.FACING, facing.getClockWise(face.getAxis()));
    }
    return state;
  }
}
