package net.createmod.ponder.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.createmod.catnip.levelWrappers.WrappedClientLevel;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderWorldParticles;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.mixin.client.PonderParticleAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Real in-client execution probe. Call on the client thread after joining a world and loading
 * particle providers. No frontend/pixel parity claim and no automatic test registration.
 */
public final class PonderEngineNativeProbe {
  public static List<String> run(ClientLevel host) {
    var client=Minecraft.getInstance();
    require(client.isSameThread()&&client.level==host,"Probe must use the current native client thread/world");
    var passed=new ArrayList<String>();
    var blockPos=new BlockPos(1,1,1);
    var chestPos=new BlockPos(2,1,1);
    var hostBlock=host.getBlockState(blockPos);
    var hostChest=host.getBlockEntity(chestPos);
    var world=new PonderLevel(BlockPos.ZERO,host);
    world.setBlock(blockPos,Blocks.STONE.defaultBlockState(),0);
    world.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),0);
    var firstChest=(ChestBlockEntity)world.getBlockEntity(chestPos);
    require(firstChest!=null&&firstChest.getLevel()==world,"Native BE must belong to the scene");
    firstChest.setItem(0,new ItemStack(Items.DIAMOND,7));
    var original=new ItemEntity(world,1,4,1,new ItemStack(Items.STONE,3));
    original.setNoGravity(true);
    world.addFreshEntity(original);
    world.createBackup();
    world.setBlock(blockPos,Blocks.GOLD_BLOCK.defaultBlockState(),0);
    firstChest.setItem(0,ItemStack.EMPTY);
    var transientEntity=new ItemEntity(world,2,4,1,new ItemStack(Items.DIRT));
    world.addFreshEntity(transientEntity);
    world.restore();
    require(world.getBlockState(blockPos).is(Blocks.STONE),"Replay must restore real block storage");
    var restoredChest=(ChestBlockEntity)world.getBlockEntity(chestPos);
    require(restoredChest!=firstChest&&firstChest.isRemoved(),"Replay must retire the previous BE");
    require(restoredChest.getItem(0).getCount()==7&&restoredChest.getItem(0).getItem()==Items.DIAMOND,
        "Replay must restore native BE inventory serialization");
    passed.add("native block/BE inventory backup and restore");
    require(original.isRemoved()&&transientEntity.isRemoved(),"Replay must retire previous entities");
    require(world.getEntityList().size()==1,"Replay must not accumulate entity snapshots");
    var restored=world.getEntityList().getFirst();
    require(restored!=original&&restored.level()==world&&restored.getUUID().equals(original.getUUID()),
        "Native entity restore must preserve identity data in the actual local owner");
    passed.add("native owned entity restore and retirement");

    var id=Identifier.fromNamespaceAndPath("ponder","native_engine_probe");
    var scene=new PonderScene(world,new PonderLocalization(),"ponder",id,List.of(),List.of());
    var first=new AtomicInteger();var last=new AtomicInteger();
    var builder=scene.builder();
    builder.addInstruction(value->{first.incrementAndGet();value.getWorld().setBlock(blockPos,Blocks.IRON_BLOCK.defaultBlockState(),0);});
    builder.idle(3);
    builder.addInstruction(value->{last.incrementAndGet();value.getWorld().setBlock(blockPos,Blocks.GOLD_BLOCK.defaultBlockState(),0);});
    builder.idle(2);
    builder.markAsFinished();
    scene.begin();
    require(world.getBlockState(blockPos).is(Blocks.STONE),"Scene begin must restore its backup");
    scene.tick();
    require(first.get()==1&&last.get()==0&&world.getBlockState(blockPos).is(Blocks.IRON_BLOCK),
        "Blocking delay must separate actual world effects");
    scene.seekToTime(32);
    require(scene.isFinished()&&last.get()==1&&world.getBlockState(blockPos).is(Blocks.GOLD_BLOCK),
        "Seeking must execute intervening source instructions");
    passed.add("instruction blocking, native world effects and forward seek");
    boolean rejected=false;
    try {scene.seekToTime(0);}catch(IllegalStateException expected){rejected=true;}
    require(rejected,"Backward seek requires an actual replay");
    scene.begin();
    require(world.getBlockState(blockPos).is(Blocks.STONE),"Replay must reset native world state");
    scene.seekToTime(32);
    require(first.get()==2&&last.get()==2,"Replay must run the schedule exactly once again");
    passed.add("instruction replay and backward-seek guard");

    var particles=new PonderWorldParticles(world);
    var liveCount=client.particleEngine.countParticles();
    try {
      var particle=particles.create(ParticleTypes.END_ROD,1,2,1,0,0,0);
      require(particle!=null,"Actual native provider must create a scene particle");
      var owner=((PonderParticleAccessor)particle).ponder$world();
      require(owner instanceof WrappedClientLevel wrapper&&wrapper.getWrappedLevel()==world,
          "Native particle provider must receive the owning scene world");
      particles.tick();
      require(liveCount.equals(client.particleEngine.countParticles()),"Local particle tick must preserve live particle counts");
      passed.add("actual native particle provider, local owner and live-engine count isolation");
    } finally {particles.invalidateResources();}
    require(host.getBlockState(blockPos)==hostBlock&&host.getBlockEntity(chestPos)==hostChest,
        "Scene instructions/replay must preserve the host world");
    passed.add("host block and BE isolation after replay");
    return List.copyOf(passed);
  }

  private static void require(boolean condition,String message) {
    if(!condition)throw new AssertionError(message);
  }

  private PonderEngineNativeProbe(){}
}
