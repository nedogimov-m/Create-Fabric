package com.simibubi.create.infrastructure.gui;

import com.google.gson.JsonPrimitive;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.ConfigScope;
import com.simibubi.create.infrastructure.config.NativeConfigBase;
import com.simibubi.create.infrastructure.config.NativeConfigStore;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.joml.Vector2i;

/** Native widgets editing the actual scope definitions; drafts are committed through the one real store. */
public final class CreateConfigScreen extends Screen {
    private static final int ROW_TOP = 71, ROW_HEIGHT = 26;
    private final Screen parent;
    private final EnumMap<ConfigScope, Draft> drafts = new EnumMap<>(ConfigScope.class);
    private final EnumMap<ConfigScope, Button> scopeButtons = new EnumMap<>(ConfigScope.class);
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private final List<VisibleRow> visibleRows = new ArrayList<>();
    private ConfigScope scope = ConfigScope.CLIENT;
    private Draft draft;
    private Button group, previous, next, apply, reset;
    private EditBox search;
    private int left, panelWidth, valueWidth, valueX, rowsPerPage;
    private boolean saving;
    private Component status = Component.empty();
    private int statusColor = 0xFFCCCCCC;

    public CreateConfigScreen(Screen parent) {
        super(ui("title"));
        this.parent = parent;
    }

    static Component ui(String key, Object... args) {
        return Component.translatable("create.config.ui." + key, args);
    }

    private static Component scopeName(ConfigScope scope) {
        if (scope == ConfigScope.SERVER && AllConfigs.activeServer().isEmpty()
            && Minecraft.getInstance().getConnection() == null)
            return ui("scope.server_defaults");
        return ui("scope." + scope.name().toLowerCase(Locale.ROOT));
    }

    private static String words(String name) {
        var text = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').replace('.', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static Component label(NativeConfigBase.CValue<?> value) {
        return Component.translatableWithFallback("create.config." + value.key(), words(value.getName()));
    }

