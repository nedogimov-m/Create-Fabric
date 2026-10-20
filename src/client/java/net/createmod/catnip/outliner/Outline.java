// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.catnip.outliner;
import java.util.List;
import net.createmod.catnip.render.BindableTexture;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

/** Actual source outline state with immutable geometry extraction in place of the removed GL renderer. */
public abstract class Outline {
  protected final OutlineParams params=new OutlineParams();
  public OutlineParams getParams(){return params;}
  public void tick(){}
  public abstract Geometry extract(Vec3 camera,float partial);
  /** Axis-aligned source box/cluster edges keep world-axis normals; free lines retain source yaw/pitch. */
  public record Segment(Vec3 start,Vec3 end,boolean axisAligned){
    public Segment(Vec3 start,Vec3 end){this(start,end,false);}
  }
  public record Face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,Direction normal,
      net.minecraft.resources.Identifier texture,int color,float maxU,float maxV,boolean cull){}
  public record Item(Vec3 position,ItemStack stack,float scale){ public Item{stack=stack.copy();} @Override public ItemStack stack(){return stack.copy();} }
  public record Geometry(List<Segment> segments,List<Face> faces,List<Item> items,int color,float width,int light,boolean disableNormals){
    public Geometry{segments=List.copyOf(segments);faces=List.copyOf(faces);items=List.copyOf(items);}
  }
  protected int color(){
    var value=new Vector4f();params.loadColor(value);
    return ((int)(value.w*255)&255)<<24|((int)(value.x*255)&255)<<16|((int)(value.y*255)&255)<<8|((int)(value.z*255)&255);
  }
  protected Geometry geometry(List<Segment> lines,List<Face> faces,List<Item> items){
    return new Geometry(lines,faces,items,color(),params.getLineWidth(),params.lightmap,params.disableLineNormals);
  }
	public static class OutlineParams {
		@Nullable
		protected BindableTexture faceTexture;
		@Nullable
		protected BindableTexture highlightedFaceTexture;
		@Nullable
		Direction highlightedFace;
		protected boolean fadeLineWidth;
		protected boolean disableCull;
		protected boolean disableLineNormals;
		protected float alpha;
		protected int lightmap;
		protected Color rgb;
		private float lineWidth;

		public OutlineParams() {
			faceTexture = highlightedFaceTexture = null;
			alpha = 1;
			lineWidth = 1 / 32f;
			fadeLineWidth = true;
			rgb = Color.WHITE;
			lightmap = net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
		}

		// builder

		public OutlineParams colored(int color) {
			rgb = new Color(color, false);
			return this;
		}

		public OutlineParams colored(Color c) {
			rgb = c.copy();
			return this;
		}

		public OutlineParams lightmap(int light) {
			lightmap = light;
			return this;
		}

		public OutlineParams lineWidth(float width) {
			this.lineWidth = width;
			return this;
		}

		public OutlineParams withFaceTexture(@Nullable BindableTexture texture) {
			this.faceTexture = texture;
			return this;
		}

		public OutlineParams clearTextures() {
			return this.withFaceTextures(null, null);
		}

		public OutlineParams withFaceTextures(@Nullable BindableTexture texture, @Nullable BindableTexture highlightTexture) {
			this.faceTexture = texture;
			this.highlightedFaceTexture = highlightTexture;
			return this;
		}

		public OutlineParams highlightFace(@Nullable Direction face) {
			highlightedFace = face;
			return this;
		}

		public OutlineParams disableLineNormals() {
			disableLineNormals = true;
			return this;
		}

		public OutlineParams disableCull() {
			disableCull = true;
			return this;
		}

		// getter

		public float getLineWidth() {
			return fadeLineWidth ? alpha * lineWidth : lineWidth;
		}

		@Nullable
		public Direction getHighlightedFace() {
			return highlightedFace;
		}

		public void loadColor(Vector4f vec) {
			vec.set(rgb.getRedAsFloat(), rgb.getGreenAsFloat(), rgb.getBlueAsFloat(), rgb.getAlphaAsFloat() * alpha);
		}
	}

}
