// Ponder1.0.82 scheduler/navigation/ordering controller, MIT; native26.3 frontend adaptation.
package net.createmod.ponder.foundation.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.api.registration.StoryBoardEntry.SceneOrderingType;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Real compiled scenes, replay-before-backward-seek, local simulation and native captured viewport. */
public final class PonderUI extends AbstractPonderScreen {
    public static int ponderTicks;
    public static float ponderPartialTicksPaused;
    private final List<PonderScene> scenes;
    private final ItemStack stack;
    private final List<PonderTag> tags;
    private final LerpedFloat lazyIndex = LerpedFloat.linear().startWithValue(0);
    private final LerpedFloat nextUp = LerpedFloat.linear().startWithValue(0).chase(0, .4f, LerpedFloat.Chaser.EXP);
    private final LerpedFloat finishingFlash = LerpedFloat.linear().startWithValue(0).chase(0, .1f, LerpedFloat.Chaser.EXP);
    private final List<PonderButton> buttons = new ArrayList<>();
    private final List<PonderButton> tagButtons = new ArrayList<>();
    private final List<LerpedFloat> tagFades = new ArrayList<>();
    private final LerpedFloat fadeIn = LerpedFloat.linear().startWithValue(0).chase(1, .1f, LerpedFloat.Chaser.EXP);
    private int index, skipCooling, extendedTickLength, extendedTickTimer, nextUpWarmup, finishingFlashWarmup;
    private boolean identifyMode, userViewMode, paused;
    private PonderTag referredToByTag;
    private ItemStack hoveredTooltipItem = ItemStack.EMPTY;
    private BlockPos hoveredBlockPos, copiedBlockPos;
    private PonderButton left, right, replay, scan, slowMode, userMode;
    private PonderProgressBar progress;
    private int viewportX, viewportY, viewportWidth, viewportHeight;
    private int lastMouseX, lastMouseY;
    private boolean rotatingScene;

    public static PonderUI of(Identifier id) { return new PonderUI(PonderIndex.getSceneAccess().compile(id)); }
    public static PonderUI of(ItemStack item) { return of(BuiltInRegistries.ITEM.getKey(item.getItem())); }
    public static PonderUI of(ItemStack item, PonderTag tag) { var ui = of(item); ui.referredToByTag = tag; return ui; }
    public PonderUI(List<PonderScene> scenes) { this(scenes, Minecraft.getInstance().gui.screen()); }
    public PonderUI(List<PonderScene> scenes, Screen parent) {
        super(text("pondering"), parent);
        if (scenes.isEmpty()) throw new IllegalArgumentException("No registered Ponder scenes for this subject");
        var location = scenes.getFirst().getLocation();
        stack = BuiltInRegistries.ITEM.getValue(location).getDefaultInstance();
        tags = List.copyOf(PonderIndex.getTagAccess().getTags(location));
        this.scenes = new ArrayList<>(orderedOrRegistered(scenes));
        getActiveScene().begin();
    }

    @Override protected void init() {
        rotatingScene = false; setDragging(false);
        buttons.clear(); tagButtons.clear(); tagFades.clear();
        super.init();
        viewportX = 0; viewportY = 0;
        viewportWidth = Math.max(1, width); viewportHeight = Math.max(1, height);
        int footer = height - 51, center = width / 2;
        scan = control(center - 96, footer, "identify", PonderGuiTextures.ICON_PONDER_IDENTIFY, this::toggleIdentify)
            .withShortcut(minecraft.options.keyDrop);
        left = control(center - 38, footer, "previous", PonderGuiTextures.ICON_PONDER_LEFT, () -> scroll(false))
            .withShortcut(minecraft.options.keyLeft);
        control(center - 10, footer, "close", PonderGuiTextures.ICON_PONDER_CLOSE, this::onClose)
            .withShortcut(minecraft.options.keyInventory);
        right = control(center + 18, footer, "next", PonderGuiTextures.ICON_PONDER_RIGHT, () -> scroll(true))
            .withShortcut(minecraft.options.keyRight);
        replay = control(center + 76, footer, "replay", PonderGuiTextures.ICON_PONDER_REPLAY, this::replay)
            .withShortcut(minecraft.options.keyDown);
        slowMode = control(width - 51, footer, "slow_text", PonderGuiTextures.ICON_PONDER_SLOW_MODE,
            () -> setComfyReadingEnabled(!isComfyReadingEnabled()));
        if (PonderIndex.editingModeActive()) buttons.add(userMode = button(width - 81, footer, 20, text("user_view"),
            () -> userViewMode = !userViewMode).showing(PonderGuiTextures.ICON_PONDER_USER_MODE)
            .hint(text("user_view")));
        progress = addRenderableWidget(new PonderProgressBar(this, Math.max(32, center - 110), height - 28, Math.max(32, Math.min(220, width - 64)), 16));
        for (int i = 0; i < tags.size(); i++) {
            var tag = tags.get(i);
            int y = 81 + i * 30;
            if (y + 20 > height - 65) break;
            var button = button(31, y, 20, Component.literal(tag.getTitle()), () -> open(new PonderTagScreen(tag, this)))
                .showingTag(tag).hint(Component.literal(tag.getTitle()));
            buttons.add(button); tagButtons.add(button);
            tagFades.add(LerpedFloat.linear().startWithValue(0).chase(0, .05f, LerpedFloat.Chaser.exp(.1)));
        }
        if (!tags.isEmpty()) button(width - 60, 12, 48, text("associated"), () -> open(new PonderTagIndexScreen(this)));
        getActiveScene().getTransform().updateScreenParams(viewportWidth, viewportHeight, 0);
        updateButtons();
    }

