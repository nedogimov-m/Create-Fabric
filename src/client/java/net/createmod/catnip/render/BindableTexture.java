// Catnip source texture identity, MIT; native extraction owns binding/submission.
package net.createmod.catnip.render;
import net.minecraft.resources.Identifier;
@FunctionalInterface
public interface BindableTexture {
  Identifier getLocation();
}
