package com.simibubi.create.content.contraptions.actors.trainControls;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.redstone.link.controller.ControllerPhysicalInput;
import com.simibubi.create.content.trains.TrainHUD;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Actual acknowledged train controls, native physical key events and source five-tick keepalive.
 */
public final class ControlsHandler {
  private static WeakReference<CarriageContraptionEntity> owner = new WeakReference<>(null);
  private static BlockPos controls;
  private static UUID session;
  private static Object level;
  private static long sequence;
  private static int cooldown;
  public static Set<Integer> currentlyPressed = Set.of();

  public static List<KeyMapping> keys(Minecraft client) {
    return List.of(
        client.options.keyUp,
        client.options.keyDown,
        client.options.keyLeft,
        client.options.keyRight,
        client.options.keyJump,
        client.options.keyShift);
  }

  public static CarriageContraptionEntity getContraption() {
    return owner.get();
  }

  public static BlockPos getControlsPos() {
    return controls;
  }

  public static boolean active() {
    return session != null && owner.get() != null;
  }

  public static void accept(ControlsSessionPacket packet, Minecraft client) {
    if (!packet.active()) {
      if (packet.session().equals(session)) reset(false);
      return;
    }
    if (client.level == null
        || client.player == null
        || client.gui.screen() != null
        || !(client.level.getEntity(packet.entityId()) instanceof CarriageContraptionEntity front)
        || !front.getUUID().equals(packet.body())
        || front.getContraption() == null) return;
    var info = front.getContraption().getBlocks().get(packet.controls());
    if (info == null || !(info.state().getBlock() instanceof ControlsBlock)) return;
    reset(false);
    owner = new WeakReference<>(front);
    controls = packet.controls();
    session = packet.session();
    level = client.level;
    sequence = 0;
    cooldown = 0;
    keys(client).forEach(key -> key.setDown(false));
    client.player.sendOverlayMessage(
        Component.translatable(
            "create.contraption.controls.start_controlling", front.getContraptionName()));
  }

  private static void send(Collection<Integer> buttons, boolean pressed, boolean stop) {
    var front = owner.get();
    if (front == null || session == null) return;
    ClientPlayNetworking.send(
        new ControlsInputPacket(
            front.getId(),
            front.getUUID(),
            controls,
            session,
            sequence++,
            List.copyOf(buttons),
            pressed,
            stop));
  }

  public static void reset(boolean notifyServer) {
    var client = Minecraft.getInstance();
    boolean previous = active();
    if (notifyServer && previous && client.getConnection() != null)
      send(currentlyPressed, false, true);
    owner.clear();
    controls = null;
    session = null;
    sequence = 0;
    cooldown = 0;
    currentlyPressed = Set.of();
    TrainHUD.reset();
    if (client.options != null)
      for (var key : keys(client))
        key.setDown(client.isWindowActive() && ControllerPhysicalInput.down(key));
    if (previous && client.player != null)
      client.player.sendOverlayMessage(
          Component.translatable("create.contraption.controls.stop_controlling"));
  }

  public static void tick(Minecraft client) {
    if (level != client.level) {
      reset(false);
      level = client.level;
    }
    var front = owner.get();
    if (front == null) {
      if (session != null) reset(false);
      return;
    }
    if (client.player == null
        || client.level == null
        || front.level() != client.level
        || front.isRemoved()
        || !client.isWindowActive()
        || client.player.isSpectator()
        || client.gui.screen() != null
        || InputConstants.isKeyDown(InputConstants.KEY_ESCAPE)) {
      reset(true);
      return;
    }
    if (client.isPaused()) return;
    var pressed = new LinkedHashSet<Integer>();
    var mappings = keys(client);
    for (int index = 0; index < mappings.size(); index++)
      if (ControllerPhysicalInput.down(mappings.get(index))) pressed.add(index);
    var released = new LinkedHashSet<>(currentlyPressed);
    released.removeAll(pressed);
    var fresh = new LinkedHashSet<>(pressed);
    fresh.removeAll(currentlyPressed);
    if (!released.isEmpty()) send(released, false, false);
    if (!fresh.isEmpty()) {
      send(fresh, true, false);
      cooldown = 5;
    }
    if (cooldown > 0) cooldown--;
    if (cooldown == 0) {
      send(pressed, true, false);
      cooldown = 5;
    }
    currentlyPressed = Set.copyOf(pressed);
    mappings.forEach(key -> key.setDown(false));
    TrainHUD.tick();
  }

  private ControlsHandler() {}
}
