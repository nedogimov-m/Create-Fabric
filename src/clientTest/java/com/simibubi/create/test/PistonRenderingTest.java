package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.piston.*;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticRenderState;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

/** Proposed unexecuted native scene: real input/network drive, terrain stop, saved same-UUID owner and cargo. */
final class PistonRenderingTest {
    private static final BlockPos B=new BlockPos(8603,5,5),MOTOR=B.north(),CARGO=B.east(3);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 8603.375 7 8.5");
        context.waitFor(client->Math.abs(client.player.getX()-8603.375)<.1&&client.level.isLoaded(B.east(8))&&client.level.isLoaded(B.west(5)),400);
        server.runCommand("fill 8597 4 -2 8620 4 18 minecraft:smooth_quartz");server.runCommand("setblock 8603 6 8 minecraft:smooth_quartz");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(B,AllBlocks.MECHANICAL_PISTON.defaultBlockState().setValue(MechanicalPistonBlock.FACING,Direction.EAST).setValue(MechanicalPistonBlock.STATE,MechanicalPistonBlock.PistonState.EXTENDED));for(var pos:List.of(B.west(),B.east()))level.setBlockAndUpdate(pos,AllBlocks.PISTON_EXTENSION_POLE.defaultBlockState().setValue(PistonExtensionPoleBlock.FACING,Direction.EAST));level.setBlockAndUpdate(B.east(2),AllBlocks.MECHANICAL_PISTON_HEAD.defaultBlockState().setValue(MechanicalPistonHeadBlock.FACING,Direction.EAST));level.setBlockAndUpdate(CARGO,Blocks.BARREL.defaultBlockState());var stock=new ItemStack(Items.DIAMOND,7);stock.set(DataComponents.CUSTOM_NAME,Component.literal("Native saved piston cargo"));((BarrelBlockEntity)level.getBlockEntity(CARGO)).setItem(0,stock);});
        NativeContraptionFixtures.awaitCaptureReady(context,world,300,B,B.west(),B.east(),B.east(2),CARGO);
        server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with create:wrench");
        context.waitFor(client->client.player.onGround()&&client.player.getMainHandItem().is(AllItems.WRENCH)&&Math.abs(client.player.getY()-7)<.1,200);
        open(context);choose(context,IControlContraption.MovementMode.MOVE_NEVER_PLACE);
        waitServer(context,world,level->piston(level).getMovementMode()==IControlContraption.MovementMode.MOVE_NEVER_PLACE,200,"Actual release-to-confirm input did not change native source movement mode");
        verifyModels(context);screenshot(context,"create-vulkan-piston-native-held-wrench-mode-and-source-head-rod-models");
        var id=new AtomicReference<UUID>();
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(MOTOR,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));((KineticBlockEntity)level.getBlockEntity(MOTOR)).setGeneratedSpeed(-16);});
        waitServer(context,world,level->{var p=piston(level);var owner=p.getMovedContraption();if(owner!=null&&p.offset>2.05){id.set(owner.getUUID());return p.getSpeed()!=0;}return false;},300,"Actual shaft network did not extend the physically extended native source from offset two");
        context.waitFor(client->find(client,id.get()) instanceof LinearContraptionEntity owner&&owner.getContraption()!=null,300);
        var first=new AtomicReference<Float>();server.runOnServer(ignored->first.set(piston(world.getConnection().getServerLevel()).offset));context.waitTicks(6);
        waitServer(context,world,level->piston(level).getMovedContraption()!=null&&piston(level).getMovedContraption().getUUID().equals(id.get())&&piston(level).offset>first.get()+.25,200,"Accepted native owner did not translate from real RPM");
        context.runOnClient(client->{var p=(MechanicalPistonBlockEntity)client.level.getBlockEntity(B);var shaft=(KineticRenderState)client.getBlockEntityRenderDispatcher().tryExtractRenderState(p,.5f,null,false);if(shaft==null||shaft.model.isEmpty()||shaft.axis!=Direction.Axis.Z)throw new AssertionError("Native piston shaft extraction rotates an incomplete/wrong model");});
        lookAtOwner(context,id.get());verifyOwner(context,id.get());screenshot(context,"create-vulkan-piston-real-shaft-network-and-translating-native-head-rods-cargo");
        waitServer(context,world,level->piston(level).waitingForSpeedChange&&piston(level).offset==3&&piston(level).getMovedContraption()!=null,300,"Source never-place endpoint did not retain exact accepted owner");
        verifyServerOwner(world,id.get());
        var old=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(AllBlocks.MECHANICAL_PISTON_HEAD.defaultBlockState()));
        var reload=context.computeOnClient(Minecraft::reloadResourcePacks);context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();context.waitTicks(3);
        context.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(AllBlocks.MECHANICAL_PISTON_HEAD.defaultBlockState())==old)throw new AssertionError("Piston head retained stale native resources");});verifyOwner(context,id.get());
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 9600 15 9600");
        waitServer(context,world,level->level.getEntity(id.get())==null&&level.getChunkSource().getChunkNow(B.getX()>>4,B.getZ()>>4)==null,800,"Real piston source and accepted owner did not unload");
        server.runCommand("tp @p 8610.5 10 12.5");waitServer(context,world,level->level.getEntity(id.get()) instanceof LinearContraptionEntity&&piston(level).getMovedContraption()!=null&&piston(level).getMovedContraption().getUUID().equals(id.get()),500,"Original native linear owner did not return by saved UUID");
        context.waitFor(client->Math.abs(client.player.getX()-8610.5)<.1&&find(client,id.get()) instanceof LinearContraptionEntity owner&&owner.getContraption()!=null,300);verifyServerOwner(world,id.get());verifyOwner(context,id.get());lookAtOwner(context,id.get());screenshot(context,"create-vulkan-piston-native-save-real-unload-return-same-owner-and-seven-named-diamonds");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();((KineticBlockEntity)level.getBlockEntity(MOTOR)).setGeneratedSpeed(64);});
        waitServer(context,world,level->piston(level).offset<2.5&&piston(level).getMovedContraption()!=null&&piston(level).getMovedContraption().getUUID().equals(id.get()),200,"Real RPM reversal replaced the owner or failed to retract");
        lookAtOwner(context,id.get());screenshot(context,"create-vulkan-piston-real-rpm-reversal-on-saved-native-owner");
        waitServer(context,world,level->piston(level).offset==0&&piston(level).waitingForSpeedChange,300,"Native retract did not reach original range boundary");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(B.east(3),Blocks.BEDROCK.defaultBlockState());((KineticBlockEntity)level.getBlockEntity(MOTOR)).setGeneratedSpeed(-64);});
        waitServer(context,world,level->piston(level).offset>.1&&!piston(level).waitingForSpeedChange,200,"Real outward drive never resumed before the terrain stop");
        waitServer(context,world,level->piston(level).waitingForSpeedChange&&piston(level).offset==1&&piston(level).getMovedContraption()!=null,300,"Actual leading-cell terrain solver did not snap to the exact safe grid boundary before opposing bedrock");verifyServerOwner(world,id.get());lookAtOwner(context,id.get());screenshot(context,"create-vulkan-piston-real-leading-cell-terrain-collision-retains-exact-cargo");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();((KineticBlockEntity)level.getBlockEntity(MOTOR)).setGeneratedSpeed(0);});
        waitServer(context,world,level->piston(level).getSpeed()==0,200,"Real source drive did not stop");
        server.runCommand("tp @p 8603.375 7 8.5");server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with create:wrench");context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-8603.375)<.1&&client.player.getMainHandItem().is(AllItems.WRENCH),200);open(context);choose(context,IControlContraption.MovementMode.MOVE_PLACE);
        waitServer(context,world,level->piston(level).getMovementMode()==IControlContraption.MovementMode.MOVE_PLACE,200,"Actual mode edit did not reach stopped controller");
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);look(context,Vec3.atCenterOf(B).add(-.125,.5,0));context.waitTicks(6);context.getInput().pressKey(options->options.keyUse);
        waitServer(context,world,level->!piston(level).isRunning()&&piston(level).getMovedContraption()==null&&level.getEntity(id.get())==null,300,"Real empty-hand block dispatch did not restore native ownership");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();int at=Math.round(piston(level).offset);var stock=((BarrelBlockEntity)level.getBlockEntity(B.east(at+1))).getItem(0);if(stock.getCount()!=7||!stock.has(DataComponents.CUSTOM_NAME)||level.getBlockState(B).getValue(MechanicalPistonBlock.STATE)==MechanicalPistonBlock.PistonState.MOVING)throw new AssertionError("Final ordinary native blocks or named cargo changed ownership incorrectly");int poles=0;for(int i=-3;i<=3;i++){var state=level.getBlockState(B.east(i));if(MechanicalPistonBlock.isExtensionPole(state)||MechanicalPistonBlock.isPistonHead(state))poles++;}if(poles!=3)throw new AssertionError("Physical piston poles were duplicated or lost: "+poles);});
        screenshot(context,"create-vulkan-piston-real-empty-hand-stop-world-head-poles-and-cargo-restored");
    }
    private static MechanicalPistonBlockEntity piston(ServerLevel level){return (MechanicalPistonBlockEntity)level.getBlockEntity(B);}
    private static void verifyServerOwner(TestSingleplayerContext world,UUID id){world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=piston(level).getMovedContraption();if(owner==null||!owner.getUUID().equals(id)||owner.getContraption().getBlocks().size()!=5)throw new AssertionError("Native piston accepted a replacement/truncated owner");var structure=(PistonContraption)owner.getContraption();if(structure.extensionLength!=3||structure.initialExtensionProgress!=2||!structure.anchor.equals(B.east())||!structure.getControllerPos().equals(B))throw new AssertionError("Native extended source lost its saved integer anchor/range/initial progress");for(int step=1;step<=4;step++){var part=structure.getBlocks().get(new BlockPos(-step,0,0));if(part==null||(step==1?!MechanicalPistonBlock.isPistonHead(part.state()):!MechanicalPistonBlock.isExtensionPole(part.state()))||part.state().getValue(MechanicalPistonBlock.FACING)!=Direction.EAST||part.nbt()!=null)throw new AssertionError("Native extended source lost an exact head/pole spindle cell "+step);}var stock=owner.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0);if(stock.getCount()!=7||!stock.has(DataComponents.CUSTOM_NAME))throw new AssertionError("Native moving cargo/components changed");});}
    private static void verifyModels(ClientGameTestContext context){context.runOnClient(client->{for(var block:List.of(AllBlocks.MECHANICAL_PISTON,AllBlocks.STICKY_MECHANICAL_PISTON,AllBlocks.PISTON_EXTENSION_POLE,AllBlocks.MECHANICAL_PISTON_HEAD))for(var state:block.getStateDefinition().getPossibleStates()){var model=client.getModelManager().getBlockStateModelSet().get(state);if(model==client.getModelManager().getBlockStateModelSet().missingModel())throw new AssertionError("Missing original piston model "+state);var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,B,state,net.minecraft.util.RandomSource.create(42),side->false);if(mesh.immutableCopy().size()==0)throw new AssertionError("Empty original piston geometry "+state);}});}
    private static void verifyOwner(ClientGameTestContext context,UUID id){context.runOnClient(client->{
        var owner=(LinearContraptionEntity)find(client,id);var rendered=((ContraptionEntityRenderer)client.getEntityRenderDispatcher().getRenderer(owner)).createRenderState(owner,.5f);
        var expected=Set.of(BlockPos.ZERO,new BlockPos(-1,0,0),new BlockPos(-2,0,0),new BlockPos(-3,0,0),new BlockPos(-4,0,0));var actual=new HashSet<BlockPos>();rendered.blocks.forEach(block->actual.add(block.pos()));
        if(rendered.blocks.size()!=5||!actual.equals(expected))throw new AssertionError("Native translating spindle/cargo positions: expected "+expected+", actual "+actual+", count="+rendered.blocks.size());
        if(rendered.angle!=0||Math.abs(rendered.orientation.x())+Math.abs(rendered.orientation.y())+Math.abs(rendered.orientation.z())>1e-6||Math.abs(rendered.orientation.w()-1)>1e-6)throw new AssertionError("Translation acquired a rotation: angle="+rendered.angle+", quaternion="+rendered.orientation);
        if(!rendered.actors.isEmpty())throw new AssertionError("Native piston invented actor geometry at "+rendered.actors.stream().map(actor->actor.pos()).toList());
        for(var block:rendered.blocks){if(!(block.model() instanceof PartialModelRenderState mesh))throw new AssertionError("Expected actual Fabric mesh state at "+block.pos()+", got "+block.model().getClass().getName());if(mesh.mesh==null||mesh.mesh.size()==0)throw new AssertionError("Empty native Fabric mesh at "+block.pos()+" (head="+block.pos().equals(new BlockPos(-1,0,0))+")");if(block.model().isEmpty())throw new AssertionError("Native BlockModelRenderState.isEmpty contradicts its actual Fabric mesh at "+block.pos());}
    });}
    private static void open(ClientGameTestContext context){look(context,Vec3.atCenterOf(B).add(-.125,.5,0));context.waitTicks(6);context.getInput().holdKey(options->options.keyUse);context.waitFor(client->client.gui.screen() instanceof PistonSettingsClient.ModeScreen screen&&screen.position().equals(B),200);}
    private static void choose(ClientGameTestContext context,IControlContraption.MovementMode mode){var point=context.computeOnClient(client->{var p=((PistonSettingsClient.ModeScreen)client.gui.screen()).coordinate(mode);float scaleX=(float)client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth();float scaleY=(float)client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight();return new Vec2(p.x*scaleX,p.y*scaleY);});context.getInput().setCursorPos(point.x,point.y);context.waitTicks(3);screenshot(context,"create-vulkan-piston-native-release-to-confirm-movement-mode");context.getInput().releaseKey(options->options.keyUse);context.waitFor(client->client.gui.screen()==null,200);}
    private static net.minecraft.world.entity.Entity find(Minecraft client,UUID id){if(client.level==null)return null;for(var entity:client.level.entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null;}
    private static void lookAtOwner(ClientGameTestContext context,UUID id){var point=context.computeOnClient(client->find(client,id).position().add(.5,.5,.5));look(context,point);}
    private static void look(ClientGameTestContext context,Vec3 point){var angles=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angles.x,angles.y);}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int ticks,String message){var ready=new AtomicBoolean();for(int i=0;i<ticks&&!ready.get();i++){world.getServer().runOnServer(ignored->ready.set(condition.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
    private static void screenshot(ClientGameTestContext context,String name){context.waitFor(client->client.gui.overlay()==null,200);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
}
