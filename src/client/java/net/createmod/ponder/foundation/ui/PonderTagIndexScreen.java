// Ponder1.0.82 category index, MIT; native26.3 GUI adaptation.
package net.createmod.ponder.foundation.ui;

import java.util.List;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;

public final class PonderTagIndexScreen extends AbstractPonderScreen {
    private record Row(String namespace, PonderTag tag) {}
    private List<Row> rows = List.of();
    private int page, pageSize = 1;
    public PonderTagIndexScreen() { this(Minecraft.getInstance().gui.screen()); }
    public PonderTagIndexScreen(Screen parent) { super(text("welcome"), parent); }
    @Override protected void init() {
        super.init();
        var grouped = new TreeMap<>(PonderIndex.getTagAccess().getListedTags().stream()
            .collect(Collectors.groupingBy(tag -> tag.getId().getNamespace())));
        var entries = new ArrayList<Row>();
        grouped.forEach((namespace, tags) -> {
            entries.add(new Row(namespace, null));
            tags.forEach(tag -> entries.add(new Row(namespace, tag)));
        });
        rows = List.copyOf(entries);
        pageSize = Math.max(1, (height - 94) / 31); page = Math.clamp(page, 0, Math.max(0, (rows.size() - 1) / pageSize));
        int entryWidth = Math.max(40, Math.min(240, width - 64)), x = (width - entryWidth) / 2;
        for (int i = 0; i < pageSize && page * pageSize + i < rows.size(); i++) {
            var tag = rows.get(page * pageSize + i).tag();
            if (tag == null) continue;
            button(x, 48 + i * 31, 22, Component.literal(tag.getTitle()), () -> open(new PonderTagScreen(tag, this)))
                .showingTag(tag).hint(Component.literal(tag.getDescription()));
            button(x + 28, 48 + i * 31, entryWidth - 28, Component.literal(tag.getTitle()), () -> open(new PonderTagScreen(tag, this)));
        }
        button(24, height - 34, 30, Component.literal("<"), () -> page(-1)).active = page > 0;
        button(width - 54, height - 34, 30, Component.literal(">"), () -> page(1)).active = (page + 1) * pageSize < rows.size();
    }
    private void page(int amount) {
        int next = Math.clamp(page + amount, 0, Math.max(0, (rows.size() - 1) / pageSize));
        if (next != page) { page = next; rebuildWidgets(); }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0) { page(vertical < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override protected void extractPonderContent(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        background(gui);
        int entryWidth = Math.max(40, Math.min(240, width - 64)), x = (width - entryWidth) / 2;
        for (int i = 0; i < pageSize && page * pageSize + i < rows.size(); i++) {
            var row = rows.get(page * pageSize + i);
            if (row.tag() != null) continue;
            String modName = FabricLoader.getInstance().getModContainer(row.namespace())
                .map(container -> container.getMetadata().getName()).orElse(row.namespace());
            gui.text(font, text("categories", modName), x, 54 + i * 31, 0xFFAAAADD);
        }
        gui.centeredText(font, (page + 1) + " / " + Math.max(1, (rows.size() + pageSize - 1) / pageSize), width / 2, height - 28, 0xFFAAAADD);
        extractWidgets(gui, mouseX, mouseY, partial);
    }
}
