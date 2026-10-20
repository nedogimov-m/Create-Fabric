// Catnip source interface, MIT; native26.3 GUI extraction boundary.
package net.createmod.catnip.gui.element;
import net.minecraft.client.gui.GuiGraphicsExtractor;
@FunctionalInterface
public interface ScreenElement {
  void render(GuiGraphicsExtractor graphics,int x,int y);
}
