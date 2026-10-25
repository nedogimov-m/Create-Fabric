package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.blueprint.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.*;

/** Physical live capture, harmless sword combat, ghost editing and paid blueprint crafting. */
public final class UtilityItemUseClientTest implements FabricClientGameTest {
    private static final BlockPos TARGET=new BlockPos(48,5,51);
    private final List<String> passed=new ArrayList<>();
    @Override public void runTest(ClientGameTestContext c){
        try(var w=c.worldBuilder().create()){
            var server=w.getServer();server.runCommand("difficulty normal");server.runCommand("gamemode spectator @p");server.runCommand("tp @p 48.5 10 54.5");
            var min=new BlockPos(32,4,32);var max=new BlockPos(64,4,64);c.waitFor(client->client.level.isLoaded(min)&&client.level.isLoaded(max),400);
            NativeScreenshotReadiness.beforeFloor(c,w,min,max,TARGET,TARGET.south(3));
            server.runCommand("fill 32 5 32 64 30 64 minecraft:air");server.runCommand("fill 32 4 32 64 4 64 minecraft:smooth_quartz");
            NativeScreenshotReadiness.afterFloor(c,w,min,max);server.runCommand("gamemode survival @p");server.runCommand("tp @p 48.5 5 54.5");
            c.waitFor(client->client.player.onGround()&&!client.player.isSpectator()&&client.player.distanceToSqr(48.5,5,54.5)<.01,200);
            c.getInput().pressKey(InputConstants.KEY_2);c.getInput().pressKey(InputConstants.KEY_1);
            await(c,w,level->player(level).getInventory().getSelectedSlot()==0,"Native first hotbar slot");
            capture(c,w);sword(c,w);hatch(c,w);blueprint(c,w);discardList(c,w);
        }finally{
            c.getInput().releaseKey(options->options.keyShift);c.getInput().releaseKey(options->options.keyUse);
            try{var file=Path.of("build/reports/utility-item-physical-use.json");Files.createDirectories(file.toAbsolutePath().getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(passed)+"\n");}
            catch(Exception failure){throw new AssertionError(failure);}
        }
    }
    private void capture(ClientGameTestContext c,TestSingleplayerContext w){
        supply(c,w,new ItemStack(AllItems.EMPTY_BLAZE_BURNER,2));var id=new AtomicReference<UUID>();
        w.getServer().runOnServer(ignored->{var level=w.getConnection().getServerLevel();var blaze=EntityTypes.BLAZE.create(level,EntitySpawnReason.COMMAND);blaze.setNoAi(true);blaze.setPos(Vec3.atBottomCenterOf(TARGET));level.addFreshEntity(blaze);id.set(blaze.getUUID());});
        c.waitFor(client->find(client.level,id.get())!=null,200);look(c,Vec3.atCenterOf(TARGET).add(0,.5,0));
        c.waitFor(client->client.hitResult instanceof EntityHitResult hit&&hit.getEntity().getUUID().equals(id.get()),200);
        c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->level.getEntity(id.get())==null&&player(level).getMainHandItem().is(AllItems.EMPTY_BLAZE_BURNER)&&player(level).getMainHandItem().getCount()==1&&player(level).getInventory().countItem(AllItems.BLAZE_BURNER)==1,"Actual live blaze capture must consume one cage and supply one filled burner");
        passed.add("Physical right click captures an actual tracked blaze, debits one cage and supplies one burner");
        w.getServer().runOnServer(ignored->{var level=w.getConnection().getServerLevel();level.setBlockAndUpdate(TARGET,Blocks.SPAWNER.defaultBlockState());((SpawnerBlockEntity)level.getBlockEntity(TARGET)).getSpawner().setEntityId(EntityTypes.BLAZE,level,level.getRandom(),TARGET);});
        c.waitFor(client->client.level.getBlockState(TARGET).is(Blocks.SPAWNER),200);look(c,Vec3.atCenterOf(TARGET).add(0,0,.49));
        c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(TARGET),200);c.waitTicks(5);c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->player(level).getMainHandItem().is(AllItems.BLAZE_BURNER)&&player(level).getInventory().countItem(AllItems.EMPTY_BLAZE_BURNER)==0&&player(level).getInventory().countItem(AllItems.BLAZE_BURNER)==2&&level.getBlockState(TARGET).is(Blocks.SPAWNER),"Actual blaze spawner capture must preserve the spawner and debit the final original cage");
        passed.add("Physical spawner right click fills the final original cage without destroying the spawner");
        c.takeScreenshot("create-native-blaze-capture-finite-two-cages");w.getServer().runOnServer(ignored->w.getConnection().getServerLevel().setBlockAndUpdate(TARGET,Blocks.AIR.defaultBlockState()));
    }
    private void sword(ClientGameTestContext c,TestSingleplayerContext w){
        supply(c,w,new ItemStack(AllItems.CARDBOARD_SWORD));var id=new AtomicReference<UUID>();
        w.getServer().runOnServer(ignored->{var level=w.getConnection().getServerLevel();var pig=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);pig.setPos(Vec3.atBottomCenterOf(TARGET.south()));level.addFreshEntity(pig);id.set(pig.getUUID());});
        c.waitFor(client->find(client.level,id.get())!=null,200);look(c,Vec3.atBottomCenterOf(TARGET.south()).add(0,.5,0));
        c.waitFor(client->client.hitResult instanceof EntityHitResult hit&&hit.getEntity().getUUID().equals(id.get()),200);c.getInput().pressKey(options->options.keyAttack);
        var observation=new AtomicReference<String>();
        await(c,w,level->{var entity=level.getEntity(id.get());if(!(entity instanceof LivingEntity pig))return false;
            if(pig.hasEffect(MobEffects.SLOWNESS)||observation.get()==null)observation.set("health="+pig.getHealth()+"/"+pig.getMaxHealth()+", slow="+pig.getEffect(MobEffects.SLOWNESS)+", position="+pig.position()+", motion="+pig.getDeltaMovement()+", held="+player(level).getMainHandItem()+", player="+player(level).position());
            return pig.hasEffect(MobEffects.SLOWNESS)&&pig.getHealth()==pig.getMaxHealth()&&pig.getZ()<TARGET.getZ()+1.45;
        },"Physical cardboard sword hit must push and slow a pig without hurting it",observation);
        w.getServer().runOnServer(ignored->{var level=w.getConnection().getServerLevel();level.getEntity(id.get()).discard();var spider=EntityTypes.SPIDER.create(level,EntitySpawnReason.COMMAND);spider.setNoAi(true);spider.setPos(Vec3.atBottomCenterOf(TARGET.south()));level.addFreshEntity(spider);id.set(spider.getUUID());});
        c.waitFor(client->find(client.level,id.get())!=null,200);c.waitTicks(25);look(c,Vec3.atBottomCenterOf(TARGET.south()).add(0,.5,0));
        c.waitFor(client->client.hitResult instanceof EntityHitResult hit&&hit.getEntity().getUUID().equals(id.get()),200);c.getInput().pressKey(options->options.keyAttack);
        await(c,w,level->level.getEntity(id.get()) instanceof LivingEntity spider&&spider.getHealth()<spider.getMaxHealth()&&player(level).getMainHandItem().is(AllItems.CARDBOARD_SWORD)&&!player(level).getMainHandItem().isDamageableItem(),"Physical cardboard sword must hurt an arthropod and retain its source no-wear behavior");
        passed.add("Physical cardboard sword combat: harmless pig knockback, arthropod damage, no tool wear");
        w.getServer().runOnServer(ignored->w.getConnection().getServerLevel().getEntity(id.get()).discard());
    }
    private void blueprint(ClientGameTestContext c,TestSingleplayerContext w){
        supply(c,w,new ItemStack(AllItems.CRAFTING_BLUEPRINT));look(c,Vec3.atBottomCenterOf(TARGET).add(0,-.001,0));
        c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(TARGET.below()),200);c.getInput().pressKey(options->options.keyUse);
        var id=new AtomicReference<UUID>();await(c,w,level->{var boards=level.getEntitiesOfClass(BlueprintEntity.class,new AABB(TARGET).inflate(5));if(boards.size()!=1)return false;id.set(boards.getFirst().getUUID());return player(level).getMainHandItem().isEmpty()&&boards.getFirst().getDirection()==net.minecraft.core.Direction.UP;},"Native floor blueprint placement must consume exactly one original board");
        c.waitFor(client->find(client.level,id.get())!=null&&client.player.getMainHandItem().isEmpty(),200);
        // Block placement reaches farther than native entity interaction. Step into the board's actual use range.
        w.getServer().runCommand("tp @p 48.5 5 53.5");c.waitFor(client->client.player.distanceToSqr(48.5,5,53.5)<.01,200);
        w.getServer().runOnServer(ignored->{var player=player(w.getConnection().getServerLevel());player.getInventory().setItem(9,new ItemStack(Items.OAK_PLANKS,4));player.containerMenu.broadcastChanges();});
        var at=c.computeOnClient(client->find(client.level,id.get()).getBoundingBox().getCenter());look(c,at);
        try{c.waitFor(client->client.hitResult instanceof EntityHitResult hit&&hit.getEntity().getUUID().equals(id.get()),200);}
        catch(AssertionError failure){var diagnostic=c.computeOnClient(client->{var board=(BlueprintEntity)find(client.level,id.get());return "hit="+client.hitResult+", board="+board.getBoundingBox()+", facing="+board.getDirection()+", player="+client.player.getEyePosition()+", entityReach="+client.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);});throw new AssertionError("Native blueprint pick failed: "+diagnostic,failure);}
        c.waitTicks(5);c.getInput().pressKey(options->options.keyUse);
        c.waitFor(client->client.gui.screen() instanceof BlueprintScreen,200);
        click(c,false,9);c.waitFor(client->client.player.containerMenu.getCarried().is(Items.OAK_PLANKS)&&client.player.containerMenu.getCarried().getCount()==4,200);
        click(c,true,0);c.waitFor(client->((BlueprintMenu)client.player.containerMenu).ghostItems().getItem(0).is(Items.OAK_PLANKS),200);
        click(c,true,3);c.waitFor(client->((BlueprintMenu)client.player.containerMenu).ghostItems().getItem(9).is(Items.STICK),200);
        click(c,false,9);c.waitFor(client->client.player.containerMenu.getCarried().isEmpty(),200);
        await(c,w,level->player(level).containerMenu instanceof BlueprintMenu menu&&menu.ghostItems().getItem(9).is(Items.STICK)&&menu.section.getItems().getItem(9).is(Items.STICK)&&player(level).getInventory().countItem(Items.OAK_PLANKS)==4,"Actual native ghost editing must infer the recipe and preserve all original wood");
        c.takeScreenshot("create-native-blueprint-actual-ghost-editing-four-original-planks");c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitFor(client->client.gui.screen()==null,200);c.waitTicks(5);look(c,at);
        c.getInput().pressKey(options->options.keyUse);await(c,w,level->player(level).getInventory().countItem(Items.STICK)==4&&player(level).getInventory().countItem(Items.OAK_PLANKS)==2,"Native board right click must pay two planks for four real sticks");
        c.getInput().holdKey(options->options.keyShift);await(c,w,level->player(level).isShiftKeyDown(),"Native sneak state for blueprint batch");look(c,at);c.waitTicks(5);c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->player(level).getInventory().countItem(Items.STICK)==8&&player(level).getInventory().countItem(Items.OAK_PLANKS)==0,"Native sneak craft must stop after the final two original planks");c.getInput().releaseKey(options->options.keyShift);
        c.runOnClient(client->{var board=(BlueprintEntity)find(client.level,id.get());var state=(BlueprintRenderer.State)client.getEntityRenderDispatcher().extractEntity(board,.5f);if(state.snapshot()==null||state.snapshot().board().size()==0||state.snapshot().icons().isEmpty()||state.snapshot().icons().stream().anyMatch(icon->icon.model().isEmpty()))throw new AssertionError("Actual placed blueprint native body/recipe icons are invisible");});
        c.takeScreenshot("create-native-blueprint-actual-paid-world-crafting-and-source-icons");
        passed.add("Physical floor blueprint placement, native GUI ghost editing, recipe inference and finite normal/sneak crafting: four planks -> eight sticks");
    }
    private void hatch(ClientGameTestContext c,TestSingleplayerContext w){
        var hatch=com.simibubi.create.AllBlocks.ITEM_HATCH;var support=TARGET.north();
        w.getServer().runOnServer(ignored->w.getConnection().getServerLevel().setBlockAndUpdate(support,Blocks.BARREL.defaultBlockState()));
        supply(c,w,new ItemStack(hatch,2));c.waitFor(client->client.level.getBlockState(support).is(Blocks.BARREL),200);look(c,Vec3.atCenterOf(support).add(0,0,.499));
        c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(support),200);c.getInput().holdKey(options->options.keyShift);await(c,w,level->player(level).isShiftKeyDown(),"Native sneak placement on the real barrel");
        c.getInput().pressKey(options->options.keyUse);await(c,w,level->level.getBlockState(TARGET).is(hatch)&&player(level).getMainHandItem().getCount()==1,"Native rear-mounted item hatch placement debits one original hatch");c.getInput().releaseKey(options->options.keyShift);
        supply(c,w,new ItemStack(Items.APPLE,7));w.getServer().runOnServer(ignored->{var player=player(w.getConnection().getServerLevel());player.getInventory().setItem(1,new ItemStack(Items.COBBLESTONE,5));player.getInventory().setItem(9,new ItemStack(Items.IRON_INGOT,3));player.containerMenu.broadcastChanges();});
        c.waitFor(client->client.player.getInventory().getItem(9).is(Items.IRON_INGOT),200);await(c,w,level->!player(level).isShiftKeyDown(),"Native ordinary hatch deposit state");look(c,Vec3.atLowerCornerOf(TARGET).add(.2,.65,.12));
        c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(TARGET),200);c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->player(level).getMainHandItem().isEmpty()&&player(level).getInventory().countItem(Items.IRON_INGOT)==3&&stored(level,support,Items.APPLE)==7,"Physical hatch right click deposits exactly the original held stack");
        c.getInput().holdKey(options->options.keyShift);await(c,w,level->player(level).isShiftKeyDown(),"Native hatch backpack deposit state");look(c,Vec3.atLowerCornerOf(TARGET).add(.2,.65,.12));c.waitTicks(5);c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->player(level).getInventory().countItem(Items.IRON_INGOT)==0&&player(level).getInventory().countItem(Items.COBBLESTONE)==5&&stored(level,support,Items.IRON_INGOT)==3&&stored(level,support,Items.APPLE)==7,"Physical sneak hatch deposit moves the original backpack stock and preserves the source hotbar");c.getInput().releaseKey(options->options.keyShift);
        passed.add("Native item hatch placement and held/backpack deposits preserve all original variants, exact totals and hotbar stock");c.takeScreenshot("create-native-item-hatch-paid-placement-held-and-backpack-stock");
        w.getServer().runOnServer(ignored->{var level=w.getConnection().getServerLevel();level.setBlockAndUpdate(TARGET,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(support,Blocks.AIR.defaultBlockState());});
    }
    private static int stored(ServerLevel level,BlockPos pos,Item item){var barrel=(net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(pos);int count=0;for(int slot=0;slot<barrel.getContainerSize();slot++)if(barrel.getItem(slot).is(item))count+=barrel.getItem(slot).getCount();return count;}
    private void discardList(ClientGameTestContext c,TestSingleplayerContext w){
        supply(c,w,new ItemStack(AllItems.SHOPPING_LIST));c.getInput().lookAt(0,-80);c.getInput().holdKey(options->options.keyShift);await(c,w,level->player(level).isShiftKeyDown(),"Native sneak discard state");
        c.getInput().pressKey(options->options.keyUse);await(c,w,level->player(level).getMainHandItem().isEmpty(),"Native shopping list discard");c.getInput().releaseKey(options->options.keyShift);passed.add("Physical sneak-use discards the actual shopping list");
    }
    private static void click(ClientGameTestContext c,boolean ghost,int index){
        var point=c.computeOnClient(client->{var screen=(BlueprintScreen)client.gui.screen();var menu=screen.getMenu();var origin=(com.simibubi.create.test.mixin.NativeContainerScreenAccessor)(Object)screen;var slot=menu.slots.stream().filter(s->s.container==(ghost?menu.ghostItems():client.player.getInventory())&&s.getContainerSlot()==index).findFirst().orElseThrow();var window=client.getWindow();return new Vec2((origin.create$getLeft()+slot.x+8f)*window.getScreenWidth()/window.getGuiScaledWidth(),(origin.create$getTop()+slot.y+8f)*window.getScreenHeight()/window.getGuiScaledHeight());});
        c.getInput().setCursorPos(point.x,point.y);c.waitTicks(2);c.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }
    private static void supply(ClientGameTestContext c,TestSingleplayerContext w,ItemStack stock){w.getServer().runCommand("clear @p");w.getServer().runOnServer(ignored->{var player=player(w.getConnection().getServerLevel());player.getInventory().setItem(0,stock);player.containerMenu.broadcastChanges();});c.waitFor(client->ItemStack.matches(client.player.getMainHandItem(),stock),200);c.waitTicks(5);}
    private static ServerPlayer player(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static Entity find(net.minecraft.world.level.Level level,UUID id){if(level instanceof ServerLevel server)return server.getEntity(id);for(var entity:((net.minecraft.client.multiplayer.ClientLevel)level).entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null;}
    private static void await(ClientGameTestContext c,TestSingleplayerContext w,Predicate<ServerLevel> condition,String message){var ok=new AtomicBoolean();for(int tick=0;tick<300;tick++){w.getServer().runOnServer(ignored->ok.set(condition.test(w.getConnection().getServerLevel())));if(ok.get())return;c.waitTicks(1);}throw new AssertionError(message);}
    private static void await(ClientGameTestContext c,TestSingleplayerContext w,Predicate<ServerLevel> condition,String message,AtomicReference<String> diagnostic){try{await(c,w,condition,message);}catch(AssertionError failure){throw new AssertionError(message+"; "+diagnostic.get(),failure);}}
    private static void look(ClientGameTestContext c,Vec3 target){var angles=c.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});c.getInput().lookAt(angles.x,angles.y);}
}
