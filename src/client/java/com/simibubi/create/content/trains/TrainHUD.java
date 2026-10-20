package com.simibubi.create.content.trains;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.actors.trainControls.*;
import com.simibubi.create.content.redstone.link.controller.ControllerPhysicalInput;
import com.simibubi.create.content.trains.entity.Carriage;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.AngleHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Original speed/throttle/prompt/direction HUD extracted through native GUI/Vulkan pipelines. */
public final class TrainHUD {
  private static final Identifier WIDGETS = Create.asResource("textures/gui/widgets.png");
  private static final Identifier DIRECTION = Create.asResource("textures/gui/train_direction.png");
  private static final LerpedFloat SPEED = LerpedFloat.linear(),
      THROTTLE = LerpedFloat.linear(),
      PROMPT = LerpedFloat.linear();
  private static Double editedThrottle;
  private static int throttleCooldown = 5, honkCooldown = 5;
  private static boolean usedToHonk;
  public static Component currentPrompt;
  public static boolean currentPromptShadow;
  public static int promptKeepAlive;

  private static Carriage carriage() {
    var front = ControlsHandler.getContraption();
    return front == null ? null : front.getCarriage();
  }

  public static void reset() {
    editedThrottle = null;
    throttleCooldown = honkCooldown = 5;
    usedToHonk = false;
    currentPrompt = null;
    promptKeepAlive = 0;
    SPEED.setValue(0);
    THROTTLE.setValue(0);
    PROMPT.setValue(0);
  }

  public static void tick() {
    if (promptKeepAlive > 0) promptKeepAlive--;
    else currentPrompt = null;
    var client = Minecraft.getInstance();
    PROMPT.chase(currentPrompt == null ? 0 : client.font.width(currentPrompt) + 17, .5, Chaser.EXP);
    PROMPT.tickChaser();
    var carriage = carriage();
    if (carriage == null) return;
    var train = carriage.train;
    double top = train.maxSpeed() * TrainConfig.client().manualTrainSpeedModifier();
    double speed = Math.clamp(Math.abs(train.speed) / Math.max(.000001, top) + .05, 0, 1);
    SPEED.chase((int) (speed * 18) / 18f, .5, Chaser.EXP);
    SPEED.tickChaser();
    THROTTLE.chase(editedThrottle == null ? train.throttle : editedThrottle, .75, Chaser.EXP);
    THROTTLE.tickChaser();
    boolean sprint = ControllerPhysicalInput.down(client.options.keySprint);
    if (sprint && honkCooldown-- <= 0) {
      train.determineHonk(client.level);
      if (train.lowHonk != null) {
        ClientPlayNetworking.send(new HonkPacket.Serverbound(train, true));
        honkCooldown = 5;
        usedToHonk = true;
      }
    }
    if (!sprint && usedToHonk) {
      ClientPlayNetworking.send(new HonkPacket.Serverbound(train, false));
      honkCooldown = 0;
      usedToHonk = false;
    }
    if (editedThrottle == null) return;
    if (Mth.equal(editedThrottle, train.throttle)) {
      editedThrottle = null;
      throttleCooldown = 5;
      return;
    }
    if (throttleCooldown-- <= 0) {
      ClientPlayNetworking.send(new TrainHUDUpdatePacket.Serverbound(train, editedThrottle));
      throttleCooldown = 5;
    }
  }

  public static boolean onScroll(double delta) {
    var carriage = carriage();
    var client = Minecraft.getInstance();
    if (!ControlsHandler.active() || carriage == null || client.gui.screen() != null || delta == 0)
      return false;
    double current = editedThrottle == null ? carriage.train.throttle : editedThrottle;
    editedThrottle = Math.clamp(current + Math.signum(delta) / 18f, 1 / 18f, 1);
    return true;
  }

  private static void widget(
      GuiGraphicsExtractor gui, int x, int y, float u, float v, int width, int height) {
    if (width > 0 && height > 0)
      gui.blit(RenderPipelines.GUI_TEXTURED, WIDGETS, x, y, u, v, width, height, 256, 256);
  }

  public static void render(GuiGraphicsExtractor gui, DeltaTracker delta) {
    var client = Minecraft.getInstance();
    var carriage = carriage();
    var front = ControlsHandler.getContraption();
    var local = ControlsHandler.getControlsPos();
    if (carriage == null
        || front == null
        || local == null
        || client.player == null
        || client.gameMode == null
        || client.getCameraEntity() == null
        || client.gui.hud.isHidden()
        || client.gameMode.isSpectator()
        || client.gui.screen() != null) return;
    float partial = delta.getGameTimeDeltaPartialTick(false);
    int x = gui.guiWidth() / 2 - 91, y = gui.guiHeight() - 29;
    widget(gui, x - 2, y + 1, 0, 200, 186, 7);
    widget(gui, x, y, 0, 190, 182, 5);
    widget(gui, x, y, 0, 185, (int) (182 * SPEED.getValue(partial)), 5);
    int covered = (int) (182 * (1 - THROTTLE.getValue(partial))), uncovered = 182 - covered;
    widget(gui, x + uncovered, y, uncovered, 195, covered, 5);
    widget(gui, x + Math.max(1, uncovered) - 3, y - 2, 0, 209, 6, 9);
    int prompt = (int) PROMPT.getValue(partial);
    if (prompt > 1) {
      int px = x + 91 - prompt / 2, py = y - 27;
      widget(gui, px - 3, py, 8, 209, 3, 16);
      widget(gui, px + prompt, py, 11, 209, 3, 16);
      widget(gui, px, py, 128 - prompt / 2f, 230, prompt, 16);
      if (currentPrompt != null && client.font.width(currentPrompt) < prompt - 10)
        gui.text(
            client.font,
            currentPrompt,
            x + 91 - client.font.width(currentPrompt) / 2,
            py + 4,
            0xFF544D45,
            currentPromptShadow);
    }
    widget(gui, x + 77, y - 20, 77, 165, 28, 20);
    var info = front.getContraption().getBlocks().get(local);
    boolean inverted =
        info != null
            && info.state().hasProperty(ControlsBlock.FACING)
            && info.state().getValue(ControlsBlock.FACING)
                != front.getInitialOrientation().getCounterClockWise();
    boolean reversing = ControlsHandler.currentlyPressed.contains(1);
    inverted ^= reversing;
    float angle =
        AngleHelper.getShortestAngleDiff(client.getCameraEntity().getYRot(), front.yaw)
            + (inverted ? -90 : 90);
    if (Math.abs(angle) < 60) angle = 0;
    int steer =
        (ControlsHandler.currentlyPressed.contains(2) ? -45 : 0)
            + (ControlsHandler.currentlyPressed.contains(3) ? 45 : 0);
    angle += reversing ? -steer : steer;
    int frame = Math.floorMod(Math.round(angle / 22.5f), 16);
    gui.pose().pushMatrix();
    gui.pose().translate(x + 91, y - 9);
    gui.pose().scale(.925f * 1.125f);
    gui.blit(RenderPipelines.GUI_TEXTURED, DIRECTION, -8, -8, 0, frame * 16, 16, 16, 16, 256);
    gui.pose().popMatrix();
  }

  private TrainHUD() {}
}
