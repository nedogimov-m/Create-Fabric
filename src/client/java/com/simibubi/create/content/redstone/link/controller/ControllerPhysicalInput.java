package com.simibubi.create.content.redstone.link.controller;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Physical events remain available while remote mode suppresses vanilla KeyMapping.isDown. */
public final class ControllerPhysicalInput {
  private static final Map<Integer, KeyEvent> KEYS = new HashMap<>();
  private static final Map<Integer, MouseButtonInfo> MOUSE = new HashMap<>();

  public static void key(int action, KeyEvent event) {
    if (action == 0) KEYS.remove(event.keycode());
    else KEYS.put(event.keycode(), event);
  }

  public static void mouse(int action, MouseButtonInfo event) {
    if (action == 0) MOUSE.remove(event.button());
    else MOUSE.put(event.button(), event);
  }

  public static boolean down(KeyMapping mapping) {
    if (mapping.isUnbound()) return false;
    var key = InputConstants.getKey(mapping.saveString());
    if (key.getType() == InputConstants.Type.KEYBOARD)
      return InputConstants.isKeyDown(key.getValue());
    if (key.getType() == InputConstants.Type.MOUSE) return MOUSE.containsKey(key.getValue());
    return KEYS.values().stream().anyMatch(mapping::matches);
  }

  public static void clear() {
    KEYS.clear();
    MOUSE.clear();
  }

  public static boolean consumes(KeyEvent event) {
    var client = net.minecraft.client.Minecraft.getInstance();
    return (LinkedControllerClientHandler.MODE != LinkedControllerClientHandler.Mode.IDLE
            || com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler
                .active())
        && client.gui.screen() == null
        && LinkedControllerClientHandler.controls(client).stream()
            .anyMatch(mapping -> mapping.matches(event));
  }

  public static boolean consumes(MouseButtonInfo event) {
    var client = net.minecraft.client.Minecraft.getInstance();
    return (LinkedControllerClientHandler.MODE != LinkedControllerClientHandler.Mode.IDLE
            || com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler
                .active())
        && client.gui.screen() == null
        && LinkedControllerClientHandler.controls(client).stream()
            .anyMatch(
                mapping -> {
                  var key = InputConstants.getKey(mapping.saveString());
                  return key.getType() == InputConstants.Type.MOUSE
                      && key.getValue() == event.button();
                });
  }

  private ControllerPhysicalInput() {}
}
