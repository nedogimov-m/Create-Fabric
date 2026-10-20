package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.redstone.link.*;
import com.simibubi.create.content.redstone.link.controller.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Actual native input, ghost binding, key capture, two-chunk save/reload and model extraction. */
final class RedstoneLinkRenderingTest{
    private static final BlockPos TX=new BlockPos(7407,5,5),RX=TX.east(3),WIRE=RX.south(),LECTERN=TX.south(5);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 7407.5 5 8.5");
        context.waitFor(client->Math.abs(client.player.getX()-7407.5)<.1&&client.level.isLoaded(RX),400);
        NativeScreenshotReadiness.beforeFloor(context, world, new BlockPos(7398, 4, -3), new BlockPos(7425, 4, 14), TX, RX, LECTERN);
        server.runCommand("fill 7398 4 -3 7425 4 14 minecraft:smooth_quartz");
        NativeScreenshotReadiness.afterFloor(context, world, new BlockPos(7398, 4, -3), new BlockPos(7425, 4, 14));block(world,TX,"create:redstone_link[facing=up,receiver=false]");block(world,RX,"create:redstone_link[facing=up,receiver=true]");block(world,WIRE,"minecraft:redstone_wire");
        server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond 8");
        context.waitFor(client->client.player.onGround()&&client.player.getMainHandItem().is(Items.DIAMOND),200);
        look(context,Vec3.atLowerCornerOf(TX).add(RedstoneLinkFrequencySlot.offset(Direction.UP,true)));use(context);
        waitServer(context,world,level->link(level,TX).getLink().getNetworkKey().getFirst().getStack().is(Items.DIAMOND)
            &&level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().getCount()==8,200,"First frequency input consumed sample or missed native hit");
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:emerald 7");context.waitFor(client->client.player.getMainHandItem().is(Items.EMERALD),200);
        look(context,Vec3.atLowerCornerOf(TX).add(RedstoneLinkFrequencySlot.offset(Direction.UP,false)));use(context);
        waitServer(context,world,level->link(level,TX).getLink().getNetworkKey().getSecond().getStack().is(Items.EMERALD)
            &&level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().getCount()==7,200,"Second frequency native input failed");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var key=link(level,TX).getLink().getNetworkKey();link(level,RX).getLink().setFrequency(true,key.getFirst().getStack());link(level,RX).getLink().setFrequency(false,key.getSecond().getStack());});
        block(world,TX.west(),"minecraft:redstone_block");
        context.waitFor(client->client.level.isLoaded(WIRE)&&client.level.getBlockState(WIRE).is(Blocks.REDSTONE_WIRE)
            &&client.level.getBlockState(WIRE).getValue(RedstoneWireBlock.POWER)==15
            &&client.level.getBlockState(RX).is(AllBlocks.REDSTONE_LINK)&&client.level.getBlockState(RX).getValue(RedstoneLinkBlock.POWERED),200);
        context.runOnClient(client->{var entity=(RedstoneLinkBlockEntity)client.level.getBlockEntity(TX);var state=(LinkRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(entity,.5f,null,false);if(state==null||state.samples[0].isEmpty()||state.samples[1].isEmpty())throw new AssertionError("Both native frequency item states required");});
        screenshot(context,"create-vulkan-wireless-link-native-two-ghost-frequencies-and-wire");
        block(world,TX.west(),"minecraft:air");waitServer(context,world,level->link(level,RX).getReceivedSignal()==0,200,"Receiver failed redstone release");
        server.runCommand("item replace entity @p weapon.mainhand with create:linked_controller");context.waitFor(client->client.player.getMainHandItem().getItem()instanceof LinkedControllerItem,200);
        look(context,Vec3.atLowerCornerOf(TX).add(RedstoneLinkFrequencySlot.offset(Direction.UP,true)));
        context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(TX),100);
        use(context);context.waitFor(client->LinkedControllerClientHandler.MODE==LinkedControllerClientHandler.Mode.BIND,200);
        var bindOrigin=context.computeOnClient(client->client.player.position());
        context.getInput().holdKey(options->options.keyUp);
        try{
            waitServer(context,world,level->LinkedControllerItem.toFrequency(level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem(),0).equals(link(level,TX).getLink().getNetworkKey()),200,"Physical key binding did not reach server controller component");
        }finally{context.getInput().releaseKey(options->options.keyUp);}
        context.waitFor(client->LinkedControllerClientHandler.MODE==LinkedControllerClientHandler.Mode.IDLE,200);
        context.runOnClient(client->{if(client.player.position().distanceTo(bindOrigin)>.02)throw new AssertionError("Binding key leaked into vanilla player movement");});
        look(context,new Vec3(7407.5,20,8.5));use(context);context.waitFor(client->LinkedControllerClientHandler.MODE==LinkedControllerClientHandler.Mode.ACTIVE,200);
        var before=context.computeOnClient(client->client.player.position());
        context.getInput().holdKey(options->options.keyUp);
        try{
            waitServer(context,world,level->link(level,RX).getReceivedSignal()==15,200,"Remote key did not power receiver");
            context.waitTicks(12);
            context.runOnClient(client->{if(client.player.position().distanceTo(before)>.02||LinkedControllerClientHandler.button(0,1)<.8)throw new AssertionError("Remote capture moved player or button animation did not depress");});
            controllerScreenshot(context,"create-vulkan-linked-controller-native-captured-input-and-button");
        }finally{context.getInput().releaseKey(options->options.keyUp);}
        waitServer(context,world,level->link(level,RX).getReceivedSignal()==0,200,"Physical release failed");
        var oldForward=context.computeOnClient(client->InputConstants.getKey(client.options.keyUp.saveString()));
        context.runOnClient(client->{client.options.keyUp.setKey(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_MIDDLE));net.minecraft.client.KeyMapping.resetMapping();});
        try{
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
            try{waitServer(context,world,level->link(level,RX).getReceivedSignal()==15,200,"Remapped physical mouse button did not become controller forward");context.waitTicks(8);controllerScreenshot(context,"create-vulkan-linked-controller-remapped-native-mouse-input");}
            finally{context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_MIDDLE);}
            waitServer(context,world,level->link(level,RX).getReceivedSignal()==0,200,"Remapped mouse release stuck on");
        }finally{context.runOnClient(client->{client.options.keyUp.setKey(oldForward);net.minecraft.client.KeyMapping.resetMapping();});}
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);context.waitFor(client->LinkedControllerClientHandler.MODE==LinkedControllerClientHandler.Mode.IDLE,200);
        // Close a possible pause screen, then open the source twelve-slot ghost menu by sneak-use.
        context.runOnClient(client->{if(client.gui.screen()!=null)client.gui.setScreen(null);});
        context.getInput().holdKey(options->options.keyShift);
        try{context.waitTicks(2);look(context,new Vec3(7407.5,20,8.5));use(context);context.waitFor(client->client.gui.screen()instanceof LinkedControllerScreen&&client.player.containerMenu instanceof LinkedControllerMenu,200);}
        finally{context.getInput().releaseKey(options->options.keyShift);}
        context.waitFor(client->client.player.containerMenu.getSlot(36).getItem().is(Items.DIAMOND)&&client.player.containerMenu.getSlot(37).getItem().is(Items.EMERALD),200);
        screenshot(context,"create-vulkan-linked-controller-real-twelve-ghost-slot-menu");context.getInput().pressKey(InputConstants.KEY_ESCAPE);context.waitFor(client->client.gui.screen()==null,100);
        var old=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(AllBlocks.REDSTONE_LINK.defaultBlockState()));
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone(),500);reload.join();context.waitTicks(3);
        context.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(AllBlocks.REDSTONE_LINK.defaultBlockState())==old)throw new AssertionError("Link retained a pre-reload block model");});
        // Real owner chunks and held item component leave and return; no manual force-load or tickets.
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 8200 14 8200");
        waitServer(context,world,level->level.getChunkSource().getChunkNow(TX.getX()>>4,0)==null&&level.getChunkSource().getChunkNow(RX.getX()>>4,0)==null,800,"Wireless source/receiver chunks did not unload");
        server.runCommand("tp @p 7407.5 5 8.5");waitServer(context,world,level->com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,TX)instanceof RedstoneLinkBlockEntity tx&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,RX)instanceof RedstoneLinkBlockEntity rx&&tx.getLink().isRegistered()&&rx.getLink().isRegistered(),500,"Saved wireless participants failed to rejoin after return");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var held=level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem();if(!LinkedControllerItem.toFrequency(held,0).equals(link(level,TX).getLink().getNetworkKey())||link(level,RX).getReceivedSignal()!=0)throw new AssertionError("Real save lost binding or retained manual input power");});
        block(world,TX.west(),"minecraft:redstone_block");context.waitFor(client->client.level.isLoaded(WIRE)&&client.level.getBlockState(WIRE).is(Blocks.REDSTONE_WIRE)
            &&client.level.getBlockState(WIRE).getValue(RedstoneWireBlock.POWER)==15,300);look(context,Vec3.atCenterOf(RX));NativeScreenshotReadiness.await(context,world);screenshot(context,"create-vulkan-wireless-link-two-chunk-return-resource-reload");
        block(world,TX.west(),"minecraft:air");
        waitServer(context,world,level->link(level,RX).getReceivedSignal()==0,200,"Reloaded link did not release");
        block(world,LECTERN,"minecraft:lectern[facing=north]");server.runCommand("tp @p 7407.5 5 11.5");server.runCommand("gamemode survival @p");
        context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getZ()-11.5)<.1,200);look(context,Vec3.atCenterOf(LECTERN).add(0,.25,0));
        context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(LECTERN),100);use(context);
        waitServer(context,world,level->com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,LECTERN)instanceof LecternControllerBlockEntity lectern
            &&LinkedControllerItem.toFrequency(lectern.getController(),0).equals(link(level,TX).getLink().getNetworkKey())
            &&level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().isEmpty(),200,"Ordinary lectern install lost bound controller ownership");
        context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);use(context);
        context.waitFor(client->LinkedControllerClientHandler.MODE==LinkedControllerClientHandler.Mode.ACTIVE,200);
        context.getInput().holdKey(options->options.keyUp);
        try{
            waitServer(context,world,level->link(level,RX).getReceivedSignal()==15,200,"Owned lectern session did not drive wireless output");context.waitTicks(8);
            context.runOnClient(client->{var entity=(LecternControllerBlockEntity)client.level.getBlockEntity(LECTERN);var state=(LecternControllerRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(entity,.5f,null,false);if(state==null||state.controller==null||!state.controller.active||state.controller.depression[0]<.5)throw new AssertionError("Native lectern controller power/depression extraction missing");assertLecternMeshPose(client,entity,state);});
            screenshot(context,"create-vulkan-linked-controller-real-lectern-session-and-button-model");
        }finally{context.getInput().releaseKey(options->options.keyUp);}
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);context.waitFor(client->LinkedControllerClientHandler.MODE==LinkedControllerClientHandler.Mode.IDLE,200);
        waitServer(context,world,level->link(level,RX).getReceivedSignal()==0&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,LECTERN)instanceof LecternControllerBlockEntity lectern&&!lectern.hasUser(),200,"Native lectern stop retained user or pressed power");
        context.runOnClient(client->{if(client.gui.screen()!=null)client.gui.setScreen(null);});server.runCommand("gamemode spectator @p");
    }
    private static RedstoneLinkBlockEntity link(ServerLevel level,BlockPos pos){return(RedstoneLinkBlockEntity)com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos);}
    private static void use(ClientGameTestContext context){context.getInput().pressKey(options->options.keyUse);}
    private static void block(TestSingleplayerContext world,BlockPos pos,String state){world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private static void controllerScreenshot(ClientGameTestContext context,String name){
        boolean hidden=context.computeOnClient(client->client.gui.hud.isHidden());
        context.runOnClient(client->{if(client.gui.hud.isHidden())client.gui.hud.toggle();});
        try{
            context.waitTicks(2);
            context.runOnClient(client->{
                var state=new net.minecraft.client.renderer.item.ItemStackRenderState();
                var display=client.options.mainHand().get()==net.minecraft.world.entity.HumanoidArm.RIGHT?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
                client.getItemModelResolver().updateForTopItem(state,client.player.getMainHandItem(),display,client.level,client.player,0);
                if(state.isEmpty()||!state.isAnimated())throw new AssertionError("Actual native controller item model did not extract its animated special layer");
            });
            screenshot(context,name);
        }finally{context.runOnClient(client->{if(client.gui.hud.isHidden()!=hidden)client.gui.hud.toggle();});}
    }
    private static void assertLecternMeshPose(net.minecraft.client.Minecraft client,LecternControllerBlockEntity entity,LecternControllerRenderer.State state){
        var bounds=new java.util.ArrayList<AABB>();var poses=new java.util.ArrayList<org.joml.Matrix4f>();
        var collector=(net.minecraft.client.renderer.SubmitNodeCollector)java.lang.reflect.Proxy.newProxyInstance(
            net.minecraft.client.renderer.SubmitNodeCollector.class.getClassLoader(),new Class<?>[]{net.minecraft.client.renderer.SubmitNodeCollector.class},(proxy,method,args)->{
                if(method.getName().equals("order"))return proxy;
                if(!method.getName().equals("submitBlockModel"))return null;
                net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh mesh=null;
                for(var argument:args)if(argument instanceof net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh value)mesh=value;
                if(mesh==null||mesh.size()==0)throw new AssertionError("Lectern submit lost its immutable controller mesh");
                var matrix=new org.joml.Matrix4f(((com.mojang.blaze3d.vertex.PoseStack)args[0]).last().pose());poses.add(matrix);
                double[] range={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
                mesh.forEach(quad->{for(int vertex=0;vertex<4;vertex++){
                    var point=new org.joml.Vector3f(quad.x(vertex),quad.y(vertex),quad.z(vertex)).mulPosition(matrix);
                    if(!point.isFinite())throw new AssertionError("Controller submit contains nonfinite transformed vertices");
                    range[0]=Math.min(range[0],point.x);range[1]=Math.min(range[1],point.y);range[2]=Math.min(range[2],point.z);
                    range[3]=Math.max(range[3],point.x);range[4]=Math.max(range[4],point.y);range[5]=Math.max(range[5],point.z);
                }});bounds.add(new AABB(range[0],range[1],range[2],range[3],range[4],range[5]));return null;
            });
        net.minecraft.client.renderer.blockentity.BlockEntityRenderer<LecternControllerBlockEntity,LecternControllerRenderer.State> renderer=client.getBlockEntityRenderDispatcher().getRenderer(entity);
        renderer.submit(state,new com.mojang.blaze3d.vertex.PoseStack(),collector,new net.minecraft.client.renderer.state.level.CameraRenderState());
        if(bounds.size()!=7)throw new AssertionError("Active lectern must submit one body and six real button meshes");
        var shape=entity.getBlockState().getShape(client.level,entity.getBlockPos());var footprint=shape.bounds();var center=bounds.getFirst().getCenter();
        if(center.x<footprint.minX||center.x>footprint.maxX||center.z<footprint.minZ||center.z>footprint.maxZ)throw new AssertionError("Controller body is outside its actual lectern footprint: "+bounds.getFirst());
        // The source housing bottom is the center of its y=0 face; compare its submitted pose to the real sloped tabletop.
        var foot=new org.joml.Vector3f(.5f,0,.5f).mulPosition(poses.getFirst());
        var surface=shape.clip(new Vec3(foot.x,footprint.maxY+1,foot.z),new Vec3(foot.x,footprint.minY,foot.z),BlockPos.ZERO);
        var heights=shape.toAabbs().stream().mapToDouble(box->box.maxY).distinct().sorted().toArray();double step=Double.POSITIVE_INFINITY;
        for(int i=1;i<heights.length;i++)step=Math.min(step,heights[i]-heights[i-1]);
        if(surface==null||!Double.isFinite(step)||Math.abs(foot.y-surface.getLocation().y)>step+1e-5)throw new AssertionError("Controller housing floats above or misses the native stepped lectern tabletop: "+foot+", surface="+surface);
    }
    private static void look(ClientGameTestContext context,Vec3 target){var angle=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message){var ready=new AtomicBoolean();for(int tick=0;tick<ticks&&!ready.get();tick++){world.getServer().runOnServer(ignored->ready.set(predicate.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
}
