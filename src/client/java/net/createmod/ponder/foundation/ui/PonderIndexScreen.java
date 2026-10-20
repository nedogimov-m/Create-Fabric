// Ponder1.0.82 registered-item index, MIT; native26.3 GUI adaptation.
package net.createmod.ponder.foundation.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.registration.PonderIndexExclusionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/** Every entry comes from the actual scene registry; selecting it compiles its genuine storyboards. */
public final class PonderIndexScreen extends AbstractPonderScreen {
    private List<Identifier> items = List.of();
    private final List<Predicate<ItemLike>> exclusions;
    private int page, pageSize = 1;
    public PonderIndexScreen() { this(Minecraft.getInstance().gui.screen()); }
    public PonderIndexScreen(Screen parent) {
        super(text("index_title"), parent);
        exclusions = PonderIndex.streamPlugins().flatMap(PonderIndexExclusionHelper::pluginToExclusions).toList();
    }
    @Override protected void init() {
        super.init();
        items = PonderIndex.getSceneAccess().getRegisteredEntries().stream().map(Map.Entry::getKey).distinct()
            .filter(BuiltInRegistries.ITEM::containsKey)
            .filter(id -> exclusions.stream().noneMatch(exclusion -> exclusion.test(BuiltInRegistries.ITEM.getValue(id))))
            .sorted(Comparator.comparing(Identifier::toString)).toList();
        int columns = Math.max(1, (width - 80) / 30), rows = Math.max(1, (height - 126) / 30);
        pageSize = columns * rows; page = Math.clamp(page, 0, Math.max(0, (items.size() - 1) / pageSize));
        int firstX = (width - columns * 30) / 2;
        for (int slot = 0; slot < pageSize && page * pageSize + slot < items.size(); slot++) {
            var id = items.get(page * pageSize + slot); var stack = BuiltInRegistries.ITEM.getValue(id).getDefaultInstance();
            var entry = button(firstX + slot % columns * 30, 69 + slot / columns * 30, 24, stack.getHoverName(),
                () -> open(PonderUI.of(stack))).showing(stack);
            entry.active = minecraft.level != null;
            if (!entry.active) entry.hint(Component.literal("Join a world to view this scene"));
        }
        button(40, height - 35, 32, Component.literal("<"), () -> page(-1)).active = page > 0;
        button(width - 72, height - 35, 32, Component.literal(">"), () -> page(1)).active = (page + 1) * pageSize < items.size();
        button(width / 2 - 65, 38, 130, text("associated"), () -> open(new PonderTagIndexScreen(this)));
    }
    private void page(int amount) {
        int next = Math.clamp(page + amount, 0, Math.max(0, (items.size() - 1) / pageSize));
        if (next != page) { page = next; rebuildWidgets(); }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0) { page(vertical < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override protected void extractPonderContent(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        background(gui);
        gui.centeredText(font, (page + 1) + " / " + Math.max(1, (items.size() + pageSize - 1) / pageSize), width / 2, height - 29, 0xFFAAAADD);
        if (items.isEmpty()) gui.centeredText(font, text("index_description"), width / 2, 80, 0xFFCCCCCC);
        extractWidgets(gui, mouseX, mouseY, partial);
    }
    @Override public boolean isEquivalentTo(AbstractPonderScreen other) { return other instanceof PonderIndexScreen; }

}
