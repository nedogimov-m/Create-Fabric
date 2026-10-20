// Catnip1.0.82 source outline, MIT; native geometry extraction adaptation.
package net.createmod.catnip.outliner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
public class AABBOutline extends Outline {
  protected AABB bb;
  public AABBOutline(AABB bounds){bb=bounds;}
  public AABB getBounds(){return bb;}
  public void setBounds(AABB bounds){bb=bounds;}
  @Override public Geometry extract(Vec3 camera,float partial){return box(bb,camera);}
  protected Geometry box(AABB bounds,Vec3 camera){
    boolean inside=bounds.contains(camera);
    boolean cull=!inside&&!params.disableCull;
    bounds=bounds.inflate(inside?-1/128f:1/128f);
    var points=new Vec3[8];
    for(int i=0;i<8;i++)points[i]=new Vec3((i&1)==0?bounds.minX:bounds.maxX,(i&2)==0?bounds.minY:bounds.maxY,(i&4)==0?bounds.minZ:bounds.maxZ);
    var lines=new ArrayList<Segment>();
    for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0)lines.add(new Segment(points[i],points[i|bit],true));
    var faces=new ArrayList<Face>();
    if(params.faceTexture!=null){
      face(faces,points,Direction.DOWN,cull,4,0,1,5);face(faces,points,Direction.UP,cull,2,6,7,3);
      face(faces,points,Direction.NORTH,cull,3,1,0,2);face(faces,points,Direction.SOUTH,cull,6,4,5,7);
      face(faces,points,Direction.WEST,cull,2,0,4,6);face(faces,points,Direction.EAST,cull,7,5,1,3);
    }
    return geometry(lines,faces,List.of());
  }
  private void face(List<Face> faces,Vec3[] p,Direction side,boolean cull,int a,int b,int c,int d){
    int color=color();
    if(side!=params.getHighlightedFace())color=(color&0xFFFFFF)|((color>>>24)/2)<<24;
    faces.add(new Face(p[a],p[b],p[c],p[d],side,params.faceTexture.getLocation(),color,
        (float)p[b].distanceTo(p[c]),(float)p[a].distanceTo(p[b]),cull));
  }
}
