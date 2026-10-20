package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.contraptions.ContraptionAssemblyGraph;
import com.simibubi.create.content.contraptions.actors.psi.*;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Unexecuted review regression: real PSI stall, actual S2C authority and native restoration ticks. */
final class GantryReviewRenderingTest {
    private static final BlockPos R=new BlockPos(31043,5,5),P=R.above(),I=P.above(),C=I.east();
    private static final BlockPos ENDPOINT=I.north(2).east(2);
    private static final int LENGTH=9;
    private static final String CARGO="Native gantry review cargo";

    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 31047.5 10 12.5");
        context.waitFor(client->Math.abs(client.player.getX()-31047.5)<.1&&client.level.isLoaded(R.east(LENGTH)),400);
        server.runCommand("fill 31039 4 -1 31056 4 16 minecraft:smooth_quartz");
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();
            for(int index=0;index<LENGTH;index++)level.setBlockAndUpdate(R.east(index),AllBlocks.GANTRY_SHAFT.defaultBlockState().setValue(GantryShaftBlock.FACING,Direction.EAST));
            level.setBlockAndUpdate(P,AllBlocks.GANTRY_CARRIAGE.defaultBlockState().setValue(com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock.FACING,Direction.UP).setValue(com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE,false));
            level.setBlockAndUpdate(I,AllBlocks.PORTABLE_STORAGE_INTERFACE.defaultBlockState().setValue(PortableStorageInterfaceBlock.FACING,Direction.NORTH));
            level.setBlockAndUpdate(C,Blocks.BARREL.defaultBlockState());var cargo=new ItemStack(Items.DIAMOND,7);cargo.set(DataComponents.CUSTOM_NAME,Component.literal(CARGO));((BarrelBlockEntity)level.getBlockEntity(C)).setItem(0,cargo);
            if(!level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(I,C))))throw new AssertionError("Native review glue reservation failed");
            level.setBlockAndUpdate(ENDPOINT,AllBlocks.PORTABLE_STORAGE_INTERFACE.defaultBlockState().setValue(PortableStorageInterfaceBlock.FACING,Direction.SOUTH));
            level.setBlockAndUpdate(ENDPOINT.below(),Blocks.HOPPER.defaultBlockState());level.setBlockAndUpdate(ENDPOINT.below(2),Blocks.BARREL.defaultBlockState());
        });
        NativeContraptionFixtures.awaitCaptureReady(context,world,400,R,R.east(LENGTH-1),P,I,C,ENDPOINT);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(R.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));((KineticBlockEntity)level.getBlockEntity(R.west())).setGeneratedSpeed(-64);});
        var id=new AtomicReference<UUID>();var pose=new AtomicReference<Vec3>();
        waitServer(context,world,level->{
            for(var owner:level.getEntitiesOfClass(GantryContraptionEntity.class,new AABB(P).inflate(12)))
                if(owner.isStalled()&&level.getBlockEntity(ENDPOINT) instanceof PortableItemInterfaceBlockEntity endpoint&&endpoint.getConnectedEntity()==owner&&output(level)>0){id.set(owner.getUUID());pose.set(owner.position());return true;}
            return false;
        },300,"The actual moving PSI and native hopper never established a real gantry actor stall");
        context.waitFor(client->find(client,id.get()) instanceof GantryContraptionEntity owner&&owner.getContraption()!=null&&owner.isStalled(),200);
        server.runOnServer(ignored->assertStopped(world.getConnection().getServerLevel(),id.get(),pose.get()));
        // Deterministic bounded prediction error after a real actor stall. The correction below
        // travels through the registered native S2C codec/receiver; no direct applyMotion call.
        context.runOnClient(client->{var owner=(GantryContraptionEntity)find(client,id.get());owner.setPos(pose.get().add(.375,0,0));});
        server.runOnServer(ignored->{var owner=(GantryContraptionEntity)world.getConnection().getServerLevel().getEntity(id.get());owner.sendMotion();});
        context.waitFor(client->find(client,id.get()) instanceof GantryContraptionEntity owner&&owner.isStalled()&&owner.position().distanceTo(pose.get())<1e-7&&owner.getDeltaMovement().lengthSqr()==0,40);
        context.waitTicks(4);
        context.runOnClient(client->{
            var owner=(GantryContraptionEntity)find(client,id.get());
            if(!owner.isStalled()||owner.position().distanceTo(pose.get())>1e-7||owner.getDeltaMovement().lengthSqr()!=0||owner.isInterpolating())throw new AssertionError("Authoritative stop pose drifted during real native client stall ticks");
            var state=((ContraptionEntityRenderer)client.getEntityRenderDispatcher().getRenderer(owner)).createRenderState(owner,.5f);
            var local=Set.of(BlockPos.ZERO,new BlockPos(0,1,0),new BlockPos(1,1,0));
            if(state.blocks.size()!=3||!state.blocks.stream().map(block->block.pos()).collect(java.util.stream.Collectors.toSet()).equals(local)||state.actors.size()!=1)throw new AssertionError("Actual native stalled gantry body/cargo/PSI layout changed");
            for(var block:state.blocks)if(!(block.model() instanceof PartialModelRenderState model)||model.mesh==null||model.mesh.size()==0||model.isEmpty())throw new AssertionError("Real stopped gantry has an empty native source mesh");
            var psi=((PortableStorageInterfaceActorRenderer.State)state.actors.getFirst().state()).state();
            if(psi.middle.mesh==null||psi.middle.mesh.size()==0||psi.middle.isEmpty()||psi.top.mesh==null||psi.top.mesh.size()==0||psi.top.isEmpty())throw new AssertionError("Actual stopped PSI actor lost its native meshes");
            if(Math.abs(state.x-pose.get().x)>1e-7||Math.abs(state.y-pose.get().y)>1e-7||Math.abs(state.z-pose.get().z)>1e-7)throw new AssertionError("Native interpolated render pose did not converge to the authoritative stalled anchor");
            var wrong=new com.simibubi.create.content.contraptions.gantry.GantryContraptionUpdatePacket(owner.getId(),UUID.randomUUID(),owner.getAxisCoord(),0,-1,pose.get().add(1,0,0));
            if(owner.applyMotion(wrong)||owner.position().distanceTo(pose.get())>1e-7)throw new AssertionError("A different native owner UUID could move the stalled carrier");
        });
        lookAt(context,pose.get().add(.5,1.5,.5));context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-gantry-real-psi-stall-native-authority-corrects-prediction");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();assertStopped(level,id.get(),pose.get());level.setBlockAndUpdate(ENDPOINT.north(),Blocks.REDSTONE_BLOCK.defaultBlockState());});
        waitServer(context,world,level->level.getEntity(id.get()) instanceof GantryContraptionEntity owner&&!owner.isStalled()&&owner.getX()>pose.get().x+.25,150,"Actual endpoint redstone disconnect failed to resume the same accepted gantry UUID");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=(GantryContraptionEntity)level.getEntity(id.get());assertTotal(level,owner);});
        var restored=new AtomicReference<BlockPos>();var retired=new AtomicReference<GantryContraptionEntity>();
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=(GantryContraptionEntity)level.getEntity(id.get());retired.set(owner);restored.set(owner.getRestorationTransform().apply(BlockPos.ZERO));if(!owner.disassemble())throw new AssertionError("Native post-stall manual restoration failed");});
        context.waitTicks(8);
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var target=restored.get();
            if(level.getEntity(id.get())!=null||retired.get().getContraption().getStorage().ownsContents()||!level.getEntitiesOfClass(GantryContraptionEntity.class,new AABB(target).inflate(10)).isEmpty())throw new AssertionError("Restored native carriage spontaneously reassembled after its explicit manual stop");
            if(!level.getBlockState(target).is(AllBlocks.GANTRY_CARRIAGE)||!level.getBlockState(target.above()).is(AllBlocks.PORTABLE_STORAGE_INTERFACE)||!(level.getBlockEntity(target.above().east()) instanceof BarrelBlockEntity barrel))throw new AssertionError("Native manual stop did not retain all three original payload cells");
            if(container(barrel)+output(level)!=7)throw new AssertionError("Exact named source cargo was lost or duplicated by stall, resume or restoration");
            if(((KineticBlockEntity)level.getBlockEntity(R)).getSpeed()!=-64)throw new AssertionError("Manual stop altered the actual rack RPM to hide a restart");
        });
        lookAt(context,Vec3.atCenterOf(restored.get().above()));context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-gantry-real-psi-stall-authority-and-native-manual-stop-retained");
    }

    private static void assertStopped(ServerLevel level,UUID id,Vec3 pose){
        if(!(level.getEntity(id) instanceof GantryContraptionEntity owner)||owner.getType()!=AllEntityTypes.GANTRY_CONTRAPTION||!owner.hasOwnedStructure()||!owner.isStalled()||owner.position().distanceTo(pose)>1e-7||owner.getDeltaMovement().lengthSqr()!=0||ContraptionAssemblyGraph.isFrozen(owner))throw new AssertionError("Actual PSI stall changed native identity, ownership or graph recovery state");
        if(!LoadedEntityAccess.isReady(level,BlockPos.containing(owner.position().add(.5,.5,.5)).below()))throw new AssertionError("Native source readiness was bypassed in the PSI stall scene");
        if(!(level.getBlockEntity(ENDPOINT) instanceof PortableItemInterfaceBlockEntity endpoint)||endpoint.getConnectedEntity()!=owner)throw new AssertionError("Real stationary endpoint lost the original accepted carrier");
        assertTotal(level,owner);
    }
    private static void assertTotal(ServerLevel level,GantryContraptionEntity owner){long held=0;for(var view:owner.getContraption().getStorage().getAllItems())if(view.getResource().isOf(Items.DIAMOND)){requireName(view.getResource().toStack());held+=view.getAmount();}if(held+output(level)!=7)throw new AssertionError("Real native PSI transfer changed total named cargo");}
    private static long output(ServerLevel level){return container((Container)level.getBlockEntity(ENDPOINT.below()))+container((Container)level.getBlockEntity(ENDPOINT.below(2)));}
    private static long container(Container container){long count=0;for(int slot=0;slot<container.getContainerSize();slot++){var stack=container.getItem(slot);if(stack.is(Items.DIAMOND)){requireName(stack);count+=stack.getCount();}}return count;}
    private static void requireName(ItemStack stack){if(stack.get(DataComponents.CUSTOM_NAME)==null||!stack.get(DataComponents.CUSTOM_NAME).getString().equals(CARGO))throw new AssertionError("Native review cargo components changed");}
    private static void lookAt(ClientGameTestContext context,Vec3 point){var angles=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new net.minecraft.world.phys.Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angles.x,angles.y);}
    private static Entity find(Minecraft client,UUID id){if(client.level==null)return null;for(var entity:client.level.entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null;}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int ticks,String message){var ready=new AtomicBoolean();for(int index=0;index<ticks&&!ready.get();index++){world.getServer().runOnServer(ignored->ready.set(condition.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
}
