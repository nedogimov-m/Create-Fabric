package com.simibubi.create.content.kinetics.chainConveyor;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Source selection/connection previews, extracted once and submitted as native Vulkan gizmos. */
public final class ChainConveyorOverlay {
    public record Frame(DrawableGizmoPrimitives primitives,BlockPos anchor,BlockPos connection,Vec3 point,int lines,int cost,boolean valid){}
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static Frame latest;
    private static BlockPos first;
    private static ResourceKey<Level> dimension;
    private static Vec3 portTarget;
    private static int portItemCount;
    private static int emitted;
    public static Frame latest(){return latest;}
    public static BlockPos first(){return first;}
    public static void selectPort(Vec3 point){var client=Minecraft.getInstance();portTarget=point;portItemCount=client.player.getMainHandItem().getCount();dimension=client.level.dimension();}
    public static boolean cancelUse(Minecraft client){
        if(first==null||client.player==null||!client.player.isHolding(stack->stack.is(Items.IRON_CHAIN)))return false;
        boolean missed=client.hitResult instanceof BlockHitResult hit&&hit.getType()!=HitResult.Type.MISS&&!(LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos()) instanceof ChainConveyorBlockEntity);
        if(!client.player.isShiftKeyDown()&&!missed)return false;first=null;ClientPlayNetworking.send(new ChainConveyorConnectionHandler.Cancel());return true;
    }
    private static void line(DrawableGizmoPrimitives output,Vec3 a,Vec3 b,int color){output.addLine(a,b,color,2);emitted++;}
    private static void box(DrawableGizmoPrimitives output,Vec3 min,Vec3 max,int color){
        var vertices=new Vec3[8];for(int i=0;i<8;i++)vertices[i]=new Vec3((i&1)==0?min.x:max.x,(i&2)==0?min.y:max.y,(i&4)==0?min.z:max.z);
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0)line(output,vertices[i],vertices[i|bit],color);
    }
    private static void ring(DrawableGizmoPrimitives output,BlockPos pos,int color){for(double y:new double[]{.125,.875}){Vec3 previous=null;for(int i=0;i<=8;i++){double angle=Math.toRadians(-22.5+45*i);var point=Vec3.atBottomCenterOf(pos).add(Math.sin(angle)*1.25,y,Math.cos(angle)*1.25);if(previous!=null)line(output,previous,point,color);previous=point;}}}
    private static void strand(DrawableGizmoPrimitives output,ChainConveyorBlockEntity conveyor,BlockPos connection,int color){
        var stats=conveyor.getConnectionStats(connection);if(stats==null)return;var direction=stats.end().subtract(stats.start()).normalize();var right=new Vec3(direction.z,0,-direction.x).normalize().scale(.175);var up=direction.cross(right).normalize().scale(.175);
        Vec3[] a=new Vec3[4],b=new Vec3[4];for(int i=0;i<4;i++){var offset=right.scale((i&1)==0?-1:1).add(up.scale((i&2)==0?-1:1));a[i]=stats.start().add(offset);b[i]=stats.end().add(offset);line(output,a[i],b[i],color);}for(int i=0;i<4;i++)for(int bit:new int[]{1,2})if((i&bit)==0){line(output,a[i],a[i|bit],color);line(output,b[i],b[i|bit],color);}
    }
    private static Frame extract(Minecraft client){
        var output=new DrawableGizmoPrimitives();emitted=0;
        if(client.player==null||client.level==null||client.gui.screen()!=null)return new Frame(output,null,null,null,0,0,false);
        if(dimension!=client.level.dimension()){first=null;portTarget=null;dimension=client.level.dimension();}
        if(first!=null&&!(LoadedLevelAccess.blockEntity(client.level,first) instanceof ChainConveyorBlockEntity))first=null;
        if(portTarget!=null&&client.player.getMainHandItem().is(com.simibubi.create.AllBlocks.PACKAGE_FROGPORT.asItem())&&client.player.getMainHandItem().getCount()!=portItemCount)portTarget=null;
        boolean rideable=client.player.isHolding(stack->stack.is(ChainConveyorRidingState.CHAIN_RIDEABLE));boolean dismantling=rideable&&client.player.isShiftKeyDown();
        boolean port=client.player.getMainHandItem().is(com.simibubi.create.AllBlocks.PACKAGE_FROGPORT.asItem());boolean active=rideable||port||PackageItem.isPackage(client.player.getMainHandItem());
        var from=client.player.getEyePosition();double range=client.player.blockInteractionRange()+1;var to=from.add(client.player.getLookAngle().scale(range));double obstacle=client.hitResult==null||client.hitResult.getType()==HitResult.Type.MISS?range*range:from.distanceToSqr(client.hitResult.getLocation())+.04;
        ChainConveyorBlockEntity best=null;ChainConveyorInteraction.Selection selected=null;double nearest=range*range;
        if(active)for(var conveyor:ChainConveyorInteractionHandler.loaded(client)){var hit=ChainConveyorInteraction.trace(conveyor,from,to);if(hit==null||dismantling&&hit.connection().equals(BlockPos.ZERO))continue;double distance=from.distanceToSqr(hit.hit());if(distance>nearest||distance>obstacle&&!(client.hitResult instanceof BlockHitResult block&&block.getBlockPos().equals(conveyor.getBlockPos())))continue;nearest=distance;best=conveyor;selected=hit;}
        BlockPos anchor=null,connection=null;Vec3 point=null;
        if(best!=null){anchor=best.getBlockPos();connection=selected.connection();point=best.getPackagePosition(selected.position(),connection);if(connection.equals(BlockPos.ZERO)){var base=Vec3.atLowerCornerOf(anchor);best.getBlockState().getInteractionShape(client.level,anchor).forAllEdges((x1,y1,z1,x2,y2,z2)->line(output,base.add(x1,y1,z1),base.add(x2,y2,z2),0xFF000000));}else strand(output,best,connection,0xFF000000);if(!rideable)box(output,point.add(-.07,-.07,-.07),point.add(.07,.07,.07),0xFFFFFFFF);}
        int cost=0;boolean valid=false;
        if(first!=null&&client.player.isHolding(stack->stack.is(Items.IRON_CHAIN))){
            int color=0xFFFFFFFF;BlockPos second=client.hitResult instanceof BlockHitResult hit&&hit.getType()!=HitResult.Type.MISS&&LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos()) instanceof ChainConveyorBlockEntity?hit.getBlockPos():null;
            if(second!=null&&!second.equals(first)){
                String error=ChainConveyorConnectionHandler.validate(client.level,first,second,client.player);valid=error==null;color=valid?0xFF95CD41:0xFFEA5C2B;ring(output,second,color);
                var a=Vec3.atCenterOf(first);var b=Vec3.atCenterOf(second);var diff=b.subtract(a);var normal=diff.cross(new Vec3(0,1,0)).normalize().scale(.875);var half=diff.normalize().scale(.5);line(output,a.add(half).add(normal),b.subtract(half).add(normal),color);line(output,a.add(half).subtract(normal),b.subtract(half).subtract(normal),color);
                if(ChainConveyorBlockEntity.validConnection(second.subtract(first)))cost=ChainConveyorBlockEntity.getChainCost(second.subtract(first));
                client.player.sendOverlayMessage(Component.translatable("create.chain_conveyor."+(error==null?"valid_connection":error)));
            }
            ring(output,first,color);
        }
        if(port&&portTarget!=null){
            int color=0xFF9EDE73;
            if(client.hitResult instanceof BlockHitResult hit&&hit.getType()!=HitResult.Type.MISS){
                var placed=hit.getBlockPos();
                if(!LoadedLevelAccess.blockState(client.level,placed).canBeReplaced())placed=placed.relative(hit.getDirection());
                var bottom=Vec3.atBottomCenterOf(placed);var diff=portTarget.subtract(bottom);
                String error=diff.y<0?"cannot_reach_down":diff.length()>PackagePortBlockEntity.RANGE?"too_far":null;
                color=error==null?0xFF9EDE73:0xFFFF7171;
                client.player.sendOverlayMessage(Component.translatable("create.package_port."+(error==null?"valid":error)).withColor(color&0xFFFFFF));
                line(output,bottom,portTarget,color);
                box(output,Vec3.atLowerCornerOf(placed).add(.125,.005,.125),Vec3.atLowerCornerOf(placed).add(.875,.025,.875),color);
            }
            box(output,portTarget.add(-.07,-.07,-.07),portTarget.add(.07,.07,.07),color);
        }
        return new Frame(output,anchor,connection,point,emitted,cost,valid);
    }
    public static void register(){
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{first=null;portTarget=null;dimension=null;latest=null;FRAMES.clear();});
        ClientPlayNetworking.registerGlobalReceiver(ChainConveyorConnectionHandler.Feedback.TYPE,(packet,context)->{first=packet.selected()?packet.pos():null;dimension=context.client().level==null?null:context.client().level.dimension();});
        LevelExtractionEvents.END_EXTRACTION.register(context->{var frame=extract(Minecraft.getInstance());FRAMES.put(context.levelState(),frame);latest=frame;});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame!=null)frame.primitives.submit(context.submitNodeCollector(),context.levelState().cameraRenderState,false);});
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context,outline)->{var frame=FRAMES.get(context.levelState());return frame==null||frame.anchor==null;});
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("chain_requirements"),(gui,delta)->{var frame=latest;if(frame!=null&&frame.cost>0){var client=Minecraft.getInstance();int x=gui.guiWidth()/2-18,y=gui.guiHeight()-70;gui.item(new ItemStack(Items.IRON_CHAIN),x,y);gui.text(client.font,Integer.toString(frame.cost),x+20,y+4,frame.valid?0xFF95CD41:0xFFEA5C2B,false);}});
    }
    private ChainConveyorOverlay(){}
}
