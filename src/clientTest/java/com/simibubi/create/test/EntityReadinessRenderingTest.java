package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.Vec3;

/** Real saved glue/passengers return after blocks: the kinetic controller must defer ownership. */
final class EntityReadinessRenderingTest {
    private static final BlockPos P=new BlockPos(5503,8,5),B=P.below(),C=P.east(),S=P.east(2),M=B.below();
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 5504.5 12 12.5");
        context.waitFor(client->Math.abs(client.player.getX()-5504.5)<.1&&client.level.isLoaded(S),400);
        server.runCommand("fill 5498 5 0 5512 5 15 minecraft:smooth_quartz");
        NativeContraptionFixtures.awaitEntityReady(context,world,400,P,S);
        var glueId=new AtomicReference<UUID>();var cowId=new AtomicReference<UUID>();var seatId=new AtomicReference<UUID>();
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();
            level.setBlockAndUpdate(B,AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));
            level.setBlockAndUpdate(P,Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(C,Blocks.BARREL.defaultBlockState());
            ((BarrelBlockEntity)level.getBlockEntity(C)).setItem(0,payload());level.setBlockAndUpdate(S,AllBlocks.SEATS.get(DyeColor.BLUE).defaultBlockState());
            var glue=new SuperGlueEntity(level,SuperGlueEntity.span(P,S));if(!level.addFreshEntity(glue))throw new AssertionError("Initial glue rejected");glueId.set(glue.getUUID());
            var cow=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);cow.setNoAi(true);cow.setPersistenceRequired();cow.setPos(Vec3.atCenterOf(S));
            if(!level.addFreshEntity(cow))throw new AssertionError("Initial cow rejected");NativeContraptionFixtures.sitDown(level,S,cow);
            if(!(cow.getVehicle() instanceof SeatEntity))throw new AssertionError("Real passenger not seated");cowId.set(cow.getUUID());seatId.set(cow.getVehicle().getUUID());
        });
        waitServer(context,world,level->ready(level)&&SuperGlueEntity.isGlued(level,P,Direction.EAST,null),400,"Initial source entities not queryable");
        server.runCommand("tp @p 6200 15 6200");
        waitServer(context,world,level->level.getEntity(glueId.get())==null&&level.getEntity(cowId.get())==null&&level.getChunkSource().getChunkNow(P.getX()>>4,0)==null&&level.getChunkSource().getChunkNow(C.getX()>>4,0)==null,800,"Actual source chunks/entities did not unload");
        var gate=new AtomicReference<NativeEntityLoadGate.Held>();
        server.runOnServer(ignored->gate.set(NativeEntityLoadGate.arm(world.getConnection().getServerLevel(),new ChunkPos(C.getX()>>4,C.getZ()>>4))));
        try {
            server.runCommand("tp @p 5504.5 12 12.5");
            waitServer(context,world,level->gate.get().holdsCompletedPayload()&&level.getChunkSource().getChunkNow(P.getX()>>4,0)!=null&&level.getChunkSource().getChunkNow(C.getX()>>4,0)!=null,500,"Real saved payload or block chunks did not return");
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();
                if(LoadedEntityAccess.isReady(level,C)||level.getEntity(glueId.get())!=null||level.getEntity(cowId.get())!=null)throw new AssertionError("Native gate failed to withhold persisted entities");
                level.setBlockAndUpdate(M,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.UP));
            });
            waitServer(context,world,level->{var bearing=(MechanicalBearingBlockEntity)level.getBlockEntity(B);return bearing!=null&&bearing.getSpeed()!=0&&bearing.getLastAssemblyException()!=null&&bearing.getLastAssemblyException().canRetry();},200,"Real kinetic bearing did not defer unavailable entity data");
            context.waitTicks(12);
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var bearing=(MechanicalBearingBlockEntity)level.getBlockEntity(B);
                if(!gate.get().holdsCompletedPayload()||bearing.isRunning()||bearing.getMovedContraption()!=null||!level.getBlockState(P).is(Blocks.STONE)||!level.getBlockState(S).is(AllBlocks.SEATS.get(DyeColor.BLUE))||!same(((BarrelBlockEntity)level.getBlockEntity(C)).getItem(0)))throw new AssertionError("Pending native data removed a root, cargo or passenger seat");
                Create.LOGGER.info("Native readiness gate: real saved payload held; kinetic retries preserve all three source blocks and seven named diamonds");
            });
            screenshot(context,"create-vulkan-entity-readiness-deferred-source-intact");
            server.runOnServer(ignored->gate.get().close());
            waitServer(context,world,level->{var bearing=(MechanicalBearingBlockEntity)level.getBlockEntity(B);var cow=level.getEntity(cowId.get());return bearing!=null&&bearing.isRunning()&&bearing.getMovedContraption()!=null&&cow!=null&&cow.getVehicle()==bearing.getMovedContraption();},400,"Bearing did not retry with original persisted passenger");
            var ownerId=new AtomicReference<UUID>();
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var owner=((MechanicalBearingBlockEntity)level.getBlockEntity(B)).getMovedContraption();ownerId.set(owner.getUUID());
                if(owner.getContraption().getBlocks().size()!=3||!same(owner.getContraption().getStorage().getAllItemStorages().get(new BlockPos(1,0,0)).getStackInSlot(0))||level.getEntity(glueId.get())!=null||level.getEntity(seatId.get())!=null||owner.getContraption().getSeatOf(cowId.get())==null)throw new AssertionError("Retry failed complete glue/cargo/passenger handoff");
            });
            context.waitFor(client->{for(var entity:client.level.entitiesForRendering())if(entity instanceof ControlledContraptionEntity owner&&entity.getUUID().equals(ownerId.get())&&owner.getContraption()!=null)return owner.getContraption().getBlocks().size()==3&&Math.abs(owner.getAngle(1))>15;return false;},300);
            context.getInput().lookAt(P);world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-entity-readiness-automatic-full-assembly");
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var bearing=(MechanicalBearingBlockEntity)level.getBlockEntity(B);var owner=bearing.getMovedContraption();
                level.setBlockAndUpdate(M,Blocks.AIR.defaultBlockState());owner.setAngle(0);if(!bearing.disassemble())throw new AssertionError("Ready native source could not restore world ownership");
                var cow=level.getEntity(cowId.get());
                if(!level.getBlockState(P).is(Blocks.STONE)||!same(((BarrelBlockEntity)level.getBlockEntity(C)).getItem(0))||cow==null||!(cow.getVehicle() instanceof SeatEntity)||!cow.getVehicle().blockPosition().equals(S)||level.getEntity(ownerId.get())!=null||!SuperGlueEntity.isGlued(level,P,Direction.EAST,null))throw new AssertionError("Retry/disassembly changed saved cargo, passenger or glue");
                Create.LOGGER.info("Native readiness gate: automatic retry captured three blocks and original cow UUID; exact world restoration passed");
            });
            screenshot(context,"create-vulkan-entity-readiness-exact-restoration");
        } finally {if(gate.get()!=null)gate.get().close();}
    }
    private static boolean ready(ServerLevel level){try{LoadedEntityAccess.requireReady(level,P);LoadedEntityAccess.requireReady(level,S);LoadedEntityAccess.requireGlueQueriesReady(level,P,new java.util.HashSet<>());return true;}catch(AssemblyException unavailable){return false;}}
    private static ItemStack payload(){var stack=new ItemStack(Items.DIAMOND,7);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Native delayed entity cargo"));return stack;}
    private static boolean same(ItemStack stack){return stack.getCount()==7&&ItemStack.isSameItemSameComponents(stack,payload());}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int timeout,String message){var done=new AtomicBoolean();for(int i=0;i<timeout&&!done.get();i++){world.getServer().runOnServer(ignored->done.set(condition.test(world.getConnection().getServerLevel())));if(!done.get())context.waitTicks(1);}if(!done.get())throw new AssertionError(message);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
}
