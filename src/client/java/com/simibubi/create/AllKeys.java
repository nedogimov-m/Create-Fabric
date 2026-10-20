package com.simibubi.create;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLMouse;

/** Create modifier bindings read their assigned physical key, including while a GUI has focus.
 * Native 26.3 supports multiple mappings per key; the old pre-1.21.9 Fabric MAP workaround
 * from Catnip's ConflictSafeKeyMapping is intentionally obsolete. */
public enum AllKeys {
    TOOL_MENU("toolmenu", "Focus Schematic Overlay", () -> com.simibubi.create.content.schematics.client.SchematicsClient.TOOL_MENU),
    ACTIVATE_TOOL(InputConstants.KEY_LCONTROL),
    TOOLBELT("toolbelt", "Access Nearby Toolboxes", () -> com.simibubi.create.content.equipment.toolbox.ToolboxHandlerClient.TOOLBELT),
    ROTATE_MENU("rotate_menu", "Open Block Rotation Menu", () -> com.simibubi.create.content.contraptions.wrench.RadialWrenchHandler.ROTATE_MENU),
    SHIFT_MODIFIER("shift_modifier", InputConstants.KEY_LSHIFT, "Shift Modifier"),
    CTRL_MODIFIER("ctrl_modifier", InputConstants.KEY_LCONTROL, "Ctrl Modifier"),
    ALT_MODIFIER("alt_modifier", InputConstants.KEY_LALT, "Alt Modifier");

    private static final class CategoryHolder {
        private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Create.asResource("modifiers"));
    }
    private static boolean registered;
    private final KeyMapping mapping, fixedMapping;
    private final Supplier<KeyMapping> existing;
    private final int fixedKey;
    private final String description, translation;

    AllKeys(String description, int key, String translation) {
        this.description = "create.keyinfo." + description;
        this.translation = translation;
        mapping = new KeyMapping(this.description, key, CategoryHolder.CATEGORY);
        existing = null;
        fixedMapping = null;
        fixedKey = InputConstants.UNKNOWN.getValue();
    }
    AllKeys(String description, String translation, Supplier<KeyMapping> existing) {
        this.existing = existing;
        mapping = fixedMapping = null;
        fixedKey = InputConstants.UNKNOWN.getValue();
        this.description = "create.keyinfo." + description;
        this.translation = translation;
    }
    AllKeys(int fixedKey) {
        this.fixedKey = fixedKey;
        mapping = null;
        fixedMapping = new KeyMapping("create.keyinfo.", fixedKey, CategoryHolder.CATEGORY);
        existing = null;
        description = translation = "";
    }

    public static void register() {
        if (registered) return;
        registered = true;
        for (var key : values()) if (key.mapping != null) KeyMappingHelper.registerKeyMapping(key.mapping);
    }
    public static void provideLang(BiConsumer<String, String> consumer) {
        for (var key : values()) if (!key.description.isEmpty()) consumer.accept(key.description, key.translation);
    }
    public KeyMapping getKeybind() { return existing == null ? (mapping == null ? fixedMapping : mapping) : existing.get(); }
    public boolean isPressed() {
        if (this == ACTIVATE_TOOL) return isKeyDown(fixedKey);
        if (mapping != null) return physicallyDown(mapping);
        return getKeybind().isDown();
    }
    public String getBoundKey() {
        return (this == ACTIVATE_TOOL ? InputConstants.Type.KEYBOARD.getOrCreate(fixedKey).getDisplayName()
            : getKeybind().getTranslatedKeyMessage()).getString().toUpperCase(Locale.ROOT);
    }
    public boolean doesModifierAndCodeMatch(int code) {
        if (this == ACTIVATE_TOOL) return code == fixedKey;
        var bound = KeyMappingHelper.getBoundKeyOf(getKeybind());
        return bound.getType() == InputConstants.Type.KEYBOARD && bound.getValue() == code;
    }
    public static boolean isKeyDown(int key) {
        return Minecraft.getInstance().getWindow().isFocused()
            && key != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(key);
    }
    public static boolean isMouseButtonDown(int button) {
        return Minecraft.getInstance().getWindow().isFocused() && button >= 1 && button <= Integer.SIZE
            && (SDLMouse.nSDL_GetMouseState(0L, 0L) & (1 << (button - 1))) != 0;
    }
    private static boolean physicallyDown(KeyMapping mapping) {
        if (mapping.isUnbound() || !Minecraft.getInstance().getWindow().isFocused()) return false;
        var bound = KeyMappingHelper.getBoundKeyOf(mapping);
        if (bound.getType() == InputConstants.Type.KEYBOARD) return isKeyDown(bound.getValue());
        return bound.getType() == InputConstants.Type.MOUSE && isMouseButtonDown(bound.getValue());
    }
    public static boolean ctrlDown() { return CTRL_MODIFIER.isPressed(); }
    public static boolean shiftDown() { return SHIFT_MODIFIER.isPressed(); }
    public static boolean altDown() { return ALT_MODIFIER.isPressed(); }
}
