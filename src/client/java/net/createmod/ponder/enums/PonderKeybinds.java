// Ponder1.0.82 hold key, MIT; native26.3 Fabric key mapping and in-screen key polling.
package net.createmod.ponder.enums;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.createmod.ponder.Ponder;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLMouse;

public enum PonderKeybinds {
    PONDER("ponder", InputConstants.KEY_W);
    private final KeyMapping mapping;
    PonderKeybinds(String description, int defaultKey) {
        mapping = new KeyMapping("key.ponder." + description, defaultKey, KeyMapping.Category.register(Ponder.asResource("ponder")));
    }
    public static void register(Consumer<KeyMapping> registration) { for (var key : values()) registration.accept(key.mapping); }
    public boolean isDown() {
        if (mapping.isUnbound() || !Minecraft.getInstance().getWindow().isFocused()) return false;
        var bound = KeyMappingHelper.getBoundKeyOf(mapping);
        if (bound.getType() == InputConstants.Type.KEYBOARD) return InputConstants.isKeyDown(bound.getValue());
        if (bound.getType() != InputConstants.Type.MOUSE) return false;
        int button = bound.getValue();
        // Actual SDL3 keyboard/mouse state remains available while native inventory screens
        // consume input. Native SDL_BUTTON_MASK is private and computes 1 << (button - 1).
        return button >= 1 && button <= Integer.SIZE
            && (SDLMouse.nSDL_GetMouseState(0L, 0L) & 1 << (button - 1)) != 0;
    }
    public Component message() { return mapping.getTranslatedKeyMessage(); }
}
