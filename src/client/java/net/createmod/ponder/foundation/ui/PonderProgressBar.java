// Ponder1.0.82 keyframe selection algorithm, MIT; native26.3 timeline widget.
package net.createmod.ponder.foundation.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class PonderProgressBar extends AbstractWidget {
    private final PonderUI ponder;
    private final LerpedFloat progress = LerpedFloat.linear().startWithValue(0);
    public PonderProgressBar(PonderUI ponder, int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("ponder.ui.replay")); this.ponder = ponder;
    }
    public void tick() { progress.chase(ponder.getActiveScene().getSceneProgress(), .5f, LerpedFloat.Chaser.EXP); progress.tickChaser(); }
    public int getHoveredKeyframeIndex(PonderScene scene, double mouseX) {
        int count = scene.getKeyframeCount();
        if (count == 0 || scene.getTotalTime() <= 0) return -1;
        int time = (int) ((mouseX - getX()) / (double) width * scene.getTotalTime());
        int last = scene.getKeyframeTime(count - 1);
        int diffToEnd = scene.getTotalTime() - time, diffToLast = time - last;
        if (diffToEnd >= 0 && diffToEnd < diffToLast / 2) return count;
        int index = -1;
        for (int i = 0; i < count; i++) { if (scene.getKeyframeTime(i) > time) break; index = i; }
        return index;
    }
    private void seek(double mouseX) {
        var scene = ponder.getActiveScene(); int index = getHoveredKeyframeIndex(scene, mouseX);
        ponder.seekToTime(index < 0 ? 0 : index == scene.getKeyframeCount() ? scene.getTotalTime() : scene.getKeyframeTime(index));
    }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { seek(event.x()); }
    @Override public boolean keyPressed(KeyEvent event) {
        if (!isFocused()) return false;
        var scene = ponder.getActiveScene();
        if (event.key() == InputConstants.KEY_HOME) { ponder.seekToTime(0); return true; }
        if (event.key() == InputConstants.KEY_END) { ponder.seekToTime(scene.getTotalTime()); return true; }
        if (event.key() != InputConstants.KEY_LEFT && event.key() != InputConstants.KEY_RIGHT) return false;
        int target = event.key() == InputConstants.KEY_RIGHT ? scene.getTotalTime() : 0;
        for (int i = 0; i < scene.getKeyframeCount(); i++) {
            int keyframe = scene.getKeyframeTime(i);
            if (event.key() == InputConstants.KEY_RIGHT && keyframe > scene.getCurrentTime()) { target = keyframe; break; }
            if (event.key() == InputConstants.KEY_LEFT && keyframe < scene.getCurrentTime()) target = keyframe;
        }
        ponder.seekToTime(target); return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput narration) { defaultButtonNarrationText(narration); }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        gui.fill(getX(), getY() + 3, getRight(), getY() + 7, 0x80404060);
        int filled = Math.clamp((int) (width * progress.getValue(partial)), 0, width);
        gui.fillGradient(getX(), getY() + 3, getX() + filled, getY() + 7, 0xFFAAAADD, 0xFF606090);
        var scene = ponder.getActiveScene(); int total = Math.max(1, scene.getTotalTime());
        int hover = isHoveredOrFocused() ? getHoveredKeyframeIndex(scene, mouseX) : -2;
        for (int i = 0; i < scene.getKeyframeCount(); i++) {
            int x = getX() + scene.getKeyframeTime(i) * width / total;
            gui.fill(x, getY(), x + 2, getY() + (hover == i ? 12 : 9), hover == i ? 0xFFFFFFFF : 0xB0AAAADD);
        }
        if (isHoveredOrFocused()) {
            int time = hover < 0 ? 0 : hover == scene.getKeyframeCount() ? scene.getTotalTime() : scene.getKeyframeTime(hover);
            gui.setTooltipForNextFrame(Component.literal(time / 20 + " s"), mouseX, mouseY);
        }
    }
}
