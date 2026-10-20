package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.equipment.armor.*;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real survival equipment, finite air, native lava ticks and progress. No direct callback/trigger. UNRUN. */
public final class LavaAttemptAdvancementGameTests {
    private static final BlockPos P=new BlockPos(3,3,3);
    private static String diagnostic(GameTestHelper h,net.minecraft.server.level.ServerPlayer player){
        var pos=player.blockPosition();var chunk=h.getLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        var nativeFields=new StringBuilder();
        for(var field:net.minecraft.server.level.ServerPlayer.class.getDeclaredFields()){
            var name=field.getName().toLowerCase(java.util.Locale.ROOT);
            if(!name.contains("invulner")&&!name.contains("changingdimension"))continue;
            try{field.setAccessible(true);nativeFields.append(field.getName()).append('=').append(field.get(player)).append(';');}
            catch(ReflectiveOperationException|RuntimeException unavailable){nativeFields.append(field.getName()).append("=<unavailable>;");}
        }
        return " inLava="+player.isInLava()+" fireImmune="+player.fireImmune()+" health="+player.getHealth()+" maxHealth="+player.getMaxHealth()+" ticks="+player.tickCount+" position="+player.position()+" bodyFluid="+h.getLevel().getFluidState(pos).getType()+" alive="+player.isAlive()+" removed="+player.isRemoved()+" abilitiesInvulnerable="+player.getAbilities().invulnerable+" nativeLevelIdentity="+(h.getLevel().getEntity(player.getUUID())==player)+" playerListIdentity="+(h.getLevel().getServer().getPlayerList().getPlayer(player.getUUID())==player)+" chunkStatus="+(chunk==null?"missing":chunk.getFullStatus())+" nativeFields="+nativeFields;
    }
    private static String chestDiagnostic(GameTestHelper h,net.minecraft.server.level.ServerPlayer player,ItemStack original,int expectedAir){
        var current=player.getItemBySlot(EquipmentSlot.CHEST);
        return diagnostic(h,player)+" chestIdentity="+(current==original)+" currentChest="+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(current.getItem())+" currentCount="+current.getCount()+" currentRawAir="+current.getOrDefault(AllDataComponents.BACKTANK_AIR,-1)+" currentDamage="+current.getDamageValue()+" originalTank="+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(original.getItem())+" originalCount="+original.getCount()+" originalRawAir="+original.getOrDefault(AllDataComponents.BACKTANK_AIR,-1)+" originalDamage="+original.getDamageValue()+" expectedAir="+expectedAir;
    }
    private static Object nativeTeleportField(net.minecraft.server.level.ServerPlayer player,String name){
        try{var field=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField(name);field.setAccessible(true);return field.get(player.connection);}
        catch(ReflectiveOperationException failure){throw new AssertionError("Actual native teleport field unavailable: "+name,failure);}
    }
    private static void acknowledgeActualNativeLoad(GameTestHelper h,net.minecraft.server.level.ServerPlayer player){
        var pending=nativeTeleportField(player,"awaitingPositionFromClient");var id=nativeTeleportField(player,"awaitingTeleport");
        h.assertTrue(pending instanceof Vec3&&pending.equals(player.position())&&id instanceof Integer,"Acknowledge only actual server-issued pending target/ID for this physical native teleport");
        var target=(Vec3)pending;
        player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket((Integer)id,target.x,target.y,target.z,player.getYRot(),player.getXRot()));
        h.assertTrue(nativeTeleportField(player,"awaitingPositionFromClient")==null,"Actual native teleport acknowledgment consumes the exact pending target");
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        h.assertTrue(player.connection.hasClientLoaded(),"Actual native player-loaded packet establishes connection readiness");
    }
    private static void attempt(GameTestHelper h,Item helmet,Item tank,int air,boolean earns){
        for(int y=2;y<=5;y++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)h.setBlock(new BlockPos(3+dx,y,3+dz),Blocks.LAVA);
        h.setBlock(P.below(2),Blocks.STONE);
        var session=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,3));
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup=()->{if(closed.compareAndSet(false,true))session.close();};
        ((com.simibubi.create.test.mixin.GameTestHelperNativeInfoAccessor)(Object)h).create$getActualNativeTestInfo().addListener(new net.minecraft.gametest.framework.GameTestListener(){
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo info,net.minecraft.gametest.framework.GameTestRunner runner){cleanup.run();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo info,net.minecraft.gametest.framework.GameTestRunner runner){cleanup.run();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo old,net.minecraft.gametest.framework.GameTestInfo next,net.minecraft.gametest.framework.GameTestRunner runner){cleanup.run();}
        });
        var player=session.player();
        player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(helmet));
        var physicalTank=tank==Items.AIR?ItemStack.EMPTY:new ItemStack(tank);if(!physicalTank.isEmpty())physicalTank.set(AllDataComponents.BACKTANK_AIR,air);
        player.setItemSlot(EquipmentSlot.CHEST,physicalTank);player.setNoGravity(true);
        h.assertTrue(!GameplayAdvancementGameTests.done(player,"diving_suit_lava"),"Actual source advancement is loaded and inventory/equipment alone did not grant lava attempt");
        var pos=Vec3.atLowerCornerOf(h.absolutePos(P)).add(.5,.1,.5);
        h.assertTrue(player.teleportTo(h.getLevel(),pos.x,pos.y,pos.z,Set.of(),0,0,false),"Real registered survival player physically enters actual native lava");
        acknowledgeActualNativeLoad(h,player);
        // placeNewPlayer's EmbeddedChannel is not in ServerConnectionListener's connection list.
        // Service its normal public native listener tick once per global test tick: tickPlayer -> doTick.
        h.onEachTick(()->{if(!closed.get())player.connection.tick();});
        h.startSequence().thenWaitUntil(()->{
            h.assertTrue(player.isInLava()&&player.isAlive()&&player.getHealth()>0&&player.getHealth()<player.getMaxHealth(),"Wait for first observed actual lava injury while the native player is alive;"+chestDiagnostic(h,player,physicalTank,air));
        }).thenExecute(()->{
            try{
            h.assertTrue(player.isInLava()&&player.isAlive()&&player.getHealth()>0&&!player.fireImmune()&&player.getHealth()<player.getMaxHealth(),"Actual lava baseTicks apply real damage to a live player; mixed/incomplete gear receives no manufactured fire protection;"+chestDiagnostic(h,player,physicalTank,air));
            h.assertTrue(!DivingHelmetItem.canProvideAir(player,true),"Fixture lacks the complete fire-resistant helmet/air-tank breathing pair;"+chestDiagnostic(h,player,physicalTank,air));
            h.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST)==physicalTank&&(physicalTank.isEmpty()||BacktankUtil.getAir(physicalTank)==air),"Exact original chest owner retains finite air without granting protected lava breathing;"+chestDiagnostic(h,player,physicalTank,air));
            h.assertTrue(GameplayAdvancementGameTests.done(player,"diving_suit_lava")==earns,"Pinned attempt criterion follows actual lava, fire-resistant helmet and any finite-air tank, independently of successful fireproof breathing;"+chestDiagnostic(h,player,physicalTank,air));
            }finally{cleanup.run();}
        }).thenSucceed();
    }
    @GameTest(maxTicks=100) public void realLavaAttemptWithNetheriteHelmetAndCopperAirTankEarnsOriginalCriterion(GameTestHelper h){attempt(h,AllItems.NETHERITE_DIVING_HELMET,AllItems.COPPER_BACKTANK,17,true);}
    @GameTest(maxTicks=100) public void actualLavaWithoutAnyAirTankDoesNotEarnAttempt(GameTestHelper h){attempt(h,AllItems.NETHERITE_DIVING_HELMET,Items.AIR,0,false);}
    @GameTest(maxTicks=100) public void actualLavaWithEmptyOriginalTankDoesNotEarnAttempt(GameTestHelper h){attempt(h,AllItems.NETHERITE_DIVING_HELMET,AllItems.COPPER_BACKTANK,0,false);}
    @GameTest(maxTicks=100) public void actualLavaWithCopperHelmetAndNetheriteAirTankDoesNotEarnAttempt(GameTestHelper h){attempt(h,AllItems.COPPER_DIVING_HELMET,AllItems.NETHERITE_BACKTANK,17,false);}
}
