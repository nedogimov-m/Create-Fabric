package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.contraptionControls.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

/** Native input, tracked controls inversion, original partials, reload and genuine two-chunk save. */
final class ControlsRenderingTest {
    private static final BlockPos P = new BlockPos(5999, 4, 5), C = P.above(5), GLOBAL = new BlockPos(2, 0, 0), SPECIFIC = new BlockPos(3, 0, 0), DRILL = new BlockPos(4, 0, 0), SAW = new BlockPos(5, 0, 0);
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer(); server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 5999.5 4 8.5");
        context.waitFor(client -> Math.abs(client.player.getX() - 5999.5) < .1 && client.level.isLoaded(C.east(5)), 400);
        context.waitFor(client -> NativeScreenshotReadiness.skyReady(client, new BlockPos(5996, 4, 7), new BlockPos(6006, 4, 7)), 300);
        Create.LOGGER.info("Native Controls initial FULL/column/SKY15 readiness confirmed before platform mutation");
        server.runCommand("fill 5994 3 -2 6011 3 16 minecraft:smooth_quartz"); server.runCommand("gamemode survival @p"); server.runCommand("item replace entity @p weapon.mainhand with create:contraption_controls 2");
        context.waitFor(client -> client.player.onGround() && client.player.getMainHandItem().is(AllBlocks.CONTRAPTION_CONTROLS.asItem()), 200);
        look(context, Vec3.atCenterOf(P.below()).add(0, .5, 0)); use(context);
        waitServer(context, world, level -> level.getBlockEntity(P) instanceof ContraptionControlsBlockEntity && level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().getCount() == 1, 200, "Real controls item placement failed");
        server.runCommand("item replace entity @p weapon.mainhand with create:mechanical_drill 9");
        context.waitFor(client -> client.player.getMainHandItem().is(AllBlocks.MECHANICAL_DRILL.asItem()), 200);
        var slot = context.computeOnClient(client -> Vec3.atLowerCornerOf(P).add(ContraptionControlsBlock.filterPosition(client.level.getBlockState(P))));
        look(context, slot); use(context);
        waitServer(context, world, level -> ((ContraptionControlsBlockEntity) level.getBlockEntity(P)).filtering.getFilter().is(AllBlocks.MECHANICAL_DRILL.asItem()) && level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().getCount() == 9, 200, "Native filter hit lost sample or failed to set filter");
        context.waitFor(client -> client.level.getBlockEntity(P) instanceof ContraptionControlsBlockEntity controls && controls.filtering.getFilter().is(AllBlocks.MECHANICAL_DRILL.asItem()), 200);
        context.runOnClient(client -> {
            var state = (ContraptionControlsRenderer.State) client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P), .5f, null, false);
            if (state == null || state.filter.isEmpty() || state.button.mesh == null || state.button.mesh.size() == 0 || state.indicator.mesh == null || state.indicator.mesh.size() == 0) throw new AssertionError("Native controls item/button/indicator state missing");
            for (var block : AllBlocks.CONTRAPTION_CONTROLS.getStateDefinition().getPossibleStates()) if (client.getModelManager().getBlockStateModelSet().get(block) == client.getModelManager().getBlockStateModelSet().missingModel()) throw new AssertionError("Missing original controls state model " + block);
        }); screenshot(context,world, "create-vulkan-controls-native-placement-filter-and-parts");
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air"); context.waitFor(client -> client.player.getMainHandItem().isEmpty(), 200);
        look(context, Vec3.atLowerCornerOf(P).add(.15, .4, .5)); use(context);
        context.waitFor(client -> client.level.getBlockEntity(P) instanceof ContraptionControlsBlockEntity controls && controls.disabled && controlsState(client, P).indicatorStage == 4, 200);
        screenshot(context,world, "create-vulkan-controls-real-button-and-disabled-indicator");
        block(world, P.east(), "minecraft:redstone_block");
        waitServer(context, world, level -> ((ContraptionControlsBlockEntity) level.getBlockEntity(P)).powered, 200, "Controls did not receive real redstone input");
        block(world, P.east(), "minecraft:air");
        context.waitFor(client -> client.level.getBlockEntity(P) instanceof ContraptionControlsBlockEntity controls && !controls.powered && !controls.disabled, 200);
        // A genuine glued row crosses the chunk boundary, carrying one global and one type control.
        server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 6001.5 11 10.5"); server.runCommand("fill 5996 8 1 6008 8 13 minecraft:smooth_quartz");
        for (int i = 0; i < 6; i++) block(world, C.east(i), i == 0 ? "minecraft:stone" : i == 1 ? "minecraft:barrel" : i < 4 ? "create:contraption_controls[facing=south]" : i == 4 ? "create:mechanical_drill[facing=up]" : "create:mechanical_saw[facing=up]");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); ((BarrelBlockEntity) level.getBlockEntity(C.east())).setItem(0, new ItemStack(Items.DIAMOND, 7));
            var global = (ContraptionControlsBlockEntity) level.getBlockEntity(C.east(2)); global.disabled = true; global.notifyUpdate();
            ((ContraptionControlsBlockEntity) level.getBlockEntity(C.east(3))).filtering.setFilter(new ItemStack(AllBlocks.MECHANICAL_DRILL));
            level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(C, C.east(5))));
        });
        waitServer(context, world, level -> SuperGlueEntity.isGlued(level, C, Direction.EAST, null), 300, "Fixture glue never became queryable");
        NativeContraptionFixtures.awaitCaptureReady(context,world,300,C,C.east(5));
        var id = new AtomicReference<UUID>();
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var c = new BearingContraption(false, Direction.UP);
            try { if (!c.assemble(level, C.below()) || c.getBlocks().size() != 6 || !c.tryRemoveBlocksFromWorld(level, BlockPos.ZERO)) throw new AssertionError("Controls capture lost a glued block"); } catch (AssemblyException exception) { throw new AssertionError(exception); }
            var owner = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, level); owner.setContraption(c); owner.setPos(Vec3.atLowerCornerOf(C)); level.addFreshEntity(owner); id.set(owner.getUUID());
            if (!c.getActorAt(DRILL).right.disabled || !c.getActorAt(SAW).right.disabled || c.getActorAt(GLOBAL).right.disabled) throw new AssertionError("Initial global disable did not reach real assembled actors");
        });
        context.waitFor(client -> find(client.level, id.get()) instanceof ControlledContraptionEntity owner && owner.getContraption() != null && owner.getContraption().getBlocks().size() == 6 && owner.getContraption().getActorAt(DRILL).right.disabled, 300);
        server.runCommand("tp @p 6001.5 9 8.5"); server.runCommand("gamemode survival @p");
        context.waitFor(client -> !client.player.isSpectator() && client.player.onGround() && Math.abs(client.player.getX() - 6001.5) < .1, 200);
        clickMounted(context, id.get(), GLOBAL);
        waitServer(context, world, level -> { var c = owner(level, id.get()).getContraption(); return !c.getActorAt(DRILL).right.disabled && !c.getActorAt(SAW).right.disabled && c.getDisabledActors().isEmpty(); }, 200, "Actual mounted global click did not enable all actors");
        context.waitFor(client -> !((ControlledContraptionEntity) find(client.level, id.get())).getContraption().getActorAt(DRILL).right.disabled && mountedState(client, id.get(), GLOBAL).indicatorStage == 0, 200);
        clickMounted(context, id.get(), SPECIFIC);
        waitServer(context, world, level -> { var c = owner(level, id.get()).getContraption(); return c.getActorAt(DRILL).right.disabled && !c.getActorAt(SAW).right.disabled; }, 200, "Actual mounted type click did not select only drill");
        context.waitFor(client -> ((ControlledContraptionEntity) find(client.level, id.get())).getContraption().getActorAt(DRILL).right.disabled && mountedState(client, id.get(), SPECIFIC).indicatorStage == 4 && mountedState(client, id.get(), GLOBAL).indicatorStage == 0, 200);
        screenshot(context,world, "create-vulkan-moving-controls-type-toggle-and-native-sync");
        clickMounted(context, id.get(), GLOBAL); waitServer(context, world, level -> owner(level, id.get()).getContraption().isActorTypeDisabled(ItemStack.EMPTY), 200, "Global click failed to replace per-type selection");
        clickMounted(context, id.get(), SPECIFIC);
        waitServer(context, world, level -> { var c = owner(level, id.get()).getContraption(); return !c.getActorAt(DRILL).right.disabled && c.getActorAt(SAW).right.disabled && !c.isActorTypeDisabled(ItemStack.EMPTY); }, 200, "Global-to-specific inversion did not keep other actors disabled");
        context.waitFor(client -> { var c = ((ControlledContraptionEntity) find(client.level, id.get())).getContraption(); return !c.getActorAt(DRILL).right.disabled && c.getActorAt(SAW).right.disabled; }, 200);
        // Native BE states render through the ordinary contraption renderer with no duplicated actor pass.
        context.runOnClient(client -> { var a = mountedState(client, id.get(), GLOBAL); var b = mountedState(client, id.get(), SPECIFIC); if (a.button.mesh.size() == 0 || b.indicator.mesh.size() == 0 || b.filter.isEmpty()) throw new AssertionError("Moving native controls geometry/filter missing"); });
        screenshot(context,world, "create-vulkan-moving-controls-global-to-specific-inversion");
        var previousMesh = context.computeOnClient(client -> mountedState(client, id.get(), SPECIFIC).button.mesh); var reload = context.computeOnClient(client -> client.reloadResourcePacks());
        context.waitFor(client -> reload.isDone(), 500); reload.join();
        context.runOnClient(client -> { if (mountedState(client, id.get(), SPECIFIC).button.mesh == previousMesh) throw new AssertionError("Controls retained old native mesh after reload"); });
        server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 6800 15 6800");
        waitServer(context, world, level -> level.getEntity(id.get()) == null && level.getChunkSource().getChunkNow(C.getX() >> 4, 0) == null && level.getChunkSource().getChunkNow(C.east(5).getX() >> 4, 0) == null, 800, "Controls retained either source chunk");
        server.runCommand("tp @p 6001.5 11 10.5");
        waitServer(context, world, level -> level.getEntity(id.get()) instanceof ControlledContraptionEntity owner && !owner.getContraption().getActorAt(DRILL).right.disabled && owner.getContraption().getActorAt(SAW).right.disabled, 500, "Native saved actor filter/disabled state did not return");
        context.waitFor(client -> find(client.level, id.get()) instanceof ControlledContraptionEntity owner && owner.getContraption() != null && owner.getContraption().getActorAt(SAW).right.disabled, 300);
        look(context, Vec3.atCenterOf(C.east(3))); screenshot(context,world, "create-vulkan-controls-real-two-chunk-save-return");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var owner = owner(level, id.get()); if (!owner.disassemble()) throw new AssertionError("Controls disassembly failed");
            var global = (ContraptionControlsBlockEntity) level.getBlockEntity(C.east(2)); var specific = (ContraptionControlsBlockEntity) level.getBlockEntity(C.east(3));
            if (global == null || specific == null || global.disabled || specific.disabled || !specific.filtering.getFilter().is(AllBlocks.MECHANICAL_DRILL.asItem()) || !((BarrelBlockEntity) level.getBlockEntity(C.east())).getItem(0).is(Items.DIAMOND) || ((BarrelBlockEntity) level.getBlockEntity(C.east())).getItem(0).getCount() != 7) throw new AssertionError("Control settings/current state/cargo changed during world restoration");
        }); screenshot(context,world, "create-vulkan-controls-exact-world-restoration");
    }
    private static ContraptionControlsRenderer.State controlsState(net.minecraft.client.Minecraft client, BlockPos pos) { return (ContraptionControlsRenderer.State) client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos), .5f, null, false); }
    private static ContraptionControlsRenderer.State mountedState(net.minecraft.client.Minecraft client, UUID id, BlockPos pos) {
        var owner = (ControlledContraptionEntity) find(client.level, id); var state = ((ContraptionEntityRenderer) client.getEntityRenderDispatcher().getRenderer(owner)).createRenderState(owner, .5f);
        return (ContraptionControlsRenderer.State) state.blockEntities.stream().filter(be -> be.pos().equals(pos)).findFirst().orElseThrow(() -> new AssertionError("Missing controls native BE state")).state();
    }
    private static void clickMounted(ClientGameTestContext context, UUID id, BlockPos pos) { var target = context.computeOnClient(client -> ((ControlledContraptionEntity) find(client.level, id)).toGlobalVector(Vec3.atLowerCornerOf(pos).add(.15, .4, .5), 1)); look(context, target); use(context); }
    private static ControlledContraptionEntity owner(ServerLevel level, UUID id) { return (ControlledContraptionEntity) level.getEntity(id); }
    private static net.minecraft.world.entity.Entity find(net.minecraft.client.multiplayer.ClientLevel level, UUID id) { for (var entity : level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity; return null; }
    private static void block(TestSingleplayerContext world, BlockPos pos, String state) { world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state); }
    private static void waitServer(ClientGameTestContext context, TestSingleplayerContext world, Predicate<ServerLevel> predicate, int ticks, String message) { var ready = new AtomicBoolean(); for (int i = 0; i < ticks && !ready.get(); i++) { world.getServer().runOnServer(ignored -> ready.set(predicate.test(world.getConnection().getServerLevel()))); if (!ready.get()) context.waitTicks(1); } if (!ready.get()) throw new AssertionError(message); }
    private static void use(ClientGameTestContext context) { context.waitTicks(6); context.getInput().pressKey(options -> options.keyUse); }
    private static final AtomicReference<FrameObservation> CAPTURE_FRAMES=new AtomicReference<>();
    private static boolean captureFrameObserverRegistered;
    private record FrameObservation(net.minecraft.client.multiplayer.ClientLevel level,AtomicLong endMainCount,AtomicLong lastTick,AtomicReference<Vec3> lastCamera,String startInputs){}
    private static void screenshot(ClientGameTestContext context,TestSingleplayerContext world,String name){
        var observed=context.computeOnClient(client->{
            if(!captureFrameObserverRegistered){
                net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN.register(render->{
                    var active=CAPTURE_FRAMES.get();if(active==null)return;
                    var minecraft=net.minecraft.client.Minecraft.getInstance();
                    if(minecraft.level!=active.level())return;
                    active.endMainCount().incrementAndGet();active.lastTick().set(minecraft.level.getGameTime());active.lastCamera().set(minecraft.gameRenderer.mainCamera().position());
                });
                captureFrameObserverRegistered=true;
            }
            var active=new FrameObservation(client.level,new AtomicLong(),new AtomicLong(-1),new AtomicReference<>(),describeFrameInputs(client));
            CAPTURE_FRAMES.set(active);return active;
        });
        try{
            NativeScreenshotReadiness.await(context,world,client->{
                if(client.player==null)return false;int y=client.player.getY()<8?4:9;
                return NativeScreenshotReadiness.skyReady(client,new BlockPos(5996,y,7),new BlockPos(6006,y,7));
            });
        }catch(AssertionError failure){
            try{
                var samples=context.computeOnClient(client->{var player=client.player;double y=player==null?Double.NaN:player.getY();int sampleY=y<8?4:9;return new CaptureSamples(player==null?null:player.position(),List.of(new BlockPos(5996,sampleY,7),new BlockPos(6006,sampleY,7)));});
                var clientState=context.computeOnClient(client->"nativeFrameObservation={END_MAIN="+observed.endMainCount().get()+", lastTick="+observed.lastTick().get()+", lastCamera="+observed.lastCamera().get()+", start={"+observed.startInputs()+"}, now={"+describeFrameInputs(client)+"}}, overlay="+(client.gui.overlay()==null?"null":client.gui.overlay().getClass().getName())+", player="+(client.player==null?"null":client.player.position())+", screen="+(client.gui.screen()==null?"null":client.gui.screen().getClass().getName())+", "+describeLightInputs(client.level,samples));
                var serverState=world.getServer().computeOnServer(ignored->{var level=world.getConnection().getServerLevel();var players=level.getServer().getPlayerList().getPlayers();return "player="+(players.isEmpty()?"absent":players.getFirst().position())+", "+describeLightInputs(level,samples);});
                System.err.println("CREATE_CONTROLS_CAPTURE_READINESS name="+name+" chosen="+samples+" client={"+clientState+"} server={"+serverState+"}");
            }catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
            throw failure;
        }finally{CAPTURE_FRAMES.compareAndSet(observed,null);}
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); context.takeScreenshot(name);
    }
    private static String describeFrameInputs(net.minecraft.client.Minecraft client){
        try{
            var frames=net.minecraft.client.Minecraft.class.getDeclaredField("frames");frames.setAccessible(true);
            var frameTimeNs=net.minecraft.client.Minecraft.class.getDeclaredField("frameTimeNs");frameTimeNs.setAccessible(true);
            String queue="level=absent";
            if(client.level!=null){
                var field=net.minecraft.client.multiplayer.ClientLevel.class.getDeclaredField("lightUpdateQueue");field.setAccessible(true);
                var updates=(java.util.Deque<?>)field.get(client.level);var head=updates.peek();
                queue="lightUpdateQueueSize="+updates.size()+", lightUpdateQueueHead="+(head==null?"null":head.getClass().getName());
            }
            return "surfaceAcquired="+client.windowSurface().isAcquired()+", gameLoadFinished="+client.isGameLoadFinished()+", nativeFramesWindow="+frames.getInt(client)+", nativeFrameTimeNs="+frameTimeNs.getLong(client)+", camera="+client.gameRenderer.mainCamera().position()+", hasRenderedAllSections="+client.levelRenderer.hasRenderedAllSections()+", "+queue;
        }catch(ReflectiveOperationException observationFailure){throw new AssertionError("Cannot observe native frame/light queue",observationFailure);}
    }
    private record CaptureSamples(Vec3 player,List<BlockPos> positions){}
    private static String describeLightInputs(net.minecraft.world.level.Level level,CaptureSamples samples){
        if(level==null)return "level=absent";
        var result=new StringBuilder("levelClass=").append(level.getClass().getName()).append(", dimension=").append(level.dimension()).append(", client=").append(level.isClientSide()).append(", tick=").append(level.getGameTime()).append(", hasLightWork=").append(level.getLightEngine().hasLightWork());
        for(var pos:samples.positions()){
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            result.append("; sample=").append(pos).append(" loaded=").append(level.isLoaded(pos)).append(" FULLnow=").append(chunk!=null).append(" column=").append(level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(pos.getX()>>4,pos.getZ()>>4)));
            if(chunk==null)continue;
            result.append(" fullStatus=").append(chunk.getFullStatus());
            if(level instanceof ServerLevel server)result.append(" chunkDebug=").append(server.getChunkSource().getChunkDebugData(new net.minecraft.world.level.ChunkPos(pos.getX()>>4,pos.getZ()>>4)));
            result.append(" skySourceLowest=").append(chunk.getSkyLightSources().getLowestSourceY(pos.getX()&15,pos.getZ()&15)).append(" skySourceHighestLowest=").append(chunk.getSkyLightSources().getHighestLowestSourceY());
            result.append(" heightmaps=[");for(var entry:chunk.getHeightmaps())result.append(entry.getKey()).append('=').append(entry.getValue().getFirstAvailable(pos.getX()&15,pos.getZ()&15)).append(',');result.append(']');
            var high=new BlockPos(pos.getX(),chunk.getMinY()+chunk.getHeight()-1,pos.getZ());
            for(var check:List.of(pos.below(),pos,pos.above(),pos.above(8),high))result.append(" {at=").append(check).append(" state=").append(chunk.getBlockState(check)).append(" SKY=").append(level.getBrightness(net.minecraft.world.level.LightLayer.SKY,check)).append(" BLOCK=").append(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,check)).append('}');
            for(int y=pos.getY()+1;y<chunk.getMinY()+chunk.getHeight();y++){var check=new BlockPos(pos.getX(),y,pos.getZ());var state=chunk.getBlockState(check);if(!state.isAir()){result.append(" firstNonAirAbove=").append(check).append(':').append(state);break;}}
            var light=level.getLightEngine();var sky=light.getLayerListener(net.minecraft.world.level.LightLayer.SKY);
            result.append(" skyListenerClass=").append(sky.getClass().getName()).append(" nativeLightSections=[").append(light.getMinLightSection()).append(',').append(light.getMaxLightSection()).append(") layers=[");
            for(int sectionY=light.getMinLightSection();sectionY<light.getMaxLightSection();sectionY++){
                var section=SectionPos.of(pos.getX()>>4,sectionY,pos.getZ()>>4);var data=sky.getDataLayerData(section);
                result.append(" {Y=").append(sectionY).append(" type=").append(light.getDebugSectionType(net.minecraft.world.level.LightLayer.SKY,section)).append(" debug=").append(light.getDebugData(net.minecraft.world.level.LightLayer.SKY,section));
                int chunkSectionIndex=sectionY-chunk.getMinSectionY();var chunkSections=chunk.getSections();
                if(chunkSectionIndex>=0&&chunkSectionIndex<chunkSections.length)result.append(" chunkOnlyAir=").append(chunkSections[chunkSectionIndex].hasOnlyAir());
                if(data==null){result.append(" layer=null}");continue;}
                var checksum=new java.util.zip.CRC32();int min=15,max=0,nonzero=0;
                for(int dy=0;dy<16;dy++)for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){int value=data.get(dx,dy,dz);checksum.update(value);min=Math.min(min,value);max=Math.max(max,value);if(value!=0)nonzero++;}
                result.append(" layer={empty=").append(data.isEmpty()).append(" homogeneous=").append(data.isDefinitelyHomogenous()).append(" filled15=").append(data.isDefinitelyFilledWith(15)).append(" min=").append(min).append(" max=").append(max).append(" nonzero=").append(nonzero).append(" nibblesCRC32=").append(Long.toHexString(checksum.getValue())).append(" witnessY0/4/15=").append(data.get(pos.getX()&15,0,pos.getZ()&15)).append('/').append(data.get(pos.getX()&15,4,pos.getZ()&15)).append('/').append(data.get(pos.getX()&15,15,pos.getZ()&15)).append("}}");
            }
            result.append(']');
            result.append(" rawStorage={").append(NativeSkyStorageObservation.describe(level,pos)).append('}');
        }
        return result.toString();
    }
    private static void look(ClientGameTestContext context, Vec3 target) { var angles = context.computeOnClient(client -> { var d = target.subtract(client.player.getEyePosition()); return new Vec2((float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)))); }); context.getInput().lookAt(angles.x, angles.y); }
}
