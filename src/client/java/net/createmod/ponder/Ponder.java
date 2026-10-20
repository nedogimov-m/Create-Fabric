// Ponder 1.0.82 source identity/services, MIT; native26.3 engine adaptation.
package net.createmod.ponder;

import java.util.Random;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.content.BasePonderPlugin;
import net.createmod.ponder.foundation.content.DebugPonderPlugin;
import net.createmod.ponder.api.level.PonderLevel;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Ponder {
  public static final String MOD_ID="ponder";
  public static final String MOD_NAME="Ponder";
  public static final Logger LOGGER=LoggerFactory.getLogger(MOD_NAME);
  public static final Random RANDOM=new Random();
  private static boolean registered;
  private static boolean indexReady;
  public static Identifier asResource(String path){return Identifier.fromNamespaceAndPath(MOD_ID,path);}
  public static LangBuilder lang(){return new LangBuilder(MOD_ID);}
  /** Real engine services only; Create scene registration and frontend are separate. */
  public static synchronized void registerEngineFoundation(){
    if(registered)return;
    registered=true;
    PonderConfig.registerConfigs();
    net.createmod.catnip.animation.AnimationTickHolder.register();
    PonderIndex.addPlugin(new BasePonderPlugin());
    if(FabricLoader.getInstance().isDevelopmentEnvironment())
      PonderIndex.addPlugin(new DebugPonderPlugin());
    // Native item defaults bind during connection configuration, after CLIENT_STARTED.
    ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{
      indexReady=false;
      PonderLevel.invalidateResources();
      PonderIndex.reload();
      indexReady=true;
    });
    ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->invalidateSession());
    ClientLifecycleEvents.CLIENT_STOPPING.register(client->invalidateSession());
    var resources=ResourceLoader.get(PackType.CLIENT_RESOURCES);
    var reloadId=asResource("scene_resources");
    resources.registerReloadListener(reloadId,(ResourceManagerReloadListener)manager->{
      PonderLevel.invalidateResources();
      if(indexReady)PonderIndex.reload();
    });
    resources.addListenerOrdering(ResourceReloaderKeys.AFTER_VANILLA,reloadId);
  }
  private static void invalidateSession(){
    indexReady=false;
    PonderIndex.clear();
    PonderLevel.invalidateResources();
  }
  private Ponder(){}
}