    private PonderButton control(int x, int y, String label, PonderGuiTextures icon, Runnable action) {
        var button = button(x, y, 20, text(label), action).showing(icon).hint(text(label));
        buttons.add(button); return button;
    }
    private void updateButtons() { if (left != null) { left.active = index > 0; right.active = index + 1 < scenes.size(); } }
    private void toggleIdentify() {
        float partial = getPartialTicks();
        identifyMode = !identifyMode;
        if (identifyMode) ponderPartialTicksPaused = partial;
        else getActiveScene().deselect();
        hoveredTooltipItem = ItemStack.EMPTY; hoveredBlockPos = null;
    }
    @Override public void tick() {
        super.tick();
        buttons.forEach(PonderButton::tick);
        if (progress != null) progress.tick();
        if (skipCooling > 0) skipCooling--;
        if (referredToByTag != null) {
            for (int i = 0; i < scenes.size(); i++) if (scenes.get(i).getTags().contains(referredToByTag)) { select(i); break; }
            referredToByTag = null;
        }
        lazyIndex.tickChaser(); fadeIn.tickChaser();
        finishingFlash.tickChaser(); nextUp.tickChaser();
        var active = getActiveScene();
        extendedTickLength = 0;
        if (isComfyReadingEnabled()) active.forEachVisible(TextWindowElement.class, text -> extendedTickLength = 2);
        if (extendedTickTimer == 0) {
            if (!identifyMode && !paused) {
                ponderTicks++;
                if (skipCooling == 0) active.tick();
                float sliding = lazyIndex.getValue();
                if (Math.abs(sliding - index) > 1 / 512f) {
                    int previous = sliding < index ? index - 1 : index + 1;
                    if (previous >= 0 && previous < scenes.size()) scenes.get(previous).tick();
                }
            }
            extendedTickTimer = extendedTickLength;
        } else extendedTickTimer--;
        if (active.getCurrentTime() == active.getTotalTime() - 1) { nextUpWarmup = 50; finishingFlashWarmup = 30; }
        if (finishingFlashWarmup > 0 && --finishingFlashWarmup == 0) finishingFlash.setValue(1);
        if (nextUpWarmup > 0 && --nextUpWarmup == 0) nextUp.updateChaseTarget(1);
        updateButtons();
        updateIdentifiedItem(active);
    }

