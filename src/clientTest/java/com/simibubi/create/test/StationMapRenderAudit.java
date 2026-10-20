package com.simibubi.create.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllMapDecorationTypes;
import com.simibubi.create.Create;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.MapRenderState.MapDecorationRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.saveddata.maps.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Read-only observations of actual native extraction, submission and deferred geometry execution. */
public final class StationMapRenderAudit {
    private static volatile Sample active;
    private static volatile NetworkSample network;
    private static final ThreadLocal<ArrayDeque<Invocation>> RENDERS=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Observation>> GEOMETRY=ThreadLocal.withInitial(ArrayDeque::new);
    private static final float EPSILON=.002f;

    public record PacketReceipt(String mapId,String label,byte x,byte y,byte rotation,boolean renderOnFrame,
        boolean actualClientDataMatches,int actualPacketStationCount) {}
    public record FrameReceipt(String phase,String mapId,boolean onFrame,String label,byte x,byte y,byte rotation,
        String sprite,String atlas,int spriteWidth,int spriteHeight,float u0,float v0,float u1,float v1,
        float[] iconRelative,float[] labelRelative,float nativeQuadZ,int packedLight,int textLight,int textColor,
        int textBackground,int textOutline,boolean nativeIconQueued,boolean nativeGeometryReturned,
        boolean nativeTextQueued,boolean nativeMapRenderReturned) {}
    public static final class NetworkSample {
        final MapId id;final String name;volatile PacketReceipt receipt;
        NetworkSample(MapId id,String name){this.id=id;this.name=name;}
        public PacketReceipt completed(){return receipt;}
    }
    public static final class Sample {
        final String phase,name;final MapId id;final boolean onFrame;
        final Map<MapRenderState,MapId> extracted=new IdentityHashMap<>();
        final List<Observation> observations=new ArrayList<>();volatile Throwable failure;
        Sample(String phase,MapId id,String name,boolean onFrame){this.phase=phase;this.id=id;this.name=name;this.onFrame=onFrame;}
        public Throwable failure(){return failure;}
        public synchronized FrameReceipt completed(){
            for(int i=observations.size()-1;i>=0;i--){var o=observations.get(i);if(o.iconQueued&&o.geometryReturned&&o.textQueued&&o.mapReturned)return receipt(o);}return null;
        }
        public synchronized TextureAtlasSprite actualSprite(){
            for(int i=observations.size()-1;i>=0;i--){var o=observations.get(i);if(o.iconQueued&&o.geometryReturned&&o.textQueued&&o.mapReturned)return o.sprite;}return null;
        }
        private FrameReceipt receipt(Observation o){return new FrameReceipt(phase,id.toString(),onFrame,o.label,o.x,o.y,o.rot,
            o.sprite.contents().name().toString(),o.sprite.atlasLocation().toString(),o.sprite.contents().width(),o.sprite.contents().height(),
            o.sprite.getU0(),o.sprite.getV0(),o.sprite.getU1(),o.sprite.getV1(),array(o.iconRelative),array(o.labelRelative),o.quadZ,o.light,o.textLight,o.textColor,o.textBackground,o.textOutline,
            o.iconQueued,o.geometryReturned,o.textQueued,o.mapReturned);}
    }
    private static final class Invocation {
        final Sample sample;final MapRenderState map;final Matrix4f base;final int light;final List<Observation> observations=new ArrayList<>();
        Invocation(Sample sample,MapRenderState map,PoseStack pose,int light){this.sample=sample;this.map=map;this.base=new Matrix4f(pose.last().pose());this.light=light;}
    }
    public static final class Observation {
        final Sample sample;final MapDecorationRenderState decoration;final TextureAtlasSprite sprite;final String label;
        final byte x,y,rot;final int light;final Matrix4f worldPose,iconRelative;
        Matrix4f labelRelative;int textLight,textColor,textBackground,textOutline;float quadZ;
        boolean iconQueued,geometryReturned,textQueued,mapReturned;
        Observation(Invocation invocation,MapDecorationRenderState decoration,PoseStack pose){
            sample=invocation.sample;this.decoration=decoration;this.sprite=decoration.atlasSprite;label=decoration.name.getString();
            x=decoration.x;y=decoration.y;rot=decoration.rot;light=invocation.light;worldPose=new Matrix4f(pose.last().pose());iconRelative=relative(invocation.base,worldPose);
        }
    }
    public static NetworkSample expectPacket(MapId id,String name){var next=new NetworkSample(id,name);network=next;return next;}
    public static Sample begin(String phase,MapId id,String name,boolean onFrame){var next=new Sample(phase,id,name,onFrame);active=next;return next;}
    public static void clear(){active=null;network=null;RENDERS.remove();GEOMETRY.remove();}
    public static void packetApplied(ClientboundMapItemDataPacket packet){
        var expected=network;var client=Minecraft.getInstance();
        if(expected==null||!client.isSameThread()||client.level==null||!packet.mapId().equals(expected.id)||packet.decorations().isEmpty())return;
        var sent=packet.decorations().orElseThrow().stream().filter(StationMapRenderAudit::station).toList();
        if(sent.size()!=1||sent.getFirst().name().isEmpty()||!sent.getFirst().name().orElseThrow().getString().equals(expected.name))return;
        var data=client.level.getMapData(expected.id);if(data==null)return;
        var actual=new ArrayList<MapDecoration>();for(var row:data.getDecorations())if(station(row))actual.add(row);
        var row=sent.getFirst();expected.receipt=new PacketReceipt(packet.mapId().toString(),row.name().orElseThrow().getString(),row.x(),row.y(),row.rot(),row.renderOnFrame(),actual.equals(sent),sent.size());
    }
    private static boolean station(MapDecoration row){return row.type().value()==AllMapDecorationTypes.STATION_MAP_DECORATION.value();}
    public static void extracted(MapId id,MapItemSavedData actual,MapRenderState state){var sample=active;if(sample==null||!sample.id.equals(id))return;synchronized(sample){sample.extracted.put(state,id);}}
    public static void enter(MapRenderState state,PoseStack pose,boolean onFrame,int light){
        var sample=active;if(sample!=null){synchronized(sample){if(!sample.extracted.containsKey(state)||sample.onFrame!=onFrame)sample=null;}}
        RENDERS.get().push(new Invocation(sample,state,pose,light));
    }
    public static void returned(MapRenderState state){
        var stack=RENDERS.get();if(stack.isEmpty())return;var invocation=stack.pop();if(invocation.sample==null)return;
        synchronized(invocation.sample){check(invocation.sample,invocation.map==state,"Actual native map render stack identity changed");for(var observation:invocation.observations)observation.mapReturned=true;}
    }
    public static Observation iconSubmission(MapDecorationRenderState decoration,PoseStack pose){
        var invocation=RENDERS.get().peek();if(invocation==null||invocation.sample==null||!isStation(decoration))return null;
        var sample=invocation.sample;synchronized(sample){
            if(decoration.name==null||!decoration.name.getString().equals(sample.name))return null;
            var observation=new Observation(invocation,decoration,pose);var expected=new Matrix4f().translate(decoration.x/2f+64,decoration.y/2f+64,-.02f)
                .rotateZ((float)Math.toRadians(decoration.rot*360f/16f)).scale(4.5f,4.5f,3f).translate(1f/9f,0,0);
            check(sample,expected.equals(observation.iconRelative,EPSILON),"Actual station icon pose differs from source9px/+0.5x/depth: "+observation.iconRelative);
            check(sample,decoration.rot==0&&decoration.renderOnFrame,"Actual source station rotation/frame flag changed");
            check(sample,decoration.atlasSprite.contents().width()==9&&decoration.atlasSprite.contents().height()==9,"Actual native station atlas sprite must contain the pinned9x9 source image");
            check(sample,decoration.atlasSprite.getU1()>decoration.atlasSprite.getU0()&&decoration.atlasSprite.getV1()>decoration.atlasSprite.getV0(),"Actual station atlas UV span is empty");
            if(sample.observations.size()<256)sample.observations.add(observation);invocation.observations.add(observation);return observation;
        }
    }
    public static void iconQueued(Observation observation){if(observation!=null)synchronized(observation.sample){observation.iconQueued=true;}}
    public static Observation textSubmission(MapDecorationRenderState decoration,PoseStack pose,float x,float y,FormattedCharSequence text,
            boolean shadow,Font.DisplayMode mode,int light,int color,int background,int outline){
        var invocation=RENDERS.get().peek();if(invocation==null||invocation.sample==null||!isStation(decoration))return null;
        var sample=invocation.sample;synchronized(sample){
            var observation=invocation.observations.stream().filter(value->value.decoration==decoration).findFirst().orElse(null);if(observation==null)return null;
            var actualText=new StringBuilder();text.accept((index,style,point)->{actualText.appendCodePoint(point);return true;});
            observation.labelRelative=relative(invocation.base,pose.last().pose());var width=Minecraft.getInstance().font.width(decoration.name);
            var expected=new Matrix4f().translate(decoration.x/2f+64+.8f*(-width/2f+.5f),decoration.y/2f+70,-.025f).scale(.8f,.8f,-1);
            check(sample,expected.equals(observation.labelRelative,EPSILON),"Actual native station label pose differs from source fixed0.8/center/+6/glyph-plane and native font Z flip: "+observation.labelRelative);
            var glyph=observation.labelRelative.transformPosition(0,0,0,new org.joml.Vector3f());
            var backgroundPosition=observation.labelRelative.transformPosition(0,0,-.01f,new org.joml.Vector3f());
            check(sample,glyph.z<backgroundPosition.z&&Math.abs(glyph.z+.025f)<EPSILON&&Math.abs(backgroundPosition.z+.015f)<EPSILON,"Actual native font background plane must lie behind the visible source station glyph plane");
            check(sample,actualText.toString().equals(sample.name)&&x==0&&y==0&&!shadow&&mode==Font.DisplayMode.NORMAL,"Actual native station text/offset/style differs from source label");
            check(sample,light==invocation.light&&color==-1&&background==Integer.MIN_VALUE&&outline==0,"Actual native source label light/color/background/order arguments changed");
            observation.textLight=light;observation.textColor=color;observation.textBackground=background;observation.textOutline=outline;return observation;
        }
    }
    public static void textQueued(Observation observation){if(observation!=null)synchronized(observation.sample){observation.textQueued=true;}}
    /** Called only by the actual native deferred lambda; no renderer/callback is substituted by this audit. */
    public static void geometryEnter(float z,TextureAtlasSprite sprite,int light,PoseStack.Pose pose){
        var sample=active;if(sample==null||!sprite.contents().name().equals(Create.asResource("station")))return;
        synchronized(sample){for(int i=sample.observations.size()-1;i>=0;i--){var o=sample.observations.get(i);
            if(o.sprite==sprite&&o.light==light&&o.worldPose.equals(pose.pose(),EPSILON)){
                check(sample,Float.isFinite(z)&&z<=0&&Math.abs(z)<1,"Actual native station geometry order depth is invalid");o.quadZ=z;GEOMETRY.get().push(o);return;
            }
        }}
    }
    public static void geometryReturned(TextureAtlasSprite sprite,PoseStack.Pose pose){var stack=GEOMETRY.get();if(stack.isEmpty())return;var o=stack.peek();if(o.sprite!=sprite||!o.worldPose.equals(pose.pose(),EPSILON))return;stack.pop();synchronized(o.sample){o.geometryReturned=true;}}
    private static boolean isStation(MapDecorationRenderState decoration){return decoration.atlasSprite!=null&&decoration.atlasSprite.contents().name().equals(Create.asResource("station"));}
    private static Matrix4f relative(Matrix4fc base,Matrix4fc actual){return new Matrix4f(base).invert().mul(actual);}
    private static float[] array(Matrix4fc matrix){return matrix==null?null:matrix.get(new float[16]);}
    private static void check(Sample sample,boolean condition,String message){if(!condition&&sample.failure==null)sample.failure=new AssertionError(message);}
    private StationMapRenderAudit(){}
}
