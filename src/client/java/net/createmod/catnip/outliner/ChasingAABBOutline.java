// Catnip1.0.82 source outline, MIT; native geometry extraction adaptation.
package net.createmod.catnip.outliner;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
public class ChasingAABBOutline extends AABBOutline {
  AABB targetBB,prevBB;
  public ChasingAABBOutline(AABB box){super(box);targetBB=prevBB=box.inflate(0);}
  public void target(AABB target){targetBB=target;}
  @Override public void tick(){prevBB=bb;setBounds(interpolate(bb,targetBB,.5f));}
  @Override public Geometry extract(Vec3 camera,float partial){return box(interpolate(prevBB,bb,partial),camera);}
  private static AABB interpolate(AABB from,AABB to,float partial){
    return new AABB(Mth.lerp(partial,from.minX,to.minX),Mth.lerp(partial,from.minY,to.minY),Mth.lerp(partial,from.minZ,to.minZ),Mth.lerp(partial,from.maxX,to.maxX),Mth.lerp(partial,from.maxY,to.maxY),Mth.lerp(partial,from.maxZ,to.maxZ));
  }
}
