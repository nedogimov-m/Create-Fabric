package com.simibubi.create.test;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crank.*;
import com.simibubi.create.content.kinetics.fan.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;

/** Actual survival input, native value board/meshes/UV, resource reload and original BE retirement. UNRUN.
 * Scene X71000..71064/Z0..16; unload72400. No screenshot or runtime success is claimed.
 */
final class NativeKineticsRenderingTest {
    private static final BlockPos P=new BlockPos(71005,6,5),V=new BlockPos(71021,6,5),N=new BlockPos(71037,6,5),F=new BlockPos(71053,6,5);
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer(); server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 71032 10 10");
        context.waitFor(client->Math.abs(client.player.getX()-71032.5)<1 && client.level.isLoaded(P) && client.level.isLoaded(F),500);
        context.waitFor(client->NativeScreenshotReadiness.skyReady(client,new BlockPos(71001,5,8),new BlockPos(71017,5,8),new BlockPos(71033,5,8),new BlockPos(71049,5,8),new BlockPos(71064,5,8)),300);
        waitServer(context,world,level->{for(int cx=71000>>4;cx<=71064>>4;cx++)for(int cz=0;cz<=16>>4;cz++)if(level.getChunkSource().getChunkNow(cx,cz)==null)return false;return true;},500,"Native kinetic fixture floor footprint did not reach native FULL readiness");
        server.runCommand("fill 71000 4 0 71064 4 16 minecraft:smooth_quartz");
        waitServer(context,world,level->{for(int x=71000;x<=71064;x++)for(int z=0;z<=16;z++)if(!LoadedLevelAccess.blockState(level,new BlockPos(x,4,z)).is(net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ))return false;return true;},500,"Native kinetic fixture floor command did not produce all1105 original quartz cells");
        block(world,P.west(),"create:creative_motor[facing=east]"); block(world,P,"create:shaft[axis=x]"); block(world,P.east(),"create:shaft[axis=x]");
        block(world,P.south(),"create:brass_casing"); block(world,V.below(),"minecraft:stone");
        block(world,N.west(),"create:creative_motor[facing=east]"); block(world,N,"create:encased_fan[facing=east]"); block(world,N.east(),"create:nozzle[facing=east]");
        block(world,F.west(),"create:creative_motor[facing=east]"); block(world,F,"create:flywheel[axis=x]"); block(world,F.east(),"create:shaft[axis=x]");
        block(world,F.south(5).below(),"create:creative_motor[facing=up]"); block(world,F.south(5),"create:turntable");
        block(world,P.south(5),"create:andesite_encased_cogwheel[axis=y,top_shaft=false,bottom_shaft=true]"); block(world,P.south(5).below(),"create:creative_motor[facing=up]");
        block(world,P.south(5).east(),"create:cogwheel[axis=y]");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel(); for(var pos:List.of(P.west(),N.west(),F.west(),F.south(5).below(),P.south(5).below())) entity(level,pos).setGeneratedSpeed(64);});
        server.runCommand("item replace entity @p hotbar.0 with create:brass_casing 2"); server.runCommand("item replace entity @p hotbar.1 with create:wrench");
        server.runCommand("item replace entity @p hotbar.2 with minecraft:blue_dye 2"); server.runCommand("item replace entity @p hotbar.3 with create:copper_valve_handle 2");
        server.runCommand("item replace entity @p hotbar.4 with minecraft:air"); server.runCommand("gamemode survival @p");
        teleport(context,world,P.getX()+.5,5,P.getZ()-2.5);
        context.waitFor(client->Math.abs(client.player.getX()-(P.getX()+.5))<.01&&Math.abs(client.player.getY()-5)<.01&&Math.abs(client.player.getZ()-(P.getZ()-2.5))<.01&&client.player.onGround(),400);
        context.getInput().pressKey(InputConstants.KEY_1);
        context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.BRASS_CASING.asItem()),200);
        int casings=context.computeOnClient(client->count(client.player.getInventory(),AllBlocks.BRASS_CASING.asItem())); useShaft(context);
        try {
        waitServer(context,world,level->LoadedLevelAccess.blockState(level,P).is(AllBlocks.BRASS_ENCASED_SHAFT)&&Math.abs(entity(level,P.east()).getSpeed())==64,250,"Native survival casing did not preserve actual shaft network");
        } catch(AssertionError failure) {
            try {server.runOnServer(ignored->Create.LOGGER.error("Native casing server: {}",casingServerObservation(world.getConnection().getServerLevel(),casings)));}catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
            try {context.runOnClient(client->Create.LOGGER.error("Native casing client: {}",casingClientObservation(client)));}catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
            throw failure;
        }
        context.waitFor(client->count(client.player.getInventory(),AllBlocks.BRASS_CASING.asItem())==casings,200); verifyModels(context); verifySourceTile(context); verifyKinetics(context,P,1);
        world.getConnection().waitForChunksRender(); screenshot(context,world,"create-vulkan-native-encased-source-cog-shaft-and-casing-tile2");
        context.getInput().pressKey(InputConstants.KEY_2); context.getInput().holdKey(options->options.keyShift);
        context.waitFor(client->client.player.getMainHandItem().is(AllItems.WRENCH)&&client.player.isShiftKeyDown()&&client.player.isCrouching(),200);
        useShaft(context); context.getInput().releaseKey(options->options.keyShift);
        try {
        waitServer(context,world,level->LoadedLevelAccess.blockState(level,P).is(AllBlocks.SHAFT),200,"Source sneak uncase failed");
        } catch(AssertionError failure) {
            try {server.runOnServer(ignored->Create.LOGGER.error("Native uncase server: {}",casingServerObservation(world.getConnection().getServerLevel(),casings)));}catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
            try {context.runOnClient(client->Create.LOGGER.error("Native uncase client: {}",casingClientObservation(client)));}catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
            throw failure;
        }
        context.waitFor(client->count(client.player.getInventory(),AllBlocks.BRASS_CASING.asItem())==casings,200);
        context.getInput().pressKey(InputConstants.KEY_1); useShaft(context);
        try {
            waitServer(context,world,level->LoadedLevelAccess.blockState(level,P).is(AllBlocks.BRASS_ENCASED_SHAFT)&&Math.abs(entity(level,P.east()).getSpeed())==64,250,"Native survival recasing did not complete before leaving the shaft network");
        } catch(AssertionError failure) {
            try {server.runOnServer(ignored->Create.LOGGER.error("Native recase server: {}",casingServerObservation(world.getConnection().getServerLevel(),casings)));}catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
            try {context.runOnClient(client->Create.LOGGER.error("Native recase client: {}",casingClientObservation(client)));}catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
            throw failure;
        }
        context.waitFor(client->client.level.getBlockState(P).is(AllBlocks.BRASS_ENCASED_SHAFT)&&count(client.player.getInventory(),AllBlocks.BRASS_CASING.asItem())==casings,200);
        // Place the valve using an actual survival BlockItem, then edit it using held native input.
        teleport(context,world,V.getX()+.5,7,8); context.getInput().pressKey(InputConstants.KEY_4); context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.COPPER_VALVE_HANDLE.asItem()),200);
        use(context,V.below()); waitServer(context,world,level->LoadedLevelAccess.blockEntity(level,V) instanceof ValveHandleBlockEntity,200,"Actual survival valve item placement failed");
        context.waitFor(client->count(client.player.getInventory(),AllBlocks.COPPER_VALVE_HANDLE.asItem())==1,200); context.getInput().pressKey(InputConstants.KEY_5); look(context,Vec3.atCenterOf(V).add(0,-.001,0));
        context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(V)&&ValveHandleSettings.testHit(client.level.getBlockState(V),hit),200);
        try(var init=ValveInitProbe.start(V)) {
        context.getInput().holdKey(options->options.keyUse); context.waitFor(client->client.gui.screen() instanceof ValveHandleScreen,200);
        context.runOnClient(client->{
            var screen=(ValveHandleScreen)client.gui.screen();var owner=(ValveHandleBlockEntity)LoadedLevelAccess.blockEntity(client.level,V);int angle=owner.getAngleInput();
            var observed=init.take();
            if(observed==null||observed.screen()!=screen||observed.world()!=client.level||observed.owner()!=owner||observed.angle()!=angle||!observed.pos().equals(V))throw new AssertionError("Actual native Valve AFTER_INIT snapshot did not belong to the original current screen/world/owner/angle: observed="+observed+", currentScreen="+System.identityHashCode(screen)+", currentOwner="+System.identityHashCode(owner)+", angle="+angle);
            var expected=screen.getCoordinateOfValue(angle<0?0:1,Math.abs(angle));double x=observed.x(),y=observed.y();
            if(!screen.position().equals(V)||Math.abs(x-expected.x)>.01||Math.abs(y-expected.y)>.01)throw new AssertionError("Actual native Valve init warp missed its original selected angle before fixture cursor movement: angle="+angle+", expectedGui="+expected.x+","+expected.y+", actualGui="+x+","+y+", raw="+observed.rawX()+","+observed.rawY()+", screen="+observed.screenWidth()+"x"+observed.screenHeight()+", framebuffer="+observed.framebufferWidth()+"x"+observed.framebufferHeight()+", gui="+observed.guiWidth()+"x"+observed.guiHeight());
        });
        }
        var at=context.computeOnClient(client->{var screen=(ValveHandleScreen)client.gui.screen();var point=screen.getCoordinateOfValue(1,90);float scaleX=(float)client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth();float scaleY=(float)client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight();return new Vec2(point.x*scaleX,point.y*scaleY);});
        context.getInput().setCursorPos(at.x,at.y); context.waitTicks(3); screenshot(context,world,"create-vulkan-native-valve-handle-source180-degree45-step-board");
        context.getInput().releaseKey(options->options.keyUse); context.waitFor(client->client.gui.screen()==null,200);
        waitServer(context,world,level->((ValveHandleBlockEntity)LoadedLevelAccess.blockEntity(level,V)).getAngleInput()==90,200,"Native source valve value packet did not reach actual owner");
        context.getInput().pressKey(InputConstants.KEY_3); context.waitFor(client->client.player.getMainHandItem().is(Items.DYE.pick(DyeColor.BLUE)),200);
        var originalValve=server.computeOnServer(ignored->LoadedLevelAccess.blockEntity(world.getConnection().getServerLevel(),V)); use(context,V);
        waitServer(context,world,level->LoadedLevelAccess.blockState(level,V).is(AllBlocks.DYED_VALVE_HANDLES.get(DyeColor.BLUE))&&LoadedLevelAccess.blockEntity(level,V)==originalValve,200,"Native dye replaced the original source valve owner");
        context.waitFor(client->count(client.player.getInventory(),Items.DYE.pick(DyeColor.BLUE))==2,200); verifyKinetics(context,V,1); screenshot(context,world,"create-vulkan-native-valve-dye-preserves-actual-owner-and-stock");
        server.runCommand("gamemode spectator @p"); teleport(context,world,N.getX()+3.5,9,10); look(context,Vec3.atCenterOf(N.east()));
        waitServer(context,world,level->((NozzleBlockEntity)LoadedLevelAccess.blockEntity(level,N.east())).getRange()>3&&((EncasedFanBlockEntity)LoadedLevelAccess.blockEntity(level,N)).getAirCurrent().maxDistance==0,250,"Actual nozzle must replace the linear fan current");
        verifyKinetics(context,F,2); verifyKinetics(context,F.south(5),1); world.getConnection().waitForChunksRender(); screenshot(context,world,"create-vulkan-native-nozzle-and-flywheel-source-mesh-parts");
        teleport(context,world,F.getX()+4.5,8,F.getZ()+4.5); look(context,Vec3.atCenterOf(F));
        screenshot(context,world,"create-vulkan-native-flywheel-actual-framed-source-mesh-parts");
        teleport(context,world,N.getX()+3.5,9,10); look(context,Vec3.atCenterOf(N.east()));
        var model=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(P)));
        var reload=context.computeOnClient(client->client.reloadResourcePacks()); context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600); reload.join();
        context.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(P))==model)throw new AssertionError("Actual native source CT model retained stale resource identity");}); verifyModels(context); verifySourceTile(context); verifyKinetics(context,F,2); screenshot(context,world,"create-vulkan-native-kinetics26-after-resource-reload");
        var oldShaft=server.computeOnServer(ignored->LoadedLevelAccess.blockEntity(world.getConnection().getServerLevel(),P));
        teleport(context,world,72400,20,72400);
        waitServer(context,world,level->List.of(P,V,N,F).stream().allMatch(pos->level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null),800,"Actual source fixture chunks did not become absent");
        NativeFixtureChunkUnload.finish(context,world,oldShaft,originalValve);
        teleport(context,world,71032,10,10);
        waitServer(context,world,level->LoadedLevelAccess.blockEntity(level,P)!=null&&LoadedLevelAccess.blockEntity(level,P)!=oldShaft
                &&LoadedLevelAccess.blockEntity(level,V) instanceof ValveHandleBlockEntity valve&&valve!=originalValve&&valve.getAngleInput()==90
                &&LoadedLevelAccess.blockState(level,V).is(AllBlocks.DYED_VALVE_HANDLES.get(DyeColor.BLUE)),600,"True native chunk return lost saved source valve/shaft state");
        context.waitFor(client->client.level.isLoaded(P)&&client.level.isLoaded(F),500); verifyModels(context); verifySourceTile(context); world.getConnection().waitForChunksRender(); screenshot(context,world,"create-vulkan-native-kinetics26-after-original-be-retirement-and-return");
    }
    private record ValveInitSnapshot(ValveHandleScreen screen,net.minecraft.client.multiplayer.ClientLevel world,BlockPos pos,ValveHandleBlockEntity owner,int angle,double x,double y,double rawX,double rawY,int screenWidth,int screenHeight,int framebufferWidth,int framebufferHeight,int guiWidth,int guiHeight) {}
    private static final class ValveInitProbe implements AutoCloseable {
        private static final AtomicReference<ValveInitProbe> ACTIVE=new AtomicReference<>();
        private final BlockPos pos;
        private final AtomicReference<ValveInitSnapshot> snapshot=new AtomicReference<>();
        static {
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,width,height)->{
                var probe=ACTIVE.get();
                if(probe==null||!(screen instanceof ValveHandleScreen valve)||!valve.position().equals(probe.pos))return;
                var world=client.level;var nativeChunk=world==null?null:world.getChunkSource().getChunkNow(probe.pos.getX()>>4,probe.pos.getZ()>>4);
                var actual=nativeChunk==null?null:nativeChunk.getBlockEntities().get(probe.pos);var owner=actual instanceof ValveHandleBlockEntity entity?entity:null;
                var window=client.getWindow();
                probe.snapshot.compareAndSet(null,new ValveInitSnapshot(valve,world,valve.position().immutable(),owner,owner==null?0:owner.getAngleInput(),client.mouseHandler.getScaledXPos(window),client.mouseHandler.getScaledYPos(window),client.mouseHandler.xpos(),client.mouseHandler.ypos(),window.getScreenWidth(),window.getScreenHeight(),window.getWidth(),window.getHeight(),window.getGuiScaledWidth(),window.getGuiScaledHeight()));
            });
        }
        private ValveInitProbe(BlockPos pos){this.pos=pos.immutable();}
        static ValveInitProbe start(BlockPos pos){var probe=new ValveInitProbe(pos);if(!ACTIVE.compareAndSet(null,probe))throw new IllegalStateException("Actual Valve init probe already armed");return probe;}
        ValveInitSnapshot take(){ACTIVE.compareAndSet(this,null);return snapshot.getAndSet(null);}
        @Override public void close(){ACTIVE.compareAndSet(this,null);snapshot.set(null);}
    }
    private static List<Block> blocks() { var blocks=new ArrayList<Block>(List.of(AllBlocks.ANDESITE_ENCASED_SHAFT,AllBlocks.BRASS_ENCASED_SHAFT,AllBlocks.ANDESITE_ENCASED_COGWHEEL,AllBlocks.BRASS_ENCASED_COGWHEEL,AllBlocks.ANDESITE_ENCASED_LARGE_COGWHEEL,AllBlocks.BRASS_ENCASED_LARGE_COGWHEEL,AllBlocks.COPPER_VALVE_HANDLE,AllBlocks.NOZZLE,AllBlocks.TURNTABLE,AllBlocks.FLYWHEEL)); blocks.addAll(AllBlocks.DYED_VALVE_HANDLES.values()); return blocks; }
    private static String casingServerObservation(ServerLevel level,int expectedCasings){
        var result=new StringBuilder("tick=").append(level.getGameTime());var player=level.getServer().getPlayerList().getPlayers().getFirst();
        result.append(" player=").append(player.position()).append(" eye=").append(player.getEyePosition()).append(" grounded=").append(player.onGround()).append(" shift=").append(player.isShiftKeyDown()).append(" spectator=").append(player.isSpectator()).append(" selected=").append(player.getInventory().getSelectedSlot()).append(" mainhand=").append(player.getMainHandItem()).append(" offhand=").append(player.getOffhandItem()).append(" expectedCasings=").append(expectedCasings).append(" actualCasings=").append(count(player.getInventory(),AllBlocks.BRASS_CASING.asItem()));
        var current=casingNativeEntity(level,P.east());var chunk=level.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4);
        result.append("; predicates encased=").append(chunk!=null&&chunk.getBlockState(P).is(AllBlocks.BRASS_ENCASED_SHAFT)).append(" eastRPM64=").append(current instanceof KineticBlockEntity kinetic&&Math.abs(kinetic.getSpeed())==64).append(" variants=").append(com.simibubi.create.content.decoration.encasing.EncasingRegistry.getVariants(AllBlocks.SHAFT));
        for(int slot=0;slot<9;slot++)result.append("; hotbar[").append(slot).append("]=").append(player.getInventory().getItem(slot));
        for(var pos:List.of(P.west(),P,P.east(),P.south(),N.west(),F.west(),F.south(5).below(),P.south(5).below()))casingOwnerObservation(result,level,pos);
        for(var side:Direction.values()){var pos=P.relative(side);var nativeChunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);result.append("; neighbor ").append(side).append("=").append(nativeChunk==null?"absent":nativeChunk.getBlockState(pos));}
        for(var item:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(P).inflate(4)))if(item.getItem().is(AllBlocks.BRASS_CASING.asItem()))result.append("; droppedCasing=").append(item.getUUID()).append("@").append(item.position()).append(" ").append(item.getItem());
        return result.toString();
    }
    private static String casingClientObservation(net.minecraft.client.Minecraft client){
        var hit=client.hitResult;var result=new StringBuilder("tick=").append(client.level.getGameTime()).append(" player=").append(client.player.position()).append(" eye=").append(client.player.getEyePosition()).append(" camera=").append(client.gameRenderer.mainCamera().position()).append(" grounded=").append(client.player.onGround()).append(" shift=").append(client.player.isShiftKeyDown()).append(" selected=").append(client.player.getInventory().getSelectedSlot()).append(" mainhand=").append(client.player.getMainHandItem()).append(" casings=").append(count(client.player.getInventory(),AllBlocks.BRASS_CASING.asItem())).append(" hitType=").append(hit==null?null:hit.getType()).append(" hitLocation=").append(hit==null?null:hit.getLocation()).append(" hitBlock=").append(hit instanceof BlockHitResult blockHit?blockHit.getBlockPos():null).append(" hitFace=").append(hit instanceof BlockHitResult sideHit?sideHit.getDirection():null);
        for(var pos:List.of(P.west(),P,P.east(),P.south()))casingOwnerObservation(result,client.level,pos);
        return result.toString();
    }
    private static net.minecraft.world.level.block.entity.BlockEntity casingNativeEntity(net.minecraft.world.level.Level level,BlockPos pos){var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk==null?null:chunk.getBlockEntities().get(pos);}
    private static void casingOwnerObservation(StringBuilder result,net.minecraft.world.level.Level level,BlockPos pos){
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);var owner=casingNativeEntity(level,pos);
        result.append("; pos=").append(pos).append(" FULLnow=").append(chunk!=null).append(" actualState=").append(chunk==null?"absent":chunk.getBlockState(pos)).append(" owner=").append(owner==null?"absent":owner.getClass().getName()+"@"+System.identityHashCode(owner));
        if(owner==null)return;result.append(" cachedState=").append(owner.getBlockState()).append(" removed=").append(owner.isRemoved()).append(" levelIdentity=").append(owner.getLevel()==level).append(" posIdentity=").append(owner.getBlockPos().equals(pos));
        if(owner instanceof KineticBlockEntity kinetic){
            result.append(" rpm=").append(kinetic.getSpeed()).append(" theoretical=").append(kinetic.getTheoreticalSpeed()).append(" generated=").append(kinetic.getDirectionalGeneratedSpeed()).append(" source=").append(kinetic.source).append(" generator=").append(kinetic.getGeneratingSource()).append(" network=").append(kinetic.network).append(" networkDirty=").append(kinetic.networkDirty).append(" capacity=").append(kinetic.getStressCapacity()).append(" impact=").append(kinetic.getStressImpact()).append(" overstressed=").append(kinetic.isOverstressed()).append(" conflicted=").append(kinetic.isConflicted()).append(" ports=").append(kinetic.getTheoreticalPortSpeeds()).append(" preventSpeedUpdate=").append(kinetic.preventSpeedUpdate).append(" smartInitialized=").append(casingField(kinetic,com.simibubi.create.foundation.blockEntity.SmartBlockEntity.class,"initialized")).append(" firstNbtRead=").append(casingField(kinetic,com.simibubi.create.foundation.blockEntity.SmartBlockEntity.class,"firstNbtRead"));
            if(kinetic instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor){
                var setting=motor.generatedSpeed;var registry=casingField(motor,com.simibubi.create.foundation.blockEntity.SmartBlockEntity.class,"behaviours");var initialized=casingField(motor,com.simibubi.create.foundation.blockEntity.SmartBlockEntity.class,"initializedBehaviours");
                result.append(" actualMotorSetting=").append(setting==null?"absent":setting.getValue()).append(" settingOwner=").append(setting!=null&&setting.blockEntity==motor).append(" settingCanonical=").append(setting!=null&&registry instanceof Map<?,?> entries&&entries.get(setting.getType())==setting).append(" settingInitialized=").append(setting!=null&&initialized instanceof Set<?> entries&&entries.contains(setting));
            }
        }
    }
    private static Object casingField(Object target,Class<?> declaring,String name){try{var field=declaring.getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(ReflectiveOperationException|RuntimeException failure){return "unavailable:"+name+":"+failure.getClass().getSimpleName();}}
    private static void verifyModels(ClientGameTestContext context) { context.runOnClient(client->{for(var block:blocks())for(var state:block.getStateDefinition().getPossibleStates())if(client.getModelManager().getBlockStateModelSet().get(state)==client.getModelManager().getBlockStateModelSet().missingModel())throw new AssertionError("Missing actual source block model "+state);}); }
    private static void verifyKinetics(ClientGameTestContext context,BlockPos pos,int parts) { context.runOnClient(client->{var be=client.level.getBlockEntity(pos);var state=(NativeKineticsRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(be,.5f,null,true);if(state==null||state.usesLegacyRenderer()||state.getParts().size()!=parts||state.getParts().stream().anyMatch(part->part.model().mesh==null||part.model().mesh.size()==0))throw new AssertionError("Actual native moving source mesh parts missing at "+pos);}); }
    private static void verifySourceTile(ClientGameTestContext context) { context.runOnClient(client->{
        var state=client.level.getBlockState(P); var model=client.getModelManager().getBlockStateModelSet().get(state);
        if(!(model instanceof NativeKineticsCTModel ct)||ct.indices(client.level,P,state).get(Direction.UP.ordinal()).tile()!=2)throw new AssertionError("Genuine source shaft/casing UP context must select8x8 tile2");
        var mesh=Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,P,state,RandomSource.create(42),face->false);
        var sprite=client.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS).getSprite(Create.asResource("block/brass_casing_connected"));var seen=new AtomicBoolean();
        mesh.forEach(quad->{if(quad.lightFace()!=Direction.UP)return;boolean inside=true;for(int vertex=0;vertex<4;vertex++)inside&=quad.u(vertex)>=sprite.getU(2f/8)-.00001&&quad.u(vertex)<=sprite.getU(3f/8)+.00001&&quad.v(vertex)>=sprite.getV(0)-.00001&&quad.v(vertex)<=sprite.getV(1f/8)+.00001;if(inside)seen.set(true);});if(!seen.get())throw new AssertionError("Actual emitted native UVs did not enter source tile2");
    }); }
    private static KineticBlockEntity entity(ServerLevel level,BlockPos pos) { return (KineticBlockEntity)LoadedLevelAccess.blockEntity(level,pos); }
    private static int count(net.minecraft.world.entity.player.Inventory inventory,Item item) { int count=0;for(int slot=0;slot<inventory.getContainerSize();slot++){var stack=inventory.getItem(slot);if(stack.is(item))count+=stack.getCount();}return count; }
    private static void block(TestSingleplayerContext world,BlockPos pos,String state) { world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state); }
    private static void teleport(ClientGameTestContext context,TestSingleplayerContext world,double x,double y,double z) { world.getServer().runCommand("tp @p "+x+" "+y+" "+z);context.waitFor(client->Math.abs(client.player.getX()-x)<1&&Math.abs(client.player.getZ()-z)<1,400); }
    private static void useShaft(ClientGameTestContext context) { context.waitTicks(4);look(context,Vec3.atCenterOf(P));context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getY()-5)<.01&&client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P),200);context.getInput().pressKey(options->options.keyUse); }
    private static void use(ClientGameTestContext context,BlockPos pos) {
        context.waitFor(client->!client.player.isSpectator()&&client.player.onGround()&&Math.abs(client.player.getY()-5)<.01,400);
        context.waitTicks(4);
        var target=context.computeOnClient(client->{var bounds=client.level.getBlockState(pos).getShape(client.level,pos).bounds();return new Vec3(pos.getX()+(bounds.minX+bounds.maxX)/2,pos.getY()+bounds.maxY-.001,pos.getZ()+(bounds.minZ+bounds.maxZ)/2);});
        look(context,target);
        context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos)&&(!pos.equals(V.below())||hit.getDirection()==Direction.UP),200);
        context.getInput().pressKey(options->options.keyUse);
    }
    private static void look(ClientGameTestContext context,Vec3 target) { var angle=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y); }
    private static void screenshot(ClientGameTestContext context,TestSingleplayerContext world,String name) { NativeScreenshotReadiness.await(context,world);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name); }
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message) { var ready=new AtomicBoolean();for(int tick=0;tick<ticks&&!ready.get();tick++){world.getServer().runOnServer(ignored->ready.set(predicate.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message); }
}
