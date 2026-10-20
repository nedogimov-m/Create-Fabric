package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.render.*;
import com.simibubi.create.content.decoration.slidingDoor.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;

/** Native pair input, immutable folding geometry, tracked moving-state packets, unload and restoration. */
final class DoorRenderingTest {
    private static final BlockPos P=new BlockPos(5803,4,5),C=P.above(6);
    private static final BlockPos TRAIN=new BlockPos(0,1,0),COPPER=new BlockPos(3,1,0),CARGO=new BlockPos(2,1,0);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 5803.5 4 8.5");
        context.waitFor(client->Math.abs(client.player.getX()-5803.5)<.1&&client.level.isLoaded(P.east(12)),400);
        context.waitFor(client->NativeScreenshotReadiness.skyReady(client,new BlockPos(5800,4,9),new BlockPos(5812,4,9)),300);
        server.runCommand("fill 5798 3 0 5820 3 15 minecraft:smooth_quartz");server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with create:train_door");
        context.waitFor(client->client.player.onGround()&&client.player.getMainHandItem().is(AllBlocks.TRAIN_DOOR.asItem()),200);look(context,Vec3.atCenterOf(P.below()).add(0,.5,0));use(context);
        waitServer(context,world,level->level.getBlockState(P).is(AllBlocks.TRAIN_DOOR)&&level.getBlockState(P.above()).is(AllBlocks.TRAIN_DOOR),200,"Real two-high train-door placement failed");
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);
        // Fixed paired hinges make the following actual player click unambiguous.
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();place(level,P,AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT);place(level,P.east(),AllBlocks.TRAIN_DOOR,DoorHingeSide.RIGHT);for(int i=0;i<3;i++)place(level,P.east(4+i*3),new SlidingDoorBlock[]{AllBlocks.ANDESITE_DOOR,AllBlocks.COPPER_DOOR,AllBlocks.FRAMED_GLASS_DOOR}[i],DoorHingeSide.LEFT);});
        context.waitFor(client->client.level.getBlockEntity(P.east(4)) instanceof SlidingDoorBlockEntity,200);look(context,Vec3.atCenterOf(P));use(context);
        context.waitFor(client->open(client.level.getBlockState(P))&&open(client.level.getBlockState(P.east()))&&client.level.getBlockEntity(P) instanceof SlidingDoorBlockEntity door&&door.getAnimation(1)>.05,200);
        context.waitTicks(8);verifyStatic(context,P,false);screenshot(context,world,"create-vulkan-paired-sliding-door-native-click-open");
        look(context,Vec3.atLowerCornerOf(P).add(-.31,.7,.9));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P),200);use(context);
        context.waitFor(client->!open(client.level.getBlockState(P))&&client.level.getBlockState(P).getValue(SlidingDoorBlock.VISIBLE)&&!open(client.level.getBlockState(P.east())),200);screenshot(context,world,"create-vulkan-open-sliding-leaf-outside-cell-native-pick-and-close");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(var pos:List.of(P.east(4),P.east(7),P.east(10)))((SlidingDoorBlock)level.getBlockState(pos).getBlock()).setOpen(null,level,level.getBlockState(pos),pos,true);});
        context.waitFor(client->client.level.getBlockEntity(P.east(7)) instanceof SlidingDoorBlockEntity door&&door.getAnimation(1)>.95,200);
        verifyStatic(context,P.east(4),true);verifyStatic(context,P.east(7),true);verifyStatic(context,P.east(10),false);look(context,Vec3.atCenterOf(P.east(6)));screenshot(context,world,"create-vulkan-native-andesite-copper-folding-and-glass-sliding-leaves");
        context.runOnClient(client->{var cutout=new AtomicBoolean();for(var block:List.of(AllBlocks.TRAIN_DOOR,AllBlocks.BRASS_DOOR,AllBlocks.FRAMED_GLASS_DOOR,AllBlocks.COPPER_DOOR,AllBlocks.ANDESITE_DOOR))for(var state:block.getStateDefinition().getPossibleStates()){
            var models=client.getModelManager().getBlockStateModelSet();var model=models.get(state);if(model==models.missingModel())throw new AssertionError("Missing door model "+state);var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,P,state,net.minecraft.util.RandomSource.create(42),face->false);var emitted=mesh.immutableCopy();if(emitted.size()==0)throw new AssertionError("Empty original door geometry "+state);emitted.forEach(quad->{if(quad.chunkLayer()==net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT)cutout.set(true);if(quad.chunkLayer()==net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT)throw new AssertionError("Source cutout door unexpectedly translucent "+state);});
        }if(!cutout.get())throw new AssertionError("Native sprite transparency did not select door cutout material");});
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();((SlidingDoorBlock)level.getBlockState(P).getBlock()).setOpen(null,level,level.getBlockState(P),P,false);});
        context.waitFor(client->client.level.getBlockState(P).getValue(SlidingDoorBlock.VISIBLE)&&client.level.getBlockState(P.above()).getValue(SlidingDoorBlock.VISIBLE)&&client.level.getBlockState(P.east()).getValue(SlidingDoorBlock.VISIBLE),200);
        // A real slime carrier captures both sliding and folding pairs plus named inventory.
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 5805.5 14 12.5");server.runCommand("fill 5799 10 7 5815 10 14 minecraft:smooth_quartz");
        NativeContraptionFixtures.awaitCaptureReady(context,world,300,C,C.east(4));
        var id=new AtomicReference<UUID>();
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();for(int x=0;x<5;x++)level.setBlockAndUpdate(C.east(x),Blocks.SLIME_BLOCK.defaultBlockState());
            place(level,C.offset(TRAIN),AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT);place(level,C.offset(TRAIN).east(),AllBlocks.TRAIN_DOOR,DoorHingeSide.RIGHT);place(level,C.offset(COPPER),AllBlocks.COPPER_DOOR,DoorHingeSide.LEFT);place(level,C.offset(COPPER).east(),AllBlocks.COPPER_DOOR,DoorHingeSide.RIGHT);level.setBlockAndUpdate(C.offset(CARGO),Blocks.BARREL.defaultBlockState());
            var stack=new ItemStack(Items.DIAMOND,7);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Door carrier cargo"));((BarrelBlockEntity)level.getBlockEntity(C.offset(CARGO))).setItem(0,stack);
            var structure=new BearingContraption(false,Direction.UP);try{if(!structure.assemble(level,C.below())||structure.getBlocks().size()!=14||!structure.tryRemoveBlocksFromWorld(level,BlockPos.ZERO))throw new AssertionError("Full14-block door capture failed");}catch(AssemblyException failure){throw new AssertionError(failure);}
            var owner=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,level);owner.setContraption(structure);owner.setPos(Vec3.atLowerCornerOf(C));level.addFreshEntity(owner);owner.tickActors();id.set(owner.getUUID());
        });
        context.waitFor(client->find(client,id.get()) instanceof ControlledContraptionEntity owner&&owner.getContraption()!=null&&owner.getContraption().getBlocks().size()==14,300);
        context.runOnClient(client->{var owner=(ControlledContraptionEntity)find(client,id.get());var rendered=((ContraptionEntityRenderer)client.getEntityRenderDispatcher().getRenderer(owner)).createRenderState(owner,.5f);var lower=rendered.blockEntities.stream().filter(be->be.pos().equals(TRAIN)).findFirst().orElseThrow();var state=(SlidingDoorRenderer.State)lower.state();if(!state.draw||state.value!=0||state.first.mesh==null||rendered.blocks.stream().anyMatch(block->block.pos().equals(TRAIN)||block.pos().equals(TRAIN.above())))throw new AssertionError("Closed mounted door must have one animated renderer at zero progress");});
        // Actual moving-block input passes through the normal server ray validation route.
        server.runCommand("tp @p 5803.5 11 8.5");server.runCommand("gamemode survival @p");context.waitFor(client->!client.player.isSpectator()&&client.player.onGround()&&Math.abs(client.player.getY()-11)<.1,200);look(context,Vec3.atCenterOf(C.offset(TRAIN)));context.waitTicks(6);
        waitServer(context,world,level->{var owner=(ControlledContraptionEntity)level.getEntity(id.get());var player=level.getServer().getPlayerList().getPlayers().getFirst();var hit=owner.rayTrace(player.getEyePosition(),player.getEyePosition().add(player.getViewVector(1).scale(5)),1);return hit!=null&&(hit.getBlockPos().equals(TRAIN)||hit.getBlockPos().equals(TRAIN.above()));},150,"Player ray missed real moving train door");
        context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->find(client,id.get()) instanceof ControlledContraptionEntity owner&&open(owner.getContraption().getBlocks().get(TRAIN).state())&&open(owner.getContraption().getBlocks().get(TRAIN.east()).state()),200);
        context.waitTicks(8);verifyMoving(context,id.get(),TRAIN,false);look(context,Vec3.atCenterOf(C.offset(TRAIN)));screenshot(context,world,"create-vulkan-moving-paired-door-real-input-authoritative-state-packets");
        look(context,Vec3.atLowerCornerOf(C.offset(TRAIN)).add(-.31,.7,.9));context.waitTicks(6);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->find(client,id.get()) instanceof ControlledContraptionEntity owner&&!open(owner.getContraption().getBlocks().get(TRAIN).state())&&!open(owner.getContraption().getBlocks().get(TRAIN.east()).state()),200);screenshot(context,world,"create-vulkan-open-moving-door-outside-cell-real-ray-close");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=(ControlledContraptionEntity)level.getEntity(id.get());MovingInteractionBehaviour.REGISTRY.get(AllBlocks.TRAIN_DOOR).handlePlayerInteraction(level.getServer().getPlayerList().getPlayers().getFirst(),InteractionHand.MAIN_HAND,TRAIN,owner);});
        context.waitFor(client->find(client,id.get()) instanceof ControlledContraptionEntity owner&&open(owner.getContraption().getBlocks().get(TRAIN).state()),200);context.waitTicks(8);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=(ControlledContraptionEntity)level.getEntity(id.get());MovingInteractionBehaviour.REGISTRY.get(AllBlocks.COPPER_DOOR).handlePlayerInteraction(level.getServer().getPlayerList().getPlayers().getFirst(),InteractionHand.MAIN_HAND,COPPER,owner);});
        context.waitFor(client->find(client,id.get()) instanceof ControlledContraptionEntity owner&&open(owner.getContraption().getBlocks().get(COPPER).state()),200);context.waitTicks(8);verifyMoving(context,id.get(),COPPER,true);
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 5805.5 14 12.5");look(context,Vec3.atCenterOf(C.east(2).above()));screenshot(context,world,"create-vulkan-moving-copper-folding-pair-and-original-sliding-pair");
        var old=context.computeOnClient(client->client.getModelManager().getModel(SlidingDoorModels.get("copper_door").left()));var reload=context.computeOnClient(Minecraft::reloadResourcePacks);context.waitFor(client->reload.isDone(),500);reload.join();context.waitTicks(3);
        context.runOnClient(client->{if(client.getModelManager().getModel(SlidingDoorModels.get("copper_door").left())==old)throw new AssertionError("Folding partial retained before resource reload");});verifyMoving(context,id.get(),COPPER,true);
        server.runCommand("tp @p 6500 15 6500");waitServer(context,world,level->level.getEntity(id.get())==null&&level.getChunkSource().getChunkNow(C.getX()>>4,0)==null,800,"Real door owner did not unload");
        server.runCommand("tp @p 5805.5 14 12.5");waitServer(context,world,level->level.getEntity(id.get()) instanceof ControlledContraptionEntity owner&&owner.getContraption()!=null&&open(owner.getContraption().getBlocks().get(TRAIN).state())&&open(owner.getContraption().getBlocks().get(COPPER).state()),500,"Saved open moving-door halves did not return");
        context.waitFor(client->find(client,id.get()) instanceof ControlledContraptionEntity owner&&owner.getContraption()!=null&&open(owner.getContraption().getBlocks().get(COPPER).state()),300);context.waitTicks(8);verifyMoving(context,id.get(),TRAIN,false);verifyMoving(context,id.get(),COPPER,true);screenshot(context,world,"create-vulkan-door-owner-save-return-native-folding-reload");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=(ControlledContraptionEntity)level.getEntity(id.get());if(!owner.disassemble())throw new AssertionError("Door world ownership restoration failed");var cargo=((BarrelBlockEntity)level.getBlockEntity(C.offset(CARGO))).getItem(0);if(!cargo.is(Items.DIAMOND)||cargo.getCount()!=7||!cargo.has(DataComponents.CUSTOM_NAME))throw new AssertionError("Moving door carrier changed named stock");for(var pos:List.of(C.offset(TRAIN),C.offset(TRAIN).east(),C.offset(COPPER),C.offset(COPPER).east())){var state=level.getBlockState(pos);if(!open(state)||!(level.getBlockEntity(pos) instanceof SlidingDoorBlockEntity))throw new AssertionError("Saved moving door failed native half/BE restoration at "+pos+": state="+state+", entity="+level.getBlockEntity(pos));}for(var pos:List.of(C.offset(TRAIN),C.offset(COPPER))){var state=level.getBlockState(pos);((SlidingDoorBlock)state.getBlock()).setOpen(null,level,state,pos,false);}});
        context.waitFor(client->client.level.getBlockState(C.offset(COPPER)).getOptionalValue(SlidingDoorBlock.VISIBLE).orElse(false)&&!open(client.level.getBlockState(C.offset(COPPER))),200);look(context,Vec3.atCenterOf(C.east(2).above()));screenshot(context,world,"create-vulkan-doors-restored-native-halves-collision-and-cargo");
    }
    private static boolean open(BlockState state){return state.getOptionalValue(DoorBlock.OPEN).orElse(false);}
    private static void place(ServerLevel level,BlockPos pos,SlidingDoorBlock block,DoorHingeSide hinge){var state=block.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HINGE,hinge);level.setBlockAndUpdate(pos,state);level.setBlockAndUpdate(pos.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));}
    private static void verifyStatic(ClientGameTestContext context,BlockPos pos,boolean fold){context.waitFor(client->client.level.getBlockEntity(pos) instanceof SlidingDoorBlockEntity door&&door.getAnimation(0)>=.999f&&door.getAnimation(1)>=.999f,200);context.runOnClient(client->{var state=(SlidingDoorRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos),.5f,null,true);verify(state,fold);});}
    private static void verifyMoving(ClientGameTestContext context,UUID id,BlockPos pos,boolean fold){context.waitFor(client->{if(!(find(client,id) instanceof ControlledContraptionEntity owner)||owner.getContraption()==null)return false;for(var lower:List.of(pos,pos.east()))if(!(owner.getContraption().getBlockEntityClientSide(lower) instanceof SlidingDoorBlockEntity door)||door.getAnimation(0)<.999f||door.getAnimation(1)<.999f)return false;return true;},200);context.runOnClient(client->{var owner=(ControlledContraptionEntity)find(client,id);var state=((ContraptionEntityRenderer)client.getEntityRenderDispatcher().getRenderer(owner)).createRenderState(owner,.5f);var door=state.blockEntities.stream().filter(be->be.pos().equals(pos)).findFirst().orElseThrow(()->new AssertionError("Moving native door BE missing"));verify((SlidingDoorRenderer.State)door.state(),fold);var view=(VirtualRenderWorld)owner.getClientRenderState();for(var half:List.of(pos,pos.above(),pos.east(),pos.east().above()))if(!open(view.getBlockState(half))||!open(owner.getContraption().getBlocks().get(half).state()))throw new AssertionError("Virtual rendering diverges from captured open door halves");if(owner.getContraption().getBlocks().get(pos).state().getCollisionShape(new ContraptionWorld(owner.getContraption()),pos).bounds().minX>=0)throw new AssertionError("Native moving collision still blocks original closed leaf");});}
    private static void verify(SlidingDoorRenderer.State state,boolean fold){if(state==null||!state.draw||state.fold!=fold||state.value<.95||state.first.mesh==null||state.first.mesh.size()==0||state.second.mesh==null||state.second.mesh.size()==0)throw new AssertionError("Native sliding/folding geometry or settled open animation missing: "+(state==null?"null":("draw="+state.draw+", fold="+state.fold+", expectedFold="+fold+", progress="+state.value+", first="+(state.first.mesh==null?"null":state.first.mesh.size())+", second="+(state.second.mesh==null?"null":state.second.mesh.size()))));}
    private static net.minecraft.world.entity.Entity find(Minecraft client,UUID id){if(client.level==null)return null;for(var entity:client.level.entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null;}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message){var ready=new AtomicBoolean();for(int i=0;i<ticks&&!ready.get();i++){world.getServer().runOnServer(ignored->ready.set(predicate.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
    private static void use(ClientGameTestContext context){context.waitTicks(6);context.getInput().pressKey(options->options.keyUse);}
    private static void look(ClientGameTestContext context,Vec3 target){var angle=context.computeOnClient(client->{var d=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void screenshot(ClientGameTestContext context,TestSingleplayerContext world,String name){
        NativeScreenshotReadiness.await(context,world,client->{
            if(client.player==null)return false;int y=client.player.getY()<10?4:11;
            return NativeScreenshotReadiness.skyReady(client,new BlockPos(5800,y,9),new BlockPos(5812,y,9));
        });
        context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);
    }
}
