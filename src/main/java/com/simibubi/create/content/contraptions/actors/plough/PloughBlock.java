package com.simibubi.create.content.contraptions.actors.plough;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.contraptions.actors.AttachedActorBlock;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class PloughBlock extends AttachedActorBlock {
    public PloughBlock(Properties properties){super(properties.forceSolidOn());}
    public static class PloughFakePlayer extends FakePlayer {
        public static final GameProfile PLOUGH_PROFILE=new GameProfile(UUID.fromString("9e2faded-eeee-4ec2-c314-dad129ae971d"),"Plough");
        public PloughFakePlayer(ServerLevel level){super(level,PLOUGH_PROFILE);}
    }
}
