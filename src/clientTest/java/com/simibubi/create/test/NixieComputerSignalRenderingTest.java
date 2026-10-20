package com.simibubi.create.test;

import com.simibubi.create.content.redstone.nixieTube.NixieSignalModels;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeRenderer;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Real server client-packet, baked lamp geometry, mount/blink state and native resource reload.
 * This is an explicit ComputerSignal fixture, not a claim of an external ComputerCraft producer.
 * Root owns compilation, execution, frame capture and visual review; initially UNRUN.
 */
final class NixieComputerSignalRenderingTest {
    private static final BlockPos BASE=new BlockPos(38305,8,5);
    private static final List<BlockPos> POSITIONS=List.of(BASE,BASE.east(3),BASE.east(6),BASE.east(9));
    private static final List<String> MOUNTS=List.of("floor","ceiling","wall","wall_reversed");

    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");
        server.runCommand("tp @p 38310.0 11.0 11.0");
        context.waitFor(client->Math.abs(client.player.getX()-38310)<.1&&client.level.isLoaded(POSITIONS.getLast())&&client.gameRenderer.mainCamera().position().distanceToSqr(client.player.getEyePosition())<1,400);
        server.runCommand("fill 38300 5 0 38320 5 14 minecraft:smooth_quartz");
        for(int i=0;i<POSITIONS.size();i++){
            var pos=POSITIONS.get(i);
            server.runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" create:nixie_tube[double_face="+MOUNTS.get(i)+",facing=east,waterlogged=false]");
        }
        context.waitFor(client->POSITIONS.stream().allMatch(pos->LoadedLevelAccess.blockEntity(client.level,pos) instanceof NixieTubeBlockEntity),300);
        byte[] initial=signal((byte)1,(byte)127,(byte)-1,(byte)127,(byte)4,(byte)2);
        send(context,world,initial);
        verify(context,2,true);
        context.runOnClient(NixieComputerSignalRenderingTest::verifyDetachedStateAndCameraBoundary);
        view(context);world.getConnection().waitForChunksRender();context.waitTicks(4);
        screenshot(context,"create-vulkan-nixie-computer-real-packet-four-mounts-original-rgb-and-additive-lamps");

        // Zero disables the first tube; a negative signed period keeps the second lit.
        send(context,world,signal((byte)0,(byte)0,(byte)-1,(byte)127,(byte)4,(byte)2));
        verify(context,1,false);
        // A positive period with an off-time above its entire period remains dark.
        send(context,world,signal((byte)2,(byte)127,(byte)1,(byte)127,(byte)4,(byte)2));
        verify(context,1,false);
        // Source width/height are signed bytes, not an unsigned or clamped size.
        send(context,world,signal((byte)2,(byte)0,(byte)-1,(byte)0,(byte)-2,(byte)3));
        context.runOnClient(client->{
            var floor=state(client,BASE);var wall=state(client,BASE.east(6));
            if(floor.lamps.size()!=2||floor.lamps.getFirst().width()!=3||floor.lamps.getFirst().height()!=-2
                    ||wall.lamps.getFirst().width()!=-2||wall.lamps.getFirst().height()!=3)
                throw new AssertionError("Native Nixie lamp dimensions lost signed bytes or mount-dependent swap");
        });

        send(context,world,initial);
        var oldModel=context.computeOnClient(client->client.getModelManager().getModel(NixieSignalModels.COMPUTER_WHITE_GLOW));
        var reload=context.computeOnClient(Minecraft::reloadResourcePacks);
        context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,650);reload.join();
        context.runOnClient(client->{if(client.getModelManager().getModel(NixieSignalModels.COMPUTER_WHITE_GLOW)==oldModel)
            throw new AssertionError("Native ComputerSignal partial model stayed stale after resource reload");});
        verify(context,2,true);context.waitTicks(4);
        screenshot(context,"create-vulkan-nixie-computer-real-model-reload-rebakes-source-lamps");

        // The real absent ComputerSignal client packet must restore ordinary glyph rendering.
        send(context,world,null);
        context.runOnClient(client->{for(var pos:POSITIONS){var state=state(client,pos);
            if(state.computerMode||!state.lamps.isEmpty()||state.tubes.size()!=2||!state.panel.isEmpty())
                throw new AssertionError("Absent ComputerSignal packet retained stale lamp mode or geometry");
        }});
    }

    private static byte[] signal(byte firstPeriod,byte firstOff,byte secondPeriod,byte secondOff,byte width,byte height){
        return new byte[]{(byte)255,(byte)128,0,firstPeriod,firstOff,width,height,
            1,64,(byte)200,secondPeriod,secondOff,1,3};
    }

    private static void send(ClientGameTestContext context,TestSingleplayerContext world,byte[] data){
        world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();
            for(var pos:POSITIONS){
                if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof NixieTubeBlockEntity owner))
                    throw new AssertionError("Real server Nixie fixture owner missing");
                if(data==null)owner.computerSignal=null;
                else{owner.computerSignal=new NixieTubeBlockEntity.ComputerSignal();owner.computerSignal.decode(data.clone());}
                owner.notifyUpdate();
            }
        });
        context.waitFor(client->POSITIONS.stream().allMatch(pos->{
            if(!(LoadedLevelAccess.blockEntity(client.level,pos) instanceof NixieTubeBlockEntity owner))return false;
            return data==null?owner.computerSignal==null:owner.computerSignal!=null&&Arrays.equals(owner.computerSignal.encode(),data);
        }),300);
    }

    private static void verify(ClientGameTestContext context,int lamps,boolean both){
        context.runOnClient(client->{for(int index=0;index<POSITIONS.size();index++){
            var state=state(client,POSITIONS.get(index));
            if(!state.computerMode||!state.tubes.isEmpty()||state.lamps.size()!=lamps||state.panel.mesh==null||state.panel.mesh.size()!=10
                    ||state.invertPanel!=(index==1)||state.roll!=(index==2?-90:index==3?90:0)||state.yaw!=(index==3?180:0))
                throw new AssertionError("Actual ComputerSignal packet did not produce original source mount/panel/lamp mode "+MOUNTS.get(index));
            for(int tube=0;tube<lamps;tube++){
                int originalTube=both?tube:1;var lamp=state.lamps.get(tube);
                boolean inverted=index==1||index==3;
                float expectedOffset=(originalTube==0)==inverted?4/16f:-4/16f;
                if(lamp.offset()!=expectedOffset||!lamp.near())throw new AssertionError("Actual native lamp side/near geometry differs from source");
                mesh(lamp.core(),0xFFFFFFFF);mesh(lamp.glow(),originalTube==0?0xFFDF8020:0xFF2050B6);
                mesh(lamp.base(),0xFF0C0C0C);mesh(lamp.tube(),originalTube==0?0xFFFF8000:0xFF0140C8);
            }
        }});
    }

    private static NixieTubeRenderer.State state(Minecraft client,BlockPos pos){
        var owner=(NixieTubeBlockEntity)LoadedLevelAccess.blockEntity(client.level,pos);
        var result=client.getBlockEntityRenderDispatcher().tryExtractRenderState(owner,.5f,null,false);
        if(!(result instanceof NixieTubeRenderer.State state))throw new AssertionError("Actual registered native Nixie renderer is missing");
        return state;
    }

    private static void mesh(PartialModelRenderState model,int color){
        if(model==null||model.mesh==null||model.mesh.size()!=6)throw new AssertionError("Actual original complete six-face lamp mesh missing");
        model.mesh.forEach(quad->{if(!quad.emissive())throw new AssertionError("Original fullbright lamp lost native emissive state");
            for(int vertex=0;vertex<4;vertex++)if(quad.color(vertex)!=color)
                throw new AssertionError("Actual native lamp mesh lost original unsigned RGB/glow tint");
        });
    }

    private static void verifyDetachedStateAndCameraBoundary(Minecraft client){
        var owner=(NixieTubeBlockEntity)LoadedLevelAccess.blockEntity(client.level,BASE);
        var renderer=(NixieTubeRenderer)client.getBlockEntityRenderDispatcher().<NixieTubeBlockEntity,NixieTubeRenderer.State>getRenderer(owner);
        var saved=renderer.createRenderState();var center=Vec3.atCenterOf(BASE);
        renderer.extractRenderState(owner,saved,.5f,center.add(95.999,0,0),null);
        if(saved.lamps.size()!=2||!saved.lamps.getFirst().near())throw new AssertionError("Native source lamp near cutoff excluded a point below96blocks");
        var source=owner.computerSignal;byte[] sourceBytes=source.encode();
        try{
            source.first.r=0;source.first.g=0;source.first.b=0;source.first.glowWidth=0;source.first.glowHeight=0;
            mesh(saved.lamps.getFirst().tube(),0xFFFF8000);mesh(saved.lamps.getFirst().glow(),0xFFDF8020);
            if(saved.lamps.getFirst().width()!=2||saved.lamps.getFirst().height()!=4)
                throw new AssertionError("Mutable ComputerSignal leaked into already extracted native render state");
        }finally{source.decode(sourceBytes);}
        renderer.extractRenderState(owner,saved,.5f,center.add(96,0,0),null);
        for(var lamp:saved.lamps)if(lamp.near()||lamp.core()!=null||lamp.glow()!=null||lamp.base()==null||lamp.tube()==null)
            throw new AssertionError("Actual96block source cutoff must remove only core and halo");
        try{
            owner.computerSignal=null;renderer.extractRenderState(owner,saved,.5f,center,null);
            if(saved.computerMode||saved.tubes.size()!=2||!saved.lamps.isEmpty()||!saved.panel.isEmpty())
                throw new AssertionError("Reused actual renderer state retained stale ComputerSignal geometry after mode switch");
        }finally{owner.computerSignal=source;}
    }

    private static void view(ClientGameTestContext context){
        var angle=context.computeOnClient(client->{var d=Vec3.atCenterOf(BASE.east(4)).subtract(client.player.getEyePosition());
            return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});
        context.getInput().lookAt(angle.x,angle.y);
    }
    private static void screenshot(ClientGameTestContext context,String name){
        context.waitFor(client->client.gui.overlay()==null,200);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);
    }
}
