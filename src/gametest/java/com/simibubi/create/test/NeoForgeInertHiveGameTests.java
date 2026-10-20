package com.simibubi.create.test;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.foundation.utility.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Original codec-empty slots remain real native capacity and follow actual physical hive ownership. */
public final class NeoForgeInertHiveGameTests {
    private static final BlockPos SOURCE=new BlockPos(2,2,2),TARGET=new BlockPos(4,2,2);
    private static ListTag rows(Tag... values){var result=new ListTag();for(var value:values)result.add(value);return result;}
    private static CompoundTag inert(int ticks,int min,boolean explicit){
        var row=new CompoundTag();row.putInt("ticks_in_hive",ticks);row.putInt("min_ticks_in_hive",min);
        if(explicit)row.put("entity_data",new CompoundTag());return row;
    }
    private static CompoundTag real(int ticks,int min,float health){
        var row=inert(ticks,min,false);var bee=new CompoundTag();bee.putString("id","minecraft:bee");bee.putFloat("Health",health);bee.putInt("Age",-100);
        row.put("entity_data",bee);return row;
    }
    private static CompoundTag original(BlockPos pos,ListTag occupants){
        var result=new CompoundTag();result.putString("id","minecraft:beehive");result.putInt("x",pos.getX());result.putInt("y",pos.getY());result.putInt("z",pos.getZ());result.put("bees",occupants.copy());return result;
    }
    private static NeoForgeInertHiveSlots inert(BeehiveBlockEntity hive){return ((NeoForgeInertHiveOwner)hive).create$inertHiveSlots();}
    private static ListTag realRows(GameTestHelper h,BeehiveBlockEntity hive){return hive.saveWithFullMetadata(h.getLevel().registryAccess()).getListOrEmpty("bees");}
    private static void load(GameTestHelper h,BlockEntity owner,CompoundTag data){
        var problems=new ProblemReporter.Collector();owner.loadWithComponents(TagValueInput.create(problems,h.getLevel().registryAccess(),data));
        h.assertTrue(problems.isEmpty(),"Actual native hive decoding must not fall back or drop any typed owner: "+problems.getReport());
    }
    private static BeehiveBlockEntity worldHive(GameTestHelper h,ListTag occupants){
        h.setBlock(SOURCE,Blocks.BEEHIVE);var pos=h.absolutePos(SOURCE);var source=original(pos,occupants);var before=source.copy();
        var converted=NeoForgeBlockEntityImport.convert(source,3955,h.getLevel().registryAccess(),NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),pos));
        var hive=(BeehiveBlockEntity)h.getLevel().getBlockEntity(pos);load(h,hive,converted);
        h.assertTrue(source.equals(before),"Import never mutates the genuine original hive");return hive;
    }
    private static List<Bee> bees(GameTestHelper h){return h.getLevel().getEntitiesOfClass(Bee.class,new AABB(h.absolutePos(SOURCE)).inflate(8));}
    private static void assertFreshReload(GameTestHelper h,BeehiveBlockEntity hive){
        var saved=hive.saveWithFullMetadata(h.getLevel().registryAccess());var fresh=new BeehiveBlockEntity(hive.getBlockPos(),hive.getBlockState());fresh.setLevel(h.getLevel());load(h,fresh,saved);
        h.assertTrue(saved.equals(fresh.saveWithFullMetadata(h.getLevel().registryAccess()))&&inert(fresh).equals(inert(hive))&&fresh.getOccupantCount()==hive.getOccupantCount(),"Fresh native hive preserves exact combined occupancy, immutable original indices and residence timers");
    }
    private static void assertCodecs(GameTestHelper h,ItemStack actual){
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);var encoded=ItemStack.CODEC.encodeStart(ops,actual).getOrThrow();
        var decoded=ItemStack.CODEC.parse(ops,encoded).getOrThrow();
        h.assertTrue(ItemStack.matches(actual,decoded)&&actual.get(AllDataComponents.INERT_HIVE_SLOTS).equals(decoded.get(AllDataComponents.INERT_HIVE_SLOTS)),"Actual ItemStack CODEC preserves genuine bee component and exact inert source interleaving/timers");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{var codec=AllDataComponents.INERT_HIVE_SLOTS.streamCodec();codec.encode(buffer,actual.get(AllDataComponents.INERT_HIVE_SLOTS));h.assertTrue(codec.decode(buffer).equals(actual.get(AllDataComponents.INERT_HIVE_SLOTS))&&buffer.readableBytes()==0,"Actual registered native component network codec preserves exact inert owner");}finally{buffer.release();}
    }

    @GameTest public void inertOnlyHiveTicksOverflowsAndRejectsRealAdmissionWithoutInventingBee(GameTestHelper h){
        var hive=worldHive(h,rows(inert(37,600,false),inert(Integer.MAX_VALUE,2400,true),inert(-7,-1,false)));
        h.assertTrue(hive.getOccupantCount()==3&&hive.isFull()&&!hive.isEmpty()&&realRows(h,hive).isEmpty()&&hive.collectComponents().getOrDefault(DataComponents.BEES,Bees.EMPTY).bees().isEmpty(),"Three original empty codec rows own full real capacity, never typed entity data");
        var before=inert(hive);int count=bees(h).size();
        BeehiveBlockEntity.serverTick(h.getLevel(),hive.getBlockPos(),hive.getBlockState(),hive);
        h.assertTrue(inert(hive).equals(before.tick())&&inert(hive).slots().get(1).ticksInHive()==Integer.MIN_VALUE,"Actual native serverTick advances every original inert timer with source signed-int overflow");
        var afterTick=inert(hive);hive.emptyAllLivingFromHive(null,hive.getBlockState(),BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        h.assertTrue(bees(h).size()==count&&inert(hive).equals(afterTick)&&hive.isFull(),"Actual emergency release cannot manufacture or release an entity from inert capacity");
        var control=h.spawn(EntityTypes.BEE,new BlockPos(2,3,4));var id=control.getUUID();hive.addOccupant(control);
        h.assertTrue(!control.isRemoved()&&h.getLevel().getEntity(id)==control&&hive.getOccupantCount()==3&&realRows(h,hive).isEmpty()&&inert(hive).equals(afterTick),"Actual full addOccupant leaves the explicit control bee alive and unadopted");control.discard();
        assertFreshReload(h,hive);h.succeed();
    }

    @GameTest public void actualNativeBeeReleaseRetainsInertOriginalIndexWhileLiveIndexMoves(GameTestHelper h){
        var hive=worldHive(h,rows(real(37,2400,7.5f),inert(712,600,true),real(83,2400,6.5f)));int before=bees(h).size();
        h.assertTrue(hive.isFull()&&realRows(h,hive).size()==2&&inert(hive).slots().getFirst().index()==1,"Actual mixed native owner starts in its original real/inert/real order");
        hive.emptyAllLivingFromHive(null,hive.getBlockState(),BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        var retained=inert(hive);h.assertTrue(bees(h).size()==before+2&&realRows(h,hive).isEmpty()&&hive.getOccupantCount()==1&&!hive.isFull()&&!hive.isEmpty(),"Actual native emergency release admits exactly the two genuine bees, retaining one real inert capacity slot");
        h.assertTrue(retained.size()==1&&retained.slots().getFirst().sourceIndex()==1&&retained.slots().getFirst().index()==0&&retained.slots().getFirst().ticksInHive()==712,"Live index follows actual native removals while immutable original index and timer remain exact");
        for(var bee:bees(h))bee.discard();assertFreshReload(h,hive);h.succeed();
    }

    @GameTest public void actualSilkBreakDropPickupAndSurvivalPlacementTransferOneCombinedHiveOwner(GameTestHelper h){
        var occupants=rows(inert(37,600,false),real(712,2400,7.5f),inert(93,601,true));var hive=worldHive(h,occupants);var expected=inert(hive);
        var pos=hive.getBlockPos();h.setBlock(TARGET.below(),Blocks.STONE);h.setBlock(TARGET,Blocks.AIR);
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(pos).add(0,0,2))){
            var player=joined.player();var tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);player.setItemInHand(InteractionHand.MAIN_HAND,tool);
            h.assertTrue(player.gameMode.destroyBlock(pos)&&h.getLevel().getBlockState(pos).isAir()&&h.getLevel().getBlockEntity(pos)==null&&hive.isRemoved(),"Actual survival game-mode destruction removes the original world hive before ownership handoff");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.5)).stream().filter(entity->entity.getItem().is(Items.BEEHIVE)).toList();
            h.assertTrue(drops.size()==1&&drops.getFirst().getItem().getCount()==1&&bees(h).isEmpty(),"Actual native loot admits exactly one physical hive item and no released real or phantom bee");
            var drop=drops.getFirst();var dropped=drop.getItem();h.assertTrue(expected.equals(dropped.get(AllDataComponents.INERT_HIVE_SLOTS))&&dropped.getOrDefault(DataComponents.BEES,Bees.EMPTY).bees().size()==1,"Actual native hive BEES loot also transfers its paired inert subset");assertCodecs(h,dropped);
            drop.setNoPickUpDelay();drop.playerTouch(player);
            int slot=-1,total=0;var inventory=player.getInventory();
            for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).is(Items.BEEHIVE)){slot=i;total+=inventory.getItem(i).getCount();}
            h.assertTrue(drop.isRemoved()&&total==1&&slot>0,"Actual native playerTouch removes admitted item entity and transfers exactly one hive to the real survival inventory");
            var actual=inventory.removeItemNoUpdate(slot);var actualTool=inventory.removeItemNoUpdate(0);inventory.setItem(0,actual);inventory.setItem(slot,actualTool);
            h.assertTrue(player.getMainHandItem()==actual&&expected.equals(actual.get(AllDataComponents.INERT_HIVE_SLOTS)),"Placement uses the actual picked-up inventory owner without a copied item fixture");
            var support=h.absolutePos(TARGET.below());var hit=new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false);
            h.assertTrue(((BlockItem)actual.getItem()).place(new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit))).consumesAction()&&actual.isEmpty(),"Actual native BlockItem.place consumes the sole survival hive item");
            var placed=(BeehiveBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(TARGET));
            h.assertTrue(placed!=null&&placed.getOccupantCount()==3&&expected.equals(inert(placed))&&realRows(h,placed).size()==1&&bees(h).isEmpty()&&h.getLevel().getBlockEntity(pos)==null,"Only the placed actual hive owns one genuine bee plus both original inert slots; original world owner stays removed");
            var combined=inert(placed).combinedRows(realRows(h,placed));for(int i=0;i<occupants.size();i++)for(String timer:List.of("ticks_in_hive","min_ticks_in_hive"))h.assertTrue(combined.getCompoundOrEmpty(i).get(timer).equals(occupants.getCompoundOrEmpty(i).get(timer)),"Actual source interleaving and residence timers survive physical transfer and placement");
            for(int i=0;i<inventory.getContainerSize();i++)h.assertTrue(!inventory.getItem(i).is(Items.BEEHIVE),"No second hive item remains in actual player inventory");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(4)).stream().noneMatch(entity->entity.getItem().is(Items.BEEHIVE)),"No duplicate hive drop remains after actual transfer");assertFreshReload(h,placed);
            placed.applyComponentsFromItemStack(new ItemStack(Items.BEEHIVE));
            h.assertTrue(placed.isEmpty()&&placed.getOccupantCount()==0&&inert(placed).isEmpty()&&realRows(h,placed).isEmpty(),"Ordinary empty item implicit application resets both actual native owner subsets");
        }h.succeed();
    }

    @GameTest public void directOriginalBeeItemComponentPreservesOriginalIndicesThroughNativePlacement(GameTestHelper h){
        var occupants=rows(real(83,2400,6.5f),inert(37,600,false),inert(712,2400,true));var source=new CompoundTag();source.putString("id","minecraft:beehive");source.putInt("count",1);var components=new CompoundTag();components.put("minecraft:bees",occupants.copy());source.put("components",components);var before=source.copy();
        var scope=NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(SOURCE)).item("inventory",0);
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);var actual=ItemStack.CODEC.parse(ops,NeoForgeSaveCodecs.item(source,3955,h.getLevel().registryAccess(),scope)).getOrThrow();
        h.assertTrue(source.equals(before)&&actual.getOrDefault(DataComponents.BEES,Bees.EMPTY).bees().size()==1&&NeoForgeInertHiveSlots.fromOriginal(occupants).equals(actual.get(AllDataComponents.INERT_HIVE_SLOTS)),"Actual original direct item converter splits only inert rows while preserving immutable original source indices");assertCodecs(h,actual);
        h.setBlock(TARGET.below(),Blocks.STONE);h.setBlock(TARGET,Blocks.AIR);
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)).add(0,0,2))){
            var player=joined.player();player.setItemInHand(InteractionHand.MAIN_HAND,actual);var support=h.absolutePos(TARGET.below());var hit=new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false);
            h.assertTrue(((BlockItem)actual.getItem()).place(new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit))).consumesAction()&&actual.isEmpty(),"Actual survival native placement consumes the converted direct source item");
            var placed=(BeehiveBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(TARGET));h.assertTrue(placed!=null&&placed.isFull()&&placed.getOccupantCount()==3&&inert(placed).equals(NeoForgeInertHiveSlots.fromOriginal(occupants))&&realRows(h,placed).size()==1,"Real native component application restores genuine bee and original absent/explicit-empty inert slots");assertFreshReload(h,placed);
        }h.succeed();
    }

    @GameTest public void malformedInertOwnerIndicesAndInventedOriginalCarrierAreRefused(GameTestHelper h){
        expectRefusal(h,()->new NeoForgeInertHiveSlots(List.of(new NeoForgeInertHiveSlots.Slot(1,0,0,600,false),new NeoForgeInertHiveSlots.Slot(1,1,0,600,true))));
        expectRefusal(h,()->new NeoForgeInertHiveSlots(List.of(new NeoForgeInertHiveSlots.Slot(0,3,0,600,false))).validateNativeCount(0));
        var pos=h.absolutePos(SOURCE);var original=original(pos,rows(inert(37,600,false),real(712,2400,7.5f)));
        var prepared=NeoForgeVanillaBlockEntityImport.prepareForVanillaFixer(net.minecraft.resources.Identifier.parse("minecraft:beehive"),original,3955,h.getLevel().registryAccess());
        var lostCarrier=prepared.copy();lostCarrier.remove(NeoForgeInertHiveSlots.KEY);
        expectRefusal(h,()->NeoForgeVanillaBlockEntityImport.finishAfterVanillaFixer(net.minecraft.resources.Identifier.parse("minecraft:beehive"),original,lostCarrier,3955,h.getLevel().registryAccess()));
        var lostBee=prepared.copy();lostBee.put("bees",new ListTag());
        expectRefusal(h,()->NeoForgeVanillaBlockEntityImport.finishAfterVanillaFixer(net.minecraft.resources.Identifier.parse("minecraft:beehive"),original,lostBee,3955,h.getLevel().registryAccess()));
        var source=new CompoundTag();source.putString("id","minecraft:beehive");source.putInt("count",1);var components=new CompoundTag();components.put(NeoForgeInertHiveSlots.COMPONENT_KEY,NeoForgeInertHiveSlots.fromOriginal(rows(inert(37,600,false))).write());source.put("components",components);
        expectRefusal(h,()->NeoForgeSaveCodecs.item(source,3955,h.getLevel().registryAccess()));
        components.remove(NeoForgeInertHiveSlots.COMPONENT_KEY);components.put("!"+NeoForgeInertHiveSlots.COMPONENT_KEY,new CompoundTag());
        expectRefusal(h,()->NeoForgeSaveCodecs.item(source,3955,h.getLevel().registryAccess()));h.succeed();
    }
    private static void expectRefusal(GameTestHelper h,Runnable action){boolean refused=false;try{action.run();}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Malformed/invented inert source owner must fail closed");}
}
