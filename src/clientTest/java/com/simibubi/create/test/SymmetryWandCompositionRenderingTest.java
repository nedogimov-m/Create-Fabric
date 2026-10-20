package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.equipment.symmetryWand.*;
import com.simibubi.create.content.equipment.symmetryWand.mirror.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Physical native two-wand composition, finite material custody and original Extendo break charge. UNRUN. */
final class SymmetryWandCompositionRenderingTest {
    private static final BlockPos SOURCE=new BlockPos(63010,5,10),A=SOURCE.east(),B=SOURCE.south(),AX=SOURCE.east(2),BZ=SOURCE.south(2),CORNER=SOURCE.offset(2,0,2);
    static void run(ClientGameTestContext c,TestSingleplayerContext w){
        var server=w.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 63008.5 10 10.5");c.waitFor(client->client.level.isLoaded(SOURCE)&&client.level.isLoaded(CORNER),450);
        NativeScreenshotReadiness.beforeFloor(c, w, new BlockPos(63006, 4, 6), new BlockPos(63016, 4, 16), SOURCE, CORNER);
        server.runCommand("fill 63006 4 6 63016 4 16 minecraft:smooth_quartz");
        NativeScreenshotReadiness.afterFloor(c, w, new BlockPos(63006, 4, 6), new BlockPos(63016, 4, 16));server.runCommand("fill 63006 5 6 63016 8 16 minecraft:air");server.runCommand("clear @p");server.runCommand("gamemode survival @p");stand(c,w,63008.5,10.5);select(c,w,InputConstants.KEY_2,1);select(c,w,InputConstants.KEY_1,0);
        var wandA=new AtomicReference<ItemStack>();var wandB=new AtomicReference<ItemStack>();var originalExtendo=new AtomicReference<ItemStack>();
        server.runOnServer(ignored->{var player=actual(w.getConnection().getServerLevel());wandA.set(new ItemStack(AllItems.WAND_OF_SYMMETRY));wandB.set(new ItemStack(AllItems.WAND_OF_SYMMETRY));originalExtendo.set(new ItemStack(AllItems.EXTENDO_GRIP));slot(player,0,wandA.get());slot(player,7,wandB.get());slot(player,1,new ItemStack(Items.COBBLESTONE,8));slot(player,2,new ItemStack(Items.DIAMOND_PICKAXE));slot(player,4,originalExtendo.get());});
        c.waitFor(client->client.player.getMainHandItem().is(AllItems.WAND_OF_SYMMETRY)&&client.player.getInventory().getItem(7).is(AllItems.WAND_OF_SYMMETRY)&&client.player.getInventory().getItem(1).getCount()==8,250);
        use(c,A.below());await(c,w,level->configured(actual(level).getInventory().getItem(0),A,PlaneMirror.Align.YZ.ordinal()),220,"Physical first actual wand did not configure independent YZ plane");
        select(c,w,InputConstants.KEY_8,7);stand(c,w,63010.5,14.5);use(c,B.below());await(c,w,level->configured(actual(level).getInventory().getItem(7),B,PlaneMirror.Align.XY.ordinal()),220,"Physical second actual wand did not configure independent XY plane");
        select(c,w,InputConstants.KEY_2,1);stand(c,w,63008.5,10.5);use(c,SOURCE.below());await(c,w,level->{var player=actual(level);return List.of(SOURCE,AX,BZ,CORNER).stream().allMatch(pos->level.getBlockState(pos).is(Blocks.COBBLESTONE))&&player.getInventory().countItem(Items.COBBLESTONE)==4&&player.getInventory().getItem(0)==wandA.get()&&player.getInventory().getItem(7)==wandB.get()&&configured(wandA.get(),A,1)&&configured(wandB.get(),B,0)&&!wandA.get().getOrDefault(AllDataComponents.SYMMETRY_WAND_SIMULATE,false)&&!wandB.get().getOrDefault(AllDataComponents.SYMMETRY_WAND_SIMULATE,false);},250,"One physical native placement did not compose exactly four actual paid blocks while preserving both actual enabled wand owners");
        c.waitFor(client->List.of(SOURCE,AX,BZ,CORNER).stream().allMatch(pos->client.level.getBlockState(pos).is(Blocks.COBBLESTONE))&&client.player.getInventory().getItem(1).getCount()==4&&SymmetryWandClient.latest().markers().size()==2,250);
        select(c,w,InputConstants.KEY_1,0);look(c,Vec3.atCenterOf(SOURCE).add(.5,.4,.5));screenshot(c,"create-vulkan-two-actual-perpendicular-wands-four-finite-paid-native-blocks");
        select(c,w,InputConstants.KEY_5,4);c.getInput().pressKey(options->options.keySwapOffhand);await(c,w,level->actual(level).getOffhandItem()==originalExtendo.get()&&actual(level).getInventory().getItem(4).isEmpty()&&originalExtendo.get().getDamageValue()==0,220,"Physical F did not move the original unused Extendo into the true offhand after all placement");
        select(c,w,InputConstants.KEY_3,2);look(c,Vec3.atCenterOf(SOURCE));c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(SOURCE),220);c.getInput().holdKey(options->options.keyAttack);
        try{await(c,w,level->level.getBlockState(SOURCE).isAir()&&level.getBlockState(AX).isAir()&&level.getBlockState(BZ).isAir()&&level.getBlockState(CORNER).is(Blocks.COBBLESTONE),260,"Physical original mining did not remove only original and two source-parity reflected native targets");}finally{c.getInput().releaseKey(options->options.keyAttack);}
        await(c,w,level->{var player=actual(level);int loose=level.getEntitiesOfClass(ItemEntity.class,new AABB(SOURCE).inflate(5)).stream().filter(entity->entity.getItem().is(Items.COBBLESTONE)).mapToInt(entity->entity.getItem().getCount()).sum();return player.getMainHandItem().is(Items.DIAMOND_PICKAXE)&&player.getMainHandItem().getDamageValue()==3&&player.getOffhandItem()==originalExtendo.get()&&originalExtendo.get().getDamageValue()==1&&player.getInventory().countItem(Items.COBBLESTONE)+loose+1==8&&configured(wandA.get(),A,1)&&configured(wandB.get(),B,0);},250,"Three actual native tool uses/original single Extendo action/exact original eight-material conservation failed");
        c.waitFor(client->client.level.getBlockState(SOURCE).isAir()&&client.level.getBlockState(AX).isAir()&&client.level.getBlockState(BZ).isAir()&&client.level.getBlockState(CORNER).is(Blocks.COBBLESTONE)&&client.player.getOffhandItem().is(AllItems.EXTENDO_GRIP)&&client.player.getOffhandItem().getDamageValue()==1,220);
        select(c,w,InputConstants.KEY_1,0);look(c,Vec3.atCenterOf(CORNER));screenshot(c,"create-vulkan-two-native-wand-markers-retained-corner-and-one-original-extendo-charge");server.runCommand("gamemode spectator @p");
    }
    private static boolean configured(ItemStack stack,BlockPos center,int orientation){var mirror=SymmetryWandItem.getMirror(stack);return SymmetryWandItem.isEnabled(stack)&&mirror.getClass()==PlaneMirror.class&&mirror.getPosition().equals(Vec3.atLowerCornerOf(center))&&mirror.getOrientationIndex()==orientation;}
    private static void screenshot(ClientGameTestContext c,String label){c.waitFor(client->client.gui.overlay()==null&&SymmetryWandClient.latest().markers().size()==2,220);c.runOnClient(client->{client.gui.hud.getChat().clearMessages(false);SymmetryWandCompositionNativeProbe.begin(label,A,B);});try{c.takeScreenshot(label);}finally{c.runOnClient(client->SymmetryWandCompositionNativeProbe.end());}}
    private static void stand(ClientGameTestContext c,TestSingleplayerContext w,double x,double z){w.getServer().runCommand("tp @p "+x+" 5 "+z);c.waitFor(client->client.player.onGround()&&!client.player.isSpectator()&&Math.abs(client.player.getX()-x)<.15&&Math.abs(client.player.getZ()-z)<.15&&client.gameRenderer.mainCamera().position().distanceToSqr(client.player.getEyePosition())<1,250);}
    private static void use(ClientGameTestContext c,BlockPos floor){look(c,Vec3.atCenterOf(floor).add(0,.499,0));c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(floor)&&hit.getDirection()==Direction.UP,220);c.getInput().pressKey(options->options.keyUse);}
    private static void look(ClientGameTestContext c,Vec3 point){c.runOnClient(client->{var diff=point.subtract(client.player.getEyePosition());client.player.setYRot((float)Math.toDegrees(Math.atan2(-diff.x,diff.z)));client.player.setXRot((float)-Math.toDegrees(Math.atan2(diff.y,Math.sqrt(diff.x*diff.x+diff.z*diff.z))));});c.waitTicks(4);}
    private static void select(ClientGameTestContext c,TestSingleplayerContext w,int key,int slot){c.getInput().pressKey(key);c.waitFor(client->client.player.getInventory().getSelectedSlot()==slot,200);await(c,w,level->actual(level).getInventory().getSelectedSlot()==slot,200,"Native selected slot was not acknowledged");}
    private static void slot(ServerPlayer player,int slot,ItemStack stack){player.getInventory().setItem(slot,stack);player.connection.send(player.getInventory().createInventoryUpdatePacket(slot));}
    private static ServerPlayer actual(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static void await(ClientGameTestContext c,TestSingleplayerContext w,Predicate<ServerLevel> predicate,int ticks,String failure){var accepted=new AtomicBoolean();for(int tick=0;tick<ticks;tick++){w.getServer().runOnServer(ignored->accepted.set(predicate.test(w.getConnection().getServerLevel())));if(accepted.get())return;c.waitTicks(1);}throw new AssertionError(failure);}
    private SymmetryWandCompositionRenderingTest(){}
}
