package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.minecart.*;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.content.contraptions.mounted.*;
import com.simibubi.create.content.kinetics.deployer.DeployerDropCapture;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Authored only: actual native owners/serialization/rail actions; no mock acceptance or direct success injection. */
public class MinecartNativeGameTests {
    private static final BlockPos P=new BlockPos(3,2,3);
    private static ItemStack named(Item item,int count,String name){var result=new ItemStack(item,count);result.set(DataComponents.CUSTOM_NAME,Component.literal(name));return result;}
    private static AbstractMinecart cart(GameTestHelper h,NativeCartKind kind,BlockPos relative){var actual=kind.createEmpty(h.getLevel(),Vec3.atLowerCornerOf(h.absolutePos(relative)).add(.5,0,.5));h.assertTrue(h.getLevel().addFreshEntity(actual),"Actual native cart admitted");return actual;}
    private static CompoundTag save(Entity entity){var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,entity.registryAccess());entity.saveWithoutId(out);return out.buildResult();}
    record Forest(MinecartChest cart,MinecartController source,OrientedContraptionEntity root,ItemStack nativeCargo,ItemStack barrelCargo){}
    static Forest forest(GameTestHelper h)throws Exception{
        h.setBlock(P.below(),Blocks.STONE);h.setBlock(P,AllBlocks.CART_ASSEMBLER.defaultBlockState().setValue(CartAssemblerBlock.POWERED,true).setValue(CartAssemblerBlock.RAIL_TYPE,CartAssembleRailType.REGULAR));
        h.setBlock(P.above(),Blocks.STONE);h.setBlock(P.above().east(),Blocks.BARREL);
        var barrelCargo=named(Items.DIAMOND,7,"Seven actual mounted barrel diamonds");h.getBlockEntity(P.above().east(),BarrelBlockEntity.class).setItem(0,barrelCargo);
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(P.above()),h.absolutePos(P.above().east())))),"Actual glue span admitted");
        var cart=(MinecartChest)cart(h,NativeCartKind.CHEST,P);var nativeCargo=named(Items.EMERALD,9,"Nine original native base emeralds");cart.getItemStacks().set(0,nativeCargo);cart.setContainerLootTableSeed(71);
        h.getBlockEntity(P,CartAssemblerBlockEntity.class).tryAssemble(cart);var source=MinecartController.get(cart);var root=h.getLevel().getEntity(source.getMountedRoot());
        h.assertTrue(root instanceof OrientedContraptionEntity owner&&owner.hasOwnedStructure()&&owner.getVehicle()==cart,"Real assembler removed source blocks into an accepted mounted root");return new Forest(cart,source,(OrientedContraptionEntity)root,nativeCargo,barrelCargo);
    }
    private record DoubleForest(MinecartChest primary,MinecartFurnace second,OrientedContraptionEntity root,ItemStack cargo){}
    private static DoubleForest doubleForest(GameTestHelper h)throws Exception{
        var state=AllBlocks.CART_ASSEMBLER.defaultBlockState().setValue(CartAssemblerBlock.POWERED,true).setValue(CartAssemblerBlock.RAIL_TYPE,CartAssembleRailType.REGULAR).setValue(CartAssemblerBlock.RAIL_SHAPE,net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST);
        h.setBlock(P.below(),Blocks.STONE);h.setBlock(P.east(4).below(),Blocks.STONE);h.setBlock(P,state);h.setBlock(P.east(4),state);for(int index=0;index<=4;index++)h.setBlock(P.above().east(index),Blocks.STONE);
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(P),h.absolutePos(P.east(4).above())))),"Actual full-height source glue connects both genuine assembler anchors");
        var first=(MinecartChest)cart(h,NativeCartKind.CHEST,P);var second=(MinecartFurnace)cart(h,NativeCartKind.FURNACE,P.east(4));var cargo=named(Items.DIAMOND,11,"Eleven actual two-base native diamonds");first.getItemStacks().set(0,cargo);h.getBlockEntity(P,CartAssemblerBlockEntity.class).tryAssemble(first);
        var root=h.getLevel().getEntity(MinecartController.get(first).getMountedRoot());h.assertTrue(root instanceof OrientedContraptionEntity owner&&owner.hasOwnedStructure()&&owner.getCoupledCartsIfPresent()!=null&&Objects.equals(MinecartController.get(second).getMountedRoot(),root.getUUID()),"Genuine source capture assembled one real owned structure over both loaded native bases");((com.simibubi.create.foundation.mixin.accessor.MinecartFurnaceAccessor)second).create$setFuel(2451);second.push=new Vec3(.375,0,0);return new DoubleForest(first,second,(OrientedContraptionEntity)root,cargo);
    }
    @GameTest public void actualTwoBaseForestPackDeployPreservesBothNativeOwnersAndStructuralTopology(GameTestHelper h)throws Exception{
        var f=doubleForest(h);var packed=CartForestTransfer.pack(f.second);h.assertTrue(packed!=null&&f.second.isRemoved()&&f.root.isRemoved()&&f.primary.getItemStacks().stream().allMatch(ItemStack::isEmpty)&&((com.simibubi.create.foundation.mixin.accessor.MinecartFurnaceAccessor)f.second).create$getFuel()==0,"Real pickup via secondary retires both old cargo owners into the genuine primary receipt");
        var result=CartForestTransfer.deploy(f.primary,Direction.EAST);h.assertTrue(result!=null,"One actual receipt deployed real primary/secondary forest");var owner=(OrientedContraptionEntity)h.getLevel().getEntity(result.actualRoot());var pair=owner.getCoupledCartsIfPresent();h.assertTrue(pair!=null&&pair.getFirst().cart()==f.primary&&pair.getSecond().cart() instanceof MinecartFurnace&&pair.getSecond().cart()!=f.second,"Actual new native secondary is bound reciprocally to real old primary; retired secondary was not resurrected");
        var furnace=(MinecartFurnace)pair.getSecond().cart();h.assertTrue(f.primary.getItemStacks().getFirst().getCount()==11&&f.primary.getItemStacks().getFirst().is(Items.DIAMOND)&&((com.simibubi.create.foundation.mixin.accessor.MinecartFurnaceAccessor)furnace).create$getFuel()==2451&&furnace.push.equals(new Vec3(.375,0,0)),"Exactly one actual native cargo/fuel/push payload survived two-base transfer");h.assertTrue(!MinecartController.get(f.primary).ownsPackedReceipt()&&owner.getMountedCartId().equals(f.primary.getUUID())&&owner.hasOwnedStructure(),"One live actual root owns original structure with no spent item owner");h.succeed();
    }
    @GameTest public void actualPaidChainReversalMovesStructuralLeadingIdBeforeCommandRemoval(GameTestHelper h)throws Exception{
        var f=doubleForest(h);var third=cart(h,NativeCartKind.RIDEABLE,P.west(3));var original=named(AllItems.MINECART_COUPLING,1,"One actual reversal paid ordinary receipt");
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,1,2))){var player=joined.player();player.setItemInHand(InteractionHand.MAIN_HAND,original);h.assertTrue(CouplingHandler.tryToCoupleCarts(player,h.getLevel(),f.primary.getId(),third.getId()),"Real survival coupling attached one exact receipt after genuine structural chain reversal");}
        h.assertTrue(f.root.getCouplingId().equals(f.second.getUUID())&&f.root.getVehicle()==f.primary&&Objects.equals(MinecartController.get(f.second).getCoupledCart(true),f.primary.getUUID())&&MinecartController.get(f.primary).inspectLink(true).actualReceipt()==original,"Authoritative actual root leading UUID follows pure topology reversal while paid receipt has one new ordinary owner");
        var server=h.getLevel().getServer();var source=server.createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput();server.getCommands().performPrefixedCommand(source,"create coupling remove "+f.primary.getUUID()+" "+third.getUUID());h.assertTrue(MinecartController.get(f.primary).ownsFirstRefund(original)&&f.root.hasOwnedStructure()&&f.root.getCoupledCartsIfPresent()!=null,"Native ordinary command removal retains exact paid refund and active structural root");
        server.getCommands().performPrefixedCommand(source,"create coupling remove "+f.primary.getUUID()+" "+f.second.getUUID());h.assertTrue(f.root.isRemoved()&&MinecartController.get(f.primary).getMountedRoot()==null&&MinecartController.get(f.second).getMountedRoot()==null&&!MinecartController.get(f.second).isLeadingCoupling()&&MinecartController.get(f.primary).ownsFirstRefund(original),"Native structural removal performs real world restoration and final attachment retirement without losing independent paid refund");h.succeed();
    }
    @GameTest public void realRefundRevokesSourceBeforeNativeOnCreatedAndSave(GameTestHelper h)throws Exception{
        var cart=cart(h,NativeCartKind.RIDEABLE,P);var source=MinecartController.get(cart);var original=named(AllItems.MINECART_COUPLING,1,"Original paid coupling components");source.retainRefund(original);var target=new AtomicReference<ItemEntity>();
        try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity instanceof ItemEntity item&&item.getItem()==original,entity->{target.set((ItemEntity)entity);h.assertTrue(NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(entity),"Actual native section contains this exact endpoint before visible tracking");h.assertFalse(source.hasPendingRefunds(),"Real original source queue revoked before native callback");var saved=save(cart);h.assertTrue(!saved.toString().contains("Original paid coupling components"),"Actual native source save contains no second paid receipt");})){
            h.assertTrue(CartReceiptPublication.returnFirstRefund(source,null),"Real accepted native ItemEntity receives original");
        }h.assertTrue(target.get()!=null&&target.get().getItem()==original&&h.getLevel().getEntity(target.get().getUUID())==target.get(),"One actual original target is natively tracked");h.succeed();
    }
    private static final class ActualCreationFault extends Error{}
    @GameTest public void genuineNativeDuplicateUuidRejectionRetainsOriginalRefundForRetry(GameTestHelper h)throws Exception{
        var cart=cart(h,NativeCartKind.RIDEABLE,P);var source=MinecartController.get(cart);var original=named(AllItems.MINECART_COUPLING,1,"One original native-refused refund");source.retainRefund(original);var offered=new AtomicReference<Entity>();
        try(var observer=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity instanceof ItemEntity item&&item.getItem()==original,entity->{offered.set(entity);entity.setUUID(cart.getUUID());})){
            h.assertFalse(CartReceiptPublication.returnFirstRefund(source,null),"Real native duplicate-UUID check rejects prospective actual entity");
        }
        h.assertTrue(offered.get()!=null&&h.getLevel().getEntity(cart.getUUID())==cart&&source.ownsFirstRefund(original)&&original.getCount()==1,"Actual original source survives genuine native refusal");
        h.assertTrue(CartReceiptPublication.returnFirstRefund(source,null)&&!source.hasPendingRefunds(),"Later genuine native insertion accepts exactly the retained original");var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(P)).inflate(2));h.assertTrue(drops.size()==1&&drops.getFirst().getItem()==original,"Only one accepted native ItemEntity owns original paid material");h.succeed();
    }
    @GameTest public void actualAcceptedThenRemovedNativeEndpointCannotRefundAgain(GameTestHelper h)throws Exception{
        var cart=cart(h,NativeCartKind.RIDEABLE,P);var source=MinecartController.get(cart);var original=named(AllItems.MINECART_COUPLING,1,"One original natively consumed refund");source.retainRefund(original);var removed=new AtomicReference<Entity>();
        try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity instanceof ItemEntity item&&item.getItem()==original,entity->{h.assertFalse(source.hasPendingRefunds(),"Original source was revoked before real removal callback");removed.set(entity);entity.discard();})){
            h.assertTrue(CartReceiptPublication.returnFirstRefund(source,null),"Actual historical native section insertion remains acceptance after real removal");
        }h.assertTrue(removed.get()!=null&&removed.get().isRemoved()&&!source.hasPendingRefunds()&&!CartReceiptPublication.returnFirstRefund(source,null),"Legitimate native removal cannot reanimate the old refund source");h.succeed();
    }
    @GameTest public void actualPostInsertionThrowCannotReanimatePaidRefund(GameTestHelper h)throws Exception{
        var cart=cart(h,NativeCartKind.RIDEABLE,P);var source=MinecartController.get(cart);var original=named(AllItems.MINECART_COUPLING,1,"One original late-fault refund");source.retainRefund(original);var actual=new AtomicReference<Entity>();var fault=new ActualCreationFault();boolean thrown=false;
        try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity instanceof ItemEntity item&&item.getItem()==original,entity->{actual.set(entity);h.assertTrue(NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(entity)&&!source.hasPendingRefunds(),"Real source debit precedes actual throwing callback");throw fault;})){
            try{CartReceiptPublication.returnFirstRefund(source,null);}catch(ActualCreationFault failure){h.assertTrue(failure==fault,"Actual native failure identity retained");thrown=true;}
        }h.assertTrue(thrown&&actual.get()!=null&&!source.hasPendingRefunds()&&!CartReceiptPublication.returnFirstRefund(source,null),"Historical actual admission leaves no second refund source");h.succeed();
    }
    @GameTest public void actualScopedItemAdmissionBypassesCopiedDeployerDropCapture(GameTestHelper h){
        var cart=cart(h,NativeCartKind.RIDEABLE,P);var source=MinecartController.get(cart);var original=named(AllItems.MINECART_COUPLING,1,"One real captured-action refund");source.retainRefund(original);
        var copies=DeployerDropCapture.run(h.getLevel(),h.absolutePos(P),()->h.assertTrue(CartReceiptPublication.returnFirstRefund(source,null),"Actual native refund endpoint admitted"));
        h.assertTrue(copies.isEmpty()&&!source.hasPendingRefunds(),"Generic capture created no copied value ahead of native admission");var real=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(P)).inflate(2));h.assertTrue(real.size()==1&&real.getFirst().getItem()==original,"Only exact actual native ItemEntity owns the original");h.succeed();
    }
    @GameTest public void nativeEmptyCartItemUsesOneOfThreeRealSurvivalItems(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);h.setBlock(P,Blocks.RAIL);try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,2,3))){var player=joined.player();var stack=named(AllItems.MINECART_CONTRAPTION,3,"Three genuine stacked source carts");player.getInventory().setItem(0,stack);var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);h.assertTrue(player.gameMode.useItemOn(player,h.getLevel(),stack,InteractionHand.MAIN_HAND,hit).consumesAction(),"Genuine native survival game mode uses source cart item");h.assertTrue(player.getMainHandItem()==stack&&stack.getCount()==2,"Exactly one actual canonical material paid");var carts=h.getLevel().getEntitiesOfClass(AbstractMinecart.class,new AABB(h.absolutePos(P)).inflate(2));h.assertTrue(carts.size()==1&&carts.getFirst().getCustomName().equals(Component.literal("Three genuine stacked source carts")),"One actual native cart preserves source name");}h.succeed();
    }
    @GameTest public void actualNativeCommandRequiresGamemasterAndCreatesOrdinaryNonRefundableEdge(GameTestHelper h){
        var first=cart(h,NativeCartKind.RIDEABLE,P);var second=cart(h,NativeCartKind.RIDEABLE,P.east(3));var server=h.getLevel().getServer();var source=server.createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput();var command="create coupling add "+first.getUUID()+" "+second.getUUID();
        server.getCommands().performPrefixedCommand(source.withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS),command);h.assertTrue(!MinecartController.get(first).isLeadingCoupling()&&!MinecartController.get(second).isConnectedToCoupling(),"Actual native parser/permission rejects unauthorized source without topology mutation");
        server.getCommands().performPrefixedCommand(source,command);var main=MinecartController.get(first);var link=main.inspectLink(true);h.assertTrue(link!=null&&link.creative()&&!link.contraption()&&link.actualReceipt().isEmpty()&&Objects.equals(MinecartController.get(second).getCoupledCart(false),first.getUUID()),"Actual authorized console command creates one ordinary edge without a fictional structural owner or paid refund");
        server.getCommands().performPrefixedCommand(source,"create coupling removeAll "+first.getUUID());h.assertTrue(!main.isLeadingCoupling()&&!MinecartController.get(second).isConnectedToCoupling()&&!main.hasPendingRefunds(),"Actual removeAll consumes non-refundable operator materialization once");h.succeed();
    }
    @GameTest public void actualNativeCommandRemovalRetainsAndReturnsOneOriginalSurvivalReceipt(GameTestHelper h){
        var first=cart(h,NativeCartKind.RIDEABLE,P);var second=cart(h,NativeCartKind.RIDEABLE,P.east(3));var original=named(AllItems.MINECART_COUPLING,1,"One actual native-command paid link");var main=MinecartController.get(first);
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,2,2))){var player=joined.player();player.setItemInHand(InteractionHand.MAIN_HAND,original);h.assertTrue(CouplingHandler.tryToCoupleCarts(player,h.getLevel(),first.getId(),second.getId())&&main.inspectLink(true).actualReceipt()==original&&player.getMainHandItem().isEmpty(),"Actual registered survival player paid one exact original coupling");}
        var server=h.getLevel().getServer();server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput(),"create coupling remove "+first.getUUID()+" "+second.getUUID());h.assertTrue(!main.isLeadingCoupling()&&!MinecartController.get(second).isConnectedToCoupling()&&main.ownsFirstRefund(original),"Actual native command topology removal durably retains its exact paid original");h.assertTrue(CartReceiptPublication.returnFirstRefund(main,null)&&!main.hasPendingRefunds(),"One genuine native receipt publication consumes original pending command refund");h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(P)).inflate(2)).stream().filter(item->item.getItem()==original).count()==1&&!CartReceiptPublication.returnFirstRefund(main,null),"No native command removal duplication");h.succeed();
    }
    @GameTest public void actualCreativeUseRestoresSourceCountWithoutCloningSpentSurvivalReceipt(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);h.setBlock(P,Blocks.RAIL);try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,2,3))){var player=joined.player();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var stack=new ItemStack(AllItems.MINECART_CONTRAPTION);player.getInventory().setItem(0,stack);var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);h.assertTrue(player.gameMode.useItemOn(player,h.getLevel(),stack,InteractionHand.MAIN_HAND,hit).consumesAction(),"Actual native infinite-material branch used");h.assertTrue(player.getMainHandItem()==stack&&stack.getCount()==1,"Native creative count restoration retains its actual authorized source");h.assertValueEqual(h.getLevel().getEntitiesOfClass(AbstractMinecart.class,new AABB(h.absolutePos(P)).inflate(2)).size(),1,"One genuine new native creative cart materialized");}h.succeed();
    }
    @GameTest public void realForestPackingRevokesNativeCargoAndOldStorageHandles(GameTestHelper h)throws Exception{
        var f=forest(h);var old=new ArrayList<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>>();f.root.getContraption().getStorage().getAllItems().forEach(old::add);var packed=CartForestTransfer.pack(f.cart);h.assertTrue(packed!=null&&f.source.actualPackedReceipt()==packed.originalReceipt()&&f.source.ownsPackedReceipt(),"One complete original actual primary receipt");h.assertTrue(f.cart.getItemStacks().stream().allMatch(ItemStack::isEmpty)&&f.cart.getContainerLootTableSeed()==0&&!f.root.hasOwnedStructure(),"Real old native/forest cargo ownership revoked");h.assertTrue(old.stream().allMatch(view->view.getAmount()==0),"Retained native old storage views expose no transferred material");var proposals=PackedCartForest.parse(packed.originalReceipt()).decodeInactiveStructures(h.getLevel());h.assertTrue(proposals.stream().noneMatch(proposal->proposal.getStorage().ownsContents()),"Decoded payload proposals are not second inventory owners");h.succeed();
    }
    @GameTest public void actualPackedNativeSaveUnloadLoadDeployRetainsAllOriginalCargo(GameTestHelper h)throws Exception{
        var f=forest(h);var tail=named(Items.GOLD_INGOT,11,"Eleven original native backing-tail gold");f.cart.getItemStacks().set(f.cart.getItemStacks().size()-1,tail);var packed=CartForestTransfer.pack(f.cart);h.assertTrue(packed!=null,"Real full source packed");var saved=save(f.cart);f.cart.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var restored=new MinecartChest(EntityTypes.CHEST_MINECART,h.getLevel());restored.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved));h.assertTrue(h.getLevel().addFreshEntity(restored),"Actual vanilla native cart/load and attachment factory restored");var controller=MinecartController.get(restored);h.assertTrue(controller.ownsPackedReceipt()&&restored.getItemStacks().stream().allMatch(ItemStack::isEmpty),"Real saved primary owns packed value without old native cargo");var result=CartForestTransfer.deploy(restored,Direction.SOUTH);h.assertTrue(result!=null&&!controller.ownsPackedReceipt()&&restored.getItemStacks().getFirst().is(Items.EMERALD)&&restored.getItemStacks().getFirst().getCount()==9&&restored.getContainerLootTableSeed()==71,"Native original chest cargo/seed returned once");h.assertTrue(restored.getItemStacks().size()==f.cart.getItemStacks().size()&&ItemStack.matches(restored.getItemStacks().getLast(),tail),"Full actual native backing extent and all eleven original tail gold survive packed native load/deploy");var root=(OrientedContraptionEntity)h.getLevel().getEntity(result.actualRoot());long diamonds=0;for(var view:root.getContraption().getStorage().getAllItems())if(view.getResource().isOf(Items.DIAMOND))diamonds+=view.getAmount();h.assertTrue(root.hasOwnedStructure()&&diamonds==7,"Original actual mounted barrel contents preserved once through genuine native save/load");h.succeed();
    }
    @GameTest public void actualWorldRestorationRetiresNativeRootAttachment(GameTestHelper h)throws Exception{
        var f=forest(h);h.assertTrue(f.root.disassemble(),"Real full graph world restoration completed");h.assertTrue(f.source.getMountedRoot()==null&&f.root.isRemoved()&&!f.source.missingMountedRoot(),"Actual loaded native attachment retired with its real root");h.assertTrue(f.cart.getItemStacks().getFirst()==f.nativeCargo&&h.getBlockEntity(P.above().east(),BarrelBlockEntity.class).getItem(0).getCount()==7,"Genuine native cart and restored world barrel retain distinct original cargo");h.succeed();
    }
    @GameTest public void packedNativeDestroyPublishesCompleteItemWithoutVanillaBaseDuplication(GameTestHelper h)throws Exception{
        var f=forest(h);var packed=CartForestTransfer.pack(f.cart);h.assertTrue(packed!=null,"Actual source packed");h.assertTrue(NativeCartLifecycle.beforeDestroy(f.cart),"Actual packed lifecycle intercepts native destructive drops");var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(P)).inflate(3));h.assertTrue(drops.size()==1&&drops.getFirst().getItem()==packed.originalReceipt()&&f.cart.isRemoved()&&!f.source.ownsPackedReceipt(),"One original complete native ItemEntity, no extra base cart receipt");h.succeed();
    }
    @GameTest public void nativeFurnaceStateRetainsActualFuelPushAndAbsentCustomDisplay(GameTestHelper h){
        var cart=(MinecartFurnace)cart(h,NativeCartKind.FURNACE,P);var accessor=(com.simibubi.create.foundation.mixin.accessor.MinecartFurnaceAccessor)cart;accessor.create$setFuel(3571);cart.push=new Vec3(.25,0,-.75);var before=((com.simibubi.create.foundation.mixin.accessor.MinecartDisplayAccessor)cart).create$getCustomDisplayBlockState();var prepared=NativeCartState.capture(cart).prepare(cart);h.assertTrue(prepared.fuel()==3571&&prepared.push().equals(cart.push)&&prepared.display().equals(before)&&before.isEmpty(),"Actual native int fuel/Vec3 push and dynamic default display survive snapshot codecs");h.assertTrue(accessor.create$getFuel()==3571&&cart.push.equals(new Vec3(.25,0,-.75)),"Read-only off-world proposal consumes no native fuel");h.succeed();
    }
    @GameTest(maxTicks=30) public void actualDispenserCallerKeepsLaterNativeSlotOwner(GameTestHelper h)throws Exception{
        h.setBlock(P.below(),Blocks.STONE);h.setBlock(P,Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.EAST));h.setBlock(P.east().below(),Blocks.STONE);h.setBlock(P.east(),Blocks.RAIL);var dispenser=h.getBlockEntity(P,DispenserBlockEntity.class);var original=new ItemStack(AllItems.MINECART_CONTRAPTION,3);var later=named(Items.EMERALD,7,"Actual later native dispenser owner");dispenser.setItem(0,original);
        var observation=CartAcceptanceTestFixtures.beforeCreated(entity->entity instanceof AbstractMinecart&&entity.level()==h.getLevel()&&entity.blockPosition().equals(h.absolutePos(P.east())),entity->{h.assertTrue(NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(entity)&&dispenser.getItem(0).getCount()==2,"Actual slot already paid before native creation callback");dispenser.setItem(1,dispenser.getItem(0));dispenser.setItem(0,later);});h.setBlock(P.west(),Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(8,()->{try{h.assertTrue(dispenser.getItem(0)==later&&dispenser.getItem(1)==original&&original.getCount()==2,"Native dispenseFrom writes back current actual owner, preserving the other two original carts");h.assertValueEqual(h.getLevel().getEntitiesOfClass(AbstractMinecart.class,new AABB(h.absolutePos(P)).inflate(3)).size(),1,"One actual native cart admitted");h.succeed();}finally{try{observation.close();}catch(Exception failure){throw new RuntimeException(failure);}}});
    }
}
