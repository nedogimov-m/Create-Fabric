// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.catnip.outliner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.Vec3;

public class BlockClusterOutline extends Outline {
  private final Cluster cluster=new Cluster();
  public BlockClusterOutline(Iterable<BlockPos> positions){positions.forEach(cluster::include);}
  @Override public Geometry extract(Vec3 camera,float partial){
    var lines=new ArrayList<Segment>();var faces=new ArrayList<Face>();
    if(cluster.isEmpty())return geometry(lines,faces,List.of());
    cluster.visibleEdges.forEach(edge->{
      var from=Vec3.atLowerCornerOf(edge.pos.offset(cluster.anchor));
      var side=Direction.get(AxisDirection.POSITIVE,edge.axis);
      lines.add(new Segment(from,from.add(side.getStepX(),side.getStepY(),side.getStepZ()),true));
    });
    if(params.faceTexture!=null)cluster.visibleFaces.forEach((face,axisDirection)->{
      var side=Direction.get(axisDirection,face.axis);var pos=face.pos.offset(cluster.anchor);
      if(axisDirection==AxisDirection.POSITIVE)pos=pos.relative(side.getOpposite());
      Vec3 a,b,c,d;var p=Vec3.atLowerCornerOf(pos).add(side.getStepX()/128f,side.getStepY()/128f,side.getStepZ()/128f);
      switch(side){
        case DOWN->{a=p.add(0,0,1);b=p;c=p.add(1,0,0);d=p.add(1,0,1);}
        case UP->{a=p.add(0,1,0);b=p.add(0,1,1);c=p.add(1,1,1);d=p.add(1,1,0);}
        case NORTH->{a=p.add(1,1,0);b=p.add(1,0,0);c=p;d=p.add(0,1,0);}
        case SOUTH->{a=p.add(0,1,1);b=p.add(0,0,1);c=p.add(1,0,1);d=p.add(1,1,1);}
        case WEST->{a=p.add(0,1,0);b=p;c=p.add(0,0,1);d=p.add(0,1,1);}
        case EAST->{a=p.add(1,1,1);b=p.add(1,0,1);c=p.add(1,0,0);d=p.add(1,1,0);}
        default->throw new IllegalStateException();
      }
      faces.add(new Face(a,b,c,d,side,params.faceTexture.getLocation(),color(),1,1,true));
    });
    return geometry(lines,faces,List.of());
  }
	private static class Cluster {

		private BlockPos anchor;
		private final Map<MergeEntry, AxisDirection> visibleFaces;
		private final Set<MergeEntry> visibleEdges;

		public Cluster() {
			visibleEdges = new HashSet<>();
			visibleFaces = new HashMap<>();
		}

		public boolean isEmpty() {
			return anchor == null;
		}

		public void include(BlockPos pos) {
			if (anchor == null)
				anchor = pos.immutable();

			pos = pos.subtract(anchor);

			// 6 FACES
			for (Axis axis : Axis.values()) {
				Direction direction = Direction.get(AxisDirection.POSITIVE, axis);
				for (int offset : new int[]{0,1}) {
					MergeEntry entry = new MergeEntry(axis, pos.relative(direction, offset));
					if (visibleFaces.remove(entry) == null)
						visibleFaces.put(entry, offset == 0 ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE);
				}
			}

			// 12 EDGES
			for (Axis axis : Axis.values()) {
				for (Axis axis2 : Axis.values()) {
					if (axis == axis2)
						continue;
					for (Axis axis3 : Axis.values()) {
						if (axis == axis3)
							continue;
						if (axis2 == axis3)
							continue;

						Direction direction = Direction.get(AxisDirection.POSITIVE, axis2);
						Direction direction2 = Direction.get(AxisDirection.POSITIVE, axis3);

						for (int offset : new int[]{0,1}) {
							BlockPos entryPos = pos.relative(direction, offset);
							for (int offset2 : new int[]{0,1}) {
								entryPos = entryPos.relative(direction2, offset2);
								MergeEntry entry = new MergeEntry(axis, entryPos);
								if (!visibleEdges.remove(entry))
									visibleEdges.add(entry);
							}
						}
					}

					break;
				}
			}

		}

	}

	private static class MergeEntry {

		private final Axis axis;
		private final BlockPos pos;

		public MergeEntry(Axis axis, BlockPos pos) {
			this.axis = axis;
			this.pos = pos;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof MergeEntry other))
				return false;

			return this.axis == other.axis && this.pos.equals(other.pos);
		}

		@Override
		public int hashCode() {
			return this.pos.hashCode() * 31 + axis.ordinal();
		}
	}

}
