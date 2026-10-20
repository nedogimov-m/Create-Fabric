package com.simibubi.create.test;

import com.google.gson.JsonObject;
import com.simibubi.create.foundation.mixin.accessor.EntitySectionVisibilityAccessor;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.test.mixin.GameTestHelperNativeInfoAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.ChunkPos;

/** Real native server settings for overlapping player-loading fixtures; never changes chunk visibility or tickets directly. */
public final class NativePlayerDistanceScope implements AutoCloseable {
    private static final Map<MinecraftServer,Owner> OWNERS=new IdentityHashMap<>();
    private static final class Owner {
        final PlayerList players;
        final int beforeView,beforeSimulation,view,simulation;
        int scopes;
        boolean settingsOwned=true;
        Owner(PlayerList players){this.players=players;beforeView=players.getViewDistance();beforeSimulation=players.getSimulationDistance();view=Math.max(beforeView,4);simulation=Math.max(beforeSimulation,4);}
        boolean current(MinecraftServer server){return server.getPlayerList()==players&&players.getViewDistance()==view&&players.getSimulationDistance()==simulation;}
    }
    private final GameTestHelper helper;
    private final MinecraftServer server;
    private final Owner owner;
    private boolean closed;

    private NativePlayerDistanceScope(GameTestHelper helper,MinecraftServer server,Owner owner){this.helper=helper;this.server=server;this.owner=owner;}
    private static void requireThread(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("Actual native player-distance scopes require the server thread");}

    public static NativePlayerDistanceScope acquire(GameTestHelper helper){
        var server=helper.getLevel().getServer();requireThread(server);var owner=OWNERS.get(server);
        if(owner==null){
            owner=new Owner(server.getPlayerList());
            try{owner.players.setViewDistance(owner.view);owner.players.setSimulationDistance(owner.simulation);}
            catch(RuntimeException|Error failure){
                try{if(server.getPlayerList()==owner.players&&owner.players.getViewDistance()==owner.view&&(owner.players.getSimulationDistance()==owner.simulation||owner.players.getSimulationDistance()==owner.beforeSimulation)){owner.players.setSimulationDistance(owner.beforeSimulation);owner.players.setViewDistance(owner.beforeView);}}
                catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}throw failure;
            }
            OWNERS.put(server,owner);
        }else if(!owner.current(server))owner.settingsOwned=false;
        helper.assertTrue(server.getPlayerList().getViewDistance()>=4&&server.getPlayerList().getSimulationDistance()>=4,"Actual native player view/simulation distances support the loading fixture without changing chunk readiness");
        owner.scopes++;var scope=new NativePlayerDistanceScope(helper,server,owner);scope.receipt("acquired",false);return scope;
    }

    private void receipt(String phase,boolean restored){
        var data=new JsonObject();data.addProperty("test",((GameTestHelperNativeInfoAccessor)(Object)helper).create$getActualNativeTestInfo().id().toString());data.addProperty("phase",phase);data.addProperty("beforeView",owner.beforeView);data.addProperty("beforeSimulation",owner.beforeSimulation);data.addProperty("ownedView",owner.view);data.addProperty("ownedSimulation",owner.simulation);data.addProperty("actualView",server.getPlayerList().getViewDistance());data.addProperty("actualSimulation",server.getPlayerList().getSimulationDistance());data.addProperty("activeScopes",owner.scopes);data.addProperty("settingsOwned",owner.settingsOwned);data.addProperty("restoredAfterOwnedCleanup",restored);System.err.println("CREATE_NATIVE_PLAYER_DISTANCE_SCOPE "+data);
    }

    public void trace(ServerLevel world,BlockPos pos,String phase){
        requireThread(server);var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);long key=ChunkPos.pack(pos.getX()>>4,pos.getZ()>>4);var manager=((ServerLevelEntityManagerAccessor)world).create$getEntityManager();var visibility=((EntitySectionVisibilityAccessor)manager).create$getChunkVisibility().get(key);
        var data=new JsonObject();data.addProperty("test",((GameTestHelperNativeInfoAccessor)(Object)helper).create$getActualNativeTestInfo().id().toString());data.addProperty("phase",phase);data.addProperty("dimension",world.dimension().identifier().toString());data.addProperty("position",pos.toString());data.addProperty("view",server.getPlayerList().getViewDistance());data.addProperty("simulation",server.getPlayerList().getSimulationDistance());data.addProperty("activeScopes",owner.scopes);data.addProperty("chunkPresent",chunk!=null);data.addProperty("fullStatus",chunk==null?null:String.valueOf(chunk.getFullStatus()));data.addProperty("entitiesLoaded",world.areEntitiesLoaded(key));data.addProperty("visibility",String.valueOf(visibility));data.addProperty("queryReady",LoadedEntityAccess.isReady(world,pos));data.addProperty("positionEntityTicking",world.isPositionEntityTicking(pos));System.err.println("CREATE_NATIVE_PLAYER_DISTANCE_READINESS "+data);
    }

    /** Call only from the fixture's final cleanup stage, after its owned Train has been handled. */
    @Override public void close(){
        if(closed)return;requireThread(server);if(OWNERS.get(server)!=owner||owner.scopes<=0)throw new IllegalStateException("Actual native player-distance scope owner changed");closed=true;if(!owner.current(server))owner.settingsOwned=false;owner.scopes--;
        if(owner.scopes!=0){receipt("released_shared_scope_keeps_settings",false);return;}
        OWNERS.remove(server);
        if(owner.settingsOwned&&owner.current(server)){
            owner.players.setSimulationDistance(owner.beforeSimulation);owner.players.setViewDistance(owner.beforeView);helper.assertTrue(owner.players.getViewDistance()==owner.beforeView&&owner.players.getSimulationDistance()==owner.beforeSimulation,"Last real native distance scope restores the exact original settings after every owned fixture cleanup");receipt("last_scope_restored",true);
        }else receipt("last_scope_preserved_external_settings",false);
    }
}
