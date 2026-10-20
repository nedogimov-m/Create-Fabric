package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.test.mixin.GameTestHelperNativeInfoAccessor;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Native consumers, real registry keys and actual resource reload; authored, runtime UNRUN. */
public final class StressApiGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos P=new BlockPos(6,5,6),L=P.above();
    private static final AtomicReference<Double> DIRECT_IMPACT=new AtomicReference<>(2d);
    private static final AtomicReference<Double> DIRECT_CAPACITY=new AtomicReference<>(8d);
    private static final DoubleSupplier LIVE_IMPACT=()->DIRECT_IMPACT.get();
    private static final DoubleSupplier LIVE_CAPACITY=()->DIRECT_CAPACITY.get();
    private static DoubleSupplier configuredImpactOverride,configuredCapacityOverride;
    private static final DoubleSupplier CONFIGURED_IMPACT=()->configuredImpactOverride==null
        ?AllConfigs.server().kinetics.stressValues.getImpact(AllBlocks.MILLSTONE).getAsDouble()
        :configuredImpactOverride.getAsDouble();
    private static final DoubleSupplier CONFIGURED_CAPACITY=()->configuredCapacityOverride==null
        ?AllConfigs.server().kinetics.stressValues.getCapacity(AllBlocks.CREATIVE_MOTOR).getAsDouble()
        :configuredCapacityOverride.getAsDouble();

    /** Baseline must fail as a native assertion, rather than crash with field linkage errors. */
    @SuppressWarnings("unchecked")
    private static SimpleRegistry<Block,DoubleSupplier> registry(GameTestHelper h,String name) {
        try {
            var field=BlockStressValues.class.getField(name);
            h.assertTrue(Modifier.isPublic(field.getModifiers())&&Modifier.isStatic(field.getModifiers())
                &&Modifier.isFinal(field.getModifiers())&&field.getType()==SimpleRegistry.class,
                "Source public static final SimpleRegistry contract exists for "+name);
            return (SimpleRegistry<Block,DoubleSupplier>)field.get(null);
        } catch(ReflectiveOperationException exception) {
            throw h.assertionException("Missing source public stress registry "+name+": "+exception);
        }
    }
    private static void direct(SimpleRegistry<Block,DoubleSupplier> registry,Block block,DoubleSupplier supplier) {
        // Repeated native attempts retain the same immutable registration and reset its live fixture value.
        if(registry.get(block)!=supplier)registry.register(block,supplier);
    }
    private static void value(GameTestHelper h,double actual,double expected,String message) {
        h.assertTrue(Math.abs(actual-expected)<.00001,message+"; actual="+actual+", expected="+expected);
    }
    private static final class SwitchingProvider implements SimpleRegistry.Provider<Block,DoubleSupplier>,AutoCloseable {
        private final Block key;
        private final DoubleSupplier supplier;
        private Runnable invalidator=()->{};
        private boolean enabled;
        SwitchingProvider(Block key,DoubleSupplier supplier,boolean enabled) {
            this.key=key;this.supplier=supplier;this.enabled=enabled;
        }
        @Override public DoubleSupplier get(Block block) { return enabled&&block==key?supplier:null; }
        @Override public void onRegister(Runnable invalidate) { invalidator=invalidate; }
        @Override public void close() { enabled=false;invalidator.run(); }
    }

    @GameTest(structure=STRUCTURE)
    public void publicDirectSuppliersBeatConfiguredProvidersAndStayLive(GameTestHelper h) {
        var impacts=registry(h,"IMPACTS");var capacities=registry(h,"CAPACITIES");
        var config=AllConfigs.server().kinetics.stressValues;
        h.assertTrue(impacts.get(AllBlocks.SHAFT)!=null,"Actual CStress provider publishes source-configured shaft entry");
        value(h,BlockStressValues.getImpact(AllBlocks.SHAFT),config.getImpact(AllBlocks.SHAFT).getAsDouble(),"Config provider is installed in actual public impact registry");
        var originalImpact=config.getImpact(AllBlocks.MILLSTONE);
        var originalCapacity=config.getCapacity(AllBlocks.CREATIVE_MOTOR);
        double baseImpact=originalImpact.getAsDouble(),baseCapacity=originalCapacity.getAsDouble();
        var impact=new AtomicReference<>(baseImpact+17.25);var capacity=new AtomicReference<>(baseCapacity+29.5);
        var rpm=BlockStressValues.RPM.get(AllBlocks.CREATIVE_MOTOR);
        try {
            configuredImpactOverride=()->impact.get();configuredCapacityOverride=()->capacity.get();
            direct(impacts,AllBlocks.MILLSTONE,CONFIGURED_IMPACT);
            direct(capacities,AllBlocks.CREATIVE_MOTOR,CONFIGURED_CAPACITY);
            value(h,BlockStressValues.getImpact(AllBlocks.MILLSTONE),baseImpact+17.25,"Explicit source registry supplier takes priority over real CStress impact");
            value(h,BlockStressValues.getCapacity(AllBlocks.CREATIVE_MOTOR),baseCapacity+29.5,"Explicit source registry supplier takes priority over real CStress capacity");
            impact.set(baseImpact+3.125);capacity.set(baseCapacity+5.75);
            value(h,BlockStressValues.getImpact(AllBlocks.MILLSTONE),baseImpact+3.125,"Cached supplier reads live impact without registry invalidation");
            value(h,BlockStressValues.getCapacity(AllBlocks.CREATIVE_MOTOR),baseCapacity+5.75,"Cached supplier reads live capacity without registry invalidation");
            value(h,originalImpact.getAsDouble(),baseImpact,"Addon registration does not rewrite configured impact");
            value(h,originalCapacity.getAsDouble(),baseCapacity,"Addon registration does not rewrite configured capacity");
            h.assertTrue(BlockStressValues.RPM.get(AllBlocks.CREATIVE_MOTOR)==rpm,"Stress API retains independent source tooltip RPM metadata");
            value(h,BlockStressValues.getImpact(Blocks.BEDROCK),0,"Absent impact remains zero");
            value(h,BlockStressValues.getCapacity(Blocks.BEDROCK),0,"Absent capacity remains zero");
        } finally { configuredImpactOverride=null;configuredCapacityOverride=null; }
        value(h,BlockStressValues.getImpact(AllBlocks.MILLSTONE),baseImpact,"Cleanup delegates directly to original live config");
        value(h,BlockStressValues.getCapacity(AllBlocks.CREATIVE_MOTOR),baseCapacity,"Cleanup preserves subsequent actual motor config checks");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE)
    public void providerSelectionCachesUntilItsCallbackWhileNativeConsumersReadLiveValues(GameTestHelper h) {
        var impacts=registry(h,"IMPACTS");var capacities=registry(h,"CAPACITIES");
        var load=StressApiTestFixtures.PROVIDER_LOAD;var source=StressApiTestFixtures.PROVIDER_SOURCE;
        BlockStressValues.registerImpact(load,3);BlockStressValues.registerCapacity(source,4);
        value(h,BlockStressValues.getImpact(load),3,"Actual native bootstrap default remains lowest-priority fallback");
        value(h,BlockStressValues.getCapacity(source),4,"Actual native capacity default remains lowest-priority fallback");
        var liveImpact=new AtomicReference<>(11d);var liveCapacity=new AtomicReference<>(23d);
        try(var oldImpact=new SwitchingProvider(load,()->7,true);
            var oldCapacity=new SwitchingProvider(source,()->15,true);
            var latestImpact=new SwitchingProvider(load,()->liveImpact.get(),false);
            var latestCapacity=new SwitchingProvider(source,()->liveCapacity.get(),false)) {
            impacts.registerProvider(oldImpact);capacities.registerProvider(oldCapacity);
            impacts.registerProvider(latestImpact);capacities.registerProvider(latestCapacity);
            h.setBlock(P,source);h.setBlock(L,load);
            var actualSource=h.getBlockEntity(P,StressApiTestFixtures.Source.class);
            var actualLoad=h.getBlockEntity(L,KineticBlockEntity.class);
            value(h,actualLoad.calculateStressApplied(),7,"Real addon block entity consumes provider rather than bootstrap fallback");
            value(h,actualSource.calculateAddedStressCapacity(),15,"Real addon generator consumes capacity provider");
            latestImpact.enabled=true;latestCapacity.enabled=true;
            value(h,actualLoad.calculateStressApplied(),7,"Provider selection remains cached before source callback");
            value(h,actualSource.calculateAddedStressCapacity(),15,"Capacity selection remains cached before source callback");
            latestImpact.invalidator.run();latestCapacity.invalidator.run();
            value(h,actualLoad.calculateStressApplied(),11,"Registered provider callback exposes newest matching impact provider");
            value(h,actualSource.calculateAddedStressCapacity(),23,"Registered provider callback exposes newest capacity provider");
            liveImpact.set(13.5);liveCapacity.set(31.25);
            value(h,actualLoad.calculateStressApplied(),13.5,"Actual consumer evaluates retained live impact supplier");
            value(h,actualSource.calculateAddedStressCapacity(),31.25,"Actual generator evaluates retained live capacity supplier");
        } finally { h.setBlock(L,Blocks.AIR);h.setBlock(P,Blocks.AIR); }
        value(h,BlockStressValues.getImpact(load),3,"Provider cleanup invalidates selection and restores real native fallback");
        value(h,BlockStressValues.getCapacity(source),4,"Capacity provider cleanup restores real fallback");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE)
    public void liveAddonSuppliersStopAndResumeTheActualConnectedStressGraphViaSourceUpdates(GameTestHelper h) {
        var impacts=registry(h,"IMPACTS");var capacities=registry(h,"CAPACITIES");
        DIRECT_IMPACT.set(2d);DIRECT_CAPACITY.set(8d);
        direct(impacts,StressApiTestFixtures.LOAD,LIVE_IMPACT);
        direct(capacities,StressApiTestFixtures.SOURCE,LIVE_CAPACITY);
        var disable=AllConfigs.server().kinetics.disableStress;boolean previous=disable.getServer();
        try {
            disable.set(false);
            h.setBlock(P,StressApiTestFixtures.SOURCE);h.setBlock(L,StressApiTestFixtures.LOAD);
            var source=h.getBlockEntity(P,StressApiTestFixtures.Source.class);
            var load=h.getBlockEntity(L,KineticBlockEntity.class);
            KineticNetworks.track(h.getLevel(),source);KineticNetworks.track(h.getLevel(),load);KineticNetworks.tick(h.getLevel());
            var graph=source.getOrCreateNetwork();
            h.assertTrue(graph==load.getOrCreateNetwork()&&graph.members.containsKey(load)&&graph.sources.containsKey(source),"Actual registered addon source and receiver share genuine canonical graph membership");
            value(h,load.getSpeed(),16,"Actual 16 RPM addon generator drives connected real receiver");
            value(h,load.getStressCapacity(),128,"Real graph multiplies supplier base capacity by actual generator RPM");
            value(h,load.getStressImpact(),32,"Real graph multiplies live impact by actual theoretical RPM");
            DIRECT_CAPACITY.set(0d);
            graph.updateCapacityFor(source,source.calculateAddedStressCapacity());
            h.assertTrue(load.isOverstressed()&&load.getSpeed()==0,"Source-supported graph capacity update stops actual powered receiver");
            value(h,load.getStressImpact(),32,"Stopped receiver retains genuine diagnostic load");
            DIRECT_CAPACITY.set(8d);graph.updateCapacityFor(source,source.calculateAddedStressCapacity());
            h.assertTrue(!load.isOverstressed()&&load.getSpeed()==16,"Same loaded receiver resumes after live capacity restoration");
            DIRECT_IMPACT.set(16d);graph.updateStressFor(load,load.calculateStressApplied());
            h.assertTrue(load.isOverstressed()&&load.getSpeed()==0,"Source-supported impact update overloads actual existing graph");
            value(h,load.getStressImpact(),256,"Stopped graph reports new live supplier impact at source RPM");
            DIRECT_IMPACT.set(1.5);graph.updateStressFor(load,load.calculateStressApplied());
            h.assertTrue(!load.isOverstressed()&&load.getSpeed()==16,"Same source and receiver resume with lowered live addon impact");
            value(h,load.getStressImpact(),24,"Actual graph retains precise fractional per-RPM supplier contribution");
        } finally {
            h.setBlock(L,Blocks.AIR);h.setBlock(P,Blocks.AIR);
            DIRECT_IMPACT.set(2d);DIRECT_CAPACITY.set(8d);disable.set(previous);
        }
        h.succeed();
    }

    @GameTest(structure=STRUCTURE,maxTicks=400)
    public void actualServerTagReloadInvalidatesStressTagProviderMissForRealAddonMembership(GameTestHelper h) {
        var impacts=registry(h,"IMPACTS");
        var tag=TagKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test","stress_api_loads"));
        var load=StressApiTestFixtures.TAG_LOAD;
        h.assertTrue(load.builtInRegistryHolder().is(tag),"Actual native datapack loader supplied independent addon block tag membership");
        var selected=new AtomicBoolean(false);
        var live=new AtomicReference<>(9.5);
        var tagged=SimpleRegistry.Provider.forBlockTag(tag,(DoubleSupplier)()->live.get());
        var invalidator=new AtomicReference<Runnable>(()->{});
        impacts.registerProvider(new SimpleRegistry.Provider<Block,DoubleSupplier>() {
            @Override public DoubleSupplier get(Block block) { return selected.get()?tagged.get(block):null; }
            @Override public void onRegister(Runnable invalidate) { invalidator.set(invalidate);tagged.onRegister(invalidate); }
        });
        var closed=new AtomicBoolean();
        Runnable cleanup=()->{if(closed.compareAndSet(false,true)){selected.set(false);invalidator.get().run();h.setBlock(P,Blocks.AIR);}};
        ((GameTestHelperNativeInfoAccessor)(Object)h).create$getActualNativeTestInfo().addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo info) {}
            public void testPassed(GameTestInfo info,GameTestRunner runner) { cleanup.run(); }
            public void testFailed(GameTestInfo info,GameTestRunner runner) { cleanup.run(); }
            public void testAddedForRerun(GameTestInfo old,GameTestInfo next,GameTestRunner runner) { cleanup.run(); }
        });
        h.setBlock(P,load);
        var actual=h.getBlockEntity(P,KineticBlockEntity.class);
        value(h,actual.calculateStressApplied(),0,"Actual public stress registry caches miss while tag provider is gated");
        selected.set(true);
        value(h,actual.calculateStressApplied(),0,"Changing provider selection alone does not bypass source cache contract");
        var server=h.getLevel().getServer();
        var reload=server.reloadResources(server.getPackRepository().getSelectedIds());
        h.startSequence().thenWaitUntil(()->h.assertTrue(reload.isDone(),"Genuine native resource reload has completed"))
            .thenExecute(()->{
                h.assertTrue(!reload.isCompletedExceptionally(),"Actual server tag/resource reload succeeds");
                h.assertTrue(load.builtInRegistryHolder().is(tag),"Actual reloaded native tag still contains genuine registered addon key");
                value(h,actual.calculateStressApplied(),9.5,"Real TAGS_LOADED callback invalidates stress-provider cached miss");
                live.set(12.25);
                value(h,actual.calculateStressApplied(),12.25,"Reloaded tag provider retains live supplier semantics for actual native block entity");
                cleanup.run();
            }).thenSucceed();
    }
}
