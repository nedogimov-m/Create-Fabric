package com.simibubi.create.infrastructure.config;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Complete native source family: local/common files and a distinct real per-world server scope. */
public final class AllConfigs {
 private static final CClient CLIENT=new CClient();
 private static final CCommon COMMON=new CCommon();
 private static final CServer SERVER=new CServer();
 private static final EnumMap<ConfigScope,NativeConfigStore> STORES=new EnumMap<>(ConfigScope.class);
 static{STORES.put(ConfigScope.CLIENT,new NativeConfigStore(ConfigScope.CLIENT,CLIENT));STORES.put(ConfigScope.COMMON,new NativeConfigStore(ConfigScope.COMMON,COMMON));STORES.put(ConfigScope.SERVER,new NativeConfigStore(ConfigScope.SERVER,SERVER));}
 private record Pending(MinecraftServer owner,Runnable operation){}
 private static final Queue<Pending> PENDING=new ConcurrentLinkedQueue<>();
 private static volatile MinecraftServer activeServer;
 private static JsonObject serverDefaults=new JsonObject();
 private static boolean registered;
 private static final ThreadLocal<Boolean> APPLYING_REMOTE=ThreadLocal.withInitial(()->false);
 public static Consumer<CClient> CLIENT_APPLY=ignored->{};
 public static CClient client(){return CLIENT;}
 public static CCommon common(){return COMMON;}
 /** Matches pinned CServer; callers of the old native descriptor are explicitly recompiled. */
 public static CServer server(){return SERVER;}
 public static NativeConfigBase byType(ConfigScope type){return store(type).root();}
 public static NativeConfigStore store(ConfigScope type){return STORES.get(Objects.requireNonNull(type));}
 public static boolean isServerThread(){var server=activeServer;return server!=null&&server.isSameThread();}
 public static Optional<MinecraftServer> activeServer(){return Optional.ofNullable(activeServer);}
 private static Path configDirectory(){return FabricLoader.getInstance().getConfigDir();}
 private static Path defaultServerFile(){return FabricLoader.getInstance().getGameDir().resolve("defaultconfigs/create-server.json");}
 private static Path defaultLegacyServerFile(){return NeoForgeTomlImport.defaultServerSource(configDirectory(),defaultServerFile().getParent());}
 public static void register(){
  if(registered)return;registered=true;
  CStress stress=SERVER.kinetics.stressValues;
  com.simibubi.create.api.stress.BlockStressValues.IMPACTS.registerProvider(stress::getImpact);
  com.simibubi.create.api.stress.BlockStressValues.CAPACITIES.registerProvider(stress::getCapacity);
  store(ConfigScope.COMMON).configure(AllConfigs::isServerThread,Runnable::run,ignored->()->{},ignored->{});
  store(ConfigScope.CLIENT).configure(AllConfigs::isServerThread,Runnable::run,values->NativeConfigBindings.prepareLocalClient(CLIENT,values),ignored->CLIENT_APPLY.accept(CLIENT));
  store(ConfigScope.SERVER).configure(AllConfigs::isServerThread,AllConfigs::writeServer,values->NativeConfigBindings.prepareServer(SERVER,values),AllConfigs::serverChanged);
  reload(ConfigScope.COMMON);if(FabricLoader.getInstance().getEnvironmentType()==EnvType.CLIENT)reload(ConfigScope.CLIENT);
  store(ConfigScope.SERVER).reload(defaultServerFile(),NativeConfigAliases.server(configDirectory(),store(ConfigScope.SERVER)),false,defaultLegacyServerFile());serverDefaults=store(ConfigScope.SERVER).serverDocument();
  ConfigSyncPacket.register();
  ServerLifecycleEvents.SERVER_STARTING.register(server->{activeServer=server;store(ConfigScope.SERVER).clearRemote();reload(ConfigScope.COMMON);store(ConfigScope.SERVER).reload(server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/create-server.json"),serverDefaults,false);});
  ServerTickEvents.END_SERVER_TICK.register(AllConfigs::drain);
  ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success)->{if(success){reload(ConfigScope.COMMON);reload(ConfigScope.SERVER);}});
  ServerLifecycleEvents.SERVER_STOPPING.register(AllConfigs::drain);
  ServerLifecycleEvents.SERVER_STOPPED.register(server->{if(activeServer!=server)return;PENDING.removeIf(pending->pending.owner==server);activeServer=null;store(ConfigScope.SERVER).clearRemote();store(ConfigScope.SERVER).reload(defaultServerFile(),NativeConfigAliases.server(configDirectory(),store(ConfigScope.SERVER)),false,defaultLegacyServerFile());serverDefaults=store(ConfigScope.SERVER).serverDocument();});
  CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(Commands.literal("create").then(Commands.literal("config").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(Commands.literal("reload").executes(context->{reload(ConfigScope.COMMON);reload(ConfigScope.SERVER);context.getSource().sendSuccess(()->Component.literal("Reloaded Create server and common settings"),true);return 1;})))));
 }
 /** Reloads the one actual CLIENT scope, preserving old overlay aliases and unknown JSON keys. */
 public static void reloadClient(Path file){var store=store(ConfigScope.CLIENT);store.reload(Objects.requireNonNull(file),NativeConfigAliases.client(NativeConfigFiles.read(file),store),true);}
 public static void reload(ConfigScope scope){
  var store=store(scope);switch(scope){
   case CLIENT->reloadClient(configDirectory().resolve("create-client.json"));
   case COMMON->store.reload(configDirectory().resolve("create-common.json"),NativeConfigAliases.common(configDirectory(),store),true);
   case SERVER->writeServer(()->{var server=activeServer;var path=server==null?defaultServerFile():server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/create-server.json");store.reload(path,server==null?NativeConfigAliases.server(configDirectory(),store):serverDefaults,true,server==null?defaultLegacyServerFile():null);});
  }
 }
 private static void writeServer(Runnable operation){var server=activeServer;if(server==null){if(store(ConfigScope.SERVER).hasRemote())throw new IllegalStateException("Remote server configuration is read-only");operation.run();return;}if(server.isSameThread()&&!Transaction.isOpen())operation.run();else PENDING.add(new Pending(server,operation));}
 private static void drain(MinecraftServer server){if(server!=activeServer||Transaction.isOpen())return;for(int remaining=PENDING.size();remaining>0;remaining--){var next=PENDING.poll();if(next==null)break;if(next.owner==server)next.operation.run();}}
 private static void serverChanged(NativeConfigStore store){
  var server=activeServer;if(server==null){serverDefaults=store.serverDocument();return;}
  NativeConfigBindings.invalidateLoadedKinetics(server);
  // SERVER_STARTING precedes PlayerList setup; JOIN sends the current snapshot to later players.
  var players=server.getPlayerList();if(players==null)return;
  for(var player:players.getPlayers())ServerPlayNetworking.send(player,new ConfigSyncPacket(store.serverDocument().toString()));
 }
 public static void installRemoteServer(JsonObject document){var store=store(ConfigScope.SERVER);var decoded=store.decode(document);var apply=NativeConfigBindings.prepareRemoteServer(SERVER,decoded);store.installRemote(document);boolean previous=APPLYING_REMOTE.get();APPLYING_REMOTE.set(true);try{apply.run();}finally{if(previous)APPLYING_REMOTE.set(true);else APPLYING_REMOTE.remove();}}
 public static void installRemoteValues(Map<String,?> values){if(!APPLYING_REMOTE.get())store(ConfigScope.SERVER).installRemoteValues(values);}
 public static void importLegacyValues(Map<String,?> values,Runnable initialApply){var store=store(ConfigScope.SERVER);store.importValues(values,registered&&store.file().isPresent());if(!registered)initialApply.run();}
 public static void clearRemoteValues(Collection<String> keys){store(ConfigScope.SERVER).clearRemoteValues(keys);}
 public static void clearRemoteServer(){SERVER.recipes.clearClient();store(ConfigScope.SERVER).clearRemote();NativeConfigBindings.clearRemoteServer();}
 private AllConfigs(){}
}
