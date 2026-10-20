package com.simibubi.create.content.trains.station;

import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;

/** Actual native station settings and server-authorized Train assembly/disassembly. */
public final class StationScreen extends Screen {
  private StationNetworking.State state;
  private DoorControl draftDoors;
  private EditBox name;
  private Button save, mode, check, doors;
  private int left, top;
  private int stateRefreshTicks;
  private Button assembly;

  public StationScreen(StationNetworking.State state) {
    super(Component.translatable("block.create.track_station"));
    this.state = state;
    draftDoors = state.doors();
  }

  public BlockPos position() {
    return state.owner().pos();
  }

  public UUID pointId() {
    return state.point();
  }

  public StationNetworking.State snapshot() {
    return state;
  }

  public EditBox nameBox() {
    return name;
  }

  public Vec2 nameCoordinate() {
    return new Vec2(name.getX() + name.getWidth() / 2f, name.getY() + name.getHeight() / 2f);
  }

  public Vec2 doorCoordinate() {
    return center(doors);
  }

  public Vec2 saveCoordinate() {
    return center(save);
  }

  public Vec2 modeCoordinate() {
    return center(mode);
  }

  public Vec2 assemblyCoordinate(){return center(assembly);}

  public Vec2 preflightCoordinate() {
    return center(check);
  }

  private static Vec2 center(Button button) {
    return new Vec2(
        button.getX() + button.getWidth() / 2f, button.getY() + button.getHeight() / 2f);
  }

  @Override
  protected void init() {
    left = (width - 270) / 2;
    top = (height - 186) / 2;
    name =
        addRenderableWidget(
            new EditBox(
                font, left + 12, top + 28, 246, 20, Component.translatable("create.station.name")));
    name.setMaxLength(256);
    name.setValue(state.name());
    doors =
        addRenderableWidget(
            Button.builder(
                    doorLabel(),
                    button -> {
                      var values = DoorControl.values();
                      draftDoors = values[(draftDoors.ordinal() + 1) % values.length];
                      button.setMessage(doorLabel());
                    })
                .bounds(left + 12, top + 54, 158, 20)
                .build());
    save =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("create.station.save"),
                    button -> configure(state.assembling()))
                .bounds(left + 178, top + 54, 80, 20)
                .build());
    mode =
        addRenderableWidget(
            Button.builder(modeLabel(), button -> configure(!state.assembling()))
                .bounds(left + 12, top + 80, 246, 20)
                .build());
    check =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("create.station.check_layout"),
                    button ->
                        ClientPlayNetworking.send(
                            new StationEditPacket(
                                position(),
                                state.point(),
                                state.revision(),
                                StationEditPacket.Action.PREFLIGHT,
                                state.name(),
                                state.assembling(),
                                state.doors())))
                .bounds(left + 12, top + 106, 246, 20)
                .build());
    check.active = state.assembling();
    assembly=addRenderableWidget(Button.builder(
        Component.translatable(state.assembling()?"create.station.assemble_train":"create.station.disassemble_train"),
        button->ClientPlayNetworking.send(new StationEditPacket(position(),state.point(),state.revision(),
            state.assembling()?StationEditPacket.Action.ASSEMBLE:StationEditPacket.Action.DISASSEMBLE,
            state.name(),state.assembling(),state.doors())))
        .bounds(left+12,top+152,246,20).build());
    assembly.active=state.assembling()||state.presentTrain().isPresent()&&state.canDisassemble();
    state.assemblyFailure().ifPresent(failure->assembly.setTooltip(Tooltip.create(failure)));
    setInitialFocus(name);
  }

  private Component doorLabel() {
    return Component.translatable(
        "create.station.doors",
        Component.translatable(
            "create.station.door." + draftDoors.name().toLowerCase(java.util.Locale.ROOT)));
  }

  private Component modeLabel() {
    return Component.translatable(
        state.assembling() ? "create.station.leave_assembly" : "create.station.enter_assembly");
  }

  private void configure(boolean assembling) {
    if (!name.getValue().isBlank())
      ClientPlayNetworking.send(
          new StationEditPacket(
              position(),
              state.point(),
              state.revision(),
              StationEditPacket.Action.CONFIGURE,
              name.getValue(),
              assembling,
              draftDoors));
  }

  public void apply(StationNetworking.State snapshot) {
    if (!snapshot.owner().equals(state.owner())
        || !snapshot.point().equals(state.point())
        || snapshot.revision() < state.revision()) return;
    boolean sameConfiguration = snapshot.revision() == state.revision()
        && snapshot.name().equals(state.name())
        && snapshot.assembling() == state.assembling()
        && snapshot.doors() == state.doors();
    state = snapshot;
    if (sameConfiguration && name != null && assembly != null) {
      // Passive snapshots refresh real Train availability, not unfinished text/door input.
      // Rebuilding here discarded the draft, cursor and focus every twenty native ticks.
      check.active = state.assembling();
      assembly.active = state.assembling()
          || state.presentTrain().isPresent() && state.canDisassemble();
      assembly.setTooltip(state.assemblyFailure().map(Tooltip::create).orElse(null));
      return;
    }
    draftDoors = state.doors();
    rebuildWidgets();
  }

  @Override
  public void tick() {
    if(minecraft.player!=null&&minecraft.level!=null&&stateRefreshTicks++%20==0)
      ClientPlayNetworking.send(new StationEditPacket(position(),state.point(),state.revision(),StationEditPacket.Action.REFRESH,state.name(),state.assembling(),state.doors()));
    if (minecraft.player == null
        || minecraft.level == null
        || !minecraft.level.dimension().equals(state.owner().dimension())
        || !minecraft.player.isWithinBlockInteractionRange(position(), 1)
        || !LoadedLevelAccess.isLoaded(minecraft.level, position())
        || !(LoadedLevelAccess.blockEntity(minecraft.level, position())
            instanceof StationBlockEntity station)
        || !station.targeting().id().equals(state.point())) onClose();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
    gui.fill(0, 0, width, height, 0x80101010);
    gui.fill(left, top, left + 270, top + 186, 0xF03E3831);
    gui.centeredText(font, title, left + 135, top + 10, 0xFFF0D9B5);
    gui.centeredText(
        font,
        Component.translatable(
            state.assemblyStatus()==StationTrainAssembly.Status.NOT_CHECKED
                ?"create.station.preflight."+state.preflight().name().toLowerCase(java.util.Locale.ROOT)
                :"create.station.train_operation."+state.assemblyStatus().name().toLowerCase(java.util.Locale.ROOT)),
        left + 135,
        top + 133,
        state.preflight() == StationAssemblyPreflight.Status.READY_FOR_CARRIAGE_CAPTURE
            ? 0xFFB5EB9A
            : 0xFFE9D5AA);
    if (!state.accepted())
      gui.centeredText(
          font,
          Component.translatable("create.station.changed_retry"),
          left + 135,
          top + 176,
          0xFFFFB3A9);
    super.extractRenderState(gui, mouseX, mouseY, partial);
  }
}