    public boolean isUserViewMode() { return userViewMode; }
    public PonderScene getActiveScene() { return scenes.get(index); }
    public ItemStack getSubject() { return stack.copy(); }
    public ItemStack getHoveredTooltipItem() { return hoveredTooltipItem.copy(); }
    public Font getFontRenderer() { return font; }
    public void seekToTime(int time) {
        time = Math.clamp(time, 0, getActiveScene().getTotalTime());
        if (getActiveScene().getCurrentTime() > time) replay();
        getActiveScene().seekToTime(time);
        if (time != 0) coolDownAfterSkip();
    }
    protected void replay() {
        identifyMode = false; hoveredTooltipItem = ItemStack.EMPTY;
        if (minecraft.hasShiftDown()) {
            var id = getActiveScene().getId(); var subject = getActiveScene().getLocation();
            PonderIndex.reload();
            var refreshed = PonderIndex.getSceneAccess().compile(subject);
            if (refreshed.isEmpty()) throw new IllegalStateException("Reload removed the current Ponder subject: " + subject);
            scenes.clear(); scenes.addAll(orderedOrRegistered(refreshed)); index = 0;
            for (int i = 0; i < scenes.size(); i++) if (scenes.get(i).getId().equals(id)) { index = i; break; }
            lazyIndex.startWithValue(index);
        }
        getActiveScene().begin(); nextUpWarmup = finishingFlashWarmup = 0; finishingFlash.startWithValue(0); nextUp.startWithValue(0); nextUp.updateChaseTarget(0);
        if (right != null) right.dim();
    }
    protected boolean scroll(boolean forward) { return select(Math.clamp(index + (forward ? 1 : -1), 0, scenes.size() - 1)); }
    private boolean select(int next) {
        if (next == index) return false;
        scenes.get(index).fadeOut(); index = next; getActiveScene().begin();
        lazyIndex.chase(index, .25f, LerpedFloat.Chaser.EXP);
        identifyMode = false; hoveredTooltipItem = ItemStack.EMPTY; nextUpWarmup = finishingFlashWarmup = 0; finishingFlash.startWithValue(0); nextUp.startWithValue(0); nextUp.updateChaseTarget(0);
        if (right != null) right.dim(); updateButtons(); return true;
    }
    public void coolDownAfterSkip() { skipCooling = 15; }
    public boolean isComfyReadingEnabled() { return PonderConfig.client().comfyReading.get(); }
    public void setComfyReadingEnabled(boolean value) { PonderConfig.client().comfyReading.set(value); }
    public void updateIdentifiedItem(PonderScene scene) {
        hoveredTooltipItem = ItemStack.EMPTY; hoveredBlockPos = null;
        if (!identifyMode) return;
        var transform = scene.getTransform();
        var first = transform.screenToScene(lastMouseX - viewportX, lastMouseY - viewportY, 1000, ponderPartialTicksPaused);
        var second = transform.screenToScene(lastMouseX - viewportX, lastMouseY - viewportY, -100, ponderPartialTicksPaused);
        var hit = scene.rayTraceScene(first, second);
        hoveredTooltipItem = hit.getFirst(); hoveredBlockPos = hit.getSecond();
    }

