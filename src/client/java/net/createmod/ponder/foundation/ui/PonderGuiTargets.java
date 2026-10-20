// Native26.3 exact-instance GUI output scope; source navigation capture only.
package net.createmod.ponder.foundation.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.IdentityHashMap;
import net.minecraft.client.gui.render.GuiRenderer;

/** Redirects only the named nested native GUI renderer, and never GameRenderer's actual main target. */
public final class PonderGuiTargets {
    private static final ThreadLocal<IdentityHashMap<GuiRenderer, RenderTarget>> SCOPES = new ThreadLocal<>();
    public static final class Scope implements AutoCloseable {
        private final GuiRenderer renderer;
        private final RenderTarget target;
        private boolean closed;
        private Scope(GuiRenderer renderer, RenderTarget target) { this.renderer = renderer; this.target = target; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            var scopes = SCOPES.get();
            RenderTarget removed = scopes.remove(renderer);
            if (scopes.isEmpty()) SCOPES.remove();
            if (removed != target) throw new IllegalStateException("Ponder native GUI output scope ownership changed");
        }
    }
    public static Scope enter(GuiRenderer renderer, RenderTarget target) {
        var scopes = SCOPES.get();
        if (scopes == null) { scopes = new IdentityHashMap<>(); SCOPES.set(scopes); }
        if (scopes.containsKey(renderer)) throw new IllegalStateException("Ponder native GUI renderer scope cannot recurse");
        scopes.put(renderer, target); return new Scope(renderer, target);
    }
    public static RenderTarget targetFor(GuiRenderer renderer, RenderTarget ordinaryTarget) {
        var scopes = SCOPES.get();
        RenderTarget captured = scopes == null ? null : scopes.get(renderer); return captured == null ? ordinaryTarget : captured;
    }
    private PonderGuiTargets() {}
}
