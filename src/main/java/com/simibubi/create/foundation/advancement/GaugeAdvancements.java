package com.simibubi.create.foundation.advancement;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
/** Original real gauge/PSI criteria, awarded only to each actual device's saved placement owner. */
public final class GaugeAdvancements {
 public static void register(){for(var id:List.of("stressometer","stressometer_maxed","portable_storage_interface")){var key=Create.asResource(id+"_builtin");if(!BuiltInRegistries.TRIGGER_TYPES.containsKey(key))com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(key);}}
 public static boolean live(BlockEntity be){return be.getLevel() instanceof ServerLevel level&&!be.isRemoved()&&LoadedLevelAccess.blockEntity(level,be.getBlockPos())==be&&LoadedLevelAccess.blockState(level,be.getBlockPos()).getBlock()==be.getBlockState().getBlock();}
 public static boolean completed(ServerPlayer player,String id){var holder=player.level().getServer().getAdvancements().get(Create.asResource(id));return holder==null||player.getAdvancements().getOrStartProgress(holder).isDone();}
 public static ServerPlayer owner(BlockEntity be,UUID owner){return !live(be)||owner==null?null:be.getLevel().getPlayerByUUID(owner) instanceof ServerPlayer player?player:null;}
 public static boolean complete(BlockEntity be,UUID owner,String... ids){var player=owner(be,owner);if(player==null)return false;for(var id:ids)if(!completed(player,id))return false;return true;}
 public static void award(BlockEntity be,UUID owner,String id){var player=owner(be,owner);if(player==null||completed(player,id))return;var criterion=BuiltInRegistries.TRIGGER_TYPES.getValue(Create.asResource(id+"_builtin"));if(!(criterion instanceof SimpleCreateTrigger actual))throw new IllegalStateException("Actual native source criterion missing: "+id);actual.trigger(player);}
 public static boolean actualPlacer(BlockEntity be,ServerPlayer player){return live(be)&&player.level()==be.getLevel()&&!(player instanceof net.fabricmc.fabric.api.entity.FakePlayer)&&be.getLevel().getPlayerByUUID(player.getUUID())==player;}
 private GaugeAdvancements(){}
}
