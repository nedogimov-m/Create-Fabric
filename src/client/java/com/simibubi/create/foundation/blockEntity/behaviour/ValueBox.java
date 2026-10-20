package com.simibubi.create.foundation.blockEntity.behaviour;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.IconRenderer;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.outliner.ChasingAABBOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Source value-box data and subclasses, extracted before detached native submission. */
public class ValueBox extends ChasingAABBOutline {
    protected Component label;
    public int overrideColor=-1;
    public boolean isPassive;
    protected ValueBoxTransform transform;
    protected WeakReference<LevelAccessor> level;
    protected BlockPos pos;
    protected BlockState blockState;
    private BlockPos interactionOrigin;
    protected AllIcons outline=AllIcons.VALUE_BOX_HOVER_4PX;
    private WeakReference<BlockEntityBehaviour> actualBehaviour;

    public ValueBox(Component label,AABB bounds,BlockPos pos){this(label,bounds,pos,Minecraft.getInstance().level.getBlockState(pos));}
    public ValueBox(Component label,AABB bounds,BlockPos pos,BlockState state){
        super(bounds);this.label=label.copy();this.pos=pos.immutable();interactionOrigin=this.pos;blockState=state;level=new WeakReference<>(Minecraft.getInstance().level);
    }
    public ValueBox transform(ValueBoxTransform transform){this.transform=transform;return this;}
    public ValueBox wideOutline(){outline=AllIcons.VALUE_BOX_HOVER_6PX;return this;}
    public ValueBox passive(boolean passive){isPassive=passive;return this;}
    public ValueBox withColor(int color){overrideColor=color;return this;}
    /** A display witness only: no setting or inventory is stored in the value box. */
    public ValueBox withOwner(BlockEntityBehaviour behaviour){
        actualBehaviour=new WeakReference<>(behaviour);level=new WeakReference<>(behaviour.getWorld());pos=behaviour.getPos().immutable();blockState=behaviour.blockEntity.getBlockState();return this;
    }
    public AllIcons getOutline(){return outline;}
    public BlockPos position(){return pos;}
    public BlockPos interactionOrigin(){return interactionOrigin;}
    public ValueBox fromInteractionOrigin(BlockPos origin){interactionOrigin=origin.immutable();return this;}
    public boolean isCurrent(){
        var world=level.get();if(world==null||LoadedLevelAccess.blockState(world,pos)!=blockState)return false;
        if(actualBehaviour==null)return true;
        var behaviour=actualBehaviour.get();return behaviour!=null&&!behaviour.blockEntity.isRemoved()
            &&behaviour.getWorld()==world&&LoadedLevelAccess.blockEntity(world,pos)==behaviour.blockEntity
            &&behaviour.blockEntity.getBehaviour(behaviour.getType())==behaviour;
    }
    private Matrix4f positioning(float partial){
        var matrix=new Matrix4f();var world=level.get();
        if(transform instanceof NativeFilterValueBoxTransform nativeFilter)nativeFilter.partial(partial);
        if(transform instanceof ValueBoxTransform.Sided sided&&params.getHighlightedFace()!=null)sided.fromSide(params.getHighlightedFace());
        if(!isCurrent()||transform!=null&&!transform.shouldRender(world,pos,blockState))return null;
        if(transform!=null)transform.transform(world,pos,blockState,matrix);return isCurrent()?matrix:null;
    }
    /** Source Outliner geometry remains functional for consumers of its geometry contract. */
    @Override public Geometry extract(Vec3 camera,float partial){
        var matrix=positioning(partial);if(matrix==null||isPassive)return geometry(List.of(),List.of(),List.of());
        var original=super.extract(camera,partial);var lines=new ArrayList<Segment>();
        var origin=Vec3.atLowerCornerOf(pos);
        for(var line:original.segments())lines.add(new Segment(point(matrix,line.start()).add(origin),point(matrix,line.end()).add(origin)));
        return geometry(lines,List.of(),List.of());
    }
    private static Vec3 point(Matrix4fc matrix,Vec3 point){var vector=new Vector3f((float)point.x,(float)point.y,(float)point.z);matrix.transformPosition(vector);return new Vec3(vector.x,vector.y,vector.z);}

