// Ponder1.0.82 text/input overlays, MIT; native26.3 GUI extraction over captured scene content.
package net.createmod.ponder.foundation.ui;

import java.util.List;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.render.PonderSceneFrame;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec2;

final class PonderOverlayRenderer {
    static void extract(GuiGraphicsExtractor gui, Font font, PonderScene scene, PonderSceneFrame frame,
                        int x, int y, int width, int height, float partial) {
        var viewport=new PonderOverlayElement.Viewport(x,y,width,height);
        extractElements(frame,PonderOverlayElement.Phase.BEFORE_BUILTINS,gui,font,viewport);
        for (var text : frame.texts()) {
            if (text.fade() < .05f) continue;
            Vec2 point = text.target() == null ? new Vec2(width / 2f, (height - 200) / 2f + text.independentY() - 8)
                : scene.getTransform().sceneToScreen(text.target(), partial);
            float yDiff = (height / 2f - point.y - 10) / 100f;
            float targetX = width * (0.75f + yDiff * yDiff * (0.625f - 0.75f));
            if (text.nearScene()) targetX = Math.min(targetX, point.x + 50);
            int textWidth = Math.max(24, (int) Math.min(width - targetX, 180));
            var content = highlighted(text.text(), text.palette().getColor());
            var lines = font.split(content, textWidth);
            int boxWidth = Math.max(16, lines.stream().mapToInt(font::width).max().orElse(16)) + 12;
            int boxHeight = Math.max(18, lines.size() * 10 + 10);
            int boxX = x + (int) targetX - 10, boxY = y + (int) point.y + 3;
            int alpha = Math.clamp((int) (text.fade() * 255), 0, 255);
            box(gui, boxX, boxY, boxWidth, boxHeight, alpha << 24 | text.palette().getColor() & 0xFFFFFF);
            for (int i = 0; i < lines.size(); i++) gui.text(font, lines.get(i), boxX + 6, boxY + 5 + i * 10, alpha << 24 | 0xEEEEEE, false);
            if (text.target() != null) {
                int px = x + (int) point.x, py = y + (int) point.y;
                int edgeX = Math.clamp(px, boxX, boxX + boxWidth), edgeY = Math.clamp(py, boxY, boxY + boxHeight);
                gui.fill(Math.min(edgeX, px), edgeY, Math.max(edgeX, px) + 1, edgeY + 1, alpha << 24 | 0xCCCCFF);
                gui.fill(px, Math.min(py, edgeY), px + 1, Math.max(py, edgeY) + 1, alpha << 24 | 0xCCCCFF);
            }
        }
        for (var input : frame.inputs()) {
            if (input.fade() < .05f) continue;
            var point = scene.getTransform().sceneToScreen(input.position(), partial);
            int boxWidth = (input.icon() == null ? 0 : 20) + (input.item().isEmpty() ? 0 : 20) + font.width(input.keyText()) + 8;
            int boxX = x + (int) point.x, boxY = y + (int) point.y;
            switch (input.direction()) {
                case DOWN -> { boxX -= boxWidth / 2; boxY -= 30; }
                case UP -> { boxX -= boxWidth / 2; boxY += 9; }
                case LEFT -> { boxX += 9; boxY -= 12; }
                case RIGHT -> { boxX -= boxWidth + 9; boxY -= 12; }
            }
            box(gui, boxX, boxY, boxWidth, 24, Math.clamp((int) (input.fade() * 255), 0, 255) << 24 | 0xC0C0FF);
            int cursor = boxX + 4;
            if (!input.keyText().isEmpty()) { gui.text(font, input.keyText(), cursor, boxY + 8, 0xFFCCCCFF); cursor += font.width(input.keyText()) + 4; }
            if (input.icon() != null) { input.icon().render(gui, cursor, boxY + 4); cursor += 20; }
            if (!input.item().isEmpty()) gui.item(input.item(), cursor, boxY + 4);
        }
        extractElements(frame,PonderOverlayElement.Phase.AFTER_BUILTINS,gui,font,viewport);
    }
    private static void extractElements(PonderSceneFrame frame,PonderOverlayElement.Phase phase,
                                        GuiGraphicsExtractor gui,Font font,PonderOverlayElement.Viewport viewport) {
        for(var state:frame.overlayElements()) {
            gui.pose().pushMatrix();
            try {state.extract(phase,gui,font,viewport);}
            finally {gui.pose().popMatrix();}
        }
    }
    private static MutableComponent highlighted(String value, int color) {
        MutableComponent result = Component.empty(); String[] parts = value.split("_", -1);
        for (int i = 0; i < parts.length; i++) {
            int rgb = i % 2 == 1 ? color & 0xFFFFFF : 0xEEEEEE;
            result.append(Component.literal(parts[i]).withStyle(style -> style.withColor(rgb)));
        }
        return result;
    }
    private static void box(GuiGraphicsExtractor gui, int x, int y, int width, int height, int color) {
        gui.fill(x, y, x + width, y + height, color);
        gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, (color & 0xFF000000) | 0x101018);
    }
    private PonderOverlayRenderer() {}
}