    @Override protected void extractPonderContent(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        lastMouseX = mouseX; lastMouseY = mouseY;
        float scenePartial = paused || identifyMode ? ponderPartialTicksPaused :
            (AnimationTickHolder.getPartialTicksUI() + extendedTickLength - extendedTickTimer) / (extendedTickLength + 1f);
        if (skipCooling > 0) scenePartial = 0;
        float sliding = lazyIndex.getValue(partial);
        int other = sliding < index ? index - 1 : index + 1;
        if (Math.abs(sliding - index) > 1 / 512f && other >= 0 && other < scenes.size()) renderScene(gui, other, sliding, scenePartial, false);
        renderScene(gui, index, sliding, scenePartial, true);
        renderSceneInformation(gui, partial, sliding - index);
        renderSourceHud(gui, mouseX, mouseY, partial, sliding);
        gui.centeredText(font, (index + 1) + " / " + scenes.size(), width / 2, height - 68, 0xFFAAAADD);
        if (identifyMode) gui.centeredText(font, text("identify_mode", minecraft.options.keyDrop.getTranslatedKeyMessage()), width / 2, height - 80, 0xFFCCCCFF);
        extractWidgets(gui, mouseX, mouseY, partial);
        // Source popups were above the widgets at z=400; native GUI extraction uses strata.
        gui.nextStratum();
        renderNextUp(gui, partial);
        boolean noWidgetHovered = children().stream().noneMatch(child -> child.isMouseOver(mouseX, mouseY));
        if (identifyMode && noWidgetHovered && mouseY < height - 80) {
            var tooltip = new ArrayList<ClientTooltipComponent>();
            Identifier style = null;
            boolean itemHeader = !hoveredTooltipItem.isEmpty();
            if (itemHeader) {
                for (var line : Screen.getTooltipFromItem(minecraft, hoveredTooltipItem))
                    tooltip.add(ClientTooltipComponent.create(line.getVisualOrderText()));
                hoveredTooltipItem.getTooltipImage().ifPresent(image -> tooltip.add(tooltip.isEmpty() ? 0 : 1, ClientTooltipComponent.create(image)));
                style = hoveredTooltipItem.get(DataComponents.TOOLTIP_STYLE);
            } else {
                var dropKey = minecraft.options.keyDrop.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.WHITE);
                var instruction = text("identify_mode", dropKey).copy().withStyle(ChatFormatting.GRAY);
                for (var line : font.split(instruction, Math.max(1, width / 3))) tooltip.add(ClientTooltipComponent.create(line));
            }
            // The native deferred slot can hold only one tooltip. Extract both real native tooltips now.
            if (!tooltip.isEmpty()) gui.tooltip(font, tooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, style, itemHeader);
            if (hoveredBlockPos != null && PonderIndex.editingModeActive() && !userViewMode) {
                String coords = hoveredBlockPos.getX() + ", " + hoveredBlockPos.getY() + ", " + hoveredBlockPos.getZ();
                var text = Component.literal(coords).withStyle(hoveredBlockPos.equals(copiedBlockPos) ? ChatFormatting.GREEN : ChatFormatting.GOLD);
                gui.tooltip(font, List.of(ClientTooltipComponent.create(text.getVisualOrderText())),
                    mouseX, mouseY - 15, DefaultTooltipPositioner.INSTANCE, null, false);
            }
        }
    }
    private void renderSceneInformation(GuiGraphicsExtractor gui, float partial, float difference) {
        float amount = Math.abs(difference), fade = fadeIn.getValue(partial);
        int other = index;
        if (scenes.size() != 1 && amount >= .01f) other += (int) Math.signum(difference);
        if (other < 0 || other >= scenes.size()) return;
        String title = getActiveScene().getTitle(), previous = scenes.get(other).getTitle();
        int titleHeight = font.split(Component.literal(title), 180).size() * 9;
        int previousHeight = font.split(Component.literal(previous), 180).size() * 9;
        int streakHeight = 26 + (int) Mth.lerp(amount, titleHeight, previousHeight);
        int streakWidth = 70 + (int) Mth.lerp(amount, Math.min(180, font.width(title)), Math.min(180, font.width(previous)));
        PonderUIRenderHelper.streak(gui, 0, 55, 19 + streakHeight / 2, streakHeight, (int) (streakWidth * fade));
        PonderUIRenderHelper.streak(gui, 180, 55, 19 + streakHeight / 2, streakHeight, (int) (30 * fade));
        gui.fillGradient(21, 21, 51, 51, 0x40FFEEDD, 0x20FFEEDD); gui.fill(22, 22, 50, 50, 0xFF000000);
        gui.pose().pushMatrix(); gui.pose().translate(20, 20); gui.pose().scale(2, 2); gui.item(stack, 0, 0); gui.pose().popMatrix();
        gui.text(font, text("pondering"), 59, 25, 0xFFA3A3A3, false);
        PonderTitleRenderer.extract(gui, font, title, previous, scenes.size() == 1 ? 0 : difference, fade);
    }
    private void renderNextUp(GuiGraphicsExtractor gui, float partial) {
        if (!getActiveScene().isFinished() || index + 1 >= scenes.size() || !scenes.get(index + 1).isNextUpEnabled()
                || nextUp.getValue() <= 1 / 16f) return;
        String title = scenes.get(index + 1).getTitle(); Component label = text("next_up");
        gui.pose().pushMatrix();
        try {
            gui.pose().translate(right.getX() + 10, right.getY() - 6 + nextUp.getValue(partial) * 5);
            int boxWidth = Math.max(font.width(title), font.width(label)) + 5;
            renderSpeechBox(gui, 0, 0, boxWidth, 20, right.isHoveredOrFocused(), Pointing.DOWN, false);
            gui.pose().translate(0, -29);
            gui.centeredText(font, label, 0, 0, 0xFFA3A3A3);
            gui.centeredText(font, title, 0, 10, 0xFFEEEEEE);
        } finally { gui.pose().popMatrix(); }
    }
    public static void renderSpeechBox(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                                       boolean highlighted, Pointing pointing, boolean returnWithLocalTransform) {
        PonderUIRenderHelper.speechBox(gui, x, y, width, height, highlighted, pointing, returnWithLocalTransform);
    }
    private void renderSourceHud(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial, float sliding) {
        if (identifyMode) scan.flash(); else scan.dim();
        if (userMode != null) { if (userViewMode) userMode.flash(); else userMode.dim(); }
        if (isComfyReadingEnabled()) slowMode.flash(); else slowMode.dim();
        float fade = fadeIn.getValue(partial);
        for (var button : buttons) button.setAlpha(fade);
        if (index == 0 || index == 1 && sliding < index) left.setAlpha(Math.clamp(sliding, 0, 1));
        if (index == scenes.size() - 1 || index == scenes.size() - 2 && sliding > index)
            right.setAlpha(Math.clamp(scenes.size() - sliding - 1, 0, 1));
        if (getActiveScene().isFinished()) right.flash(); else { right.dim(); nextUp.updateChaseTarget(0); }
        PonderUIRenderHelper.breadcrumb(gui, width / 2 - 20, height - 51, 20, 20, 5, 0x40AA9999, 0x20AA9999);
        PonderUIRenderHelper.breadcrumb(gui, width / 2 + 20, height - 51, -20, 20, -5, 0x40AA9999, 0x20AA9999);
        PonderUIRenderHelper.breadcrumb(gui, width / 2 - 90, height - 51, 70, 20, 5, 0x40AA9999, 0x10AA9999);
        PonderUIRenderHelper.breadcrumb(gui, width / 2 + 90, height - 51, -70, 20, -5, 0x40AA9999, 0x10AA9999);
        var sceneTags = getActiveScene().getTags();
        boolean highlightAll = sceneTags.stream().anyMatch(tag -> tag.getId().equals(PonderTag.Highlight.ALL));
        for (int i = 0; i < tagButtons.size(); i++) {
            var button = tagButtons.get(i); var tag = button.getTag(); var chase = tagFades.get(i);
            chase.updateChaseTarget(button.isMouseOver(mouseX, mouseY) ? 1 : 0); chase.tickChaser();
            if (highlightAll || sceneTags.contains(tag)) button.flash(); else button.dim();
            int x = button.getRight() + 4, y = button.getY() - 2 + (int) (5 * (1 - fade));
            int fadedWidth = (int) (200 * chase.getValue(partial));
            PonderUIRenderHelper.streak(gui, 0, x, y + 12, 26, fadedWidth);
            if (fadedWidth > 0) {
                gui.enableScissor(x, 0, x + fadedWidth, height);
                gui.text(font, tag.getTitle(), x + 3, y + 8, 0xFFDDEEFF, false); gui.disableScissor();
            }
        }
    }
    private void renderScene(GuiGraphicsExtractor gui, int sceneIndex, float sliding, float partial, boolean overlays) {
        var scene = scenes.get(sceneIndex);
        var state = PonderViewport.extract(gui, scene, viewportX, viewportY, viewportWidth, viewportHeight,
            (sceneIndex - sliding) * viewportWidth * 1.5, partial, sceneIndex == index ? finishingFlash.getValue(partial) : 0);
        if (overlays) PonderViewport.enqueue(gui, state);
        else PonderViewport.enqueueTransition(gui, state);
        if (overlays && !identifyMode) PonderOverlayRenderer.extract(gui, font, scene, state.frame(), viewportX, viewportY, viewportWidth, viewportHeight, partial);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return vertical != 0 && scroll(vertical > 0) || super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (rotatingScene && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            getActiveScene().getTransform().rotateFromMouse(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (rotatingScene && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            rotatingScene = false;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (identifyMode && hoveredBlockPos != null && event.y() > viewportY && event.y() < viewportY + viewportHeight) {
            if (PonderIndex.editingModeActive() && !userViewMode) {
                String position = hoveredBlockPos.getX() + ", " + hoveredBlockPos.getY() + ", " + hoveredBlockPos.getZ();
                String copied = event.button() == InputConstants.MOUSE_BUTTON_RIGHT && copiedBlockPos != null ? "util.select().fromTo(" + copiedBlockPos.getX() + ", " + copiedBlockPos.getY() + ", " + copiedBlockPos.getZ() + ", " + position + ")" :
                    (minecraft.hasShiftDown() ? "util.select().position(" : "util.grid().at(") + position + ")";
                TextFieldHelper.setClipboardContents(minecraft, copied); copiedBlockPos = hoveredBlockPos; return true;
            }
            if (!hoveredTooltipItem.isEmpty() && PonderIndex.getSceneAccess().doScenesExistForId(BuiltInRegistries.ITEM.getKey(hoveredTooltipItem.getItem()))) {
                open(PonderUI.of(hoveredTooltipItem)); return true;
            }
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= viewportX
                && event.x() < viewportX + viewportWidth && event.y() >= viewportY
                && event.y() < viewportY + viewportHeight - 65) {
            rotatingScene = true;
            setDragging(true);
            return true;
        }
        return false;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (progress != null && progress.isFocused() && progress.keyPressed(event)) return true;
        if (getFocused() instanceof PonderButton focused && focused.keyPressed(event)) return true;
        if (event.key() == InputConstants.KEY_SPACE) { ponderPartialTicksPaused = getPartialTicks(); paused = !paused; return true; }
        for (var button : buttons) if (button.keyPressed(event)) return true;
        return super.keyPressed(event);
    }
    public static float getPartialTicks() {
        float render = AnimationTickHolder.getPartialTicksUI();
        if (Minecraft.getInstance().gui.screen() instanceof PonderUI ui) {
            if (ui.identifyMode || ui.paused) return ponderPartialTicksPaused;
            return (render + ui.extendedTickLength - ui.extendedTickTimer) / (ui.extendedTickLength + 1f);
        }
        return render;
    }
    @Override public void removed() { super.removed(); rotatingScene = false; setDragging(false); hoveredTooltipItem = ItemStack.EMPTY; }

    private static List<PonderScene> orderedOrRegistered(List<PonderScene> scenes) {
        try { return orderScenes(scenes); }
        catch (RuntimeException cycle) {
            Ponder.LOGGER.warn("Unable to sort Ponder scenes; retaining registered order", cycle);
            return scenes;
        }
    }
    private static List<PonderScene> orderScenes(List<PonderScene> scenes) {
        Map<Boolean, List<PonderScene>> partitioned = scenes.stream().collect(Collectors.partitioningBy(scene -> scene.getOrderingEntries().isEmpty()));
        var withOrdering = partitioned.get(false); var withoutOrdering = partitioned.get(true);
        if (withOrdering.isEmpty()) return scenes;
        var reversed = new ArrayList<>(scenes); Collections.reverse(reversed);
        var lookup = scenes.stream().collect(Collectors.toMap(PonderScene::getId, scene -> scene));
        MutableGraph<PonderScene> graph = GraphBuilder.directed().nodeOrder(ElementOrder.insertion()).build();
        reversed.forEach(graph::addNode);
        IntStream.range(1, withoutOrdering.size()).forEach(i -> graph.putEdge(withoutOrdering.get(i - 1), withoutOrdering.get(i)));
        withOrdering.forEach(scene -> scene.getOrderingEntries().stream().filter(entry -> lookup.containsKey(entry.sceneId())).forEach(entry -> {
            var other = lookup.get(entry.sceneId());
            if (entry.type() == SceneOrderingType.BEFORE) graph.putEdge(scene, other);
            else if (entry.type() == SceneOrderingType.AFTER) graph.putEdge(other, scene);
        }));
        var result = new ArrayList<PonderScene>(); var visited = new HashSet<PonderScene>(); var visiting = new HashSet<PonderScene>();
        for (var scene : graph.nodes()) if (!dfs(scene, graph, visited, visiting, result)) throw new IllegalArgumentException("Ponder scene ordering has a cycle");
        Collections.reverse(result); return result;
    }
    private static boolean dfs(PonderScene node, MutableGraph<PonderScene> graph, Set<PonderScene> visited, Set<PonderScene> visiting, List<PonderScene> result) {
        if (visiting.contains(node)) return false;
        if (visited.add(node)) {
            visiting.add(node);
            for (var next : graph.successors(node)) if (!dfs(next, graph, visited, visiting, result)) return false;
            visiting.remove(node); result.add(node);
        }
        return true;
    }
    @Override public boolean isEquivalentTo(AbstractPonderScreen other) {
        return other instanceof PonderUI ui && !ui.stack.isEmpty() && stack.is(ui.stack.getItem());
    }
    @Override public void shareContextWith(AbstractPonderScreen other) {
        if (other instanceof PonderUI ui) ui.referredToByTag = referredToByTag;
    }

}