    @Override protected void init() {
        panelWidth = Math.min(430, width - 24);
        left = (width - panelWidth) / 2;
        valueWidth = Math.min(110, panelWidth / 3);
        valueX = left + panelWidth - valueWidth - 24;
        rowsPerPage = Math.max(1, (height - 64 - ROW_TOP) / ROW_HEIGHT);
        draft = drafts.computeIfAbsent(scope, Draft::new);
        scopeButtons.clear();
        rowWidgets.clear();
        int tabWidth = (panelWidth - 8) / 3;
        for (var type : ConfigScope.values()) {
            var button = addRenderableWidget(Button.builder(scopeName(type), ignored -> selectScope(type))
                .bounds(left + type.ordinal() * (tabWidth + 4), 24, tabWidth, 20).build());
            var store = AllConfigs.store(type);
            boolean remote = type == ConfigScope.SERVER && AllConfigs.activeServer().isEmpty()
                && (store.hasRemote() || minecraft.getConnection() != null);
            button.setTooltip(Tooltip.create(remote ? ui("remote_read_only")
                : scopeName(type).copy().append("\n").append(store.file().map(Path::toString).orElse(ui("not_loaded").getString()))));
            scopeButtons.put(type, button);
        }
        int half = (panelWidth - 4) / 2;
        group = addRenderableWidget(Button.builder(groupLabel(), button -> changeGroup())
            .bounds(left, 48, half, 18).build());
        search = addRenderableWidget(new EditBox(font, left + half + 4, 48, panelWidth - half - 4, 18, ui("search")));
        search.setMaxLength(160);
        search.setHint(ui("search"));
        search.setValue(draft.filter);
        search.setResponder(value -> {
            draft.filter = value;
            draft.page = 0;
            refreshRows();
        });
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
            .bounds(left, height - 44, 24, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
            .bounds(left + panelWidth - 24, height - 44, 24, 18).build());
        int actionWidth = (panelWidth - 8) / 3;
        reset = addRenderableWidget(Button.builder(ui("reset_scope", scopeName(scope)), button -> resetScope())
            .bounds(left, height - 22, actionWidth, 20).build());
        reset.setTooltip(Tooltip.create(ui("reset_scope_hint", scopeName(scope))));
        apply = addRenderableWidget(Button.builder(ui("apply"), button -> apply())
            .bounds(left + actionWidth + 4, height - 22, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(left + (actionWidth + 4) * 2, height - 22, actionWidth, 20).build());
        refreshRows();
    }

    private void selectScope(ConfigScope nextScope) {
        if (saving || scope == nextScope) return;
        scope = nextScope;
        status = Component.empty();
        rebuildWidgets();
    }

    private Component groupLabel() {
        return draft.group.isEmpty() ? ui("all_groups") : Component.literal(words(draft.group));
    }

    private void changeGroup() {
        var groups = draft.groups;
        int direction = minecraft.hasShiftDown() ? -1 : 1;
        draft.group = groups.get(Math.floorMod(groups.indexOf(draft.group) + direction, groups.size()));
        draft.page = 0;
        group.setMessage(groupLabel());
        refreshRows();
    }

    private void changePage(int change) {
        int pages = Math.max(1, (draft.filtered().size() + rowsPerPage - 1) / rowsPerPage);
        draft.page = Math.clamp(draft.page + change, 0, pages - 1);
        refreshRows();
    }

    private void refreshRows() {
        rowWidgets.forEach(this::removeWidget);
        rowWidgets.clear();
        visibleRows.clear();
        var filtered = draft.filtered();
        int pages = Math.max(1, (filtered.size() + rowsPerPage - 1) / rowsPerPage);
        draft.page = Math.clamp(draft.page, 0, pages - 1);
        int start = draft.page * rowsPerPage;
        for (int i = start; i < Math.min(filtered.size(), start + rowsPerPage); i++) {
            var field = filtered.get(i);
            int y = ROW_TOP + (i - start) * ROW_HEIGHT;
            visibleRows.add(new VisibleRow(field, y));
            AbstractWidget control;
            if (field.value instanceof Boolean) {
                control = Button.builder(booleanLabel(field), button -> {
                    field.set(!(Boolean) field.value);
                    button.setMessage(booleanLabel(field));
                    edited();
                }).bounds(valueX, y + 2, valueWidth, 20).build();
            } else if (field.value instanceof Enum<?> choice) {
                control = Button.builder(enumLabel(choice), button -> {
                    var selected = (Enum<?>) field.value;
                    var choices = Arrays.asList(selected.getDeclaringClass().getEnumConstants());
                    int direction = minecraft.hasShiftDown() ? -1 : 1;
                    field.set(choices.get(Math.floorMod(choices.indexOf(selected) + direction, choices.size())));
                    button.setMessage(enumLabel((Enum<?>) field.value));
                    edited();
                }).bounds(valueX, y + 2, valueWidth, 20).build();
            } else {
                var number = new EditBox(font, valueX, y + 2, valueWidth, 20, label(field.definition));
                number.setMaxLength(256);
                number.setValue(field.raw);
                number.setTextColor(field.error == null ? 0xFFE0E0E0 : 0xFFFF7777);
                number.setResponder(value -> {
                    field.number(value);
                    number.setTextColor(field.error == null ? 0xFFE0E0E0 : 0xFFFF7777);
                    edited();
                });
                control = number;
            }
            control.setTooltip(Tooltip.create(field.tooltip()));
            rowWidgets.add(addRenderableWidget(control));
            var resetField = Button.builder(Component.literal("↶"), button -> {
                field.defaults();
                status = Component.empty();
                refreshRows();
            }).bounds(left + panelWidth - 20, y + 2, 20, 20).build();
            resetField.setTooltip(Tooltip.create(ui("reset_value", String.valueOf(field.definition.defaultValue()))));
            rowWidgets.add(addRenderableWidget(resetField));
        }
        previous.active = draft.page > 0;
        next.active = draft.page + 1 < pages;
        refreshControls();
    }

    private static Component booleanLabel(Field field) {
        return Component.translatable((Boolean) field.value ? "options.on" : "options.off");
    }

    private void edited() {
        status = Component.empty();
        statusColor = 0xFFCCCCCC;
        refreshControls();
    }

    private static Component enumLabel(Enum<?> value) {
        return Component.literal(words(value.name().toLowerCase(Locale.ROOT)));
    }

    private static boolean remoteSession(NativeConfigStore store) {
        var client = Minecraft.getInstance();
        return client.getConnection() != null && !client.hasSingleplayerServer()
            || store.hasRemote() && AllConfigs.activeServer().isEmpty();
    }

    private boolean readOnly() {
        return scope == ConfigScope.SERVER && (draft.remoteView || remoteSession(draft.store));
    }

    private boolean editable() {
        return !saving && !readOnly() && draft.validContext() && draft.file != null;
    }

    private void refreshControls() {
        boolean edit = editable();
        apply.active = edit && draft.dirty() && draft.fields.values().stream().noneMatch(field -> field.error != null);
        reset.active = edit;
        for (var widget : rowWidgets) {
            if (widget instanceof EditBox number) {
                number.active = !saving;
                number.setEditable(edit);
            } else widget.active = edit;
        }
        for (var entry : scopeButtons.entrySet()) {
            var state = drafts.get(entry.getKey());
            entry.getValue().setMessage(scopeName(entry.getKey()).copy()
                .append(state != null && state.dirty() ? " *" : ""));
            entry.getValue().active = !saving && entry.getKey() != scope;
        }
        group.active = !saving;
        search.setEditable(!saving);
    }

    private void resetScope() {
        if (!editable()) return;
        draft.fields.values().forEach(Field::defaults);
        status = Component.empty();
        refreshRows();
    }

    private void apply() {
        if (!editable()) return;
        var changes = new LinkedHashMap<String, Object>();
        for (var field : draft.fields.values()) {
            if (field.error != null) return;
            if (field.dirty()) changes.put(field.definition.key(), field.value);
        }
        if (changes.isEmpty()) return;
        var target = draft;
        var client = minecraft;
        saving = true;
        status = ui("applying");
        statusColor = 0xFFCCCCCC;
        refreshControls();
        Runnable commit = () -> {
            if (target.remoteView || !target.validAuthority() || target.file == null)
                throw new IllegalStateException(ui("context_changed").getString());
            // The real SERVER dispatcher now runs this write inline, so completion includes persistence.
            if (target.scope == ConfigScope.SERVER && Transaction.isOpen())
                throw new IllegalStateException(ui("server_busy").getString());
            target.store.importValues(changes, true);
        };
        CompletableFuture<Void> result;
        if (target.scope == ConfigScope.SERVER && target.owner != null) {
            result = CompletableFuture.runAsync(commit, target.owner::execute);
        } else {
            try {
                // Recheck authority after the click; remote snapshots never reach a writable endpoint.
                if (readOnly()) throw new IllegalStateException(ui("remote_read_only").getString());
                commit.run();
                result = CompletableFuture.completedFuture(null);
            } catch (RuntimeException failure) {
                result = CompletableFuture.failedFuture(failure);
            }
        }
        result.whenComplete((ignored, failure) -> client.execute(() -> {
            saving = false;
            if (failure == null) {
                target.load(false);
                status = ui("saved");
                statusColor = 0xFF99DD99;
            } else {
                var cause = failure.getCause() == null ? failure : failure.getCause();
                status = ui("save_failed", Objects.toString(cause.getMessage(), cause.getClass().getSimpleName()));
                statusColor = 0xFFFF7777;
            }
            if (client.gui.screen() == this) refreshRows();
        }));
    }

    @Override public void tick() {
        if (readOnly()) {
            var snapshot = draft.store.effectiveValues();
            if (snapshot != draft.shownSnapshot) {
                draft.load(true);
                refreshRows();
            }
        }
        refreshControls();
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= ROW_TOP && y < height - 64 && vertical != 0) {
            changePage(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    private String fit(String text, int pixels) {
        if (font.width(text) <= pixels) return text;
        return font.plainSubstrByWidth(text, Math.max(0, pixels - font.width("…"))) + "…";
    }

    private void tooltip(GuiGraphicsExtractor gui, Component message, int mouseX, int mouseY) {
        var lines = Tooltip.splitTooltip(minecraft, message);
        int left = 0, top = 0, right = 0, bottom = 0, lineY = 0;
        for (var line : lines) {
            // Native tooltip widths use advance; actual glyphs and their shadow can extend further.
            var bounds = font.prepareText(line, 0, lineY, 0xFFFFFFFF, true, false, 0).bounds();
            if (bounds != null) {
                left = Math.min(left, bounds.left());
                top = Math.min(top, bounds.top());
                right = Math.max(right, bounds.right());
                bottom = Math.max(bottom, bounds.bottom());
            }
            lineY += ClientTooltipComponent.create(line).getHeight(font);
        }
        var ink = new ScreenRectangle(left, top, right - left, bottom - top);
        gui.setTooltipForNextFrame(font, lines, (guiWidth, guiHeight, x, y, tooltipWidth, tooltipHeight) -> {
            int layoutRight = Math.max(tooltipWidth, ink.right());
            int layoutBottom = Math.max(tooltipHeight, ink.bottom());
            var position = DefaultTooltipPositioner.INSTANCE.positionTooltip(guiWidth, guiHeight, x, y,
                layoutRight - ink.left(), layoutBottom - ink.top());
            return new Vector2i(
                Math.clamp(position.x() - ink.left(), -ink.left(), Math.max(-ink.left(), guiWidth - layoutRight)),
                Math.clamp(position.y() - ink.top(), -ink.top(), Math.max(-ink.top(), guiHeight - layoutBottom)));
        }, mouseX, mouseY, false);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        gui.centeredText(font, title, width / 2, 8, 0xFFE4BB67);
        for (var row : visibleRows) {
            var field = row.field();
            int labelWidth = valueX - left - 6;
            int color = field.error != null ? 0xFFFF7777 : field.dirty() ? 0xFFE4BB67 : 0xFFE0E0E0;
            gui.text(font, fit(label(field.definition).getString(), labelWidth), left, row.y() + 2, color, false);
            var hint = field.error != null ? field.error
                : field.definition.comments().isEmpty() ? field.definition.key() : field.definition.comments().getFirst();
            gui.text(font, fit(hint, labelWidth), left, row.y() + 14, 0xFF999999, false);
            if (mouseX >= left && mouseX < valueX && mouseY >= row.y() && mouseY < row.y() + ROW_HEIGHT)
                tooltip(gui, field.tooltip(), mouseX, mouseY);
        }
        if (visibleRows.isEmpty()) gui.centeredText(font, ui("no_results"), width / 2, ROW_TOP + 8, 0xFFAAAAAA);
        int count = draft.filtered().size();
        int pages = Math.max(1, (count + rowsPerPage - 1) / rowsPerPage);
        gui.centeredText(font, ui("page", draft.page + 1, pages, count), width / 2, height - 39, 0xFFCCCCCC);
        var message = status;
        int color = statusColor;
        if (!saving && !draft.validContext()) { message = ui("context_changed"); color = 0xFFFFBB77; }
        else if (!saving && readOnly()) {
            message = ui(draft.store.hasCompleteRemoteSnapshot() ? "remote_read_only" : "waiting_snapshot");
            color = 0xFFFFCC88;
        } else if (!saving && draft.file == null) { message = ui("not_loaded"); color = 0xFFFFBB77; }
        else if (!saving && draft.dirty() && message.getString().isEmpty()) message = ui("unsaved");
        gui.text(font, fit(message.getString(), panelWidth), left, height - 59, color, false);
        if (!message.getString().isEmpty() && mouseY >= height - 61 && mouseY < height - 47)
            tooltip(gui, message, mouseX, mouseY);
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
    }

    private record VisibleRow(Field field, int y) {}

    private static final class Draft {
        final ConfigScope scope;
        final NativeConfigStore store;
        final Path file;
        final MinecraftServer owner;
        final Object connection;
        final boolean remoteView;
        final LinkedHashMap<String, Field> fields = new LinkedHashMap<>();
        final List<String> groups;
        Map<String, Object> shownSnapshot;
        String group = "", filter = "";
        int page;

        Draft(ConfigScope scope) {
            this.scope = scope;
            store = AllConfigs.store(scope);
            file = store.file().orElse(null);
            owner = scope == ConfigScope.SERVER ? AllConfigs.activeServer().orElse(null) : null;
            connection = scope == ConfigScope.SERVER ? Minecraft.getInstance().getConnection() : null;
            remoteView = scope == ConfigScope.SERVER && remoteSession(store);
            load(remoteView);
            var ordered = new LinkedHashSet<String>();
            ordered.add("");
            fields.values().forEach(field -> ordered.add(field.group()));
            groups = List.copyOf(ordered);
        }

        void load(boolean remote) {
            shownSnapshot = remote ? store.effectiveValues() : store.serverValues();
            fields.clear();
            for (var definition : store.definitions())
                fields.put(definition.key(), new Field(definition,
                    shownSnapshot.getOrDefault(definition.key(), definition.defaultValue())));
        }

        boolean validContext() {
            return validAuthority() && (scope != ConfigScope.SERVER
                || connection == Minecraft.getInstance().getConnection() && remoteView == remoteSession(store));
        }

        boolean validAuthority() {
            return Objects.equals(file, store.file().orElse(null))
                && (scope != ConfigScope.SERVER || AllConfigs.activeServer().orElse(null) == owner);
        }

        boolean dirty() { return fields.values().stream().anyMatch(Field::dirty); }

        List<Field> filtered() {
            String query = filter.toLowerCase(Locale.ROOT).trim();
            return fields.values().stream().filter(field -> group.isEmpty() || field.group().equals(group))
                .filter(field -> query.isEmpty() || (field.definition.key() + " " + label(field.definition).getString()
                    + " " + String.join(" ", field.definition.comments())).toLowerCase(Locale.ROOT).contains(query)).toList();
        }
    }

    private static final class Field {
        final NativeConfigBase.CValue<?> definition;
        final Object original;
        Object value;
        String raw, error;

        Field(NativeConfigBase.CValue<?> definition, Object original) {
            this.definition = definition;
            this.original = original;
            value = original;
            raw = String.valueOf(original);
        }

        String group() {
            var path = definition.path();
            return String.join(".", path.subList(0, path.size() - 1));
        }

        boolean dirty() { return error != null || !Objects.equals(original, value); }

        void set(Object next) {
            value = definition.validate(next);
            raw = String.valueOf(value);
            error = null;
        }

        void number(String next) {
            raw = next;
            try {
                String trimmed = next.trim();
                var encoded = trimmed.equals("NaN") || trimmed.equals("Infinity") || trimmed.equals("-Infinity")
                    ? new JsonPrimitive(trimmed) : new JsonPrimitive(new BigDecimal(trimmed));
                value = definition.decode(encoded);
                error = null;
            } catch (RuntimeException failure) {
                error = ui("invalid_value").getString();
            }
        }

        void defaults() { set(definition.defaultValue()); }

        Component tooltip() {
            var text = label(definition).copy();
            for (var comment : definition.comments()) text.append("\n").append(comment);
            text.append("\n").append(ui("default", String.valueOf(definition.defaultValue())));
            if (definition.minimum().isPresent() && definition.maximum().isPresent())
                text.append("\n").append(ui("bounds", definition.minimum().get(), definition.maximum().get()));
            if (definition instanceof NativeConfigBase.ConfigEnum<?> choices)
                text.append("\n").append(choices.choices().toString());
            if (error != null) text.append("\n").append(error);
            return text;
        }
    }
}
