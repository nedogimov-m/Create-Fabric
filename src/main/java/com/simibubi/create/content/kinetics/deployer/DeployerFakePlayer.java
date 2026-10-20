package com.simibubi.create.content.kinetics.deployer;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;

public final class DeployerFakePlayer extends FakePlayer {
    public java.util.List<net.minecraft.world.item.ItemStack> movingOverflow=new java.util.ArrayList<>();
    public boolean acting;
    public boolean placedTracks;
    public org.apache.commons.lang3.tuple.Pair<net.minecraft.core.BlockPos,Float> blockBreakingProgress;
    public net.minecraft.core.BlockPos workTarget;
    public static final UUID FALLBACK_ID=UUID.fromString("9e2faded-cafe-4ec2-c314-dad129ae971d");
    public DeployerFakePlayer(ServerLevel level,UUID owner){super(level,new GameProfile(owner==null?FALLBACK_ID:owner,"Deployer"));setGameMode(GameType.SURVIVAL);refreshDimensions();}
    @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider){return java.util.OptionalInt.empty();}
    @Override public void remove(RemovalReason reason){DeployerHandler.clearBreaking(this);super.remove(reason);}
    @Override public Component getDisplayName(){return Component.translatable("create.block.deployer.damage_source_name");}
    @Override public EntityDimensions getDefaultDimensions(Pose pose){return super.getDefaultDimensions(pose).withEyeHeight(0);}
    @Override public float getCurrentItemAttackStrengthDelay(){return 1/64f;}
    @Override public boolean canEat(boolean ignoreHunger){return false;}
    @Override public boolean canBeAffected(MobEffectInstance effect){return false;}
}
