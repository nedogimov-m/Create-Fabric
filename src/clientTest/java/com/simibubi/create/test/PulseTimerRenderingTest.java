package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.redstone.diodes.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;

/** Proposed independent native region11700..11710, actual held timing UI and true clock chunk return. UNRUN. */
final class PulseTimerRenderingTest {
    private static final BlockPos P=new BlockPos(11703,5,5);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 11703.5 6 6.5");context.waitFor(client->client.level.isLoaded(P)&&Math.abs(client.player.getX()-11703.5)<.1,500);server.runCommand("fill 11700 4 0 11710 4 12 minecraft:smooth_quartz");block(world,P,"create:pulse_timer[facing=north,inverted=false,powered=false,powering=false]");
        waitServer(context,world,level->timer(level)!=null&&timer(level).getMaxState()==20,100,"Actual source timer default20 missing");
        // Native source timing slot must use the shared hold/release board, not direct config calls.
        server.runCommand("gamemode creative @p");server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().isEmpty(),200);open(context);choose(context,0,3);
        waitServer(context,world,level->timer(level).getMaxState()==3,200,"Native tick row did not configure actual timer");
        var high=new AtomicInteger();var low=new AtomicInteger();for(int tick=0;tick<18;tick++){server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(signal(level)==15)high.incrementAndGet();else low.incrementAndGet();});context.waitTicks(1);}if(high.get()==0||low.get()==0)throw new AssertionError("Native autonomous timer did not actually oscillate after UI edit");
        block(world,P.north(),"minecraft:redstone_block");waitServer(context,world,level->timer(level).getState()==0&&signal(level)==0&&timer(level).getBlockState().getValue(BrassDiodeBlock.POWERED),150,"Physical back input did not inhibit/reset clock");context.waitTicks(8);waitServer(context,world,level->timer(level).getState()==0&&signal(level)==0,50,"Held physical input allowed unwanted timer pulse");screenshot(context,"create-vulkan-pulse-timer-native-physical-inhibit-input");
        block(world,P.north(),"minecraft:air");waitServer(context,world,level->!timer(level).getBlockState().getValue(BrassDiodeBlock.POWERED)&&timer(level).getState()!=0,100,"Release did not resume native clock");
        // Slow enough to preserve a real consumed phase over actual chunk save/unload/rejoin.
        open(context);choose(context,2,60);waitServer(context,world,level->timer(level).getMaxState()==72000&&timer(level).getState()>2,200,"Native minute row failed to set long interval");
        open(context);context.getInput().pressKey(InputConstants.KEY_ESCAPE);context.getInput().releaseKey(options->options.keyUse);context.waitFor(client->client.gui.screen()==null,200);waitServer(context,world,level->timer(level).getMaxState()==72000,50,"Escape rewrote timer settings");
        context.waitFor(client->LoadedLevelAccess.blockEntity(client.level,P)instanceof PulseTimerBlockEntity entity&&entity.getMaxState()==72000,200);checkMesh(context);look(context,Vec3.atCenterOf(P));screenshot(context,"create-vulkan-pulse-timer-amethyst-native-model-and-indicator");
        var oldModel=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(AllBlocks.PULSE_TIMER.defaultBlockState()));var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();context.runOnClient(client->{var models=client.getModelManager().getBlockStateModelSet();if(models.get(AllBlocks.PULSE_TIMER.defaultBlockState())==oldModel)throw new AssertionError("Timer retained stale models after actual resource reload");for(var state:AllBlocks.PULSE_TIMER.getStateDefinition().getPossibleStates())if(models.get(state)==models.missingModel())throw new AssertionError("Missing one of32 native timer blockstate models: "+state);});checkMesh(context);
        var old=new AtomicReference<PulseTimerBlockEntity>();server.runOnServer(ignored->old.set(timer(world.getConnection().getServerLevel())));server.runCommand("gamemode spectator @p");server.runCommand("tp @p 13000 14 13000");waitServer(context,world,level->level.getChunkSource().getChunkNow(P.getX()>>4,0)==null,800,"Actual timer chunk did not unload");
        // Native save completion can enqueue retirement after the first unload-queue drain.
        NativeFixtureChunkUnload.finish(context,world,old.get());
        waitServer(context,world,level->level.getChunkSource().getChunkNow(P.getX()>>4,0)==null&&old.get().isRemoved(),800,"Native save/flush must finish actual timer BE teardown before returning");
        var savedPhase=new AtomicInteger();server.runOnServer(ignored->savedPhase.set(old.get().getState()));if(savedPhase.get()<2)throw new AssertionError("Real saved timer must have consumed a nonzero midperiod phase");server.runCommand("tp @p 11703.5 6 6.5");
        waitServer(context,world,level->{var entity=timer(level);return entity!=null&&entity!=old.get()&&entity.getMaxState()==72000&&entity.getState()>=savedPhase.get()&&entity.getState()<savedPhase.get()+400&&signal(level)==0;},600,"True chunk return reset/replayed autonomous phase or lost native period");
        world.getConnection().waitForChunksRender();look(context,Vec3.atCenterOf(P));screenshot(context,"create-vulkan-pulse-timer-real-chunk-return-keeps-native-clock-phase");
    }
    private static void checkMesh(ClientGameTestContext context){context.runOnClient(client->{var entity=(PulseTimerBlockEntity)LoadedLevelAccess.blockEntity(client.level,P);var state=(BrassDiodeRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(entity,.5f,null,false);if(state==null||state.indicator.mesh==null||state.indicator.mesh.size()==0||state.progress<0||state.progress>1)throw new AssertionError("Actual shared native timer indicator mesh/progress missing");});}
    private static void open(ClientGameTestContext context){look(context,Vec3.atLowerCornerOf(P).add(.5,2/16d,.5));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P)&&hit.getDirection()==Direction.UP,150);context.waitTicks(6);context.getInput().holdKey(options->options.keyUse);context.waitFor(client->client.gui.screen()instanceof BrassDiodeScreen screen&&screen.position().equals(P),200);}
    private static void choose(ClientGameTestContext context,int row,int value){var point=context.computeOnClient(client->{var target=((BrassDiodeScreen)client.gui.screen()).getCoordinateOfValue(row,value);float scaleX=(float)client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth();float scaleY=(float)client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight();return new Vec2(target.x*scaleX,target.y*scaleY);});context.getInput().setCursorPos(point.x,point.y);context.waitTicks(3);screenshot(context,"create-vulkan-pulse-timer-native-ticks-seconds-minutes-settings");context.getInput().releaseKey(options->options.keyUse);context.waitFor(client->client.gui.screen()==null,200);}
    private static PulseTimerBlockEntity timer(ServerLevel level){return LoadedLevelAccess.blockEntity(level,P)instanceof PulseTimerBlockEntity timer?timer:null;}
    private static int signal(ServerLevel level){return LoadedLevelAccess.blockState(level,P).getSignal(level,P,Direction.NORTH);}
    private static void block(TestSingleplayerContext world,BlockPos pos,String state){world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private static void look(ClientGameTestContext context,Vec3 target){var angle=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message){var ready=new AtomicBoolean();for(int tick=0;tick<ticks&&!ready.get();tick++){world.getServer().runOnServer(ignored->ready.set(predicate.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
}
