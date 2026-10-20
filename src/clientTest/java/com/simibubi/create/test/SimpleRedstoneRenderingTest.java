package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.redstone.analogLever.*;
import com.simibubi.create.content.redstone.diodes.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;

/** Proposed region11000..11020; actual held UI, timed output, native meshes and real chunk unload. STAGED, UNRUN. */
final class SimpleRedstoneRenderingTest {
    private static final BlockPos R=new BlockPos(11007,5,5),E=R.south(3),T=R.east(4),L=T.south(3),A=R.east(8);
    private record Saved(int repeaterDelay,int extenderDelay,int extenderState,int strength){}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 11010.5 8 9.5");context.waitFor(client->client.level.isLoaded(A)&&Math.abs(client.player.getX()-11010.5)<.1,500);
        server.runCommand("fill 11000 4 0 11022 4 15 minecraft:smooth_quartz");block(world,R,"create:pulse_repeater[facing=north,inverted=false,powered=false,powering=false]");block(world,E,"create:pulse_extender[facing=north,inverted=false,powered=false,powering=false]");block(world,T,"create:powered_toggle_latch[facing=north,powered=false,powering=false]");block(world,L,"create:powered_latch[facing=north,powered=false,powered_side=false,powering=false]");block(world,A,"create:analog_lever[face=floor,facing=north]");
        server.runCommand("gamemode creative @p");server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().isEmpty(),200);
        // Physical hold and release select source seconds/minutes rows; no direct screen creation/config injection.
        teleport(context,world,11007.5,6,6.5);open(context,R);choose(context,1,2);waitServer(context,world,level->diode(level,R).getMaxState()==40,200,"Native seconds board did not commit40ticks");
        open(context,R);context.getInput().pressKey(InputConstants.KEY_ESCAPE);context.getInput().releaseKey(options->options.keyUse);context.waitFor(client->client.gui.screen()==null,200);waitServer(context,world,level->diode(level,R).getMaxState()==40,50,"Escape changed source timing");
        look(context,Vec3.atLowerCornerOf(R).add(.5,2/16d,.5));context.getInput().pressKey(options->options.keyUse);waitServer(context,world,level->LoadedLevelAccess.blockState(level,R).getValue(BrassDiodeBlock.INVERTED),200,"Real short click did not invert diode");
        look(context,Vec3.atLowerCornerOf(R).add(.5,2/16d,.5));context.getInput().pressKey(options->options.keyUse);waitServer(context,world,level->!LoadedLevelAccess.blockState(level,R).getValue(BrassDiodeBlock.INVERTED),200,"Second short click did not restore noninverted output");
        block(world,R.north(),"minecraft:redstone_block");waitServer(context,world,level->diode(level,R).getState()>41&&signal(level,R)==0,200,"Held repeater must emit once and wait for low input");
        teleport(context,world,11007.5,6,9.5);open(context,E);choose(context,2,60);waitServer(context,world,level->diode(level,E).getMaxState()==72000,200,"Native minute row did not preserve maximum72000ticks");
        block(world,E.north(),"minecraft:redstone_block");waitServer(context,world,level->diode(level,E).getState()==72000&&signal(level,E)==15,200,"Actual extender input did not power a real long interval");block(world,E.north(),"minecraft:air");
        context.waitFor(client->LoadedLevelAccess.blockEntity(client.level,E)instanceof BrassDiodeBlockEntity diode&&diode.getState()>0,200);verifyIndicator(context,E);look(context,Vec3.atCenterOf(E));screenshot(context,"create-vulkan-simple-redstone-extender-native-timed-indicator");
        // Native empty-hand interactions exercise both latch types and the animated analog handle.
        teleport(context,world,11011.5,6,6.5);use(context,T);waitServer(context,world,level->signal(level,T)==15,200,"Native toggle latch click did not hold output");use(context,T);waitServer(context,world,level->signal(level,T)==0,200,"Second native toggle click failed");
        block(world,L.north(),"minecraft:redstone_block");waitServer(context,world,level->signal(level,L)==15,100,"Powered latch back input failed");block(world,L.east(),"minecraft:redstone_block");waitServer(context,world,level->signal(level,L)==0,100,"Powered latch real side input failed");block(world,L.east(),"minecraft:air");waitServer(context,world,level->signal(level,L)==15,100,"Source side falling recovery failed");
        teleport(context,world,11015.5,6,6.5);for(int click=0;click<3;click++){use(context,A);context.waitTicks(5);}waitServer(context,world,level->lever(level).getState()==3,200,"Native analog clicks must change actual strength");
        context.waitFor(client->LoadedLevelAccess.blockEntity(client.level,A)instanceof AnalogLeverBlockEntity lever&&Math.abs(lever.getClientState(1)-3)<.01,200);verifyLever(context);screenshot(context,"create-vulkan-simple-redstone-native-analog-handle-and-indicator");
        var oldModel=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(AllBlocks.ANALOG_LEVER.defaultBlockState()));var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();context.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(AllBlocks.ANALOG_LEVER.defaultBlockState())==oldModel)throw new AssertionError("Redstone models remained stale across actual resource reload");});verifyLever(context);verifyIndicator(context,E);verifyAllModels(context);
        var oldExtender=new AtomicReference<BrassDiodeBlockEntity>();var oldLever=new AtomicReference<AnalogLeverBlockEntity>();var saved=new AtomicReference<Saved>();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();oldExtender.set(diode(level,E));oldLever.set(lever(level));saved.set(new Saved(diode(level,R).getMaxState(),diode(level,E).getMaxState(),diode(level,E).getState(),lever(level).getState()));});
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 12000 14 12000");
        var unloadWatch=server.computeOnServer(ignored->NativeRedstoneUnloadWatch.capture(world.getConnection().getServerLevel(),oldExtender.get(),oldLever.get()));
        var incompleteRemovalObserved=new AtomicBoolean();
        NativeRedstoneUnloadWatch.waitFor(context,world,level->{
            boolean unavailable=level.getChunkSource().getChunkNow(R.getX()>>4,0)==null&&level.getChunkSource().getChunkNow(A.getX()>>4,0)==null;
            if(unavailable&&(!oldExtender.get().isRemoved()||!oldLever.get().isRemoved()))incompleteRemovalObserved.set(true);
            return unavailable;
        },800,"Both real redstone chunks must leave the native FULL map before the native save/flush",unloadWatch,"simple-visible-unload");
        // The accelerated native test scheduler can leave the normal unload queue at its
        // 2000-entry idle-budget threshold. The actual native world save/flush API runs
        // saveAllChunks(true), including real queued teardown and disk synchronization.
        server.runOnServer(ignored->{if(!world.getConnection().getServerLevel().getServer().saveEverything(true,true,true))throw new AssertionError("Actual native world save/flush was refused");});
        NativeRedstoneUnloadWatch.waitFor(context,world,level->{
            boolean unavailable=level.getChunkSource().getChunkNow(R.getX()>>4,0)==null&&level.getChunkSource().getChunkNow(A.getX()>>4,0)==null;
            boolean removed=oldExtender.get().isRemoved()&&oldLever.get().isRemoved();
            if(unavailable&&!removed)incompleteRemovalObserved.set(true);
            return unavailable&&removed;
        },800,"Both original native redstone BEs must actually be removed with their unloaded chunks before returning",unloadWatch,"simple-unload");
        server.runOnServer(ignored->Create.LOGGER.info("Redstone fixture native unload: unavailable-before-removal={}, oldExtenderRemoved={}, oldLeverRemoved={}",incompleteRemovalObserved.get(),oldExtender.get().isRemoved(),oldLever.get().isRemoved()));
        server.runCommand("tp @p 11010.5 8 9.5");var returned=new AtomicReference<String>("no sample");
        try{NativeRedstoneUnloadWatch.waitFor(context,world,level->{var snapshot=saved.get();var r=diode(level,R);var e=diode(level,E);var a=lever(level);
            returned.set("saved="+snapshot+", repeater="+(r==null?null:r.getMaxState())+", extender="+(e==null?null:e.getMaxState()+"/"+e.getState())+", sameExtender="+(e==oldExtender.get())+", lever="+(a==null?null:a.getState())+", sameLever="+(a==oldLever.get())+", latch="+signal(level,L));
            return r!=null&&e!=null&&a!=null&&e!=oldExtender.get()&&a!=oldLever.get()&&r.getMaxState()==snapshot.repeaterDelay&&e.getMaxState()==snapshot.extenderDelay&&e.getState()>0&&e.getState()<=snapshot.extenderState&&a.getState()==snapshot.strength&&signal(level,L)==15;
        },600,"True chunk return lost settings, replayed tail, lost latch, or reset analog strength",unloadWatch,"simple-return");}
        catch(AssertionError timeout){throw new AssertionError(returned.get(),timeout);}
        world.getConnection().waitForChunksRender();look(context,Vec3.atCenterOf(E));screenshot(context,"create-vulkan-simple-redstone-real-chunk-return-keeps-clock-latch-and-strength");
    }
    private static void verifyIndicator(ClientGameTestContext context,BlockPos pos){context.runOnClient(client->{var entity=(BrassDiodeBlockEntity)LoadedLevelAccess.blockEntity(client.level,pos);var state=(BrassDiodeRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(entity,.5f,null,false);if(state==null||state.indicator.mesh==null||state.indicator.mesh.size()==0||state.progress<=0||state.color==0)throw new AssertionError("Actual native diode tint mesh/progress missing");});}
    private static void verifyLever(ClientGameTestContext context){context.runOnClient(client->{var entity=(AnalogLeverBlockEntity)LoadedLevelAccess.blockEntity(client.level,A);var state=(AnalogLeverRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(entity,.5f,null,false);if(state==null||state.handle.mesh==null||state.handle.mesh.size()==0||state.indicator.mesh==null||state.indicator.mesh.size()==0||Math.abs(state.handleAngle-18)>.1)throw new AssertionError("Native analog handle18degrees and genuine colored indicator required");});}
    private static void verifyAllModels(ClientGameTestContext context){context.runOnClient(client->{var models=client.getModelManager().getBlockStateModelSet();for(var block:new net.minecraft.world.level.block.Block[]{AllBlocks.PULSE_EXTENDER,AllBlocks.PULSE_REPEATER,AllBlocks.POWERED_TOGGLE_LATCH,AllBlocks.POWERED_LATCH,AllBlocks.ANALOG_LEVER})for(var state:block.getStateDefinition().getPossibleStates())if(models.get(state)==models.missingModel())throw new AssertionError("Missing native redstone state model: "+state);});}
    private static void teleport(ClientGameTestContext context,TestSingleplayerContext world,double x,double y,double z){
        world.getServer().runCommand("tp @p "+x+" "+y+" "+z);
        context.waitFor(client->Math.abs(client.player.getX()-x)<.1&&Math.abs(client.player.getZ()-z)<.1,200);
    }
    private static void open(ClientGameTestContext context,BlockPos pos){
        look(context,Vec3.atLowerCornerOf(pos).add(.5,2/16d,.5));
        try{context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos)&&hit.getDirection()==Direction.UP,150);}
        catch(AssertionError timeout){throw new AssertionError("Native diode top ray missing: "+context.computeOnClient(client->"target="+pos+", state="+client.level.getBlockState(pos)+", player="+client.player.position()+", eye="+client.player.getEyePosition()+", hit="+client.hitResult),timeout);}
        context.waitTicks(6);context.getInput().holdKey(options->options.keyUse);
        context.waitFor(client->client.gui.screen()instanceof BrassDiodeScreen screen&&screen.position().equals(pos),200);
    }
    private static void choose(ClientGameTestContext context,int row,int value){var point=context.computeOnClient(client->{var target=((BrassDiodeScreen)client.gui.screen()).getCoordinateOfValue(row,value);float scaleX=(float)client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth();float scaleY=(float)client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight();return new Vec2(target.x*scaleX,target.y*scaleY);});context.getInput().setCursorPos(point.x,point.y);context.waitTicks(3);screenshot(context,"create-vulkan-simple-redstone-native-three-unit-time-board");context.getInput().releaseKey(options->options.keyUse);context.waitFor(client->client.gui.screen()==null,200);}
    private static void use(ClientGameTestContext context,BlockPos pos){look(context,Vec3.atLowerCornerOf(pos).add(.5,2/16d,.5));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),150);context.waitTicks(6);context.getInput().pressKey(options->options.keyUse);}
    private static BrassDiodeBlockEntity diode(ServerLevel level,BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos)instanceof BrassDiodeBlockEntity diode?diode:null;}
    private static AnalogLeverBlockEntity lever(ServerLevel level){return LoadedLevelAccess.blockEntity(level,A)instanceof AnalogLeverBlockEntity lever?lever:null;}
    private static int signal(ServerLevel level,BlockPos pos){return LoadedLevelAccess.blockState(level,pos).getSignal(level,pos,Direction.NORTH);}
    private static void block(TestSingleplayerContext world,BlockPos pos,String state){world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private static void look(ClientGameTestContext context,Vec3 target){var angle=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message){var ready=new AtomicBoolean();for(int tick=0;tick<ticks&&!ready.get();tick++){world.getServer().runOnServer(ignored->ready.set(predicate.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
}
