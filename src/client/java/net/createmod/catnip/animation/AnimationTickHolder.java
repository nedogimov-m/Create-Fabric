// Catnip1.0.82 source clock, MIT; native26.3 scene-owned timing adaptation.
package net.createmod.catnip.animation;

import net.createmod.catnip.levelWrappers.WrappedClientLevel;
import net.createmod.ponder.api.level.PonderLevel;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.LevelAccessor;

public final class AnimationTickHolder {
  private static int ticks,pausedTicks;
  private static boolean registered;
  public static void register(){
    if(registered)return;registered=true;
    ClientTickEvents.END_CLIENT_TICK.register(client->tick());
    ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
  }
  public static void reset(){ticks=pausedTicks=0;}
  public static void tick(){
    if(!Minecraft.getInstance().isPaused())ticks=(ticks+1)%1_728_000;
    else pausedTicks=(pausedTicks+1)%1_728_000;
  }
  public static int getTicks(){return getTicks(false);}
  public static int getTicks(boolean includePaused){return includePaused?ticks+pausedTicks:ticks;}
  public static int getTicks(LevelAccessor level){
    if(level instanceof WrappedClientLevel wrapped)return getTicks(wrapped.getWrappedLevel());
    return level instanceof PonderLevel ponder?ponder.simulationTicks():getTicks();
  }
  public static float getPartialTicks(){return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);}
  public static float getPartialTicks(LevelAccessor level){
    if(level instanceof WrappedClientLevel wrapped)return getPartialTicks(wrapped.getWrappedLevel());
    return level instanceof PonderLevel ponder?ponder.scenePartialTicks():getPartialTicks();
  }
  public static float getPartialTicksUI(){
    var timer=Minecraft.getInstance().getDeltaTracker();
    return timer instanceof net.createmod.ponder.mixin.client.PonderTimerAccessor accessor
        ?accessor.ponder$deltaTickResidual():getPartialTicks();
  }
  public static float getRenderTime(){return getTicks()+getPartialTicks();}
  public static float getRenderTime(LevelAccessor level){return getTicks(level)+getPartialTicks(level);}
  private AnimationTickHolder(){}
}
