package com.simibubi.create.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Literal native survival player, registered by the same embedded login transport as vanilla GameTests. */
public record NativeSurvivalTestPlayer(ServerPlayer player,EmbeddedChannel channel) implements AutoCloseable {
    public static NativeSurvivalTestPlayer join(GameTestHelper helper,Vec3 position){
        var level=helper.getLevel();
        var profile=new GameProfile(UUID.randomUUID(),"device_"+UUID.randomUUID().toString().substring(0,8));
        var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);
        var channel=new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection,player,CommonListenerCookie.createInitial(profile,false));
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(player.teleportTo(level,position.x,position.y,position.z,Set.of(),0,0,false),"Actual registered survival player reaches the physical source fixture");
        helper.assertTrue(player.getClass()==ServerPlayer.class&&!(player instanceof FakePlayer)&&player.gameMode()==GameType.SURVIVAL&&!player.hasInfiniteMaterials()&&player.mayBuild()&&level.getServer().getPlayerList().getPlayer(player.getUUID())==player&&level.getEntity(player.getUUID())==player,"Literal native player, survival debit abilities, real PlayerList and native level retain one exact UUID owner");
        return new NativeSurvivalTestPlayer(player,channel);
    }
    @Override public void close(){if(player.level().getServer().getPlayerList().getPlayer(player.getUUID())==player)player.level().getServer().getPlayerList().remove(player);channel.finishAndReleaseAll();}
}
