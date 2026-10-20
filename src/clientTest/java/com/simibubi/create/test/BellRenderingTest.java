package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.*;
import com.simibubi.create.content.equipment.bell.*;
import com.simibubi.create.content.redstone.deskBell.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Native bell input/audio/particles and real saved actor cooldown/campfire food ownership. */
final class BellRenderingTest {
    private static final BlockPos P=new BlockPos(4909,4,5),H=P.east(2).above(),D=P.east(4),C=new BlockPos(4911,10,5),HAUNT=new BlockPos(3,0,0),DESK=new BlockPos(4,0,0);
    private static final class Sounds implements SoundEventListener {
        private final Map<Identifier,Integer> counts=new HashMap<>();
        @Override public void onPlaySound(SoundInstance sound,WeighedSoundEvents event,float range) { counts.merge(sound.getIdentifier(),1,Integer::sum); }
        int count(String name) { return counts.getOrDefault(Create.asResource(name),0); }
    }
    private static final class Vertices implements VertexConsumer {
        final List<Vec3> positions=new ArrayList<>();
        @Override public VertexConsumer addVertex(float x,float y,float z) { positions.add(new Vec3(x,y,z));return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u,float v) { return this; }
        @Override public VertexConsumer setUv1(int u,int v) { return this; }
        @Override public VertexConsumer setUv2(int u,int v) { return this; }
        @Override public VertexConsumer setUv3(float u,float v) { return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        var sounds=new Sounds();context.runOnClient(client->client.getSoundManager().addListener(sounds));
        try { scene(context,world,sounds); } finally { context.runOnClient(client->client.getSoundManager().removeListener(sounds)); }
    }
    private static void scene(ClientGameTestContext context,TestSingleplayerContext world,Sounds sounds) {
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 4909.5 4 8.5");
        context.waitFor(client->Math.abs(client.player.getX()-4909.5)<.1&&client.level.isLoaded(C.east(7)),400);
        server.runCommand("fill 4900 3 -5 4928 3 18 minecraft:smooth_quartz");server.runCommand("time set midnight");server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with create:peculiar_bell 2");
        context.waitFor(client->client.player.onGround()&&client.player.getMainHandItem().is(AllBlocks.PECULIAR_BELL.asItem()),200);look(context,Vec3.atCenterOf(P.below()).add(0,.5,0));use(context);
        waitServer(context,world,level->level.getBlockState(P).is(AllBlocks.PECULIAR_BELL)&&level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().getCount()==1,200,"Peculiar bell item placement failed");
        // Empty hand is needed for an unambiguous bell hit while retaining the second item.
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);look(context,Vec3.atCenterOf(P));use(context);
        context.waitFor(client->client.level.getBlockEntity(P) instanceof PeculiarBellBlockEntity bell&&bell.isRinging&&sounds.count("peculiar_bell_use")==1,200);
        context.runOnClient(client->{var state=(BellRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),.5f,null,false);if(state==null||state.bell.mesh==null||state.bell.mesh.size()==0||Math.abs(state.swing)<.001)throw new AssertionError("Native custom bell swing/head missing");});
        screenshot(context,world,"create-vulkan-peculiar-bell-real-hit-sound-and-swing");
        // Hang the conversion bell from a real ceiling using the ordinary item.
        block(world,H.above(),"minecraft:stone");server.runCommand("tp @p 4911.5 4 8.5");server.runCommand("item replace entity @p weapon.mainhand with create:peculiar_bell");
        context.waitFor(client->Math.abs(client.player.getX()-4911.5)<.1&&client.player.getMainHandItem().is(AllBlocks.PECULIAR_BELL.asItem()),200);look(context,Vec3.atCenterOf(H.above()).add(0,-.5,0));
        context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(H.above())&&hit.getDirection()==Direction.DOWN,150);use(context);
        waitServer(context,world,level->level.getBlockState(H).is(AllBlocks.PECULIAR_BELL)&&level.getBlockState(H).getValue(BellBlock.ATTACHMENT)==net.minecraft.world.level.block.state.properties.BellAttachType.CEILING,200,"Ceiling bell input failed");
        block(world,H.below(),"minecraft:soul_campfire[lit=false]");
        context.waitFor(client->client.level.getBlockEntity(H) instanceof HauntedBellBlockEntity&&sounds.count("haunted_bell_convert")>=1,200);look(context,Vec3.atCenterOf(H));screenshot(context,world,"create-vulkan-haunted-bell-unlit-soul-conversion");
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);use(context);
        context.waitFor(client->client.level.getBlockEntity(H) instanceof HauntedBellBlockEntity bell&&bell.isRinging&&BellEffects.PULSES.activePulses()>0&&sounds.count("haunted_bell_use")>=1,200);
        // Desk item, real output wire, single predicted sound and native animated parts.
        server.runCommand("tp @p 4913.5 4 8.5");server.runCommand("item replace entity @p weapon.mainhand with create:desk_bell");context.waitFor(client->Math.abs(client.player.getX()-4913.5)<.1&&client.player.getMainHandItem().is(AllBlocks.DESK_BELL.asItem()),200);
        look(context,Vec3.atCenterOf(D.below()).add(0,.5,0));use(context);waitServer(context,world,level->level.getBlockState(D).is(AllBlocks.DESK_BELL)&&level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().isEmpty(),200,"Desk bell item placement failed");
        block(world,D.east(),"minecraft:redstone_wire");context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);look(context,Vec3.atLowerCornerOf(D).add(.5,.3,.5));use(context);
        context.waitFor(client->client.level.getBlockEntity(D) instanceof DeskBellBlockEntity bell&&bell.getAnimation(1)>.1&&client.level.getBlockState(D.east()).getValue(RedstoneWireBlock.POWER)==15,200);
        context.runOnClient(client->{var state=(DeskBellRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(D),.5f,null,false);if(state==null||!state.draw||state.bell.mesh==null||state.bell.mesh.size()==0||state.plunger.mesh.size()==0||sounds.count("desk_bell")!=1)throw new AssertionError("Native desk animation/audio missing or duplicated");});
        screenshot(context,world,"create-vulkan-desk-bell-native-input-wire-and-animation");
        waitServer(context,world,level->!level.getBlockState(D).getValue(DeskBellBlock.POWERED)&&level.getBlockState(D.east()).getValue(RedstoneWireBlock.POWER)==0,200,"Desk bell failed real twenty-tick release");
        context.runOnClient(client->{var pressed=AllBlocks.DESK_BELL.defaultBlockState().setValue(DeskBellBlock.POWERED,true);var detached=new DeskBellBlockEntity(D,pressed);var data=new CompoundTag();data.putInt("PressTimer",12);data.putLong("DingSequence",4);detached.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,client.level.registryAccess(),data));detached.setLevel(client.level);if(Math.abs(detached.getAnimation(1)-.6f)>.001)throw new AssertionError("Chunk load before setLevel lost desk animation");});
        // Held haunted bell sends source pulses; the preview includes actual original soul sprites.
        context.runOnClient(client->BellEffects.PULSES.refresh());server.runCommand("tp @p 4909.5 4 12.5");server.runCommand("item replace entity @p weapon.mainhand with create:haunted_bell");
        context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.HAUNTED_BELL.asItem())&&BellEffects.PULSES.activePulses()>0&&BellEffects.PULSES.occupiedPositions()>0,300);
        verifyParticles(context,world);look(context,new Vec3(4909.5,4.2,9.5));context.waitTicks(36);verifyLivePreview(context);screenshot(context,world,"create-vulkan-held-haunted-bell-spawn-preview");
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");server.runCommand("gamemode spectator @p");context.runOnClient(client->BellEffects.PULSES.refresh());
        // Source models for every attachment, power and waterlogged state.
        context.runOnClient(client->{for(var block:List.of(AllBlocks.PECULIAR_BELL,AllBlocks.HAUNTED_BELL,AllBlocks.DESK_BELL))for(var state:block.getStateDefinition().getPossibleStates()){var model=client.getModelManager().getBlockStateModelSet().get(state);if(model==client.getModelManager().getBlockStateModelSet().missingModel())throw new AssertionError("Missing bell block model "+state);var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,P,state,net.minecraft.util.RandomSource.create(42),side->false);if(mesh.immutableCopy().size()==0)throw new AssertionError("Empty bell base model "+state);}});
        for(int i=0;i<8;i++)block(world,C.east(i),i==0?"minecraft:stone":i==1?"minecraft:barrel":i==2?"create:peculiar_bell[facing=north]":i==3?"create:haunted_bell[facing=north]":i==4?"create:desk_bell[powered=true]":i==5?"minecraft:campfire[signal_fire=false]":i==6?"minecraft:bell[facing=north]":"minecraft:soul_campfire[signal_fire=true]");
        // Re-place floor bells after installing their external support.
        for(int i:new int[]{2,3,6}){block(world,C.east(i).below(),"minecraft:stone");block(world,C.east(i),i==2?"create:peculiar_bell[facing=north]":i==3?"create:haunted_bell[facing=north]":"minecraft:bell[facing=north]");}
        var id=new AtomicReference<UUID>();var recharge=new AtomicInteger();var beforePeculiar=context.computeOnClient(client->sounds.count("peculiar_bell_use"));var beforeDesk=context.computeOnClient(client->sounds.count("desk_bell"));
        server.runCommand("tp @p 4915.5 14 15.5");
        NativeContraptionFixtures.awaitCaptureReady(context,world,200,C,C.east(7));
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();((BarrelBlockEntity)level.getBlockEntity(C.east())).setItem(0,new ItemStack(Items.DIAMOND,7));((DeskBellBlockEntity)level.getBlockEntity(C.east(4))).ding();
            for(int i:new int[]{5,7}){var fire=(CampfireBlockEntity)level.getBlockEntity(C.east(i));var food=new ItemStack(Items.BEEF);food.set(DataComponents.CUSTOM_NAME,Component.literal("Saved meal "+i));fire.getItems().set(0,food);var tag=fire.saveWithFullMetadata(level.registryAccess());tag.putIntArray("CookingTimes",new int[]{17,0,0,0});tag.putIntArray("CookingTotalTimes",new int[]{600,0,0,0});fire.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),tag));}
            level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(C,C.east(7))));var structure=new BearingContraption(false,Direction.UP);try{if(!structure.assemble(level,C.below())||!structure.tryRemoveBlocksFromWorld(level,BlockPos.ZERO))throw new AssertionError("Bell carrier capture failed");}catch(AssemblyException failure){throw new AssertionError(failure);}
            var owner=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,level);owner.setContraption(structure);owner.setPos(Vec3.atLowerCornerOf(C));structure.setActorsActive(ItemStack.EMPTY,false);level.addFreshEntity(owner);owner.tickActors();structure.setActorsActive(ItemStack.EMPTY,true);owner.setAngle(30);owner.tickActors();
            var actor=(HauntedBellMovementBehaviour)com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(AllBlocks.HAUNTED_BELL);recharge.set(actor.getRecharge(structure.getActorAt(HAUNT).right));structure.setActorsActive(new ItemStack(AllBlocks.HAUNTED_BELL),false);id.set(owner.getUUID());
            if(recharge.get()!=64)throw new AssertionError("Source actor did not begin a saved recharge");
        });
        context.waitFor(client->find(client.level,id.get()) instanceof ControlledContraptionEntity owner&&owner.getContraption()!=null&&sounds.count("peculiar_bell_use")==beforePeculiar+1&&sounds.count("desk_bell")==beforeDesk+1,300);
        look(context,Vec3.atCenterOf(C.east(3)));context.runOnClient(client->{var owner=(ControlledContraptionEntity)find(client.level,id.get());var state=((ContraptionEntityRenderer)client.getEntityRenderDispatcher().getRenderer(owner)).createRenderState(owner,.5f);if(state.blocks.size()!=8||state.blockEntities.size()<6)throw new AssertionError("Native bell/campfire moving extraction incomplete");var view=(VirtualRenderWorld)owner.getClientRenderState();for(int i:new int[]{5,7})if(!((CampfireBlockEntity)view.getBlockEntity(new BlockPos(i,0,0))).getItems().getFirst().has(DataComponents.CUSTOM_NAME))throw new AssertionError("Moving campfire food preview lost components");});
        context.waitTicks(40);screenshot(context,world,"create-vulkan-moving-bells-and-both-campfire-smokes");
        server.runCommand("tp @p 5600 14 5600");waitServer(context,world,level->level.getEntity(id.get())==null&&level.getChunkSource().getChunkNow(C.getX()>>4,0)==null&&level.getChunkSource().getChunkNow(C.east().getX()>>4,0)==null,800,"Bell owner/source chunks did not unload");
        server.runCommand("tp @p 4915.5 14 15.5");waitServer(context,world,level->level.getEntity(id.get()) instanceof ControlledContraptionEntity,500,"Bell owner did not return with original UUID");
        server.runOnServer(ignored->{var owner=(ControlledContraptionEntity)world.getConnection().getServerLevel().getEntity(id.get());var ctx=owner.getContraption().getActorAt(HAUNT).right;var actor=(HauntedBellMovementBehaviour)com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(AllBlocks.HAUNTED_BELL);if(!ctx.disabled||actor.getRecharge(ctx)!=recharge.get()||Math.abs(owner.getAngle(1)-30)>.01||owner.getContraption().getActorAt(DESK).right.blockEntityData.getIntOr("PressTimer",0)!=20)throw new AssertionError("Real save/unload lost cooldown/pose/frozen desk timer");});
        context.waitFor(client->find(client.level,id.get()) instanceof ControlledContraptionEntity owner&&owner.getContraption()!=null,300);
        var old=context.computeOnClient(client->client.getModelManager().getModel(AllPartialModels.PECULIAR_BELL));var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone(),500);reload.join();context.waitTicks(3);
        context.runOnClient(client->{if(client.getModelManager().getModel(AllPartialModels.PECULIAR_BELL)==old)throw new AssertionError("Bell retained pre-reload partial model");});screenshot(context,world,"create-vulkan-bells-cooldown-and-food-unload-return-reload");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=(ControlledContraptionEntity)level.getEntity(id.get());owner.getContraption().setActorsActive(ItemStack.EMPTY,false);owner.setAngle(0);owner.tickActors();if(!owner.disassemble())throw new AssertionError("Bell carrier restore failed");if(((BarrelBlockEntity)level.getBlockEntity(C.east())).getItem(0).getCount()!=7||((DeskBellBlockEntity)level.getBlockEntity(C.east(4))).getPressTimer()!=20)throw new AssertionError("Carrier stock/remaining timer changed");for(int i:new int[]{5,7}){var fire=(CampfireBlockEntity)level.getBlockEntity(C.east(i));if(!fire.getItems().getFirst().is(Items.BEEF)||!fire.getItems().getFirst().has(DataComponents.CUSTOM_NAME))throw new AssertionError("Restoration lost raw meal");var tag=fire.saveWithFullMetadata(level.registryAccess());if(!Arrays.equals(tag.getIntArray("CookingTimes").orElseThrow(),new int[]{17,0,0,0}))throw new AssertionError("Moving campfire cooked or lost native progress");}});
        waitServer(context,world,level->!level.getBlockState(C.east(4)).getValue(DeskBellBlock.POWERED),200,"Restored desk bell timer stranded power");look(context,Vec3.atCenterOf(C.east(3)));screenshot(context,world,"create-vulkan-bells-campfires-world-stock-timer-restored");server.runCommand("time set day");
    }
    private static void verifyLivePreview(ClientGameTestContext context) {
        context.runOnClient(client->{
            if(!SoulPulseEffect.isDark(client.level,client.player.blockPosition()))throw new AssertionError("Held-preview fixture must be dark");
            try {
                var groups=ParticleEngine.class.getDeclaredField("particles");groups.setAccessible(true);
                var particles=ParticleGroup.class.getDeclaredField("particles");particles.setAccessible(true);
                var live=new ArrayList<SoulParticle>();
                for(var group:((Map<?,?>)groups.get(client.particleEngine)).values())for(var particle:(Collection<?>)particles.get(group))if(particle instanceof SoulParticle soul&&soul.isAlive())live.add(soul);
                var nearby=live.stream().filter(soul->soul.getBoundingBox().getCenter().z>9&&soul.getBoundingBox().getCenter().z<16).toList();
                Create.LOGGER.info("Held soul preview native particles near player: {}",nearby.stream().limit(6).map(soul->soul.spriteName()+" "+soul.getBoundingBox()).toList());
                if(nearby.isEmpty())throw new AssertionError("Held haunted bell did not put nearby live soul particles in the native engine");
            } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }
    private static void verifyParticles(ClientGameTestContext context,TestSingleplayerContext world) {
        var bright=new BlockPos(4935,4,5);block(world,bright.below(),"minecraft:glowstone");context.waitFor(client->client.level.isLoaded(bright)&&!SoulPulseEffect.isDark(client.level,bright),200);
        context.runOnClient(client->{
            var pos=new Vec3(4909.5,4.5,11.5);var particle=client.particleEngine.createParticle(AllParticleTypes.SOUL,pos.x,pos.y,pos.z,0,0,0);if(!(particle instanceof SoulParticle soul)||!soul.spriteName().getPath().contains("soul_sprite_start_"))throw new AssertionError("Original soul sprite/factory missing");
            var state=new QuadParticleRenderState();soul.extract(state,client.getEntityRenderDispatcher().camera,.5f);if(state.isEmpty()||!state.layers().contains(SingleQuadParticle.Layer.TRANSLUCENT))throw new AssertionError("Soul did not extract native translucent quad");verifyFrontFace(state,false);
            boolean loop=false,end=false;for(int i=0;i<200&&soul.isAlive();i++){soul.tick();loop|=soul.spriteName().getPath().contains("soul_sprite_anim_");end|=soul.spriteName().getPath().contains("soul_sprite_end_");}if(soul.isAlive()||!loop||!end)throw new AssertionError("Source soul animation stages did not complete");
            var base=client.particleEngine.createParticle(AllParticleTypes.SOUL_BASE,pos.x,pos.y-.49,pos.z,0,0,0);if(!(base instanceof SoulBaseParticle root)||!root.spriteName().getPath().contains("soul_base_"))throw new AssertionError("Soul floor sprite missing");var floor=new QuadParticleRenderState();root.extract(floor,client.getEntityRenderDispatcher().camera,.5f);verifyFrontFace(floor,true);
            for(var type:List.of(AllParticleTypes.SOUL_PERIMETER,AllParticleTypes.SOUL_EXPANDING_PERIMETER)){var ring=(SoulParticle)client.particleEngine.createParticle(type,bright.getX()+.5,bright.getY()+.5,bright.getZ()+.5,0,0,0);ring.tick();var extracted=new QuadParticleRenderState();ring.extract(extracted,client.getEntityRenderDispatcher().camera,.5f);if(extracted.isEmpty())throw new AssertionError("Lit perimeter native quad missing");}
            var lit=client.particleEngine.createParticle(AllParticleTypes.SOUL,bright.getX()+.5,bright.getY()+.5,bright.getZ()+.5,0,0,0);lit.tick();if(lit.isAlive())throw new AssertionError("Lit normal soul must disappear");
        });
    }
    private static void verifyFrontFace(QuadParticleRenderState state,boolean floor) {
        var vertices=new Vertices();state.buildLayer(SingleQuadParticle.Layer.TRANSLUCENT,vertices);
        if(vertices.positions.size()!=4)throw new AssertionError("Expected one native particle quad");
        var a=vertices.positions.get(0);var normal=vertices.positions.get(1).subtract(a).cross(vertices.positions.get(2).subtract(a));
        var center=vertices.positions.stream().reduce(Vec3.ZERO,Vec3::add).scale(.25);
        if(floor?normal.y<=0:normal.dot(center.scale(-1))<=0)throw new AssertionError("Source soul front face was culled by native quad winding");
    }
    private static net.minecraft.world.entity.Entity find(net.minecraft.client.multiplayer.ClientLevel level,UUID id) { for(var entity:level.entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null; }
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message) { var ready=new AtomicBoolean();for(int i=0;i<ticks&&!ready.get();i++){world.getServer().runOnServer(ignored->ready.set(predicate.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message); }
    private static void block(TestSingleplayerContext world,BlockPos pos,String state) { world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state); }
    private static void use(ClientGameTestContext context) { context.waitTicks(6);context.getInput().pressKey(options->options.keyUse); }
    private static void screenshot(ClientGameTestContext context,TestSingleplayerContext world,String name){
        NativeScreenshotReadiness.await(context,world);
        context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);
    }
    private static void look(ClientGameTestContext context,Vec3 target) { var angles=context.computeOnClient(client->{var d=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});context.getInput().lookAt(angles.x,angles.y); }
}
