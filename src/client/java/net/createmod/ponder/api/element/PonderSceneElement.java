// Ponder/Catnip 1.0.82 element lifecycle, MIT; native26.3 detached extraction contract.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.api.element;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jetbrains.annotations.Nullable;

/** A scene element with an optional independent native renderer. */
public interface PonderSceneElement extends PonderElement {
    /**
     * Capture on the client extraction thread while the actual scene is available. Return a fresh,
     * detached immutable snapshot, or null for an element rendered by another native adapter.
     * The returned state must not retain this element, its scene/world, mutable source stacks,
     * or callbacks that close over them. Copy mutable transforms and other caller-owned values.
     */
    default @Nullable RenderState extractSceneRenderState(PonderScene scene,float partialTicks) { return null; }

    /** Source first/layer/last ordering, adapted to native queued rendering. */
    enum Phase { BEFORE_WORLD, WORLD, AFTER_WORLD }

    /**
     * Detached native render content. It is called once in each phase, with an isolated pose scope.
     * BEFORE_WORLD corresponds to renderFirst. WORLD follows captured block/BE sections and replaces
     * the old per-RenderType renderLayer loop: enqueue every required native opaque/translucent
     * layer through SubmitNodeCollector in this one call; Minecraft owns layer batching and draws.
     * AFTER_WORLD corresponds to renderLast, after captured cart/parrot content and before ordinary
     * captured world entities and the viewport's fluid/particle/outline passes. The phase describes
     * submission order; native queues still own actual pipeline batching and drawing.
     * No live source object is passed here.
     * Do not modify shared render resources, flush queues, or change render targets/projection.
     */
    @FunctionalInterface
    interface RenderState {
        void submit(Phase phase,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera);
    }
}
