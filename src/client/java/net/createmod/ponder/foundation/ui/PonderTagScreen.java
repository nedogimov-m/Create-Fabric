// Ponder1.0.82 category components, MIT; native26.3 GUI adaptation.
package net.createmod.ponder.foundation.ui;

import java.util.Comparator;
import java.util.List;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class PonderTagScreen extends AbstractPonderScreen {
    private final PonderTag tag;
    private List<Identifier> items = List.of();
    private int page, pageSize = 1;
    public PonderTagScreen(PonderTag tag) { this(tag, Minecraft.getInstance().gui.screen()); }
    public PonderTagScreen(PonderTag tag, Screen parent) { super(Component.literal(tag.getTitle()), parent); this.tag = tag; }
    @Override protected void init() {
        super.init();
        items = PonderIndex.getTagAccess().getItems(tag).stream().filter(BuiltInRegistries.ITEM::containsKey)
            .filter(id -> tag.getMainItem().isEmpty() || BuiltInRegistries.ITEM.getValue(id) != tag.getMainItem().getItem())
            .sorted(Comparator.comparing(Identifier::toString)).toList();
        int columns = Math.max(1, (width - 80) / 30), rows = Math.max(1, (height - 155) / 30);
        pageSize = columns * rows; page = Math.clamp(page, 0, Math.max(0, (items.size() - 1) / pageSize));
        int firstX = (width - columns * 30) / 2;
        for (int slot = 0; slot < pageSize && page * pageSize + slot < items.size(); slot++) {
            var id = items.get(page * pageSize + slot); var stack = BuiltInRegistries.ITEM.getValue(id).getDefaultInstance();
            var entry = button(firstX + slot % columns * 30, 96 + slot / columns * 30, 24, stack.getHoverName(),
                () -> open(PonderUI.of(stack, tag))).showing(stack);
            boolean scenesExist = PonderIndex.getSceneAccess().doScenesExistForId(id);
            entry.active = minecraft.level != null && scenesExist;
            if (!scenesExist) entry.missingScene(id.getNamespace().equals("minecraft"));
        }
        if (!tag.getMainItem().isEmpty()) {
            var stack = tag.getMainItem(); var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            var main = button(Math.max(5, firstX - 48), 96, 24, stack.getHoverName(),
                () -> open(PonderUI.of(stack, tag))).showing(stack);
            boolean scenesExist = PonderIndex.getSceneAccess().doScenesExistForId(id);
            main.active = minecraft.level != null && scenesExist;
            if (!scenesExist) main.missingScene(id.getNamespace().equals("minecraft"));
        }
        button(24, height - 34, 30, Component.literal("<"), () -> page(-1)).active = page > 0;
        button(width - 54, height - 34, 30, Component.literal(">"), () -> page(1)).active = (page + 1) * pageSize < items.size();
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
        background(gui); tag.render(gui, width / 2 - 8, 37);
        gui.textWithWordWrap(font, Component.literal(tag.getDescription()), 40, 60, Math.max(1, width - 80), 0xFFCCCCDD);
        gui.centeredText(font, (page + 1) + " / " + Math.max(1, (items.size() + pageSize - 1) / pageSize), width / 2, height - 28, 0xFFAAAADD);
        extractWidgets(gui, mouseX, mouseY, partial);
    }
    @Override public boolean isEquivalentTo(AbstractPonderScreen other) {
        return other instanceof PonderTagScreen screen && tag == screen.tag;
    }

}
