package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.*;

final class StockLinkRenderingTest {
    private static final BlockPos FIRST=new BlockPos(1602,4,5),SECOND=new BlockPos(1610,4,5);
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();var id=UUID.randomUUID();var initialRead=new InitialReadScope(context,id);
        try {
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 1607 9 14");context.waitFor(client->Math.abs(client.player.getX()-1607.5)<.1,300);waitChunks(context,world,new BlockPos(1596,3,0),new BlockPos(1631,3,15));server.runCommand("fill 1596 3 0 1631 3 15 minecraft:smooth_quartz");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();station(level,FIRST,id,20);station(level,SECOND,id,30);int index=0;for(var face:AttachFace.values())for(var facing:Direction.Plane.HORIZONTAL){var pos=new BlockPos(1598+index%6*3,7,2+index/6*4);level.setBlockAndUpdate(pos,AllBlocks.STOCK_LINK.defaultBlockState().setValue(PackagerLinkBlock.FACE,face).setValue(PackagerLinkBlock.FACING,facing));index++;}});
        try {
            context.waitFor(client->client.level.getBlockEntity(FIRST.above()) instanceof PackagerLinkBlockEntity link&&link.behaviour.freqId.equals(id),300);
        } catch(AssertionError failure) {
            try {
                var serverState=server.computeOnServer(ignored->linkObservation(world.getConnection().getServerLevel()));
                var clientState=context.computeOnClient(client->linkObservation(client.level));
                System.err.println("CREATE_STOCK_LINK_INITIAL_READ expected="+id+" server={"+serverState+"} client={"+clientState+"}");
            } catch(RuntimeException|Error observationFailure) {
                failure.addSuppressed(observationFailure);
            }
            throw failure;
        }
        initialRead.verify(context);
        world.getConnection().waitForChunksRender();context.getInput().lookAt(new BlockPos(1607,6,5));
        context.runOnClient(client->{for(var face:AttachFace.values())for(var facing:Direction.Plane.HORIZONTAL)for(boolean powered:new boolean[]{false,true}){var state=AllBlocks.STOCK_LINK.defaultBlockState().setValue(PackagerLinkBlock.FACE,face).setValue(PackagerLinkBlock.FACING,facing).setValue(PackagerLinkBlock.POWERED,powered);var model=client.getModelManager().getBlockStateModelSet().get(state);var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),(net.minecraft.client.renderer.block.BlockAndTintGetter)client.level,FIRST,state,net.minecraft.util.RandomSource.create(42),side->false);if(mesh.immutableCopy().size()==0)throw new AssertionError("Missing stock link orientation: "+state);}});context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-stock-link-orientations");
        server.runCommand("tp @p 1602 4 9");context.waitFor(client->Math.abs(client.player.getX()-1602.5)<.1&&Math.abs(client.player.getY()-4)<.1,300);server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with create:stock_link 2");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().is(AllBlocks.STOCK_LINK.asItem()),200);lookAt(context,new Vec3(1602.5,5.18,5.5));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(FIRST.above()),200);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->id.equals(LogisticallyLinkedBlockItem.networkFromStack(client.player.getMainHandItem())),200);context.runOnClient(client->{if(client.player.getMainHandItem().getCount()!=2)throw new AssertionError("Tuning consumed a stock link");});
        server.runCommand("tp @p 1618 4 9");context.waitFor(client->Math.abs(client.player.getX()-1618.5)<.1,300);var placed=new BlockPos(1618,4,5);lookAt(context,new Vec3(1618.5,4,5.5));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(placed.below()),200);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.level.getBlockEntity(placed) instanceof PackagerLinkBlockEntity link&&link.behaviour.freqId.equals(id)&&client.player.getMainHandItem().getCount()==1,200);
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 1607 9 14");context.waitFor(client->client.player.isSpectator()&&Math.abs(client.player.getX()-1607.5)<.1,200);context.getInput().lookAt(FIRST.above());
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(LogisticsManager.getStockOf(level,id,new ItemStack(Items.APPLE),null)!=50)throw new AssertionError("Survival-tuned network has wrong stock");var order=PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.APPLE),40)));if(!LogisticsManager.broadcastPackageRequest(level,id,LogisticallyLinkedBehaviour.RequestType.PLAYER,order,null,"Vulkan order"))throw new AssertionError("Network request rejected");});
        context.waitFor(client->client.level.getBlockEntity(FIRST) instanceof PackagerBlockEntity first&&!first.heldBox.isEmpty()&&client.level.getBlockEntity(SECOND) instanceof PackagerBlockEntity second&&!second.heldBox.isEmpty(),200);
        var extracted=new AtomicBoolean();for(int i=0;i<8&&!extracted.get();i++){server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();((PackagerLinkBlockEntity)level.getBlockEntity(FIRST.above())).flash();});context.waitTicks(1);context.runOnClient(client->{var renderer=client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(FIRST.above()),.5f,null,false);if(renderer instanceof PackagerLinkRenderer.State state&&state.visible&&state.halo.mesh!=null&&state.halo.mesh.size()>0)extracted.set(true);});}if(!extracted.get())throw new AssertionError("Stock link bulb never extracted native pulse mesh");context.waitTicks(1);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-stock-link-order-and-pulse");
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();((PackagerLinkBlockEntity)level.getBlockEntity(FIRST.above())).flash();});context.waitTicks(2);context.runOnClient(client->{var state=client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(FIRST.above()),.5f,null,false);if(!(state instanceof PackagerLinkRenderer.State link)||!link.visible||link.tube.mesh==null)throw new AssertionError("Stock link glow did not recover after reload");});
        chunkUnload(context,world);
        } finally { initialRead.close(); }
    }
    /** Public addon event on the actual native first chunk read; no manual chunk hydration. */
    private static final class InitialReadScope implements AutoCloseable {
        private final UUID expected;
        private final AtomicReference<InitialReadProbe> selected=new AtomicReference<>();
        private final AtomicReference<java.util.function.Consumer<BlockEntityBehaviourEvent>> observer=new AtomicReference<>();
        private InitialReadScope(ClientGameTestContext context,UUID expected){
            this.expected=expected;observer.set(this::onBehaviours);var relay=observer;
            context.runOnClient(client->BlockEntityBehaviourEvent.EVENT.register(event->{var active=relay.get();if(active!=null)active.accept(event);}));
        }
        private void onBehaviours(BlockEntityBehaviourEvent event){
            if(!(event.getBlockEntity() instanceof PackagerLinkBlockEntity link)||link.getLevel()==null||!link.getLevel().isClientSide()
                ||!List.of(FIRST.above(),SECOND.above()).contains(link.getBlockPos()))return;
            var level=link.getLevel();var pos=link.getBlockPos();
            if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null)return;
            var counter=new InitialReadProbe(link,expected);if(!selected.compareAndSet(null,counter))return;
            event.attach(counter);
            var poison=new CompoundTag();poison.putBoolean(InitialReadProbe.POISON,true);
            link.readClient(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),poison));
            if(counter.nativeReads!=0||counter.poisonReads!=0)throw new AssertionError("Recursive addon input read behaviours before the public publication event returned");
        }
        private void verify(ClientGameTestContext context){
            context.runOnClient(client->{
                observer.set(null);var counter=selected.get();
                if(counter==null)throw new AssertionError("StockLink regression did not observe an actual native client chunk read before global publication");
                var link=(PackagerLinkBlockEntity)counter.blockEntity;
                if(client.level!=link.getLevel()||LoadedLevelAccess.blockEntity(client.level,link.getBlockPos())!=link
                    ||link.getBehaviour(LogisticallyLinkedBehaviour.TYPE)!=link.behaviour||link.getBehaviour(InitialReadProbe.TYPE)!=counter
                    ||!link.behaviour.freqId.equals(expected))throw new AssertionError("Initial native input lost its actual canonical StockLink owner or frequency");
                if(counter.nativeReads!=1||counter.poisonReads!=0)throw new AssertionError("Initial chunk DATA scope leaked to recursive addon input or lost/duplicated the native payload: native="+counter.nativeReads+", poison="+counter.poisonReads);
                counter.active=false;
            });
        }
        @Override public void close(){observer.set(null);var counter=selected.get();if(counter!=null)counter.active=false;}
    }
    private static final class InitialReadProbe extends BlockEntityBehaviour {
        private static final BehaviourType<InitialReadProbe> TYPE=new BehaviourType<>();
        private static final String POISON="CreateInitialChunkReadPoison";
        private final UUID expected;
        private volatile boolean active=true;
        private int nativeReads,poisonReads;
        private InitialReadProbe(PackagerLinkBlockEntity owner,UUID expected){super(owner);this.expected=expected;}
        @Override public BehaviourType<?> getType(){return TYPE;}
        @Override public void read(ValueInput input,boolean clientPacket){
            if(!active)return;var level=getWorld();var pos=getPos();
            // Normal later BE packets are outside the initial unpublished-chunk regression.
            if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null)return;
            var link=(PackagerLinkBlockEntity)blockEntity;
            if(!clientPacket||!level.isClientSide()||link.isRemoved()||link.isChunkUnloaded()
                ||link.getBehaviour(LogisticallyLinkedBehaviour.TYPE)!=link.behaviour||link.getBehaviour(TYPE)!=this)
                throw new AssertionError("Initial native DATA read did not retain its client packet and canonical registered owners");
            if(input.getBooleanOr(POISON,false)){poisonReads++;return;}
            if(!input.read("Freq",UUIDUtil.CODEC).filter(expected::equals).isPresent())throw new AssertionError("Actual initial native StockLink input did not contain the genuine server frequency");
            nativeReads++;
        }
    }
    private static void station(net.minecraft.server.level.ServerLevel level,BlockPos pos,UUID id,int count) {level.setBlockAndUpdate(pos.north(),Blocks.BARREL.defaultBlockState());((BarrelBlockEntity)level.getBlockEntity(pos.north())).setItem(0,new ItemStack(Items.APPLE,count));level.setBlockAndUpdate(pos,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));level.setBlockAndUpdate(pos.above(),AllBlocks.STOCK_LINK.defaultBlockState());var link=(PackagerLinkBlockEntity)level.getBlockEntity(pos.above());link.behaviour.setFrequency(id);link.initialize();}
    private static void chunkUnload(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();var pos=new BlockPos(1663,4,5);var linkPos=pos.east();var id=UUID.randomUUID();server.runCommand("tp @p 1663 9 12");context.waitFor(client->Math.abs(client.player.getX()-1663.5)<.1,300);waitChunks(context,world,pos,linkPos);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(pos.north(),Blocks.BARREL.defaultBlockState());((BarrelBlockEntity)level.getBlockEntity(pos.north())).setItem(0,new ItemStack(Items.APPLE,17));level.setBlockAndUpdate(pos,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));level.setBlockAndUpdate(linkPos,AllBlocks.STOCK_LINK.defaultBlockState().setValue(PackagerLinkBlock.FACE,AttachFace.WALL).setValue(PackagerLinkBlock.FACING,Direction.EAST));var link=(PackagerLinkBlockEntity)level.getBlockEntity(linkPos);link.behaviour.setFrequency(id);link.initialize();if(LogisticsManager.getStockOf(level,id,new ItemStack(Items.APPLE),null)!=17)throw new AssertionError("Boundary network setup failed");level.getChunkSource().addTicket(new net.minecraft.server.level.Ticket(net.minecraft.server.level.TicketType.FORCED,33),new net.minecraft.world.level.ChunkPos(pos.getX()>>4,pos.getZ()>>4));});
        server.runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();for(int i=0;i<120&&!unloaded.get();i++){context.waitTicks(5);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var packager=(PackagerBlockEntity)LoadedLevelAccess.blockEntity(level,pos);if(packager==null)throw new AssertionError("Boundary owner did not stay FULL");packager.recheckIfLinksPresent();LogisticsManager.getSummaryOfNetwork(level,id,true);unloaded.set(!level.isLoaded(linkPos));});}if(!unloaded.get())throw new AssertionError("Stock network queries renewed unloaded link chunk tickets");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var manager=GlobalLogisticsManager.get(level);if(manager.getUnloadedLinkCount(id)!=1||LogisticsManager.getStockOf(level,id,new ItemStack(Items.APPLE),null)!=0)throw new AssertionError("Unloaded link remained live or lost persistent membership: unloaded="+manager.getUnloadedLinkCount(id)+", stock="+LogisticsManager.getStockOf(level,id,new ItemStack(Items.APPLE),null));});
        server.runCommand("tp @p 1663 9 12");context.waitFor(client->Math.abs(client.player.getX()-1663.5)<.1&&client.level.getBlockEntity(linkPos) instanceof PackagerLinkBlockEntity,400);context.waitTicks(5);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();
            try {
                if(GlobalLogisticsManager.get(level).getUnloadedLinkCount(id)!=0||LogisticsManager.getStockOf(level,id,new ItemStack(Items.APPLE),null)!=17)throw new AssertionError("Network did not recover existing stock after reload");
            } catch(AssertionError failure) {
                try {System.err.println("CREATE_STOCK_LINK_NATIVE_RETURN "+returnObservation(level,pos,linkPos,id));}
                catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
                throw failure;
            }
            level.getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.FORCED,new net.minecraft.world.level.ChunkPos(pos.getX()>>4,pos.getZ()>>4),0);
        });
    }
    private static Object observedField(Class<?> type,Object owner,String name){
        try {var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot observe existing field "+type.getName()+"."+name,failure);}
    }
    private static String returnObservation(net.minecraft.server.level.ServerLevel level,BlockPos packagerPos,BlockPos linkPos,UUID id){
        var manager=GlobalLogisticsManager.get(level);var network=manager.logisticsNetworks.get(id);
        var pending=(Set<?>)observedField(GlobalLogisticsManager.class,manager,"pendingInitializations");
        var live=(Map<?,?>)observedField(GlobalLogisticsManager.class,manager,"links");
        var result=new StringBuilder("expected=").append(id).append(" tick=").append(level.getGameTime()).append(" managerTick=").append(manager.ticks()).append(" totalLinks=").append(network==null?"absent":network.totalLinks).append(" loadedLinks=").append(network==null?"absent":network.loadedLinks).append(" unloadedCount=").append(manager.getUnloadedLinkCount(id)).append(" pendingCount=").append(pending.size());
        var entries=live.get(id);
        if(entries instanceof Map<?,?> refs)for(var entry:refs.entrySet()){
            var behaviour=((java.lang.ref.Reference<?>)entry.getValue()).get();
            result.append(" live[").append(entry.getKey()).append("]=");
            if(behaviour instanceof LogisticallyLinkedBehaviour link)result.append(link.freqId).append("@").append(link.getPos()).append(" removed=").append(link.blockEntity.isRemoved()).append(" chunkUnloaded=").append(link.blockEntity.isChunkUnloaded());else result.append("collected");
        }
        for(var pos:List.of(packagerPos,packagerPos.north(),linkPos)){
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);var entity=chunk==null?null:chunk.getBlockEntities().get(pos);
            result.append("; pos=").append(pos).append(" nativeLoaded=").append(level.isLoaded(pos)).append(" FULLnow=").append(chunk!=null).append(" fullStatus=").append(chunk==null?"absent":chunk.getFullStatus()).append(" chunkdebug=").append(level.getChunkSource().getChunkDebugData(new net.minecraft.world.level.ChunkPos(pos.getX()>>4,pos.getZ()>>4))).append(" actualClass=").append(entity==null?"absent":entity.getClass().getName());
            if(entity instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart)result.append(" smartInitialized=").append(observedField(com.simibubi.create.foundation.blockEntity.SmartBlockEntity.class,smart,"initialized")).append(" removed=").append(smart.isRemoved()).append(" chunkUnloaded=").append(smart.isChunkUnloaded());
            if(entity instanceof PackagerLinkBlockEntity link)result.append(" freq=").append(link.behaviour.freqId).append(" registeredFreq=").append(observedField(LogisticallyLinkedBehaviour.class,link.behaviour,"registeredFreq")).append(" linkInitialized=").append(observedField(PackagerLinkBlockEntity.class,link,"initialized")).append(" pending=").append(pending.contains(link)).append(" canonicalBehaviour=").append(((Map<?,?>)observedField(com.simibubi.create.foundation.blockEntity.SmartBlockEntity.class,link,"behaviours")).get(LogisticallyLinkedBehaviour.TYPE)==link.behaviour).append(" power=").append(link.behaviour.redstonePower);
            if(entity instanceof PackagerBlockEntity packager)result.append(" linked=").append(packager.getBlockState().getValue(PackagerBlock.LINKED));
            if(entity instanceof BarrelBlockEntity barrel)result.append(" item0=").append(barrel.getItem(0));
        }
        return result.toString();
    }
    private static String linkObservation(net.minecraft.world.level.Level level) {
        if(level==null)return "level=absent";
        var result=new StringBuilder("tick=").append(level.getGameTime()).append(" clientSide=").append(level.isClientSide());
        for(var pos:List.of(FIRST.above(),SECOND.above())) {
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            var entity=chunk==null?null:chunk.getBlockEntities().get(pos);
            result.append("; pos=").append(pos).append(" nativeLoaded=").append(level.isLoaded(pos)).append(" FULLnow=").append(chunk!=null).append(" fullStatus=").append(chunk==null?"absent":chunk.getFullStatus()).append(" actualClass=").append(entity==null?"absent":entity.getClass().getName());
            if(level instanceof net.minecraft.server.level.ServerLevel server)result.append(" chunkdebug=").append(server.getChunkSource().getChunkDebugData(new net.minecraft.world.level.ChunkPos(pos.getX()>>4,pos.getZ()>>4)));
            if(entity instanceof PackagerLinkBlockEntity link) {
                // Read the existing registry directly: public getBehaviour() may lazily collect fields.
                Object canonical;
                try {
                    var field=com.simibubi.create.foundation.blockEntity.SmartBlockEntity.class.getDeclaredField("behaviours");field.setAccessible(true);
                    canonical=((Map<?,?>)field.get(link)).get(LogisticallyLinkedBehaviour.TYPE);
                } catch(ReflectiveOperationException reflectionFailure) {
                    throw new IllegalStateException("Could not observe existing native behaviour registry",reflectionFailure);
                }
                result.append(" actualFreq=").append(link.behaviour.freqId).append(" canonicalBehaviour=").append(canonical==link.behaviour).append(" canonicalClass=").append(canonical==null?"absent":canonical.getClass().getName()).append(" behaviourOwner=").append(link.behaviour.blockEntity==link).append(" removed=").append(link.isRemoved()).append(" chunkUnloaded=").append(link.isChunkUnloaded()).append(" levelIdentity=").append(link.getLevel()==level).append(" power=").append(link.behaviour.redstonePower);
            }
        }
        return result.toString();
    }
    private static void waitChunks(ClientGameTestContext context,TestSingleplayerContext world,BlockPos first,BlockPos last) {var ready=new AtomicBoolean();for(int i=0;i<300&&!ready.get();i++){world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();ready.set(level.isLoaded(first)&&level.isLoaded(last));});if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError("Stock link chunks did not load");}
    private static void lookAt(ClientGameTestContext context,Vec3 point) {var rotation=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(rotation.x,rotation.y);}
}
