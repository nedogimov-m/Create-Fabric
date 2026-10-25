// Ponder1.0.82 hover/hold state machine, MIT; native26.3 tooltip extraction/Fabric tick adaptation.
package net.createmod.ponder.foundation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;
import java.util.WeakHashMap;
import net.minecraft.util.FormattedCharSequence;
import java.util.function.Consumer;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.enums.PonderKeybinds;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.mixin.client.PonderTextTooltipAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class PonderTooltipHandler {
    // Formatted-text identities and live hover state belong to the client thread.
    private static final Set<FormattedCharSequence> tooltipText = Collections.newSetFromMap(new WeakHashMap<>());
    public static final String HOLD_TO_PONDER = "ui.hold_to_ponder", SUBJECT = "ui.subject";
    public static boolean enable = true;
    private static final List<Consumer<ItemStack>> callbacks = new ArrayList<>();
    private static ItemStack trackingStack = ItemStack.EMPTY;
    private static float progress;
    private static int ticks, lastSeen = -100;
    private static boolean subject;

    public static void addToTooltip(List<Component> tooltip, ItemStack stack) {
        if (!enable || stack.isEmpty()) return;
        var client = Minecraft.getInstance();
        boolean clientThread = client.isSameThread();
        if (!PonderIndex.getSceneAccess().doScenesExistForId(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            if (clientThread) lastSeen = -100;
            return;
        }
        // Native recipe/creative search requests tooltips concurrently on worker threads.
        // Keep their searchable hint without touching UI progress, callbacks, font or the weak identity set.
        if (!clientThread) {
            tooltip.add(Math.min(1, tooltip.size()), holdMessage());
            return;
        }
        if (!ItemStack.isSameItemSameComponents(trackingStack, stack)) { trackingStack = stack.copy(); progress = 0; }
        lastSeen = ticks;
        subject = client.gui.screen() instanceof PonderUI ui && ItemStack.isSameItemSameComponents(ui.getSubject(), stack);
        callbacks.forEach(callback -> callback.accept(stack.copy()));
        Component line;
        if (subject) line = Component.translatable("ponder." + SUBJECT).withStyle(ChatFormatting.GREEN);
        else if (progress == 0) line = holdMessage();
        else {
            // Source progress spans the width of the actual localized hold instruction.
            int total = Math.max(1, (int) (client.font.width(holdMessage()) / (float) Math.max(1, client.font.width("|"))));
            int filled = Math.clamp((int) (Math.min(1, progress * 8 / 7f) * total), 0, total);
            line = Component.literal("|".repeat(filled)).withStyle(ChatFormatting.GRAY)
                .append(Component.literal("|".repeat(total - filled)).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltipText.add(line.getVisualOrderText());
        tooltip.add(Math.min(1, tooltip.size()), line);
    }
    public static void tick() {
        ticks++;
        var client = Minecraft.getInstance();
        if (!enable || client.level == null || client.gui.screen() == null || ticks - lastSeen > 2 || trackingStack.isEmpty()) {
            progress = 0; return;
        }
        if (!subject && PonderKeybinds.PONDER.isDown()) {
            progress = Math.min(1, progress + Math.max(.25f, progress) * .25f);
            if (progress >= 1) {
                var subjectStack = trackingStack.copy(); reset();
                ScreenOpener.transitionTo(PonderUI.of(subjectStack));
            }
        } else progress = Math.max(0, progress - .05f);
    }
    public static Optional<Couple<Color>> handleTooltipColor(ItemStack stack) {
        if (progress == 0 || !ItemStack.isSameItemSameComponents(stack, trackingStack)) return Optional.empty();
        var a = new Color(0x5000FF, false);
        var b = new Color(0x5555FF, false);
        var c = new Color(0xFFFFFF, false);
        float visualProgress = Math.min(1, progress * 8 / 7f);
        var color = visualProgress < .5f ? a.mixWith(b, visualProgress * 2) : b.mixWith(c, (visualProgress - .5f) * 2);
        return Optional.of(Couple.create(color.copy(), color.copy()));
    }
    /** Runs after the actual native tooltip background with its computed content rectangle.
     * Actual native cached formatted-text identity scopes this border to the source Ponder line. */
    public static void extractBorder(GuiGraphicsExtractor gui, List<ClientTooltipComponent> components,
                                     int x, int y, int width, int height) {
        boolean marked = false;
        for (var component : components) if (component instanceof PonderTextTooltipAccessor text
                && tooltipText.contains(text.ponder$text())) { marked = true; break; }
        if (!marked) return;
        var colors = handleTooltipColor(trackingStack);
        if (colors.isEmpty()) return;
        int first = colors.get().getFirst().getRGB(), last = colors.get().getSecond().getRGB();
        gui.fillGradient(x - 4, y - 4, x + width + 4, y - 3, first, last);
        gui.fillGradient(x - 4, y + height + 3, x + width + 4, y + height + 4, first, last);
        gui.fillGradient(x - 4, y - 3, x - 3, y + height + 3, first, last);
        gui.fillGradient(x + width + 3, y - 3, x + width + 4, y + height + 3, first, last);
    }
    public static synchronized void registerHoveredPonderStackCallback(Consumer<ItemStack> callback) { callbacks.add(callback); }
    public static synchronized void removeHoveredPonderStackCallback(Consumer<ItemStack> callback) { callbacks.remove(callback); }
    public static void reset() { trackingStack = ItemStack.EMPTY; progress = 0; subject = false; lastSeen = -100; tooltipText.clear(); }
    private static Component holdMessage() {
        return Component.translatable("ponder." + HOLD_TO_PONDER,
            PonderKeybinds.PONDER.message().copy().withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_GRAY);
    }
    private PonderTooltipHandler() {}
}
