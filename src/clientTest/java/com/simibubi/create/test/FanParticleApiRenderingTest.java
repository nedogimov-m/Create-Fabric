package com.simibubi.create.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.AirFlowParticle;
import com.simibubi.create.content.kinetics.fan.AirFlowParticleData;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** One focused native Vulkan source callback/factory/extraction case. Authored; runtime UNRUN. */
public final class FanParticleApiRenderingTest implements FabricClientGameTest {
    private static final BlockPos FAN=new BlockPos(16,5,16),CATALYST=FAN.east(),ITEM=FAN.east(2);
    private static final String INPUT_NAME="Original native fan particle callback cargo";

    @Override public void runTest(ClientGameTestContext context) {
        var density=new AtomicReference<Double>();
        try {
        context.runOnClient(client->{
            require(RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(Locale.ROOT).contains("vulkan"),"Actual focused fan particle test must use native Vulkan backend");
            FanParticleApiTestFixtures.reset();FanParticleApiTestFixtures.ACTIVE.set(true);
            density.set(AllConfigs.client().fanParticleDensity.get());AllConfigs.client().fanParticleDensity.set(1d);
            require(CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getValue(FanParticleApiTestFixtures.ID)==FanParticleApiTestFixtures.TYPE,"Actual addon fan callback type must be present in native public registry");
            require(BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.fromNamespaceAndPath("create","air_flow"))==AllParticleTypes.AIR_FLOW,"Actual common native registry must contain the source airflow payload type");
        });
        try(var world=context.worldBuilder().create()) {
            world.getServer().runCommand("fill 12 4 12 25 4 22 minecraft:smooth_quartz");
            world.getServer().runCommand("tp @p 20.5 5 21.5");
            context.waitFor(client->client.level!=null&&client.level.isLoaded(FAN)&&client.level.isLoaded(FAN.east(7))&&client.player.onGround(),400);
            world.getServer().runCommand("setblock 15 5 16 create:creative_motor[facing=east]");
            world.getServer().runCommand("setblock 16 5 16 create:encased_fan[facing=east]");
            world.getServer().runCommand("setblock 17 5 16 minecraft:redstone_torch");
            context.waitFor(client->client.level.getBlockEntity(FAN) instanceof EncasedFanBlockEntity fan&&fan.getSpeed()!=0
                &&fan.getAirCurrent().isPushing()&&fan.getAirCurrent().maxDistance>3
                &&fan.getAirCurrent().getTypeAt(2)==FanParticleApiTestFixtures.TYPE,250);
            context.runOnClient(client->{
                assertClientCookingRecipeSync(client);
                assertClientCreateFanRecipeSync(client);
            });
            world.getServer().runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();
                var stock=new ItemStack(Items.STRUCTURE_VOID,3);stock.set(DataComponents.CUSTOM_NAME,Component.literal(INPUT_NAME));
                var item=new ItemEntity(level,ITEM.getX()+.5,ITEM.getY()+.5,ITEM.getZ()+.5,stock);
                item.setNoGravity(true);item.setNeverPickUp();level.addFreshEntity(item);
            });
            context.waitFor(client->FanParticleApiTestFixtures.MORPH_CALLS.get()>0&&FanParticleApiTestFixtures.PROCESSING_PARTICLE_CALLS.get()>0,180);
            context.waitFor(client->particles(client).stream().anyMatch(particle->particle instanceof AirFlowParticle&&particle.isAlive()),80);
            context.runOnClient(client->{
                require(client.level.getBlockState(CATALYST).is(Blocks.REDSTONE_TORCH),"Actual source catalyst remains in original native stream");
                var source=(EncasedFanBlockEntity)client.level.getBlockEntity(FAN);
                require(source.getAirCurrent().getTypeAt(2)==FanParticleApiTestFixtures.TYPE,"Real client airflow segment dispatches registered addon type");
                long extraBefore=count(particles(client),"EndRodParticle");int callbacksBefore=FanParticleApiTestFixtures.MORPH_CALLS.get();
                var position=Vec3.atCenterOf(ITEM);
                var json=AirFlowParticleData.CODEC.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE,new AirFlowParticleData(FAN)).getOrThrow();
                require(json.getAsJsonObject().get("x").getAsInt()==FAN.getX()&&json.getAsJsonObject().get("y").getAsInt()==FAN.getY()
                    &&json.getAsJsonObject().get("z").getAsInt()==FAN.getZ(),"Native data codec retains all three original source block coordinates");
                var fromJson=AirFlowParticleData.CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
                var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),client.level.registryAccess());
                AirFlowParticleData fromWire;
                try {
                    AirFlowParticleData.STREAM_CODEC.encode(buffer,fromJson);
                    require(buffer.readableBytes()==12,"Source airflow native wire contract uses exactly three four-byte coordinates");
                    fromWire=AirFlowParticleData.STREAM_CODEC.decode(buffer);
                    require(buffer.readableBytes()==0,"Source airflow native decoder consumes the complete payload once");
                } finally { buffer.release(); }
                var created=client.particleEngine.createParticle(fromWire,position.x,position.y,position.z,0,0,0);
                require(created instanceof AirFlowParticle,"Actual registered native ParticleEngine factory creates source-aware AirFlowParticle");
                var airflow=(AirFlowParticle)created;
                require(airflow.getLifetime()==40,"Source airflow retains its real finite forty-tick lifetime");
                airflow.tick();
                require(airflow.isAlive()&&FanParticleApiTestFixtures.MORPH_CALLS.get()==callbacksBefore+1,"Actual source-aware particle tick invokes addon morphAirFlow exactly once");
                require(count(particles(client),"EndRodParticle")==extraBefore+1,"Source spawnExtraParticle adapter emits actual native particle through ClientLevel into host engine");
                var state=new QuadParticleRenderState();airflow.extract(state,client.gameRenderer.mainCamera(),.5f);
                require(!state.isEmpty()&&state.layers().contains(SingleQuadParticle.Layer.TRANSLUCENT),"Actual source airflow extracts native translucent quad render state");
                var vertices=new Vertices();state.buildLayer(SingleQuadParticle.Layer.TRANSLUCENT,vertices);
                require(vertices.positions.size()==4&&vertices.colors.size()==4,"Native extraction builds exactly one real camera-facing quad");
                for(var color:vertices.colors)require((color&0xFFFFFF)==FanParticleApiTestFixtures.COLOR&&Math.abs((color>>>24)-159)<=1,"Real native vertices carry addon callback RGB and alpha, rather than default gray source values");
                var sprite=sprite(airflow).contents().name();
                require(sprite.getNamespace().equals("minecraft")&&sprite.getPath().contains("generic_"),"Registered native factory resolves actual source eight-sprite resource set");
                require(particles(client).stream().anyMatch(particle->particle.getClass().getSimpleName().equals("HeartParticle")),"Client common item-processing path emits actual native processing callback particles");
                System.out.println("CREATE_FAN_PARTICLE_NATIVE_CALLBACKS morph="+FanParticleApiTestFixtures.MORPH_CALLS.get()+" processing="+FanParticleApiTestFixtures.PROCESSING_PARTICLE_CALLS.get()+" sprite="+sprite+" vertices="+vertices.positions.size());
            });
            context.getInput().lookAt(ITEM);world.getConnection().waitForChunksRender();context.waitTicks(2);
            context.takeScreenshot("create-vulkan-fan-addon-native-airflow-morph-and-processing-particles");
            context.runOnClient(client->{
                var position=Vec3.atCenterOf(ITEM);
                var stale=client.particleEngine.createParticle(new AirFlowParticleData(FAN.above(12)),position.x,position.y,position.z,0,0,0);
                require(stale instanceof AirFlowParticle,"Native factory tolerates missing source using the source finite cleanup path");
                stale.tick();require(!stale.isAlive(),"Missing native source cannot leave a persistent airflow particle");
            });
        }
        } finally {
            context.runOnClient(client->{FanParticleApiTestFixtures.reset();if(density.get()!=null)AllConfigs.client().fanParticleDensity.set(density.get());});
        }
    }

    /** Full synchronized recipe maps are checked before the public builtin API, so burn fallback cannot hide missing cooking data. */
    private static void assertClientCookingRecipeSync(Minecraft client) {
        var level=client.level;var recipes=level.recipeAccess().getSynchronizedRecipes();
        var cod=new ItemStack(Items.COD);var iron=new ItemStack(Items.RAW_IRON);var zinc=new ItemStack(AllItems.RAW_ZINC);
        FanProcessingType smoking=AllFanProcessingTypes.SMOKING,blasting=AllFanProcessingTypes.BLASTING;
        require(recipes.getFirstMatch(RecipeType.SMOKING,new SingleRecipeInput(cod),level).isPresent()
            &&recipes.getFirstMatch(RecipeType.SMELTING,new SingleRecipeInput(iron),level).isPresent()
            &&recipes.getFirstMatch(RecipeType.BLASTING,new SingleRecipeInput(iron),level).isPresent()
            &&recipes.getFirstMatch(RecipeType.SMELTING,new SingleRecipeInput(zinc),level).isPresent()
            &&recipes.getFirstMatch(RecipeType.BLASTING,new SingleRecipeInput(zinc),level).isPresent()
            &&smoking.canProcess(cod,level)&&blasting.canProcess(iron,level)&&blasting.canProcess(zinc,level),
            "Actual native CLIENT full cooking recipe sync contains smoking cod and smelting/blasting raw iron/raw zinc, and public fan builtins consume those genuine matching recipes");
    }
    private static void assertClientCreateFanRecipeSync(Minecraft client) {
        var level=client.level;var recipes=level.recipeAccess().getSynchronizedRecipes();
        var flour=new ItemStack(AllItems.WHEAT_FLOUR);var sand=new ItemStack(Items.SAND);
        FanProcessingType splashing=AllFanProcessingTypes.SPLASHING,haunting=AllFanProcessingTypes.HAUNTING;
        require(recipes.getFirstMatch(AllRecipeTypes.SPLASHING,new SingleRecipeInput(flour),level).isPresent()
            &&recipes.getFirstMatch(AllRecipeTypes.HAUNTING,new SingleRecipeInput(sand),level).isPresent()
            &&splashing.canProcess(flour,level)&&haunting.canProcess(sand,level),
            "Actual native CLIENT full Create recipe sync contains splashing wheat flour and haunting sand, and source public fan builtin canProcess reads those actual synchronized matches");
    }

    private static long count(List<Particle> particles,String name) { return particles.stream().filter(particle->particle.getClass().getSimpleName().equals(name)).count(); }
    private static List<Particle> particles(Minecraft client) {
        try {
            var result=new ArrayList<Particle>();
            var groups=ParticleEngine.class.getDeclaredField("particles");groups.setAccessible(true);
            var entries=ParticleGroup.class.getDeclaredField("particles");entries.setAccessible(true);
            for(var group:((Map<?,?>)groups.get(client.particleEngine)).values())for(var entry:(Collection<?>)entries.get(group))result.add((Particle)entry);
            var pending=ParticleEngine.class.getDeclaredField("particlesToAdd");pending.setAccessible(true);
            for(var entry:(Collection<?>)pending.get(client.particleEngine))result.add((Particle)entry);
            return result;
        } catch(ReflectiveOperationException failure) { throw new AssertionError("Actual native particle engine queues must be inspectable",failure); }
    }
    private static TextureAtlasSprite sprite(Particle particle) {
        try { var field=SingleQuadParticle.class.getDeclaredField("sprite");field.setAccessible(true);return (TextureAtlasSprite)field.get(particle); }
        catch(ReflectiveOperationException failure) { throw new AssertionError("Actual native airflow sprite must be inspectable",failure); }
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    private static final class Vertices implements VertexConsumer {
        final List<Vec3> positions=new ArrayList<>();final List<Integer> colors=new ArrayList<>();
        @Override public VertexConsumer addVertex(float x,float y,float z) { positions.add(new Vec3(x,y,z));return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { colors.add(a<<24|r<<16|g<<8|b);return this; }
        @Override public VertexConsumer setColor(int color) { colors.add(color);return this; }
        @Override public VertexConsumer setUv(float u,float v) { return this; }
        @Override public VertexConsumer setUv1(int u,int v) { return this; }
        @Override public VertexConsumer setUv2(int u,int v) { return this; }
        @Override public VertexConsumer setUv3(float u,float v) { return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
