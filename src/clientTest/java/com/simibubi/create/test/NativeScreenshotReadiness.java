package com.simibubi.create.test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LightLayer;

/** Observe actual native light inputs and a real completed world frame before capture. */
final class NativeScreenshotReadiness {
    private record FrameWait(ClientLevel level,Predicate<Minecraft> ready,AtomicBoolean rendered){}
    private static final AtomicReference<FrameWait> ACTIVE=new AtomicReference<>();
    private static boolean registered;

    static void await(ClientGameTestContext context,TestSingleplayerContext world){await(context,world,client->true);}
    static void await(ClientGameTestContext context,TestSingleplayerContext world,Predicate<Minecraft> inputs){
        Predicate<Minecraft> ready=client->client.level!=null&&client.gui.overlay()==null&&inputs.test(client);
        context.waitFor(ready,300);
        world.getConnection().waitForChunksRender();
        var waiting=context.computeOnClient(client->{
            if(!registered){
                LevelRenderEvents.END_MAIN.register(render->{
                    var current=ACTIVE.get();if(current==null)return;
                    var minecraft=Minecraft.getInstance();
                    current.rendered().set(minecraft.level==current.level()&&current.ready().test(minecraft));
                });
                registered=true;
            }
            var current=new FrameWait(client.level,ready,new AtomicBoolean());ACTIVE.set(current);return current;
        });
        try{
            context.waitFor(client->client.level==waiting.level()&&ready.test(client)&&waiting.rendered().get(),300);
        }finally{ACTIVE.compareAndSet(waiting,null);}
    }
    static boolean skyReady(Minecraft client,BlockPos... samples){
        if(client.level==null)return false;
        for(var pos:samples)if(client.level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null
            ||!client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(pos.getX()>>4,pos.getZ()>>4))
            ||client.level.getBrightness(LightLayer.SKY,pos)!=15)return false;
        return true;
    }
    /** Admit the original floor command only after its physical inputs are ready. */
    static void beforeFloor(ClientGameTestContext context,TestSingleplayerContext world,
            BlockPos from,BlockPos to,BlockPos... exposedSamples){
        awaitFloorServer(context,world,level->{
            for(int x=from.getX()>>4;x<=to.getX()>>4;x++)
                for(int z=from.getZ()>>4;z<=to.getZ()>>4;z++)
                    if(level.getChunkSource().getChunkNow(x,z)==null)return false;
            return true;
        },"Actual native fixture floor chunks did not become FULL before fill: "+from+".."+to);
        context.waitFor(client->skyReady(client,exposedSamples),300);
    }
    /** Observe every original quartz floor cell before later fixture mutations or survival. */
    static void afterFloor(ClientGameTestContext context,TestSingleplayerContext world,
            BlockPos from,BlockPos to){
        awaitFloorServer(context,world,level->{
            for(int x=from.getX();x<=to.getX();x++)
                for(int z=from.getZ();z<=to.getZ();z++){
                    var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
                    if(chunk==null||!chunk.getBlockState(new BlockPos(x,from.getY(),z)).is(Blocks.SMOOTH_QUARTZ))return false;
                }
            return true;
        },"Actual native fixture fill did not place every original quartz floor cell: "+from+".."+to);
    }
    private static void awaitFloorServer(ClientGameTestContext context,TestSingleplayerContext world,
            Predicate<ServerLevel> ready,String failure){
        var observed=new AtomicBoolean();
        for(int tick=0;tick<500;tick++){
            world.getServer().runOnServer(ignored->observed.set(ready.test(world.getConnection().getServerLevel())));
            if(observed.get())return;
            context.waitTicks(1);
        }
        throw new AssertionError(failure);
    }
    private NativeScreenshotReadiness(){}
}
