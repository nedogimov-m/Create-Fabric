// Catnip1.0.82 source outline, MIT; native geometry extraction adaptation.
package net.createmod.catnip.outliner;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
public class ItemOutline extends Outline {
  protected final Vec3 pos;
  protected final ItemStack stack;
  public ItemOutline(Vec3 pos,ItemStack stack){this.pos=pos;this.stack=stack;}
  @Override public Geometry extract(Vec3 camera,float partial){return geometry(List.of(),List.of(),List.of(new Item(pos,stack,params.alpha)));}
}
