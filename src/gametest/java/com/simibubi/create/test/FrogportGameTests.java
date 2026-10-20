package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.logistics.box.*;
import com.simibubi.create.content.logistics.packagePort.*;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.frogport.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;

public class FrogportGameTests {
    private static final BlockPos P=new BlockPos(1,5,2),Q=P.east(5),A=P.south(2).below(2),B=Q.south(2).below(2);
    private static ItemStack box(String address){var box=PackageItem.containing(List.of(new ItemStack(Items.DIAMOND,7)));PackageItem.addAddress(box,address);return box;}
    private static FrogportBlockEntity port(GameTestHelper h,BlockPos pos,String address){h.setBlock(pos,AllBlocks.PACKAGE_FROGPORT);var port=h.getBlockEntity(pos,FrogportBlockEntity.class);port.addressFilter=address;return port;}
    private static ChainConveyorBlockEntity conveyor(GameTestHelper h,BlockPos pos){h.setBlock(pos,AllBlocks.CHAIN_CONVEYOR);return h.getBlockEntity(pos,ChainConveyorBlockEntity.class);}
    private static KineticBlockEntity drive(GameTestHelper h){h.setBlock(P.below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.UP));var drive=h.getBlockEntity(P.below(),KineticBlockEntity.class);drive.setGeneratedSpeed(128);return drive;}
    private static ChainConveyorFrogportTarget target(GameTestHelper h,BlockPos port,BlockPos conveyor,float position,BlockPos branch){return new ChainConveyorFrogportTarget(h.absolutePos(conveyor).subtract(h.absolutePos(port)),position,branch,false);}
    private static BarrelBlockEntity barrel(GameTestHelper h,BlockPos pos){h.setBlock(pos,Blocks.BARREL);return h.getBlockEntity(pos,BarrelBlockEntity.class);}
    @GameTest public void automationSeparatesIncomingAndOutgoingAndRollsBackAllSlots(GameTestHelper h){
        var port=port(h,A,"Home");var incoming=ItemVariant.of(box("Home"));var outgoing=ItemVariant.of(box("Away"));
        try(var tx=Transaction.openOuter()){h.assertValueEqual(port.itemHandler.insert(incoming,1,tx),0L,"Addressed incoming mail cannot enter from automation");h.assertValueEqual(port.itemHandler.insert(outgoing,2,tx),2L,"Two outgoing parcels staged");}h.assertTrue(port.inventory.isEmpty(),"Aborted insert restores both slots");
        try(var tx=Transaction.openOuter()){port.inventory.insert(incoming,1,tx);port.inventory.insert(outgoing,1,tx);tx.commit();}
        try(var tx=Transaction.openOuter()){h.assertValueEqual(port.itemHandler.extract(outgoing,1,tx),0L,"Outgoing parcel not extractable");for(var view:port.itemHandler)if(view.getResource().equals(outgoing))h.assertValueEqual(view.extract(outgoing,1,tx),0L,"Views cannot bypass address restriction");h.assertValueEqual(port.itemHandler.extract(incoming,1,tx),1L,"Incoming parcel may leave");}h.assertFalse(port.inventory.getItem(0).isEmpty(),"Aborted extraction restores incoming parcel");
        long version=port.itemHandler.getVersion();port.acceptsPackages=false;port.filterChanged();h.assertTrue(port.itemHandler.getVersion()!=version,"Changing automation permissions invalidates cached inventory versions");try(var tx=Transaction.openOuter()){h.assertValueEqual(port.itemHandler.extract(incoming,1,tx),0L,"Send-only exposes no incoming mail");h.assertValueEqual(port.itemHandler.insert(incoming,1,tx),1L,"Send-only accepts any parcel");h.assertValueEqual(port.itemHandler.insert(ItemVariant.of(Items.APPLE),1,tx),0L,"Ordinary items rejected");}h.succeed();
    }
    @GameTest public void catchingOwnsParcelUntilTenTickAnimationCompletesAndHonorsCapacity(GameTestHelper h){
        var port=port(h,A,"Home");h.assertTrue(port.tryReceive(box("Home")),"Real frogport claims incoming parcel");h.assertFalse(port.tryReceive(box("Home")),"Animation cannot be overwritten");for(int i=0;i<9;i++)port.tick();h.assertTrue(port.inventory.isEmpty()&&!port.animatedPackage.isEmpty(),"No premature inventory exposure");port.tick();h.assertTrue(!port.inventory.isEmpty()&&port.animatedPackage.isEmpty(),"Ownership moves into inventory at completion");
        for(int i=0;i<18;i++)port.inventory.setItem(i,box("Home"));h.assertTrue(port.isBackedUp(),"All eighteen slots occupied");h.assertFalse(port.tryReceive(box("Home")),"Full receiver leaves cargo on conveyor");h.assertValueEqual(port.getComparatorOutput(),15,"Full comparator output");h.succeed();
    }
    @GameTest(maxTicks=130) public void twoRealFrogportsRouteParcelFromBarrelThroughPoweredChainToAnotherBarrel(GameTestHelper h){
        var first=conveyor(h,P);var second=conveyor(h,Q);first.connectTo(second,null);drive(h);var sender=port(h,A,"Sender");sender.acceptsPackages=false;var receiver=port(h,B,"Market");sender.setTarget(target(h,A,P,0,null));receiver.setTarget(target(h,B,Q,90,null));var source=barrel(h,A.below());var mailed=box("Market");source.setItem(0,mailed.copy());var destination=barrel(h,B.below());
        h.runAfterDelay(100,()->{h.assertTrue(source.isEmpty(),"Sender really extracted from barrel");h.assertTrue(ItemStack.isSameItemSameComponents(destination.getItem(0),mailed),"Receiver delivered exact address and seven diamonds");h.assertTrue(sender.inventory.isEmpty()&&receiver.inventory.isEmpty()&&sender.animatedPackage.isEmpty()&&receiver.animatedPackage.isEmpty(),"No other port owner remains");h.assertValueEqual(first.packageCount()+second.packageCount(),0,"Conveyor relinquished cargo");int count=0;for(var item:destination)if(!item.isEmpty())count+=item.getCount();h.assertValueEqual(count,1,"Exactly one physical parcel delivered");h.succeed();});
    }
    @GameTest(maxTicks=110) public void realTravelPortCatchesBeforeEndpointArrival(GameTestHelper h){
        var first=conveyor(h,P);var second=conveyor(h,Q);first.connectTo(second,null);drive(h);var pos=new BlockPos(4,3,4);var receiver=port(h,pos,"Market");h.assertTrue(receiver.setTarget(target(h,pos,P,1.5f,Q.subtract(P))),"Real mid-chain target accepted");
        h.runAfterDelay(5,()->first.addLoopingPackage(new ChainConveyorPackage(0,box("Market"))));
        h.runAfterDelay(65,()->{h.assertFalse(receiver.inventory.isEmpty(),"Travel frogport caught actual cargo");h.assertValueEqual(first.packageCount()+second.packageCount(),0,"Package no longer owned by either chain");h.succeed();});
    }
    @GameTest(maxTicks=45) public void reversalRebindsPortToOppositeOwnerAndStillCatchesSamePhysicalParcel(GameTestHelper h){
        var first=conveyor(h,P);var second=conveyor(h,Q);first.connectTo(second,null);var motor=drive(h);var pos=new BlockPos(4,3,4);var receiver=port(h,pos,"Market");receiver.setTarget(target(h,pos,P,1.5f,Q.subtract(P)));
        h.runAfterDelay(5,()->{first.addTravellingPackage(new ChainConveyorPackage(2.1f,box("Market")),Q.subtract(P));motor.setGeneratedSpeed(-128);});
        h.runAfterDelay(30,()->{var target=(ChainConveyorFrogportTarget)receiver.target;h.assertTrue(target.conveyor(receiver)==second&&target.connection.equals(P.subtract(Q)),"Port rebinds to reversed strand owner");h.assertFalse(receiver.inventory.isEmpty(),"Reversed travelling parcel caught");h.assertValueEqual(first.packageCount()+second.packageCount(),0,"No reversal duplicate");h.succeed();});
    }
    @GameTest(maxTicks=45) public void depositSpendsSourceOnceAndNativeReloadAfterHandoffCannotDuplicate(GameTestHelper h){
        var first=conveyor(h,P);drive(h);var sender=port(h,A,"Sender");sender.acceptsPackages=false;sender.setTarget(target(h,A,P,0,null));var source=barrel(h,A.below());
        h.runAfterDelay(5,()->{source.setItem(0,box("Market"));h.assertTrue(sender.tryPullingFrom(ItemStorage.SIDED.find(h.getLevel(),source.getBlockPos(),Direction.UP)),"Real barrel parcel reserved");h.assertTrue(source.isEmpty()&&!sender.animatedPackage.isEmpty(),"Animation owns extracted parcel");for(int i=0;i<6;i++)sender.tick();h.assertValueEqual(first.packageCount(),1,"Sixth tick performs handoff");h.assertTrue(sender.animatedPackage.isEmpty(),"Port no longer owns deposited parcel");var saved=sender.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(FrogportBlockEntity)BlockEntity.loadStatic(sender.getBlockPos(),sender.getBlockState(),saved,h.getLevel().registryAccess());h.assertTrue(restored.animatedPackage.isEmpty(),"Reload preserves completed handoff");h.assertFalse(restored.displayPackage.isEmpty(),"Visual-only package may finish animation without owning items");sender.tick();sender.tick();h.assertTrue(sender.displayPackage.isEmpty(),"Visual parcel clears at the source client threshold without another item handoff");h.assertValueEqual(first.packageCount(),1,"Visual cleanup never creates cargo");h.succeed();});
    }
    @GameTest public void targetRejectsBelowRangeAndInvalidChainCoordinatesAndRoundTripsRegistryCodec(GameTestHelper h){
        var conveyor=conveyor(h,P);var port=port(h,A,"Home");var valid=target(h,A,P,90,null);h.assertTrue(port.setTarget(valid),"Upward nearby target valid");var tag=PackagePortTarget.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,valid).getOrThrow();var decoded=(ChainConveyorFrogportTarget)PackagePortTarget.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,tag).getOrThrow();h.assertTrue(decoded.relativePos.equals(valid.relativePos)&&decoded.chainPos==90,"Source registry id and fields roundtrip");
        h.assertFalse(port.setTarget(target(h,A,P,Float.NaN,null)),"Nonfinite selection rejected");h.assertFalse(port.setTarget(target(h,A,P,400,null)),"Invalid loop angle rejected");var above=port(h,P.above(2),"Top");h.assertFalse(above.setTarget(target(h,P.above(2),P,0,null)),"Frogport cannot reach down");var far=port(h,new BlockPos(1,1,7),"Far");h.assertFalse(far.setTarget(target(h,new BlockPos(1,1,7),P,0,null)),"Beyond five blocks rejected");h.succeed();
    }
    @GameTest(maxTicks=40) public void disablingReceiverWithdrawsRealRouteAndAddressChangeAdvertisesReplacement(GameTestHelper h){
        var first=conveyor(h,P);var second=conveyor(h,Q);first.connectTo(second,null);var receiver=port(h,B,"Market");receiver.setTarget(target(h,B,Q,0,null));
        h.runAfterDelay(5,()->{h.assertValueEqual(first.routingTable.getExitFor(box("Market")),Q.subtract(P),"Real frogport advertisement crosses chain");receiver.acceptsPackages=false;receiver.filterChanged();});
        h.runAfterDelay(10,()->{h.assertValueEqual(first.routingTable.getExitFor(box("Market")),BlockPos.ZERO,"Disabled receiver route removed");receiver.addressFilter="Other";receiver.acceptsPackages=true;receiver.filterChanged();});
        h.runAfterDelay(15,()->{h.assertValueEqual(first.routingTable.getExitFor(box("Other")),Q.subtract(P),"Replacement address propagated");h.succeed();});
    }
    @GameTest public void destructionDuringCatchDropsOwnedParcelAndInventoryExactlyOnce(GameTestHelper h){
        var port=port(h,A,"Home");port.inventory.setItem(0,box("Home"));port.tryReceive(box("Away"));h.setBlock(A,Blocks.AIR);var area=new AABB(h.absolutePos(A)).inflate(3);h.assertValueEqual(h.getLevel().getEntitiesOfClass(PackageEntity.class,area).size(),2,"Inventory and active animation each drop one actual parcel");h.succeed();
    }
    @GameTest public void sourceSurvivalRecipeAndComparatorCountActualNonstackableSlots(GameTestHelper h){
        var port=port(h,A,"Home");h.assertValueEqual(port.getComparatorOutput(),0,"Empty comparator");port.inventory.setItem(0,box("Home"));h.assertValueEqual(port.getComparatorOutput(),1,"One of eighteen parcels");
        var grid=net.minecraft.world.item.crafting.CraftingInput.of(1,3,List.of(new ItemStack(Items.SLIME_BALL),new ItemStack(AllBlocks.ITEM_VAULT),new ItemStack(AllItems.ANDESITE_ALLOY)));var recipe=h.getLevel().getServer().getRecipeManager().getRecipes().stream().filter(holder->holder.id().identifier().equals(Create.asResource("crafting/logistics/package_frogport"))).findFirst().orElseThrow();var crafting=(net.minecraft.world.item.crafting.CraftingRecipe)recipe.value();h.assertTrue(crafting.matches(grid,h.getLevel())&&crafting.assemble(grid).is(AllBlocks.PACKAGE_FROGPORT.asItem()),"Original slimeball/vault/alloy recipe");h.succeed();
    }
    @GameTest(maxTicks=30) public void ownIncomingMailIsHeldButPackagerOutputBypassesOwnAddressFilter(GameTestHelper h){
        conveyor(h,P);drive(h);var port=port(h,A,"Market");port.setTarget(target(h,A,P,0,null));port.inventory.setItem(0,box("Market"));
        h.setBlock(A.below(),AllBlocks.PACKAGER);var packager=h.getBlockEntity(A.below(),com.simibubi.create.content.logistics.packager.PackagerBlockEntity.class);
        h.runAfterDelay(5,()->{packager.heldBox=box("Market");h.assertFalse(port.tryPullingFrom(port.inventory.storage),"Incoming mail is retained in own inventory");h.assertTrue(port.tryPullingFrom(packager.inventory),"Newly packed outgoing mail bypasses own address filter");h.assertTrue(packager.heldBox.isEmpty()&&!port.animatedPackage.isEmpty(),"One transaction transfers Packager ownership to animation");h.succeed();});
    }
    @GameTest public void nativeCatchSaveRestoresRemainingAnimationAndExactComponents(GameTestHelper h){
        var port=port(h,A,"Market");var mailed=box("Market");h.assertTrue(port.tryReceive(mailed),"Original world owner catches the parcel");for(int i=0;i<4;i++)port.tick();var saved=port.saveWithFullMetadata(h.getLevel().registryAccess());
        port.onChunkUnloaded();h.getLevel().removeBlockEntity(port.getBlockPos());
        var restored=(FrogportBlockEntity)BlockEntity.loadStatic(port.getBlockPos(),port.getBlockState(),saved,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);
        h.assertTrue(port.isRemoved()&&h.getLevel().getBlockEntity(port.getBlockPos())==restored,"Native reload installs exactly one new active world owner");
        h.assertValueEqual(restored.getAnimationTicks(),4,"Exact animation time restored");h.assertTrue(ItemStack.isSameItemSameComponents(restored.animatedPackage,mailed),"Animation owns original component-sensitive parcel after load");
        port.tick();h.assertValueEqual(port.getAnimationTicks(),4,"Removed original cannot advance or hand off a second parcel");
        for(int i=0;i<5;i++)restored.tick();h.assertTrue(restored.inventory.isEmpty()&&ItemStack.isSameItemSameComponents(restored.animatedPackage,mailed),"Five remaining ticks keep cargo in the animation owner");
        restored.tick();h.assertTrue(restored.animatedPackage.isEmpty()&&ItemStack.isSameItemSameComponents(restored.inventory.getItem(0),mailed),"Remaining six ticks transfer one parcel into inventory");
        restored.tick();int parcels=0;for(int slot=0;slot<18;slot++)parcels+=restored.inventory.getItem(slot).getCount();h.assertValueEqual(parcels,1,"Completed animation exposes exactly one physical parcel");h.succeed();
    }
    @GameTest public void survivalPlacementUsesServerSelectedTargetAndConsumesOneBlock(GameTestHelper h){
        var conveyor=conveyor(h,P);h.setBlock(A.below(),Blocks.STONE);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atBottomCenterOf(h.absolutePos(A.south(2))));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.PACKAGE_FROGPORT,2));var point=conveyor.getPackagePosition(0,null);var diff=point.subtract(player.getEyePosition());player.setYRot((float)Math.toDegrees(Math.atan2(-diff.x,diff.z)));player.setXRot((float)-Math.toDegrees(Math.atan2(diff.y,Math.hypot(diff.x,diff.z))));
        var hit=ChainConveyorInteraction.trace(conveyor,player.getEyePosition(),player.getEyePosition().add(player.getLookAngle().scale(player.blockInteractionRange()+1)));h.assertTrue(hit!=null,"Chain target selected by actual player ray");h.assertTrue(ChainConveyorInteraction.apply(player,new ChainConveyorInteraction.Packet(conveyor.getBlockPos(),hit.connection(),hit.position(),ChainConveyorInteraction.SELECT_PORT,0)),"Server validated selection");
        var floor=h.absolutePos(A.below());var result=player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atLowerCornerOf(floor).add(.5,1,.5),Direction.UP,floor,false)));h.assertTrue(result.consumesAction(),"Native BlockItem placement");var port=h.getBlockEntity(A,FrogportBlockEntity.class);h.assertTrue(port.target instanceof ChainConveyorFrogportTarget target&&target.conveyor(port)==conveyor,"Placed frogport uses stored authoritative selection");h.assertValueEqual(player.getMainHandItem().getCount(),1,"One frogport item consumed");h.succeed();
    }
    @GameTest public void settingsRequireCurrentNearbyBlockAndPreserveInventory(GameTestHelper h){
        var port=port(h,A,"Old");port.inventory.setItem(0,box("Old"));var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(A)));var menu=new PackagePortMenu(3,player.getInventory(),new PackagePortMenu.Opening(port.getBlockPos(),port.getUpdateTag(h.getLevel().registryAccess())));
        h.assertTrue(menu.apply(player,new PackagePortMenu.Settings(3,"New",false)),"Nearby player configures own open menu");h.assertTrue(!port.acceptsPackages&&port.addressFilter.equals("New")&&PackageItem.getAddress(port.inventory.getItem(0)).equals("Old"),"Settings do not rewrite real mail");player.setPos(player.position().add(50,0,0));h.assertFalse(menu.apply(player,new PackagePortMenu.Settings(3,"Forged",true)),"Distant update denied");h.succeed();
    }
    @GameTest public void originalNeoForgeItemHandlerInventoryKeepsSlotAndComponents(GameTestHelper h){
        var port=port(h,A,"Market");var mailed=box("Market");var saved=port.saveWithFullMetadata(h.getLevel().registryAccess());var stack=(net.minecraft.nbt.CompoundTag)ItemStack.CODEC.encodeStart(h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),mailed).getOrThrow();stack.putInt("Slot",17);var items=new net.minecraft.nbt.ListTag();items.add(stack);var inventory=new net.minecraft.nbt.CompoundTag();inventory.putInt("Size",18);inventory.put("Items",items);saved.put("Inventory",inventory);
        var restored=(FrogportBlockEntity)BlockEntity.loadStatic(port.getBlockPos(),port.getBlockState(),saved,h.getLevel().registryAccess());h.assertTrue(ItemStack.isSameItemSameComponents(restored.inventory.getItem(17),mailed),"Original ItemStackHandler slot and complete native components restored");h.assertTrue(restored.inventory.getItem(0).isEmpty(),"Original empty slots remain empty");h.succeed();
    }
}
