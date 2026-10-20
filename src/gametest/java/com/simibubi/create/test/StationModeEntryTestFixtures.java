package com.simibubi.create.test;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Real registered actor callback failure, scoped to one actual owner; no readiness or custody mutation. */
public final class StationModeEntryTestFixtures implements ModInitializer {
  public static Block ACTOR;
  private static final Set<UUID> ONCE=ConcurrentHashMap.newKeySet();
  public static final class PlannedRestoreFailure extends RuntimeException{}
  public static void failOnce(UUID actualOwner){ONCE.add(actualOwner);}
  public static void clear(UUID actualOwner){ONCE.remove(actualOwner);}
  @Override public void onInitialize(){
    var id=Identifier.fromNamespaceAndPath("create_test","station_entry_actor");
    var key=ResourceKey.create(Registries.BLOCK,id);
    ACTOR=Registry.register(BuiltInRegistries.BLOCK,key,new Block(BlockBehaviour.Properties.of().setId(key)));
    MovementBehaviour.REGISTRY.register(ACTOR,new MovementBehaviour(){
      @Override public void writeExtraData(MovementContext context){
        var owner=context.contraption.entity;
        if(owner!=null&&context.contraption.phase()==Contraption.Phase.REMOVED&&ONCE.remove(owner.getUUID()))throw new PlannedRestoreFailure();
      }
    });
  }
}
