package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.copycat.*;
import com.simibubi.create.content.decoration.palettes.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** One focused real native fixture, all eight unchanged source boundary helpers. Runtime UNRUN. */
public final class CopycatCTBoundaryRenderingTest implements FabricClientGameTest {
    private static final Path REPORT=Path.of(System.getProperty("create.test.copycatCTReport","build/reports/create-copycat/ct-boundary-runtime.json")).toAbsolutePath();
    private static final BlockPos APPEAR=origin(0),FILTER=origin(1),COPIED=origin(2),ORDINARY=origin(3),AXIS=origin(4),BLOCKER=origin(5),ROOF=origin(6),CASING=origin(7);
    private static final int[] KEYS={InputConstants.KEY_1,InputConstants.KEY_2,InputConstants.KEY_3,InputConstants.KEY_4,InputConstants.KEY_5,InputConstants.KEY_6,InputConstants.KEY_7,InputConstants.KEY_8,InputConstants.KEY_9};
    private record Payment(int slot,BlockPos pos,Block material,boolean southView){
        ItemStack stock(int count){var stack=new ItemStack(material.asItem(),count);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Original CT boundary material "+slot));return stack;}
        BlockPos platform(){return southView?pos.south(2):pos.north(2);}
    }
    private static final class Report {String status="RUNNING",stage="SETUP",backend,failure,screenshot;boolean sodiumLoaded;int paidOwners,executedHelpers;List<Map<String,Object>> checks=new ArrayList<>(),payments=new ArrayList<>();}
    private static BlockPos origin(int index){return new BlockPos(32+index*8,8,32);}
    @Override public void runTest(ClientGameTestContext context){
        var report=new Report();
        try(var world=context.worldBuilder().create()){
            context.runOnClient(client->{report.backend=RenderSystem.getDevice().getDeviceInfo().backendName();report.sodiumLoaded=FabricLoader.getInstance().isModLoaded("sodium");require(report.backend.toLowerCase(Locale.ROOT).contains("vulkan")&&!report.sodiumLoaded,"Focused actual native Vulkan/no-Sodium runtime required");});
            var pillar=context.computeOnClient(client->block("andesite_pillar"));var roof=context.computeOnClient(client->block("waxed_copper_shingles"));
            require(pillar instanceof ConnectedPillarBlock,"Real registered source pillar required");require(roof.defaultBlockState().getSoundType()==SoundType.COPPER,"Actual source copper roof required");
            var payments=List.of(new Payment(0,APPEAR,Blocks.STONE,false),new Payment(1,FILTER,Blocks.STONE,false),new Payment(2,FILTER.below(),Blocks.GLASS,true),new Payment(3,FILTER.east(),Blocks.STONE,false),new Payment(4,COPIED,pillar,false),new Payment(5,AXIS,pillar,false),new Payment(6,BLOCKER.east(),pillar,false),new Payment(7,ROOF.east(),roof,false),new Payment(8,CASING,AllBlocks.ANDESITE_CASING,false));
            var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 60.5 14 28.5");
            context.waitFor(client->client.level!=null&&List.of(APPEAR,CASING,ROOF).stream().allMatch(client.level::isLoaded),450);
            server.runCommand("fill 28 4 25 96 4 39 minecraft:smooth_quartz");server.runCommand("fill 28 5 25 96 12 39 minecraft:air");
            for(var payment:payments){set(world,payment.pos,"create:copycat_panel[facing=down,waterlogged="+(payment.pos.equals(FILTER.below())?"true":"false")+",light=0]");set(world,payment.platform(),"minecraft:smooth_quartz");}
            set(world,APPEAR.east(),"minecraft:stone");
            set(world,COPIED.east(),"create:andesite_pillar[axis=y]");
            set(world,ORDINARY,"create:andesite_pillar[axis=y]");set(world,ORDINARY.east(),"create:andesite_pillar[axis=y]");set(world,ORDINARY.east().above(),"create:andesite_pillar[axis=y]");
            // Unequal genuine column heights make native column updates keep both horizontal masks false.
            set(world,AXIS.east(),"create:andesite_pillar[axis=x]");set(world,BLOCKER,"create:andesite_pillar[axis=y]");
            for(var pos:List.of(ROOF,ROOF.north(),ROOF.south()))set(world,pos,"create:waxed_copper_shingles");set(world,CASING.east(),"create:andesite_casing");
            server.runCommand("clear @p");server.runCommand("gamemode survival @p");
            server.runOnServer(ignored->{var player=player(world.getConnection().getServerLevel());for(var payment:payments){player.getInventory().setItem(payment.slot,payment.stock(2));player.connection.send(player.getInventory().createInventoryUpdatePacket(payment.slot));}});
            context.waitFor(client->!client.player.isSpectator()&&client.player.getOffhandItem().isEmpty()&&payments.stream().allMatch(p->ItemStack.matches(client.player.getInventory().getItem(p.slot),p.stock(2))),250);
            report.stage="REAL_PAID_NATIVE_INTERACTIONS";write(report);
            for(var payment:payments){
                report.stage="PAYMENT_"+payment.slot+"_"+payment.pos;write(report);
                stand(context,world,payment);context.getInput().pressKey(KEYS[payment.slot]);context.waitFor(client->client.player.getInventory().getSelectedSlot()==payment.slot&&ItemStack.matches(client.player.getMainHandItem(),payment.stock(2)),200);
                waitServer(context,world,level->player(level).getInventory().getSelectedSlot()==payment.slot,200,"Native selected slot was not acknowledged");
                look(context,new Vec3(payment.pos.getX()+.5,payment.pos.getY()+1-.001,payment.pos.getZ()+.5));
                context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(payment.pos)&&hit.getDirection()==Direction.UP,200);
                context.getInput().pressKey(options->options.keyUse);
                waitServer(context,world,level->paid(level,payment),250,"Actual native survival use did not debit exactly one and install original source material at "+payment.pos);
                context.waitFor(client->client.level.getBlockEntity(payment.pos) instanceof CopycatBlockEntity owner&&owner.getMaterial().is(payment.material)&&ItemStack.matches(owner.getConsumedItem(),payment.stock(1))&&ItemStack.matches(client.player.getInventory().getItem(payment.slot),payment.stock(1)),250);
                report.payments.add(Map.of("slot",payment.slot,"position",payment.pos.toString(),"material",BuiltInRegistries.BLOCK.getKey(payment.material).toString(),"initial",2,"inventoryRemaining",1,"actualOriginalOwnedReceipt",1,"nativeSurvivalInput",true));report.paidOwners++;
            }
            // The genuine rotated full blocker is added after the UP-face material payment, without editing that material.
            set(world,BLOCKER.east().above(),"create:andesite_pillar[axis=x]");
            server.runCommand("gamemode spectator @p");server.runCommand("tp @p 60.5 14 28.5");
            context.waitFor(client->List.of(APPEAR,FILTER,COPIED,ORDINARY,AXIS,BLOCKER,ROOF,CASING).stream().allMatch(client.level::isLoaded)
                &&payments.stream().allMatch(p->client.level.getBlockEntity(p.pos) instanceof CopycatBlockEntity copy&&copy.getMaterial().is(p.material)&&ItemStack.matches(copy.getConsumedItem(),p.stock(1))),300);
            context.waitTicks(5);
            waitServer(context,world,level->payments.stream().allMatch(p->paid(level,p))&&noMasks(level.getBlockState(ORDINARY))&&noMasks(level.getBlockState(ORDINARY.east())),200,"Original finite paid stock or deliberate ordinary false-mask control was not retained");
            context.waitFor(client->client.level.getBlockState(BLOCKER.east().above()).is(pillar)&&client.level.getBlockState(BLOCKER.east().above()).getValue(LayeredBlock.AXIS)==Direction.Axis.X&&noMasks(client.level.getBlockState(ORDINARY))&&noMasks(client.level.getBlockState(ORDINARY.east())),250);
            report.stage="EIGHT_REAL_VIEW_BOUNDARIES";write(report);
            context.runOnClient(client->{
                var view=client.level;require(view!=null,"Actual joined native BlockAndTintGetter required");
                for(var payment:payments)require(view.getBlockEntity(payment.pos) instanceof CopycatBlockEntity owner&&view.getBlockEntityRenderData(payment.pos) instanceof CopycatBlockEntity.RenderData data&&data.material()==owner.getMaterial()&&ItemStack.matches(owner.getConsumedItem(),payment.stock(1)),"Real immutable attachment must come from the actual paid native owner at "+payment.pos);
                //1: prove both conditional branches are deliberate, then call the original helper unchanged twice.
                var panel=(CopycatBlock)view.getBlockState(APPEAR).getBlock();
                require(panel.isIgnoredConnectivitySide(view,view.getBlockState(APPEAR),Direction.NORTH,APPEAR,APPEAR.east()),"Fixture1 ignored NORTH branch must be true");
                require(!panel.isIgnoredConnectivitySide(view,view.getBlockState(APPEAR),Direction.UP,APPEAR,APPEAR.east()),"Fixture1 admitted UP branch must be false");
                CopycatCTBoundaryChecks.actualAppearanceHonorsIgnoredSideBeforeMaterialLookup(view,APPEAR,APPEAR.east(),Direction.NORTH);CopycatCTBoundaryChecks.actualAppearanceHonorsIgnoredSideBeforeMaterialLookup(view,APPEAR,APPEAR.east(),Direction.UP);
                checked(report,"appearance_ignored_and_admitted",mesh(client,APPEAR,Direction.NORTH,null,-1,0,CopycatModel.class));
                //2: real denied target has all three observable data kinds; admitted target is another real paid owner.
                var source=(CopycatBlock)view.getBlockState(FILTER).getBlock();
                require(!source.canConnectTexturesToward(view,FILTER,FILTER.below(),view.getBlockState(FILTER)),"Fixture2 denied target must actually be denied");
                require(source.canConnectTexturesToward(view,FILTER,FILTER.east(),view.getBlockState(FILTER)),"Fixture2 allowed target must actually be admitted");
                require(!view.getFluidState(FILTER.below()).isEmpty()&&view.getBlockEntity(FILTER.below()) instanceof CopycatBlockEntity&&view.getBlockEntityRenderData(FILTER.below()) instanceof CopycatBlockEntity.RenderData,"Fixture2 denied raw native target must contain real water, BE and material attachment");
                require(view.getBlockEntity(FILTER.east()) instanceof CopycatBlockEntity&&view.getBlockEntityRenderData(FILTER.east()) instanceof CopycatBlockEntity.RenderData,"Fixture2 admitted raw target must carry real paid material attachment");
                CopycatCTBoundaryChecks.actualDirectionalFilterKeepsDeniedStateFluidAndAttachmentInvisible(view,FILTER,FILTER.below());CopycatCTBoundaryChecks.actualDirectionalFilterKeepsDeniedStateFluidAndAttachmentInvisible(view,FILTER,FILTER.east());
                checked(report,"filter_denied_and_admitted",mesh(client,FILTER,Direction.NORTH,null,-1,0,CopycatModel.class));
                //3–8: each untouched helper runs on a separate fully populated native source context.
                CopycatCTBoundaryChecks.copiedOriginBypassesPillarMasksAfterSameBlockAndAxisGuards(view,COPIED);
                checked(report,"copied_pillar_side_mask_bypass",mesh(client,COPIED,Direction.NORTH,"palettes/stone_types/pillar/andesite_cut_pillar_connected",15,4,CopycatModel.class));
                CopycatCTBoundaryChecks.ordinaryPillarsKeepTheirOriginalMaskBoundary(view,ORDINARY);
                checked(report,"ordinary_pillar_mask_rejection",mesh(client,ORDINARY,Direction.NORTH,"palettes/stone_types/pillar/andesite_cut_pillar_connected",12,4,PaletteCTModel.class));
                CopycatCTBoundaryChecks.differentAxisRejectsBeforeTheCopycatBypass(view,AXIS);
                checked(report,"axis_guard_before_copycat_bypass",mesh(client,AXIS,Direction.NORTH,"palettes/stone_types/pillar/andesite_cut_pillar_connected",12,4,CopycatModel.class));
                CopycatCTBoundaryChecks.rotatedSameBlockForwardBlockerStillPrecedesCopiedEndpointBypass(view,BLOCKER);
                checked(report,"rotated_full_blocker_before_bypass",mesh(client,BLOCKER,Direction.UP,"palettes/stone_types/cap/andesite_cut_cap_connected",0,8,PaletteCTModel.class));
                CopycatCTBoundaryChecks.roofTargetAppearanceIsResolvedBeforeHeightAndSoundConnectivity(view,ROOF);
                checked(report,"roof_target_appearance",mesh(client,ROOF,Direction.UP,"copper/copper_shingles_top_connected",4,4,WindowRoofCTModel.class));
                CopycatCTBoundaryChecks.encasedContextUsesTheActualCasingIdentityWithoutAdmissionOverrides(view,CASING);
                checked(report,"actual_casing_identity_and_side_predicate",mesh(client,CASING,Direction.NORTH,"andesite_casing_connected",8,8,CopycatModel.class));
                require(report.executedHelpers==8,"All eight original helper contexts must execute");
            });
            report.stage="FINAL_CUSTODY";waitServer(context,world,level->payments.stream().allMatch(p->paid(level,p)),200,"Boundary checks must retain every original paid material and actual finite remaining stock");
            context.runOnClient(client->{for(var payment:payments)require(client.level.getBlockEntityRenderData(payment.pos) instanceof CopycatBlockEntity.RenderData data&&data.material().is(payment.material),"Actual immutable native attachment changed during read-only boundary checks");});
            server.runCommand("gamemode spectator @p");server.runCommand("tp @p 53.5 13 25.5");context.waitFor(client->client.player.isSpectator()&&client.player.position().distanceToSqr(new Vec3(53.5,13,25.5))<.1,220);look(context,new Vec3(53,8.8,32.5));world.getConnection().waitForChunksRender();context.waitTicks(2);
            report.screenshot=context.takeScreenshot("create-vulkan-copycat-native-ct-copied-and-ordinary-source-boundaries").toAbsolutePath().toString();
            report.stage="COMPLETE";report.status="PASS";
        }catch(Throwable failure){report.status="FAIL";report.failure=failure.toString();throw failure instanceof AssertionError assertion?assertion:new AssertionError("Focused Copycat CT stage "+report.stage+" failed",failure);}
        finally{context.getInput().releaseKey(options->options.keyUse);write(report);}
    }
    private static Map<String,Object> mesh(Minecraft client,BlockPos pos,Direction face,String sheet,int tile,int size,Class<?> expectedModel){
        var state=client.level.getBlockState(pos);var model=client.getModelManager().getBlockStateModelSet().get(state);require(expectedModel.isInstance(model),"Actual installed native model wrapper missing at "+pos+": "+model.getClass().getName());
        if(state.getBlock() instanceof CopycatBlock){var material=CopycatModel.getMaterial(client.level,pos);require(!material.is(AllBlocks.COPYCAT_BASE),"Actual model must read real paid immutable native material");if(sheet!=null)require(CopycatModel.getModelOf(material) instanceof PaletteCTModel||CopycatModel.getModelOf(material) instanceof com.simibubi.create.content.kinetics.NativeKineticsCTModel,"Actual copied material must invoke installed native CT wrapper");}
        var target=sheet==null?null:client.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(Create.asResource("block/"+sheet));if(target!=null)require(target.contents().name().equals(Create.asResource("block/"+sheet)),"Real target CT sheet must be stitched");
        var mesh=Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,pos,state,RandomSource.create(42),side->false);var count=new AtomicInteger();
        mesh.immutableCopy().forEach(quad->{if(quad.lightFace()!=face)return;count.incrementAndGet();for(int v=0;v<4;v++){
            require(Float.isFinite(quad.x(v))&&Float.isFinite(quad.y(v))&&Float.isFinite(quad.z(v))&&Float.isFinite(quad.u(v))&&Float.isFinite(quad.v(v)),"Actual native emitted source geometry/UV must be finite");
            if(target!=null){float u=(quad.u(v)-target.getU0())/(target.getU1()-target.getU0())*size,w=(quad.v(v)-target.getV0())/(target.getV1()-target.getV0())*size;require(u>=tile%size-.001&&u<=tile%size+1.001&&w>=tile/size-.001&&w<=tile/size+1.001,"Actual native emitted UV must enter exact source CT sheet/tile at "+pos+" expected="+tile+" u="+u+" v="+w);}
        }});require(count.get()>0,"Actual native model must emit the checked source face");
        return Map.of("position",pos.toString(),"model",model.getClass().getName(),"face",face.toString(),"sheet",sheet==null?"native_material":sheet,"tile",tile,"sheetSize",size,"actualFaceQuads",count.get());
    }
    private static void checked(Report report,String name,Map<String,Object> data){var row=new LinkedHashMap<String,Object>(data);row.put("helper",name);row.put("status","PASS");report.checks.add(row);report.executedHelpers++;}
    private static boolean paid(ServerLevel level,Payment payment){var owner=level.getBlockEntity(payment.pos);return owner instanceof CopycatBlockEntity copy&&copy.getMaterial().is(payment.material)&&ItemStack.matches(copy.getConsumedItem(),payment.stock(1))&&ItemStack.matches(player(level).getInventory().getItem(payment.slot),payment.stock(1));}
    private static boolean noMasks(BlockState state){if(!(state.getBlock() instanceof ConnectedPillarBlock)||state.getValue(LayeredBlock.AXIS)!=Direction.Axis.Y)return false;for(var side:Direction.Plane.HORIZONTAL)if(ConnectedPillarBlock.getConnection(state,side))return false;return true;}
    private static ServerPlayer player(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static Block block(String name){var value=BuiltInRegistries.BLOCK.getValue(Create.asResource(name));require(value!=null&&value!=Blocks.AIR,"Real source registered block missing: "+name);return value;}
    private static void set(TestSingleplayerContext world,BlockPos pos,String state){world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state);}
    private static void stand(ClientGameTestContext context,TestSingleplayerContext world,Payment payment){var pos=payment.platform();var point=new Vec3(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);world.getServer().runCommand("tp @p "+point.x+" "+point.y+" "+point.z);context.waitFor(client->!client.player.isSpectator()&&client.player.onGround()&&client.player.position().distanceToSqr(point)<.05,250);}
    private static void look(ClientGameTestContext context,Vec3 target){context.runOnClient(client->{var delta=target.subtract(client.player.getEyePosition());client.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));client.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.waitTicks(3);}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message){var done=new AtomicBoolean();for(int tick=0;tick<ticks&&!done.get();tick++){world.getServer().runOnServer(ignored->done.set(predicate.test(world.getConnection().getServerLevel())));if(!done.get())context.waitTicks(1);}require(done.get(),message);}
    private static void write(Report report){try{Files.createDirectories(REPORT.getParent());Files.writeString(REPORT,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
