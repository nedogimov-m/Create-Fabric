package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.depot.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.*;

/** Proposed physical SDL/paid stock/native motion/reload/true chunk retirement scene. UNRUN. */
final class EjectorRenderingTest {
    private static final BlockPos P=new BlockPos(61008,5,8),TARGET=P.east(32),MOTOR=P.south(),LEVER=P.north();
    private static final String NAME="Original native weighted ejector stock";
    static void run(ClientGameTestContext c,TestSingleplayerContext w){
        var camera=c.computeOnClient(client->client.options.getCameraType());boolean hidden=c.computeOnClient(client->client.gui.hud.isHidden());
        try{c.runOnClient(client->{client.options.setCameraType(CameraType.FIRST_PERSON);if(client.gui.hud.isHidden())client.gui.hud.toggle();});scene(c,w);}
        finally{c.getInput().releaseKey(options->options.keyUse);c.getInput().releaseKey(options->options.keyShift);c.getInput().releaseKey(options->options.keyUp);c.getInput().releaseKey(options->options.keyJump);c.runOnClient(client->{client.options.setCameraType(camera);if(client.gui.hud.isHidden()!=hidden)client.gui.hud.toggle();});}
    }
    private static void scene(ClientGameTestContext c,TestSingleplayerContext w){
        var server=w.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 61008.5 10 8.5");c.waitFor(client->client.level.isLoaded(P)&&client.level.isLoaded(TARGET),500);
        NativeScreenshotReadiness.beforeFloor(c, w, new BlockPos(61000, 4, 0), new BlockPos(61064, 4, 40), P, TARGET);
        server.runCommand("fill 61000 4 0 61064 4 40 minecraft:smooth_quartz");
        NativeScreenshotReadiness.afterFloor(c, w, new BlockPos(61000, 4, 0), new BlockPos(61064, 4, 40));server.runCommand("clear @p");stand(c,w,TARGET.getX()+.5,TARGET.getZ()+2.5);
        // Establish native slot ownership after survival, before original finite stock is issued once.
        select(c,w,InputConstants.KEY_7,6);select(c,w,InputConstants.KEY_1,0);var parcel=new AtomicReference<ItemStack>();
        server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();var player=actual(level);
            player.getInventory().setItem(0,named(AllBlocks.WEIGHTED_EJECTOR.asItem(),1));player.getInventory().setItem(1,named(Items.DIAMOND,7));player.getInventory().setItem(2,named(AllItems.WRENCH,1));player.getInventory().setItem(3,named(Items.ELYTRA,1));
            var sealed=PackageItem.containing(List.of(named(Items.GOLD_INGOT,3)));PackageItem.addAddress(sealed,"Original native ejector parcel");sealed.set(DataComponents.CUSTOM_NAME,Component.literal(NAME));parcel.set(sealed.copy());player.getInventory().setItem(4,sealed);
            level.setBlockAndUpdate(TARGET,AllBlocks.DEPOT.defaultBlockState());level.setBlockAndUpdate(MOTOR,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.NORTH));((KineticBlockEntity)level.getBlockEntity(MOTOR)).setGeneratedSpeed(256);
            level.setBlockAndUpdate(LEVER,Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE,AttachFace.FLOOR).setValue(LeverBlock.FACING,Direction.EAST).setValue(LeverBlock.POWERED,true));
        });
        c.waitFor(client->client.player.getMainHandItem().getItem() instanceof EjectorItem&&client.level.getBlockEntity(TARGET) instanceof DepotBlockEntity,250);
        look(c,Vec3.atLowerCornerOf(TARGET).add(.5,13/16d,.5));c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(TARGET),250);
        c.getInput().holdKey(options->options.keyShift);
        try{
            c.waitFor(client->client.player.isShiftKeyDown(),250);
            look(c,Vec3.atLowerCornerOf(TARGET).add(.5,13/16d,.5));
            c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(TARGET),250);
            c.getInput().pressKey(options->options.keyUse);
            c.waitFor(client->TARGET.equals(EjectorTargetHandler.selection()),250);
        }finally{c.getInput().releaseKey(options->options.keyShift);}
        c.waitFor(client->!client.player.isShiftKeyDown()&&client.player.getPose()==Pose.STANDING,250);
        // Approach from the empty west side; the south motor occludes the floor placement ray.
        stand(c,w,P.getX()-2.5,P.getZ()+.5);look(c,Vec3.atLowerCornerOf(P.below()).add(.5,.999,.5));
        try{
            c.waitFor(client->EjectorTargetHandler.latest()!=null&&P.equals(EjectorTargetHandler.latest().candidate())&&EjectorTargetHandler.latest().valid()&&EjectorTargetHandler.latest().arcSegments()>0,250);
        }catch(AssertionError failure){
            var snapshot=c.computeOnClient(client->"selection="+EjectorTargetHandler.selection()+", frame="+EjectorTargetHandler.latest()+", hit="+client.hitResult+", position="+client.player.position()+", eye="+client.player.getEyePosition()+", yaw="+client.player.getYRot()+", pitch="+client.player.getXRot()+", shift="+client.player.isShiftKeyDown()+", pose="+client.player.getPose()+", main="+client.player.getMainHandItem());
            throw new AssertionError("Native ejector preview did not retain original candidate/valid/arc: "+snapshot,failure);
        }
        screenshot(c,"create-vulkan-ejector-source-shift-target-native-ballistic-preview-before-paid-placement");c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->owner(level)!=null&&owner(level).getTargetPosition().equals(TARGET)&&actual(level).getInventory().getItem(0).isEmpty(),250,"Physical original ejector item did not pay for actual placement and captured target settings");
        c.waitFor(client->client.level.getBlockEntity(P) instanceof EjectorBlockEntity ejector&&ejector.getTargetPosition().equals(TARGET),250);
        await(c,w,level->owner(level).getState()==EjectorBlockEntity.State.CHARGED&&owner(level).isPowered(),300,"Actual motor/lever did not naturally charge and inhibit the source");threshold(c,w,7);
        select(c,w,InputConstants.KEY_2,1);stand(c,w,P.getX()-2.5,P.getZ()+.5);use(c,P,Vec3.atLowerCornerOf(P).add(.5,13/16d,.5));
        await(c,w,level->ItemStack.matches(owner(level).depotBehaviour.getHeldItemStack(),named(Items.DIAMOND,7))&&actual(level).getInventory().getItem(1).isEmpty()&&owner(level).getLaunchedItems().isEmpty(),250,"Physical top input did not transfer exactly original seven named diamonds into real source custody");
        c.waitFor(client->client.level.getBlockEntity(P) instanceof EjectorBlockEntity ejector&&ejector.depotBehaviour.getHeldItemStack().getCount()==7,250);verify(c,true,false);look(c,Vec3.atCenterOf(P));screenshot(c,"create-vulkan-ejector-native-charged-lid-holds-seven-original-paid-diamonds-under-real-redstone-lock");
        selectEmpty(c,w);use(c,LEVER,Vec3.atCenterOf(LEVER).add(0,-.2,0));await(c,w,level->flight(owner(level),named(Items.DIAMOND,1))==7,220,"Physical actual lever did not release original stock into genuine flight");
        c.waitFor(client->client.level.getBlockEntity(P) instanceof EjectorBlockEntity ejector&&!ejector.getLaunchedItems().isEmpty(),200);verify(c,false,true);screenshot(c,"create-vulkan-ejector-native-source-lid-pivot-and-real-owned-ballistic-item-submissions");
        await(c,w,level->held(level,TARGET,named(Items.DIAMOND,1))==7&&flight(owner(level),named(Items.DIAMOND,1))==0,300,"Actual trajectory did not debit flight and insert original seven once");
        stand(c,w,TARGET.getX()+.5,TARGET.getZ()+2.5);look(c,Vec3.atCenterOf(TARGET));c.waitFor(client->client.level.getBlockEntity(TARGET) instanceof DepotBlockEntity depot&&ItemStack.matches(depot.getHeldItem(),named(Items.DIAMOND,7)),250);screenshot(c,"create-vulkan-ejector-actual-target-depot-receives-all-seven-original-components-and-counts");
        selectEmpty(c,w);use(c,TARGET,Vec3.atLowerCornerOf(TARGET).add(.5,13/16d,.5));await(c,w,level->held(level,TARGET,named(Items.DIAMOND,1))==0&&ItemStack.matches(actual(level).getInventory().getItem(0),named(Items.DIAMOND,7))&&actual(level).getInventory().getItem(1).isEmpty(),250,"Physical retrieval did not return original seven diamonds to the native first free slot0 while retaining empty slot1");
        stand(c,w,P.getX()-2.5,P.getZ()+.5);select(c,w,InputConstants.KEY_3,2);look(c,Vec3.atCenterOf(P));var oldMesh=c.computeOnClient(client->extract(client).lid.mesh);var reload=c.computeOnClient(Minecraft::reloadResourcePacks);c.waitFor(client->reload.isDone()&&client.gui.overlay()==null,700);reload.join();
        c.runOnClient(client->{if(extract(client).lid.mesh==oldMesh)throw new AssertionError("Native reload retained stale original lid geometry");});verify(c,false,false);c.waitFor(client->EjectorTargetHandler.latest()!=null&&TARGET.equals(EjectorTargetHandler.latest().selection())&&EjectorTargetHandler.latest().valid(),250);screenshot(c,"create-vulkan-ejector-native-resource-reload-refreshes-original-lid-and-real-wrench-target-preview");
        selectEmpty(c,w);use(c,LEVER,Vec3.atCenterOf(LEVER).add(0,-.2,0));await(c,w,level->owner(level).isPowered(),220,"Physical lever did not inhibit source before original parcel transfer");threshold(c,w,0);
        select(c,w,InputConstants.KEY_5,4);stand(c,w,P.getX()-2.5,P.getZ()+.5);use(c,P,Vec3.atLowerCornerOf(P).add(.5,13/16d,.5));await(c,w,level->ItemStack.matches(owner(level).depotBehaviour.getHeldItemStack(),parcel.get())&&actual(level).getInventory().getItem(4).isEmpty(),250,"Original sealed parcel did not move into real source custody");
        selectEmpty(c,w);use(c,LEVER,Vec3.atCenterOf(LEVER).add(0,-.2,0));var originals=new AtomicReference<BlockEntity[]>();
        await(c,w,level->{if(flight(owner(level),parcel.get())!=1)return false;originals.set(new BlockEntity[]{owner(level),LoadedLevelAccess.blockEntity(level,TARGET),LoadedLevelAccess.blockEntity(level,MOTOR)});return true;},220,"Original parcel never entered actual persistent flight");
        server.runCommand("save-all flush");server.runCommand("gamemode spectator @p");server.runCommand("tp @p 62400.5 15 16.5");await(c,w,EjectorRenderingTest::noSourceFullChunks,1000,"Actual source chunks remained FULL after native save/travel");NativeFixtureChunkUnload.finish(c,w,originals.get());
        server.runOnServer(ignored->{if(flight((EjectorBlockEntity)originals.get()[0],parcel.get())!=1)throw new AssertionError("Source flight completed before natural chunk retirement; this run cannot prove in-flight persistence");});server.runCommand("tp @p 61008.5 10 10.5");
        await(c,w,level->{var restored=owner(level);return restored!=null&&restored!=originals.get()[0]&&flight(restored,parcel.get())==1&&restored.getTargetPosition().equals(TARGET)&&restored.getMinimumStackSize()==0&&LoadedLevelAccess.blockEntity(level,TARGET)!=null&&LoadedLevelAccess.blockEntity(level,TARGET)!=originals.get()[1];},700,"Fresh real BEs did not restore original flight/target/wildcard");
        server.runOnServer(ignored->((EjectorBlockEntity)originals.get()[0]).dropFlyingItems());await(c,w,level->held(level,TARGET,parcel.get())==1&&flight(owner(level),parcel.get())==0&&world(level,parcel.get())==0,350,"Fresh source failed actual parcel delivery or retired source exported a duplicate");
        stand(c,w,TARGET.getX()+.5,TARGET.getZ()+2.5);look(c,Vec3.atCenterOf(TARGET));c.waitFor(client->client.level.getBlockEntity(TARGET) instanceof DepotBlockEntity depot&&ItemStack.matches(depot.getHeldItem(),parcel.get()),250);screenshot(c,"create-vulkan-ejector-true-in-flight-save-full-chunk-retirement-fresh-owner-restores-original-sealed-parcel");motion(c,w);
        server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();var player=actual(level);if(!ItemStack.matches(player.getInventory().getItem(0),named(Items.DIAMOND,7))||!player.getInventory().getItem(1).isEmpty()||!player.getInventory().getItem(4).isEmpty()||!player.getInventory().getItem(2).is(AllItems.WRENCH)||!player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)||held(level,TARGET,parcel.get())!=1||world(level,parcel.get())!=0)throw new AssertionError("Native motion/reload/unload lost original paid source/diamond/parcel/equipment receipts");});server.runCommand("gamemode spectator @p");
    }
    private static void motion(ClientGameTestContext c,TestSingleplayerContext w){
        var server=w.getServer();stand(c,w,P.getX()-2.5,P.getZ()+.5);selectEmpty(c,w);powerOn(c,w);var pigId=new AtomicReference<UUID>();
        server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();var pig=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);if(pig==null)throw new AssertionError("Actual original pig could not construct");pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);pig.setCustomName(Component.literal("Original native ejector passenger"));pig.setPos(P.getX()+.5,P.getY()+2,P.getZ()+.5);if(!level.addFreshEntity(pig))throw new AssertionError("Native level refused original pig");pigId.set(pig.getUUID());});
        await(c,w,level->level.getEntity(pigId.get()) instanceof LivingEntity pig&&pig.onGround()&&owner(level).activationBounds().intersects(pig.getBoundingBox()),220,"Original NPC did not naturally land on the actual powered source");use(c,LEVER,Vec3.atCenterOf(LEVER).add(0,-.2,0));
        await(c,w,level->level.getEntity(pigId.get()) instanceof LivingEntity pig&&pig.getX()>P.getX()+2&&pig.getY()>P.getY()+1&&pig.getDeltaMovement().x>0&&!pig.onGround(),220,"Real source did not launch original NPC with actual native velocity");c.waitFor(client->find(client,pigId.get())!=null,220);look(c,c.computeOnClient(client->find(client,pigId.get()).getBoundingBox().getCenter()));screenshot(c,"create-vulkan-ejector-original-real-npc-launches-with-native-source-velocity-and-gravity");
        await(c,w,level->level.getEntity(pigId.get()) instanceof LivingEntity pig&&pig.isAlive()&&pig.onGround()&&pig.getX()>P.getX()+20,400,"Original NPC failed real ballistic flight/natural landing");
        walkOntoSource(c,w);use(c,LEVER,Vec3.atCenterOf(LEVER).add(0,-.2,0));await(c,w,level->{var player=actual(level);return !player.onGround()&&player.getX()>P.getX()+2&&player.getY()>P.getY()+1&&player.getDeltaMovement().x>0&&!player.isFallFlying();},220,"Physical bare survival player failed genuine native source launch");
        await(c,w,level->{var holder=level.getServer().getAdvancements().get(Create.asResource("ejector_maxed"));return holder!=null&&actual(level).getAdvancements().getOrStartProgress(holder).isDone();},220,"Genuine distance32 launch did not earn original recent-launch criterion");await(c,w,level->actual(level).onGround()&&actual(level).getX()>P.getX()+20,400,"Original bare survival player failed natural landing");
        stand(c,w,P.getX()-2.5,P.getZ()+.5);select(c,w,InputConstants.KEY_4,3);look(c,new Vec3(P.getX()+.5,P.getY()+40,P.getZ()+2.5));c.getInput().pressKey(options->options.keyUse);await(c,w,level->actual(level).getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)&&actual(level).getInventory().getItem(3).isEmpty(),220,"Actual physical equip-on-use failed to move original Elytra into its true chest slot");
        selectEmpty(c,w);walkOntoSource(c,w);use(c,LEVER,Vec3.atCenterOf(LEVER).add(0,-.2,0));await(c,w,level->actual(level).isFallFlying()&&!actual(level).onGround()&&actual(level).getX()>P.getX()+2,220,"Actual equipped GLIDER did not start real native source gliding");c.waitFor(client->client.player.isFallFlying()&&client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA),220);c.runOnClient(client->client.options.setCameraType(CameraType.THIRD_PERSON_BACK));c.waitTicks(3);screenshot(c,"create-vulkan-ejector-physically-equipped-original-elytra-real-native-gliding-and-earned-launch-criterion");
    }
    private static void powerOn(ClientGameTestContext c,TestSingleplayerContext w){if(!c.computeOnClient(client->((EjectorBlockEntity)client.level.getBlockEntity(P)).isPowered()))use(c,LEVER,Vec3.atCenterOf(LEVER).add(0,-.2,0));await(c,w,level->owner(level).isPowered()&&owner(level).getState()==EjectorBlockEntity.State.CHARGED,350,"Actual source failed natural charge/inhibition");}
    private static void walkOntoSource(ClientGameTestContext c,TestSingleplayerContext w){stand(c,w,P.getX()-2.5,P.getZ()+.5);selectEmpty(c,w);powerOn(c,w);look(c,Vec3.atCenterOf(P).add(0,1,0));c.getInput().holdKey(options->options.keyUp);c.getInput().holdKey(options->options.keyJump);try{await(c,w,level->{var player=actual(level);return player.onGround()&&owner(level).activationBounds().intersects(player.getBoundingBox())&&player.getY()>P.getY()+.7;},200,"Physical forward/jump failed to put actual survival player on powered source");}finally{c.getInput().releaseKey(options->options.keyUp);c.getInput().releaseKey(options->options.keyJump);}c.waitTicks(2);}
    private static void threshold(ClientGameTestContext c,TestSingleplayerContext w,int value){stand(c,w,P.getX()-2.5,P.getZ()+.5);selectEmpty(c,w);use(c,P,Vec3.atLowerCornerOf(P).add(.001,6/16d,.5));c.waitFor(client->client.gui.screen() instanceof EjectorSettingsScreen,220);int current=c.computeOnClient(client->((EjectorSettingsScreen)client.gui.screen()).selected());for(int index=current;index!=value;index+=value>current?1:-1){c.getInput().scroll(0,value>current?1:-1);c.waitTicks(1);}c.waitFor(client->client.gui.screen() instanceof EjectorSettingsScreen screen&&screen.selected()==value,220);c.clickScreenButton("Done");c.waitFor(client->client.gui.screen()==null,220);await(c,w,level->owner(level).getMinimumStackSize()==value,220,"Physical native threshold input failed actual server acknowledgement");}
    private static boolean noSourceFullChunks(ServerLevel level){for(int x=61000>>4;x<=61064>>4;x++)for(int z=0;z<=40>>4;z++)if(level.getChunkSource().getChunkNow(x,z)!=null)return false;return true;}
    private static EjectorBlockEntity owner(ServerLevel level){return LoadedLevelAccess.blockEntity(level,P) instanceof EjectorBlockEntity ejector?ejector:null;}
    private static EjectorRenderer.State extract(Minecraft client){var state=client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),.5f,null,true);if(!(state instanceof EjectorRenderer.State ejector))throw new AssertionError("Actual native renderer returned no source state");return ejector;}
    private static void verify(ClientGameTestContext c,boolean stock,boolean flying){c.runOnClient(client->{var state=extract(client);if(state.shaft.isEmpty()||state.lid.isEmpty()||state.shaft.mesh==null||state.lid.mesh==null||!state.shaftPose.isFinite()||!state.lidPose.isFinite()||stock&&state.stock.isEmpty()||flying&&state.flying.isEmpty())throw new AssertionError("Native extraction lost original meshes/pivots/owned stock");for(var item:state.stock)if(item.item().isEmpty()||!item.pose().isFinite())throw new AssertionError("Held stock has missing actual item or nonfinite native pose");for(var item:state.flying)if(item.item().isEmpty()||!item.pose().isFinite())throw new AssertionError("Flight stock has missing actual item or nonfinite native pose");});}
    private static int flight(EjectorBlockEntity source,ItemStack stack){return source.getLaunchedItems().stream().filter(item->ItemStack.isSameItemSameComponents(item.getValue(),stack)).mapToInt(item->item.getValue().getCount()).sum();}
    private static int held(ServerLevel level,BlockPos pos,ItemStack stack){if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof DepotBlockEntity depot))return -1;return ItemStack.isSameItemSameComponents(depot.getHeldItem(),stack)?depot.getHeldItem().getCount():0;}
    private static int world(ServerLevel level,ItemStack stack){return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(P).inflate(64),item->ItemStack.isSameItemSameComponents(item.getItem(),stack)).stream().mapToInt(item->item.getItem().getCount()).sum();}
    private static Entity find(Minecraft client,UUID id){for(var entity:client.level.entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null;}
    private static ItemStack named(Item item,int amount){var stack=new ItemStack(item,amount);stack.set(DataComponents.CUSTOM_NAME,Component.literal(NAME));return stack;}
    private static ServerPlayer actual(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static void use(ClientGameTestContext c,BlockPos pos,Vec3 point){look(c,point);c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),250);c.getInput().pressKey(options->options.keyUse);}
    private static void selectEmpty(ClientGameTestContext c,TestSingleplayerContext w){int slot=c.computeOnClient(client->java.util.stream.IntStream.range(0,9).filter(index->client.player.getInventory().getItem(index).isEmpty()).findFirst().orElseThrow(()->new AssertionError("Original native hotbar has no genuinely empty hand slot")));select(c,w,InputConstants.KEY_1+slot,slot);await(c,w,level->actual(level).getMainHandItem().isEmpty(),220,"Actual SDL hotbar selection did not acknowledge a genuine empty main hand");}
    private static void select(ClientGameTestContext c,TestSingleplayerContext w,int key,int slot){c.getInput().pressKey(key);c.waitFor(client->client.player.getInventory().getSelectedSlot()==slot,220);c.waitFor(client->{var state=new FirstPersonHandsAndItemsRenderState();client.player.firstPersonHandsAndItems().extractRenderState(client.player,.5f,state);return state.mainHandItem.getItem()==client.player.getMainHandItem().getItem();},220);await(c,w,level->actual(level).getInventory().getSelectedSlot()==slot,220,"Physical hotbar input failed actual selected-slot acknowledgement");}
    private static void stand(ClientGameTestContext c,TestSingleplayerContext w,double x,double z){w.getServer().runCommand("gamemode survival @p");w.getServer().runCommand("tp @p "+x+" 5 "+z);c.waitFor(client->!client.player.isSpectator()&&client.player.onGround()&&Math.abs(client.player.getX()-x)<.1&&Math.abs(client.player.getZ()-z)<.1,300);}
    private static void look(ClientGameTestContext c,Vec3 point){var angles=c.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});c.getInput().lookAt(angles.x,angles.y);c.waitTicks(2);}
    private static void await(ClientGameTestContext c,TestSingleplayerContext w,Predicate<ServerLevel> predicate,int ticks,String failure){var complete=new AtomicBoolean();for(int tick=0;tick<ticks;tick++){w.getServer().runOnServer(ignored->complete.set(predicate.test(w.getConnection().getServerLevel())));if(complete.get())return;c.waitTicks(1);}throw new AssertionError(failure);}
    private static void screenshot(ClientGameTestContext c,String name){c.waitFor(client->client.gui.overlay()==null,250);c.runOnClient(client->client.gui.hud.getChat().clearMessages(false));c.takeScreenshot(name);}
    private EjectorRenderingTest(){}
}
