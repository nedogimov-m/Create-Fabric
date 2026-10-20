package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.kinetics.fan.AirFlowParticle;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.foundation.ui.PonderViewport;
import net.createmod.ponder.test.PonderRegistryRenderAudit;
import net.createmod.ponder.test.mixin.PonderSceneScheduleAuditAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import static com.simibubi.create.test.PonderFanAirFlowAudit.require;

/** Failed-processing-only revision; retain direction PASS at170 separately. Natural ticks/assertions unchanged. UNRUN. */
public final class PonderFanAirFlowLocalTest implements FabricClientGameTest {
    private static final int MAX_TICKS=1500;
    private static final List<String> SCHEMATICS=List.of("create:fan/processing");
    private static final Path REPORT=Path.of(System.getProperty("create.test.ponderFanLocalReport","build/reports/create-ponder/fan-local-runtime.json")).toAbsolutePath();
    private static final class Result {
        String schematic,title,stage="COMPILE",status="STARTED",failure;
        int executedTicks,hostSnapshotChecks,sourceOwnerCount,naturalFlowOwnerCount,nativeAirFlowQuads,nativeEngineVertices;
        boolean sourceScheduleDrained,sourceFinished,hostExactQueuesAndFootprintUnchanged,replayOwnersRetired,replayQueuesCleared,freshSourceUsed;
        Map<String,Integer> morphed=new LinkedHashMap<>(),extraCalls=new LinkedHashMap<>(),extraCreated=new LinkedHashMap<>();
        Set<String> actualBuiltinStages=new LinkedHashSet<>();
        List<Map<String,Object>> immediateExtraReceipts=List.of();
        List<Map<String,Object>> boundedStageClockTrace=new ArrayList<>();
        String lastTraceCatalyst;
        List<PonderRegistryRenderAudit.FrameReceipt> frames=new ArrayList<>();
        String screenshot;
    }
    private static final class Report {String status="RUNNING",backend,failure;boolean sodiumLoaded;int selectedBindings;List<Result> bindings=new ArrayList<>();}
    private record SavedOwner(BlockEntity identity,CompoundTag data){}
    private record Initial(Map<BlockPos,BlockState> blocks,Map<BlockPos,SavedOwner> owners){
        static Initial capture(PonderLevel world){var owners=new HashMap<BlockPos,SavedOwner>();for(var owner:PonderFanAirFlowLocalTest.owners(world))owners.put(owner.getBlockPos(),new SavedOwner(owner,owner.saveWithFullMetadata(world.registryAccess()).copy()));return new Initial(Map.copyOf(world.getBlockMap()),Map.copyOf(owners));}
        void assertRestored(PonderLevel world){
            require(blocks.equals(world.getBlockMap()),"REPLAY: exact original source block map must return");var actual=Initial.capture(world);require(actual.owners.keySet().equals(owners.keySet()),"REPLAY: exact original BE positions return");
            for(var entry:owners.entrySet()){var next=actual.owners.get(entry.getKey());require(next.identity!=entry.getValue().identity&&next.data.equals(entry.getValue().data),"REPLAY: fresh source owner retains actual serialized data at "+entry.getKey());}
        }
    }
    private record Session(PonderScene scene,PonderUI ui,Initial initial,PonderFanAirFlowAudit.Session probe,Result result){}
    private record Host(Map<BlockPos,BlockState> blocks,Map<BlockPos,SavedOwner> owners,PonderFanAirFlowAudit.EngineSnapshot engine){
        static Host capture(Minecraft client,PonderLevel scene){
            var blocks=new HashMap<BlockPos,BlockState>();var owners=new HashMap<BlockPos,SavedOwner>();var bounds=scene.getBounds();
            for(int x=bounds.minX()-2;x<=bounds.maxX()+2;x++)for(int y=bounds.minY()-2;y<=bounds.maxY()+2;y++)for(int z=bounds.minZ()-2;z<=bounds.maxZ()+2;z++){
                var pos=new BlockPos(x,y,z);blocks.put(pos,client.level.getBlockState(pos));var owner=client.level.getBlockEntity(pos);if(owner!=null)owners.put(pos,new SavedOwner(owner,owner.saveWithFullMetadata(client.level.registryAccess()).copy()));
            }
            return new Host(Map.copyOf(blocks),Map.copyOf(owners),PonderFanAirFlowAudit.snapshot(client.particleEngine));
        }
        void assertUnchanged(Minecraft client,String stage){
            for(var entry:blocks.entrySet()){
                require(client.level.getBlockState(entry.getKey())==entry.getValue(),stage+": coincident host block changed");
                var old=owners.get(entry.getKey());var now=client.level.getBlockEntity(entry.getKey());
                require(old==null?now==null:now==old.identity&&old.data.equals(now.saveWithFullMetadata(client.level.registryAccess())),stage+": host BE identity/data changed");
            }
            engine.assertUnchanged(stage);
        }
    }
    @Override public void runTest(ClientGameTestContext context){
        var report=new Report();var density=new AtomicReference<Double>();
        try(var joined=context.worldBuilder().create()){
            context.runOnClient(client->{report.backend=RenderSystem.getDevice().getDeviceInfo().backendName();report.sodiumLoaded=FabricLoader.getInstance().isModLoaded("sodium");require(report.backend.toLowerCase(Locale.ROOT).contains("vulkan")&&!report.sodiumLoaded,"Native Vulkan and no Sodium required");density.set(AllConfigs.client().fanParticleDensity.get());AllConfigs.client().fanParticleDensity.set(1d);});
            var selected=context.computeOnClient(client->{
                var result=new ArrayList<StoryBoardEntry>();var entries=PonderIndex.getSceneAccess().getRegisteredEntries();
                for(var key:SCHEMATICS){var exact=java.util.stream.StreamSupport.stream(entries.spliterator(),false).map(Map.Entry::getValue).filter(e->e.getComponent().toString().equals("create:encased_fan")&&e.getSchematicLocation().toString().equals(key)).toList();require(exact.size()==1,"EXACT_REGISTRY_SELECTOR: expected precisely one "+key+", got "+exact.size());result.add(exact.getFirst());}
                require(result.size()==1,"Only the failed processing source entry may compile");return List.copyOf(result);
            });report.selectedBindings=selected.size();write(report);
            for(var entry:selected){
                var result=new Result();result.schematic=entry.getSchematicLocation().toString();report.bindings.add(result);var active=new AtomicReference<Session>();
                try{
                    var session=context.computeOnClient(client->{
                        var compiled=PonderIndex.getSceneAccess().compile(List.of(entry));require(compiled.size()==1,"COMPILE: one exact real entry creates one source scene");var scene=compiled.getFirst();
                        require(scene.getLocation().equals(entry.getComponent()),"COMPILE: actual source component retained");result.title=scene.getId().toString();require(result.title.equals(result.schematic.endsWith("direction")?"create:fan_direction":"create:fan_processing"),"COMPILE: exact registered source program title required");
                        var ui=new PonderUI(List.of(scene),null);client.gui.setScreen(ui);ui.setFocused(null);require(ui.keyPressed(new KeyEvent(InputConstants.KEY_SPACE,0,0)),"PAUSE: real Ponder UI accepts native space event");
                        var probe=new PonderFanAirFlowAudit.Session(scene.getWorld());PonderFanAirFlowAudit.begin(probe);return new Session(scene,ui,Initial.capture(scene.getWorld()),probe,result);
                    });active.set(session);
                    int pausedAt=context.computeOnClient(client->session.scene.getCurrentTime());context.waitTicks(2);require(context.computeOnClient(client->client.gui.screen()==session.ui&&session.scene.getCurrentTime()==pausedAt),"PAUSE: scene time stays fixed while actual native frames continue");
                    result.stage="NATURAL_LOCAL_SOURCE";context.runOnClient(client->advance(client,session,true));capture(context,session);
                    if(result.schematic.endsWith("processing"))result.screenshot=context.takeScreenshot("create-vulkan-ponder-local-fan-builtin-airflow-and-native-pip").toAbsolutePath().toString();
                    result.stage="FULL_SOURCE_PROGRAM";context.runOnClient(client->advance(client,session,false));
                    context.runOnClient(client->{
                        require(!scheduled(session.scene)&&session.scene.isFinished(),"SOURCE_FINISH: exact source scheduler drains and source finishes within1500 total ticks");result.sourceScheduleDrained=true;result.sourceFinished=true;
                        require(session.probe.actualFlowQuads>0,"NATURAL_QUADS: source-owned naturally emitted airflow must extract native quad vertices");
                        if(result.schematic.endsWith("processing"))for(var type:List.<FanProcessingType>of(AllFanProcessingTypes.BLASTING,AllFanProcessingTypes.SMOKING,AllFanProcessingTypes.SPLASHING)){
                            var key=PonderFanAirFlowAudit.type(type);require(result.actualBuiltinStages.contains(key),"BUILTIN_STAGE: genuine catalyst/stream stage missing "+key);require(session.probe.morphed.getOrDefault(key,0)>0,"BUILTIN_MORPH: actual natural native vertices missing registered type color/alpha "+key);require(session.probe.extraCreated.getOrDefault(key,0)>0,"BUILTIN_EXTRA: no immediate actual local native particle append observed for "+key);
                        }
                    });
                    result.stage="REPLAY";context.runOnClient(client->replay(client,session));capture(context,session);
                    result.status="PASS";result.stage="COMPLETE";
                }catch(Throwable failure){result.status="FAIL";result.failure=failure.toString();throw failure instanceof AssertionError a?a:new AssertionError("Focused Ponder fan stage "+result.stage+" failed",failure);}
                finally{
                    context.runOnClient(client->{var session=active.get();if(session!=null){copyProbe(session);PonderFanAirFlowAudit.service(session.scene.getWorld()).invalidateResources();for(var owner:owners(session.scene.getWorld()))owner.setRemoved();for(var entity:List.copyOf(session.scene.getWorld().getEntityList()))entity.discard();}PonderFanAirFlowAudit.clear();PonderRegistryRenderAudit.clear();client.gui.setScreen(null);});write(report);
                }
            }
            require(report.bindings.size()==1&&report.bindings.stream().allMatch(r->r.status.equals("PASS")),"Only failed processing is rerun; direction PASS170 is retained separately");report.status="PASS";
        }catch(Throwable failure){report.status="FAIL";report.failure=failure.toString();throw failure instanceof AssertionError a?a:new AssertionError("Focused Ponder local fan test failed",failure);}
        finally{context.runOnClient(client->{PonderFanAirFlowAudit.clear();PonderRegistryRenderAudit.clear();if(density.get()!=null)AllConfigs.client().fanParticleDensity.set(density.get());});write(report);}
    }
    private static void advance(Minecraft client,Session session,boolean presentation){
        var host=Host.capture(client,session.scene.getWorld());
        try{
            while(scheduled(session.scene)){
                require(session.result.executedTicks<MAX_TICKS,"TICK_BOUND: natural scene.tick exceeded1500 total per exact selected entry");session.scene.tick();session.result.executedTicks++;
                PonderFanAirFlowAudit.observeNatural(session.probe,client.gameRenderer.mainCamera());observeBuiltinStage(session);
                if(presentation&&session.probe.actualFlowQuads>0&&(!session.result.schematic.endsWith("processing")||session.probe.extraCreated.getOrDefault(PonderFanAirFlowAudit.type(AllFanProcessingTypes.BLASTING),0)>0))return;
            }
            require(!presentation,"PRESENTATION: source program ended before natural local airflow/native builtin morph was observed");
        }finally{host.assertUnchanged(client,session.result.stage);session.result.hostSnapshotChecks++;session.result.hostExactQueuesAndFootprintUnchanged=true;copyProbe(session);}
    }
    private static void observeBuiltinStage(Session session){
        if(!session.result.schematic.endsWith("processing"))return;
        var world=session.scene.getWorld();var pos=new BlockPos(4,1,2);var state=world.getBlockState(pos);
        String catalyst=state.toString();
        if(session.result.boundedStageClockTrace.size()<40&&(!catalyst.equals(session.result.lastTraceCatalyst)||session.result.executedTicks%50==0)){
            var fans=new ArrayList<Map<String,Object>>();
            for(var owner:owners(world))if(owner instanceof EncasedFanBlockEntity fan){
                var segments=new ArrayList<String>();
                for(int distance=1;distance<=Math.ceil(fan.getAirCurrent().maxDistance);distance++)segments.add(PonderFanAirFlowAudit.type(fan.getAirCurrent().getTypeAt(distance)));
                fans.add(Map.of("pos",fan.getBlockPos().toString(),"identity",System.identityHashCode(fan),"virtual",fan.isVirtual(),"removed",fan.isRemoved(),"speed",fan.getSpeed(),"facing",fan.getAirflowOriginSide().toString(),"range",fan.getAirCurrent().maxDistance,"types",segments));
            }
            session.result.boundedStageClockTrace.add(Map.of("sceneTime",session.scene.getCurrentTime(),"executedTicks",session.result.executedTicks,"worldGameTime",world.getGameTime(),"hostGameTime",Minecraft.getInstance().level.getGameTime(),"virtualGameTime",world.virtualGameTime(),"checkRate",AllConfigs.server().kinetics.fanBlockCheckRate.get(),"catalyst",catalyst,"fans",fans));
            session.result.lastTraceCatalyst=catalyst;
        }
        FanProcessingType expected=state.is(Blocks.LAVA)?AllFanProcessingTypes.BLASTING:state.is(Blocks.FIRE)?AllFanProcessingTypes.SMOKING:state.is(Blocks.WATER)?AllFanProcessingTypes.SPLASHING:null;
        if(expected==null)return;
        require(FanProcessingType.getAt(world,pos)==expected,"BUILTIN_CATALYST: actual source lava/fire/water resolves actual registered builtin");
        for(var owner:owners(world))if(owner instanceof EncasedFanBlockEntity fan&&fan.getSpeed()!=0)for(int distance=1;distance<=Math.ceil(fan.getAirCurrent().maxDistance);distance++)if(fan.getAirCurrent().getTypeAt(distance)==expected)session.result.actualBuiltinStages.add(PonderFanAirFlowAudit.type(expected));
    }
    private static void replay(Minecraft client,Session session){
        var host=Host.capture(client,session.scene.getWorld());
        try{
            var oldOwners=owners(session.scene.getWorld());var oldSources=Collections.newSetFromMap(new IdentityHashMap<IAirCurrentSource,Boolean>());oldSources.addAll(session.probe.sources);
            var oldFlows=Collections.newSetFromMap(new IdentityHashMap<AirFlowParticle,Boolean>());oldFlows.addAll(session.probe.flows);
            session.ui.seekToTime(0);require(session.scene.getCurrentTime()==0,"REPLAY: actual UI backward seek returns exact source time0");session.initial.assertRestored(session.scene.getWorld());
            for(var owner:oldOwners)require(owner.isRemoved(),"REPLAY: every previous virtual BE owner is retired");for(var source:oldSources)require(source.isSourceRemoved(),"REPLAY: every previously observed airflow source is retired");session.result.replayOwnersRetired=true;
            require(PonderFanAirFlowAudit.particles(session.scene.getWorld()).isEmpty(),"REPLAY: actual local particle groups AND pending queue must clear");session.result.replayQueuesCleared=true;
            for(int tick=0;tick<120&&!session.result.freshSourceUsed;tick++){
                require(session.result.executedTicks<MAX_TICKS,"REPLAY_TICK_BOUND: combined natural ticks exceeded1500");session.scene.tick();session.result.executedTicks++;PonderFanAirFlowAudit.observeNatural(session.probe,client.gameRenderer.mainCamera());
                for(var particle:PonderFanAirFlowAudit.particles(session.scene.getWorld()))if(particle instanceof AirFlowParticle flow&&flow.isAlive()){
                    var source=PonderFanAirFlowAudit.source(flow);require(!oldFlows.contains(flow)&&!oldSources.contains(source),"REPLAY: fresh native particle cannot retain previous source/particle identity");if(PonderFanAirFlowAudit.number(flow,"age")>0)session.result.freshSourceUsed=true;
                }
            }
            require(session.result.freshSourceUsed,"REPLAY: fresh current virtual fan must naturally create and tick source-aware airflow");
        }finally{host.assertUnchanged(client,"REPLAY");session.result.hostSnapshotChecks++;copyProbe(session);}
    }
    private static void capture(ClientGameTestContext context,Session session){
        var sample=context.computeOnClient(client->PonderRegistryRenderAudit.begin(session.scene));context.waitFor(client->sample.failure()!=null||sample.completed()!=null,200);
        require(sample.failure()==null,"PIP: actual source renderer failed: "+sample.failure());var receipt=sample.completed();require(receipt!=null&&receipt.nativeSubmissionReturned()&&receipt.nativeBlitReturned(),"PIP: actual native extraction/submission/blit must complete");session.result.frames.add(receipt);
        context.runOnClient(client->{
            int vertices=0;var states=(Map<?,?>)PonderFanAirFlowAudit.read(sample,PonderRegistryRenderAudit.Sample.class,"observations");
            for(var entry:states.entrySet())if(entry.getKey() instanceof PonderViewport.State state){var observation=entry.getValue();if(Boolean.TRUE.equals(PonderFanAirFlowAudit.read(observation,observation.getClass(),"submitted"))&&Boolean.TRUE.equals(PonderFanAirFlowAudit.read(observation,observation.getClass(),"blitted")))vertices=Math.max(vertices,PonderFanAirFlowAudit.capturedQuadVertices(state.frame().particles()));}
            require(vertices>0,"PIP_LOCAL_ENGINE: actual PonderWorld.extractParticles/native engine extraction must capture real quad vertices");session.probe.actualEngineQuadVertices+=vertices;copyProbe(session);PonderRegistryRenderAudit.clear();
        });
    }
    private static void copyProbe(Session session){var p=session.probe;var r=session.result;r.sourceOwnerCount=p.sources.size();r.naturalFlowOwnerCount=p.flows.size();r.nativeAirFlowQuads=p.actualFlowQuads;r.nativeEngineVertices=p.actualEngineQuadVertices;r.morphed=new LinkedHashMap<>(p.morphed);r.extraCalls=new LinkedHashMap<>(p.extraCalls);r.extraCreated=new LinkedHashMap<>(p.extraCreated);r.immediateExtraReceipts=List.copyOf(p.extraReceipts);}
    private static List<BlockEntity> owners(PonderLevel world){var out=new ArrayList<BlockEntity>();world.getBlockEntities().forEach(out::add);return out;}
    private static boolean scheduled(PonderScene scene){return !((PonderSceneScheduleAuditAccessor)scene).create$actualActiveSchedule().isEmpty();}
    private static void write(Report report){try{Files.createDirectories(REPORT.getParent());Files.writeString(REPORT,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}}
}