    public sealed interface Draw permits IconDraw,TextDraw {void submit(SubmitNodeCollector collector);}
    public record IconDraw(Matrix4fc matrix,AllIcons icon,int color) implements Draw {
        public IconDraw{matrix=new Matrix4f(matrix);}
        @Override public Matrix4fc matrix(){return new Matrix4f(matrix);}
        @Override public void submit(SubmitNodeCollector collector){var pose=new PoseStack();pose.mulPose(matrix);IconRenderer.submit(icon,pose,collector,color);}
    }
    public record TextDraw(Matrix4fc matrix,FormattedCharSequence text,float x,float y,int color,int outlineColor) implements Draw {
        public TextDraw{matrix=new Matrix4f(matrix);}
        @Override public Matrix4fc matrix(){return new Matrix4f(matrix);}
        @Override public void submit(SubmitNodeCollector collector){
            var pose=new PoseStack();pose.mulPose(matrix);collector.submitText(pose,x,y,text,false,Font.DisplayMode.NORMAL,LightCoordsUtil.FULL_BRIGHT,color,0,outlineColor);
        }
    }
    public record State(BlockPos position,List<Draw> draws,boolean highlighted){
        public State{position=position.immutable();draws=List.copyOf(draws);}
        public void submit(SubmitNodeCollector collector){for(var draw:draws)draw.submit(collector);}
    }
    public State extractRenderState(Vec3 camera,float partial,float alpha){
        var positioning=positioning(partial);if(positioning==null||alpha<=0)return null;
        var pose=new PoseStack();pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);pose.mulPose(positioning);
        var draws=new ArrayList<Draw>();
        if(!isPassive){
            pose.pushPose();pose.scale(-2.01f,-2.01f,2.01f);pose.translate(-.5,-.5,-.5/16d);
            draws.add(new IconDraw(pose.last().pose(),getOutline(),tint(overrideColor==-1?0xFFFFFF:overrideColor,alpha)));pose.popPose();
        }
        float scale=transform==null?-1/64f:-transform.getFontScale();pose.scale(scale,scale,scale);
        extractContents(pose,draws,alpha);
        return isCurrent()?new State(pos,draws,!isPassive):null;
    }
    /** The base source box is a frame; subclasses add their actual extracted contents. */
    protected void extractContents(PoseStack pose,List<Draw> draws,float alpha){}
    protected int contentColor(){int color=overrideColor;if(color==-1&&transform!=null)color=transform.getOverrideColor();return color;}
    protected static int tint(int rgb,float alpha){return Math.clamp(Math.round(alpha*255),0,255)<<24|rgb&0xFFFFFF;}
    protected static void text(PoseStack pose,List<Draw> draws,Component text,int rgb,boolean outlined,float alpha){
        draws.add(new TextDraw(pose.last().pose(),text.copy().getVisualOrderText(),0,0,tint(rgb,alpha),outlined?tint(0x333333,alpha):0));
    }
    public static class TextValueBox extends ValueBox {
        Component text;
        public TextValueBox(Component label,AABB box,BlockPos pos,Component text){super(label,box,pos);this.text=text.copy();}
        public TextValueBox(Component label,AABB box,BlockPos pos,BlockState state,Component text){super(label,box,pos,state);this.text=text.copy();}
        @Override protected void extractContents(PoseStack pose,List<Draw> draws,float alpha){
            var font=Minecraft.getInstance().font;pose.scale(3,3,1);pose.translate(-4,-3.75,5);
            int width=font.width(text);float numberScale=font.lineHeight/(float)Math.max(1,width);boolean single=width<10;if(single)numberScale/=2;
            float margin=(width-font.lineHeight)/2f;pose.scale(numberScale,numberScale,numberScale);pose.translate(single?width/2f:0,single?-margin:margin,0);
            int color=contentColor();text(pose,draws,text,color==-1?0xEDEDED:color,color==-1,alpha);
        }
    }
    public static class IconValueBox extends ValueBox {
        AllIcons icon;
        public IconValueBox(Component label,INamedIconOptions value,AABB bounds,BlockPos pos){super(label,bounds,pos);icon=value.getIcon();}
        @Override protected void extractContents(PoseStack pose,List<Draw> draws,float alpha){
            pose.scale(32,32,32);pose.translate(-.5,-.5,5/32f);int color=contentColor();draws.add(new IconDraw(pose.last().pose(),icon,tint(color==-1?0xFFFFFF:color,alpha)));
        }
    }
    public static class ItemValueBox extends ValueBox {
        ItemStack stack;
        MutableComponent count;
        public ItemValueBox(Component label,AABB bounds,BlockPos pos,ItemStack stack,MutableComponent count){super(label,bounds,pos);this.stack=stack.copy();this.count=count==null?null:count.copy();}
        @Override public AllIcons getOutline(){return stack.isEmpty()?super.getOutline():AllIcons.VALUE_BOX_HOVER_6PX;}
        @Override protected void extractContents(PoseStack pose,List<Draw> draws,float alpha){
            if(count==null||count.getString().isEmpty())return;
            var client=Minecraft.getInstance();var font=client.font;pose.translate(17.5-font.width(count),-5,7);
            boolean filter=FilterItemStack.of(stack).isFilterItem(),empty=stack.isEmpty(),blockItem=false;
            if(!empty&&level.get() instanceof Level world){var item=new ItemStackRenderState();client.getItemModelResolver().updateForTopItem(item,stack,ItemDisplayContext.FIXED,world,null,0);blockItem=item.usesBlockLight();}
            float scale=1.5f;if(filter)pose.translate(-5,8,0);else if(empty){pose.translate(-15,-1,-2.75);scale=1.65f;}else pose.translate(-7,10,blockItem?10.25:0);
            if(count.getString().equals("*"))pose.translate(-1,3,0);pose.scale(scale,scale,scale);text(pose,draws,count,filter?0xFFFFFF:0xEDEDED,true,alpha);
        }
    }
}
