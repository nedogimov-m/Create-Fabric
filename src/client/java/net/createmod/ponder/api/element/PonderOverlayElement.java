// Ponder/Catnip 1.0.82 element lifecycle, MIT; native26.3 detached overlay extraction contract.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.element;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

/** Overlay content captured independently of builtin text/input element implementations. */
public interface PonderOverlayElement extends PonderElement {
    /**
     * Capture a fresh immutable snapshot while scene/transform data is available. Coordinates
     * projected by scene.getTransform().sceneToScreen are local to the supplied viewport.
     * The returned state must not retain the element, scene/world, mutable source stacks, or
     * callbacks closing over them. Copy caller-owned transforms and other mutable values.
     */
    default @Nullable RenderState extractOverlayRenderState(PonderScene scene,float partialTicks) { return null; }

    enum Phase { BEFORE_BUILTINS, AFTER_BUILTINS }
    record Viewport(int x,int y,int width,int height) {}

    /**
     * Emit actual native GUI states from detached values; Minecraft owns their later drawing.
     * Both phases run at the source scene-overlay stage, before the screen HUD/widgets. Builtin
     * text/input overlays lie between the two phases. Identify mode suppresses the entire stage.
     * Each invocation has its own GUI pose scope. Restore any clipping/stratum changes you make.
     * The font/GUI/viewport are render infrastructure, never authority to read a live source world.
     */
    @FunctionalInterface
    interface RenderState {
        void extract(Phase phase,GuiGraphicsExtractor gui,Font font,Viewport viewport);
    }
}
