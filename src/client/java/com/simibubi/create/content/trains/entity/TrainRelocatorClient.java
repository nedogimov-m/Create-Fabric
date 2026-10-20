package com.simibubi.create.content.trains.entity;

import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.ContraptionRelocationPacket;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.simibubi.create.content.trains.track.UnloadedTrackException;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.createmod.catnip.math.AngleHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual source wrench selection, live wheel preview and native relocation feedback. */
public final class TrainRelocatorClient {
    private record Selection(BlockPos pos,BezierTrackPointLocation bezier,boolean direction) {}
    private record Frame(DrawableGizmoPrimitives primitives) {}
    private static final Map<LevelRenderState,Frame> FRAMES=Collections.synchronizedMap(new WeakHashMap<>());
    private static WeakReference<CarriageContraptionEntity> hovered=new WeakReference<>(null);
    private static ClientLevel level;
    private static UUID trainId,entityUuid;
    private static int entityId;
    private static BlockPos origin;
    private static TrainRelocator.Preview preview;
    private static boolean registered;

    public static boolean isRelocating(){return trainId!=null;}
    public static boolean carriageWrenched(Vec3 hit,CarriageContraptionEntity entity) {
        var client=Minecraft.getInstance();var carriage=entity.getCarriage();
        if(client.player==null||client.level!=entity.level()||carriage==null
                ||TrainClientRegistry.get(client.level,carriage.train.id)!=carriage.train)return false;
        level=client.level;trainId=carriage.train.id;entityId=entity.getId();entityUuid=entity.getUUID();
        origin=BlockPos.containing(hit);preview=null;return true;
    }
    private static void clear(){trainId=entityUuid=null;origin=null;preview=null;FRAMES.clear();}
    private static void message(String key,ChatFormatting color){
        var player=Minecraft.getInstance().player;
        if(player!=null)player.sendOverlayMessage(Component.translatable("create."+key).withStyle(color));
    }
    private static Selection selection(Minecraft client) {
        var curve=TrackBlockOutline.result();
        if(curve!=null)return new Selection(curve.owner(),
            new BezierTrackPointLocation(curve.target(),curve.segment()),
            client.player.getLookAngle().dot(curve.direction())<0);
        if(client.hitResult instanceof BlockHitResult hit
                &&LoadedLevelAccess.isLoaded(client.level,hit.getBlockPos())
                &&LoadedLevelAccess.blockState(client.level,hit.getBlockPos()).getBlock() instanceof ITrackBlock)
            return new Selection(hit.getBlockPos(),null,false);
        return null;
    }
    /** Called before native use/attack handlers so an actual relocation consumes its own click. */
    public static boolean onClicked(Minecraft client) {
        if(!isRelocating()||client.player==null||client.level!=level||client.player.isSpectator())return false;
        if(client.player.isShiftKeyDown()||!client.player.getMainHandItem().is(AllItems.WRENCH)
                ||!client.player.isWithinBlockInteractionRange(origin,24)) {
            clear();message("train.relocate.abort",ChatFormatting.RED);return false;
        }
        if(client.player.isPassenger())return false;
        var train=TrainClientRegistry.get(client.level,trainId);
        var front=client.level.getEntity(entityId);
        if(train==null||!(front instanceof CarriageContraptionEntity carriage)
                ||!front.getUUID().equals(entityUuid)||carriage.getCarriage()==null
                ||carriage.getCarriage().train!=train){clear();return false;}
        var selected=selection(client);if(selected==null)return false;
        try {
            preview=TrainRelocator.preview(train,client.level,selected.pos,selected.bezier,
                selected.direction,client.player.getLookAngle());
        } catch(UnloadedTrackException pending){preview=new TrainRelocator.Preview(false,List.of());}
        if(preview.valid()) {
            for(var row:train.carriages)row.forEachPresentEntity(entity->entity.nonDamageTicks=10);
            ClientPlayNetworking.send(new TrainRelocationPacket(trainId,selected.pos,
                client.player.getLookAngle(),entityId,selected.direction,selected.bezier,entityUuid));
            clear();
        }
        return true;
    }
    private static void tick(Minecraft client) {
        if(client.level!=level){clear();hovered=new WeakReference<>(null);level=client.level;}
        if(client.player==null||client.level==null||client.isPaused())return;
        if(client.player.isPassenger())return;
        if(isRelocating()) {
            var train=TrainClientRegistry.get(client.level,trainId);var entity=client.level.getEntity(entityId);
            if(train==null||!(entity instanceof CarriageContraptionEntity front)
                    ||!entity.getUUID().equals(entityUuid)||front.getCarriage()==null
                    ||front.getCarriage().train!=train){clear();return;}
            if(entity.position().subtract(new Vec3(entity.xo,entity.yo,entity.zo)).lengthSqr()>1/1024d) {
                clear();message("train.cannot_relocate_moving",ChatFormatting.RED);return;
            }
            if(!client.player.getMainHandItem().is(AllItems.WRENCH)) {
                clear();message("train.relocate.abort",ChatFormatting.RED);return;
            }
            if(!client.player.isWithinBlockInteractionRange(origin,24)) {
                preview=null;message("train.relocate.too_far",ChatFormatting.RED);return;
            }
            var selected=selection(client);
            if(selected==null) {
                preview=null;client.player.sendOverlayMessage(Component.translatable("create.train.relocate",train.name));return;
            }
            try {
                // Recompute against the actual graph each tick: topology/look/wheel changes cannot keep a stale valid preview.
                preview=TrainRelocator.preview(train,client.level,selected.pos,selected.bezier,
                    selected.direction,client.player.getLookAngle());
            } catch(UnloadedTrackException pending){preview=new TrainRelocator.Preview(false,List.of());}
            message(preview.valid()?"train.relocate.valid":"train.relocate.invalid",
                preview.valid()?ChatFormatting.GREEN:ChatFormatting.RED);return;
        }
        var from=client.player.getEyePosition();var to=from.add(client.player.getViewVector(1)
            .scale(client.player.blockInteractionRange()+1));
        CarriageContraptionEntity nearest=null;double distance=Double.MAX_VALUE;
        for(var entity:client.level.getEntitiesOfClass(CarriageContraptionEntity.class,new AABB(from,to).inflate(1))) {
            var hit=entity.rayTrace(from,to,1);
            if(hit==null||!entity.visibleFrom(from,to,hit,1))continue;
            double found=from.distanceToSqr(entity.toGlobalVector(hit.getLocation(),1));
            if(found<distance){distance=found;nearest=entity;}
        }
        hovered=new WeakReference<>(nearest);
    }
    private static Frame extract() {
        var primitives=new DrawableGizmoPrimitives();var client=Minecraft.getInstance();
        var train=client.level==null||trainId==null?null:TrainClientRegistry.get(client.level,trainId);
        if(train==null||preview==null)return new Frame(primitives);
        boolean upsideDown=train.carriages.getFirst().leadingBogey().isUpsideDown();
        var offset=new Vec3(0,upsideDown?-.5-.925:-.925,0);
        var points=preview.positions();
        for(int i=0;i<points.size()-1;i++)
            line(primitives,points.get(i).add(offset),points.get(i+1).add(offset),
                preview.valid()||i!=points.size()-2?0xFF95CD41:0xFFEA5C2B,i%2==1?1/6f:1/4f);
        return new Frame(primitives);
    }
    /** Source world-width cuboid line, submitted through the native renderer. */
    private static void line(DrawableGizmoPrimitives output,Vec3 start,Vec3 end,int color,float width) {
        var delta=end.subtract(start);float length=(float)delta.length();
        float yaw=AngleHelper.deg(Math.atan2(delta.x,delta.z));
        float pitch=AngleHelper.deg(Math.atan2(Math.hypot(delta.x,delta.z),delta.y))-90;
        var rotation=new Matrix4f().rotateY(AngleHelper.rad(yaw)).rotateX(AngleHelper.rad(pitch));
        float half=width/2;var points=new Vec3[8];
        for(int i=0;i<8;i++) {
            var p=rotation.transformPosition(new Vector3f((i&1)==0?-half:half,
                (i&2)==0?-half:half,(i&4)==0?-half:length+half));
            points[i]=start.add(p.x,p.y,p.z);
        }
        int[][] faces={{4,0,1,5},{2,6,7,3},{3,1,0,2},{6,4,5,7},{2,0,4,6},{7,5,1,3}};
        for(var face:faces)output.addQuad(points[face[0]],points[face[1]],points[face[2]],points[face[3]],color);
    }
    public static void register() {
        if(registered)return;registered=true;
        ClientTickEvents.END_CLIENT_TICK.register(TrainRelocatorClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{clear();hovered=new WeakReference<>(null);level=null;});
        ClientPlayNetworking.registerGlobalReceiver(ContraptionRelocationPacket.TYPE,(packet,context)->{
            if(context.client().level!=null&&context.client().level.getEntity(packet.entityId()) instanceof OrientedContraptionEntity oriented)
                oriented.nonDamageTicks=10;
        });
        LevelExtractionEvents.END_EXTRACTION.register(context->FRAMES.put(context.levelState(),extract()));
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{
            var frame=FRAMES.get(context.levelState());
            if(frame!=null)frame.primitives.submit(context.submitNodeCollector(),context.levelState().cameraRenderState,false);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("derailed_train_hint"),(gui,delta)->{
            var client=Minecraft.getInstance();var front=hovered.get();
            if(client.player==null||client.gui.hud.isHidden()||client.gui.screen()!=null||isRelocating()
                    ||!com.simibubi.create.infrastructure.config.AllConfigs.client().tooltips.get()
                    ||!client.player.getMainHandItem().is(AllItems.WRENCH)||front==null||front.getCarriage()==null
                    ||!front.getCarriage().train.derailed)return;
            var lines=new java.util.ArrayList<Component>();
            com.simibubi.create.foundation.item.TooltipHelper.addHint(lines,"hint.derailed_train");
            int width=lines.stream().mapToInt(client.font::width).max().orElse(0);
            int x=Math.max(4,Math.min(gui.guiWidth()/2+12,gui.guiWidth()-width-8));
            int y=Math.max(4,Math.min(gui.guiHeight()/2+24,gui.guiHeight()-lines.size()*10-8));
            gui.fill(x-4,y-4,x+width+4,y+lines.size()*10+3,0xD0100010);
            for(int i=0;i<lines.size();i++)gui.text(client.font,lines.get(i),x,y+i*10,0xFFFFFFFF,false);
        });
    }
    private TrainRelocatorClient() {}
}
