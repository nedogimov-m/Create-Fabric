// Catnip1.0.82 source outline, MIT; native geometry extraction adaptation.
package net.createmod.catnip.outliner;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
public class LineOutline extends Outline {
  protected Vec3 start=Vec3.ZERO,end=Vec3.ZERO;
  public LineOutline set(Vec3 start,Vec3 end){this.start=start;this.end=end;return this;}
  public LineOutline set(Vector3d start,Vector3d end){return set(new Vec3(start.x,start.y,start.z),new Vec3(end.x,end.y,end.z));}
  @Override public Geometry extract(Vec3 camera,float partial){return geometry(List.of(new Segment(start,end)),List.of(),List.of());}
  public static class EndChasingLineOutline extends LineOutline {
    private float progress,prevProgress;
    private final boolean lockStart;
    public EndChasingLineOutline(boolean lockStart){this.lockStart=lockStart;}
    public EndChasingLineOutline setProgress(float value){prevProgress=progress;progress=value;return this;}
    @Override public Geometry extract(Vec3 camera,float partial){
      float amount=Mth.lerp(partial,prevProgress,progress);
      Vec3 fixed=lockStart?start:end;
      if(!lockStart)amount=1-amount;
      Vec3 moving=fixed.add(start.subtract(fixed).scale(amount));
      return geometry(List.of(new Segment(moving,fixed)),List.of(),List.of());
    }
  }
}
