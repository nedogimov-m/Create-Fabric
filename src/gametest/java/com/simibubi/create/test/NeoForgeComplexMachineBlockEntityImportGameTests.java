package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.elevator.ElevatorColumn;
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock;
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlockEntity;
import com.simibubi.create.content.contraptions.elevator.ElevatorContraption;
import com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerInventory;
import com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour;
import com.simibubi.create.content.kinetics.deployer.DeployerOutputRecovery;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
import com.simibubi.create.foundation.utility.NeoForgeBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeMovementContextImport;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Exact original serializers through registered native owners, actual slots/cabins/payment and reload. UNRUN. */
public final class NeoForgeComplexMachineBlockEntityImportGameTests {
    private static final int VERSION=NeoForgeSavedDataImport.SOURCE_DATA_VERSION;
    private static final BlockPos P=new BlockPos(3,6,3);
    private static Identifier id(String path){return Identifier.parse("create:"+path);}
    private static CompoundTag item(String id,int count){var tag=new CompoundTag();tag.putString("id",id);tag.putInt("count",count);return tag;}
    private static ListTag directions(int...values){var tag=new ListTag();for(int value:values)tag.add(IntTag.valueOf(value));return tag;}
    private static CompoundTag fixture(String type){
        var source=new CompoundTag();switch(type){
            case "andesite_tunnel"->{source.put("Flaps",directions(2,3));source.put("Sides",directions(2,3,5));}
            case "portable_storage_interface","portable_fluid_interface"->{source.putInt("Timer",0);source.putFloat("Distance",1.5f);source.putBoolean("Powered",false);}
            case "elevator_pulley"->{source.putInt("InitialOffset",0);source.putFloat("Offset",-1);source.putBoolean("Running",false);source.putBoolean("Waiting",false);}
            case "elevator_contact"->{source.putString("ShortName","2");source.putString("LongName","Source floor");source.putString("DoorControl","NORTH");source.putBoolean("Activate",false);}
            case "mechanical_press"->{source.putBoolean("Running",false);source.putBoolean("Finished",false);source.putInt("Mode",0);source.putInt("Ticks",0);}
            case "mechanical_mixer"->{source.putBoolean("Running",false);source.putInt("Ticks",0);}
            case "deployer"->{source.putString("Mode","USE");source.putString("State","WAITING");source.putInt("Timer",0);source.putBoolean("Powered",false);source.put("Inventory",new ListTag());}
            default->throw new AssertionError(type);
        }return source;
    }
    private static CompoundTag converted(GameTestHelper h,String type,CompoundTag source,BlockPos relative){
        var at=h.absolutePos(relative);var old=source.copy();old.putString("id",id(type).toString());old.putInt("x",at.getX());old.putInt("y",at.getY());old.putInt("z",at.getZ());var before=old.copy();
        var result=NeoForgeBlockEntityImport.convert(id(type),old,VERSION,h.getLevel().registryAccess(),NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),at));h.assertTrue(old.equals(before),"Actual source tag is unchanged");return result;
    }
    private static BlockEntity loaded(GameTestHelper h,String type,Block block,CompoundTag source){
        var tag=converted(h,type,source,P);var first=BlockEntity.loadStatic(h.absolutePos(P),block.defaultBlockState(),tag,h.getLevel().registryAccess());h.assertTrue(first!=null&&first.getLevel()==null,"Actual registered native factory accepts "+type);
        load(h,first,tag);var second=BlockEntity.loadStatic(first.getBlockPos(),first.getBlockState(),first.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(second!=null&&second!=first,"Actual independent native reload accepts "+type);return second;
    }
    private static void load(GameTestHelper h,BlockEntity owner,CompoundTag tag){var problems=new ProblemReporter.Collector();owner.loadWithComponents(TagValueInput.create(problems,h.getLevel().registryAccess(),tag));h.assertTrue(problems.isEmpty(),"Actual native decoder retained the entire typed payload: "+problems.getReport());}
    private static void install(GameTestHelper h,BlockEntity owner){h.setBlock(P,owner.getBlockState());owner.setLevel(h.getLevel());h.getLevel().setBlockEntity(owner);}

    @GameTest public void allEightComplexSourceTypesPassTheirActualRegisteredNativeFactoriesAndReloads(GameTestHelper h){
        String[] types={"andesite_tunnel","elevator_pulley","elevator_contact","portable_storage_interface","portable_fluid_interface","mechanical_press","mechanical_mixer","deployer"};
        Block[] blocks={AllBlocks.ANDESITE_TUNNEL,AllBlocks.ELEVATOR_PULLEY,AllBlocks.ELEVATOR_CONTACT,AllBlocks.PORTABLE_STORAGE_INTERFACE,AllBlocks.PORTABLE_FLUID_INTERFACE,AllBlocks.MECHANICAL_PRESS,AllBlocks.MECHANICAL_MIXER,AllBlocks.DEPLOYER};
        for(int i=0;i<types.length;i++)loaded(h,types[i],blocks[i],fixture(types[i]));h.succeed();
    }

    @GameTest public void originalTunnelSafeFlapsAndDeployerSafeModeCarryOnlyTheirGenuinePartialWriterData(GameTestHelper h){
        var tunnel=(BeltTunnelBlockEntity)loaded(h,"andesite_tunnel",AllBlocks.ANDESITE_TUNNEL,fixture("andesite_tunnel"));h.assertTrue(tunnel.flaps.keySet().equals(java.util.Set.of(Direction.NORTH,Direction.SOUTH))&&tunnel.sides.contains(Direction.EAST),"Actual source full flap/side sets survive native reload");
        var scope=NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(P));var safe=NeoForgeBlockEntityImport.convertSafe(id("andesite_tunnel"),fixture("andesite_tunnel"),VERSION,h.getLevel().registryAccess(),scope);h.assertTrue(safe.getListOrEmpty("Flaps").size()==2&&!safe.contains("FlapSequences")&&!safe.contains("Inventory"),"Source tunnel safe data invents neither feedback sequence nor stock");
        var mode=new CompoundTag();mode.putString("Mode","PUNCH");var deployer=NeoForgeBlockEntityImport.convertSafe(id("deployer"),mode,VERSION,h.getLevel().registryAccess(),scope);h.assertTrue(deployer.getStringOr("Mode","").equals("PUNCH")&&!deployer.contains("HeldItem")&&!deployer.contains("PlayerInventory")&&!deployer.contains("Timer"),"Source safe deployer writes mode and safe filter only");h.succeed();
    }

    @GameTest public void savedInterfaceTimeoutSurvivesLoadAndCannotFabricateAConnectedCargoOwner(GameTestHelper h){
        for(String type:List.of("portable_storage_interface","portable_fluid_interface")){
            var old=fixture(type);old.putInt("Timer",2500000);var block=type.equals("portable_storage_interface")?AllBlocks.PORTABLE_STORAGE_INTERFACE:AllBlocks.PORTABLE_FLUID_INTERFACE;
            var owner=(PortableStorageInterfaceBlockEntity)loaded(h,type,block,old);h.assertTrue(owner.getTransferTimer()==2500000&&owner.getConnectionDistance()==1.5f&&owner.getConnectedEntity()==null&&!owner.canTransfer(),"Original save retains countdown independently of a reduced timeout config; actual transient attachment and cargo are absent");
        }h.succeed();
    }

    @GameTest public void originalIdleElevatorSentinelAndColumnMetadataSurviveWithoutInventingBodyIdentity(GameTestHelper h){
        var idle=(ElevatorPulleyBlockEntity)loaded(h,"elevator_pulley",AllBlocks.ELEVATOR_PULLEY,fixture("elevator_pulley"));h.assertTrue(idle.offset==-1&&idle.getMovedContraption()==null&&!idle.saveWithoutMetadata(h.getLevel().registryAccess()).contains("MovedEntity"),"Actual idle -1 elevator sentinel survives its real pulley superclass reader");
        var source=fixture("elevator_contact");source.putInt("ColumnTarget",h.absolutePos(P).getY()-2);source.putBoolean("ColumnActive",true);source.putString("LastReportedCurrentFloor","1");var contact=(ElevatorContactBlockEntity)loaded(h,"elevator_contact",AllBlocks.ELEVATOR_CONTACT,source);var saved=contact.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(contact.shortName.equals("2")&&contact.longName.equals("Source floor")&&contact.lastReportedCurrentFloor.equals("1")&&contact.doorControls.mode.name().equals("NORTH")&&saved.getBooleanOr("ColumnActive",false)&&!saved.contains("ColumnOwner")&&!saved.contains("ColumnController"),"Genuine original plain labels/door policy/target/active marker retain no unstored cabin UUID");h.succeed();
    }

    @GameTest public void originalActiveColumnBindsOnlyItsActualPublishedMatchingLoadedCabin(GameTestHelper h)throws Exception{
        var body=P.below(3);var moving=body.east();var floor=moving.east();h.setBlock(P,AllBlocks.ELEVATOR_PULLEY);h.setBlock(body,Blocks.BARREL);h.setBlock(moving,AllBlocks.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING,Direction.EAST));h.setBlock(floor,AllBlocks.REDSTONE_CONTACT.defaultBlockState().setValue(RedstoneContactBlock.FACING,Direction.WEST));
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(body),h.absolutePos(moving)))),"Actual native cabin glue admitted");
        var pulley=h.getBlockEntity(P,ElevatorPulleyBlockEntity.class);pulley.setNetworkState(64,64,false,new KineticGraph.Stress(512,64,false),Map.of(Direction.WEST,64f));h.assertTrue(pulley.assemble(),"Actual native cabin assembled");var owner=pulley.getMovedContraption();h.assertTrue(owner!=null&&owner.hasOwnedStructure()&&h.getLevel().getEntity(owner.getUUID())==owner,"Original cabin has real native section ownership");var coords=((ElevatorContraption)owner.getContraption()).getGlobalColumn();
        ElevatorColumn.clear(h.getLevel());var target=fixture("elevator_contact");target.putInt("ColumnTarget",h.absolutePos(floor).getY());target.putBoolean("ColumnActive",true);var state=AllBlocks.ELEVATOR_CONTACT.defaultBlockState().setValue(ElevatorContactBlock.FACING,Direction.WEST);h.setBlock(floor,state);var contact=h.getBlockEntity(floor,ElevatorContactBlockEntity.class);load(h,contact,converted(h,"elevator_contact",target,floor));contact.initialize();var column=ElevatorColumn.get(h.getLevel(),coords);
        h.assertTrue(column!=null&&column.isActive()&&column.activeOwner()==null&&!column.claim(java.util.UUID.randomUUID(),h.absolutePos(P)),"Saved active marker is not arbitrary UUID ownership and cannot be stolen through ordinary claim");
        ElevatorPulleyBlockEntity.tick(h.getLevel(),h.absolutePos(P),pulley.getBlockState(),pulley);h.assertTrue(column.activeOwner().equals(owner.getUUID())&&column.activeController().equals(pulley.getBlockPos())&&pulley.getMovedContraption()==owner,"Only actual loaded matching owned cabin/controller supplies its real UUID");h.succeed();
    }

    @GameTest public void originalPressTicksRetainTheActualImpactBoundaryWithoutInventingPendingOutput(GameTestHelper h){
        var before=fixture("mechanical_press");before.putBoolean("Running",true);before.putInt("Ticks",PressingBehaviour.CYCLE/2);var pre=(MechanicalPressBlockEntity)loaded(h,"mechanical_press",AllBlocks.MECHANICAL_PRESS,before);h.assertTrue(!pre.getPressingBehaviour().hasAppliedImpact(),"Original saved impact boundary is still unpaid and must use actual currently loaded stock");
        var after=before.copy();after.putInt("Ticks",PressingBehaviour.CYCLE/2+1);var post=(MechanicalPressBlockEntity)loaded(h,"mechanical_press",AllBlocks.MECHANICAL_PRESS,after);var saved=post.saveWithoutMetadata(h.getLevel().registryAccess());h.assertTrue(post.getPressingBehaviour().hasAppliedImpact()&&!saved.contains("PendingOutputs")&&!saved.contains("PendingRecipe"),"Actual passed source boundary prevents replay without manufacturing an output/RNG/payment receipt");
        after.putInt("Ticks",2500000);var longCycle=(MechanicalPressBlockEntity)loaded(h,"mechanical_press",AllBlocks.MECHANICAL_PRESS,after);h.assertTrue(longCycle.getPressingBehaviour().runningTicks==2500000,"Source public saved cycle integer is retained before its next actual completion tick");h.succeed();
    }

    @GameTest public void originalMixerAtImpactUsesOnlyTheActualLoadedPayableBasinRecipeAndDebitsOnce(GameTestHelper h){
        h.setBlock(P.below(2),AllBlocks.BASIN);var basin=h.getBlockEntity(P.below(2),BasinBlockEntity.class);try(var tx=Transaction.openOuter()){h.assertTrue(basin.inventory.automation.insert(ItemVariant.of(Items.ANDESITE),1,tx)==1&&basin.inventory.automation.insert(ItemVariant.of(Items.IRON_NUGGET),1,tx)==1,"Original finite source input is actually held by the native basin");tx.commit();}
        var source=fixture("mechanical_mixer");source.putBoolean("Running",true);source.putInt("Ticks",20);var mixer=(MechanicalMixerBlockEntity)loaded(h,"mechanical_mixer",AllBlocks.MECHANICAL_MIXER,source);install(h,mixer);
        for(int tick=0;tick<100;tick++){mixer.setNetworkState(64,64,false,new KineticGraph.Stress(512,64,false),Map.of(Direction.WEST,64f));MechanicalMixerBlockEntity.tick(h.getLevel(),mixer.getBlockPos(),mixer.getBlockState(),mixer);}
        long output=0,input=0;for(var slot:basin.inventory.outputs)if(slot.getResource().isOf(AllItems.ANDESITE_ALLOY))output+=slot.getAmount();for(var slot:basin.inventory.inputs)if(slot.getResource().isOf(Items.ANDESITE)||slot.getResource().isOf(Items.IRON_NUGGET))input+=slot.getAmount();h.assertTrue(output==1&&input==0&&!mixer.saveWithoutMetadata(h.getLevel().registryAccess()).getBooleanOr("OriginalProcessing",true),"Real recipe adoption commits exactly one original andesite+nugget and one actual alloy output");
        var saved=mixer.saveWithFullMetadata(h.getLevel().registryAccess());var copy=(MechanicalMixerBlockEntity)BlockEntity.loadStatic(mixer.getBlockPos(),mixer.getBlockState(),saved,h.getLevel().registryAccess());install(h,copy);for(int tick=0;tick<10;tick++)MechanicalMixerBlockEntity.tick(h.getLevel(),copy.getBlockPos(),copy.getBlockState(),copy);long after=0;for(var slot:basin.inventory.outputs)if(slot.getResource().isOf(AllItems.ANDESITE_ALLOY))after+=slot.getAmount();h.assertTrue(after==1,"Real native reload cannot repay the original consumed input");h.succeed();
    }

    private static CompoundTag deployerStock(){
        var source=fixture("deployer");source.putString("State","EXPANDING");source.putInt("Timer",7777);var inventory=(ListTag)source.get("Inventory");for(int slot=0;slot<36;slot++){var row=item("minecraft:stone",slot==0?5:slot+1);row.putByte("Slot",(byte)slot);inventory.add(row);}
        String[] armor={"leather_boots","leather_leggings","leather_chestplate","leather_helmet","shield"};int[] slots={100,101,102,103,150};for(int i=0;i<slots.length;i++){var row=item("minecraft:"+armor[i],1);row.putByte("Slot",(byte)slots[i]);inventory.add(row);}source.put("HeldItem",item("minecraft:stone",5));var overflow=new ListTag();overflow.add(item("minecraft:gold_ingot",7));source.put("Overflow",overflow);return source;
    }
    private static DeployerBlockEntity stocked(GameTestHelper h){var owner=(DeployerBlockEntity)loaded(h,"deployer",AllBlocks.DEPLOYER,deployerStock());install(h,owner);return owner;}
    @GameTest public void allFortyOneOriginalPlayerSlotsRetainEquipmentAddressesAndOneAuthoritativeHand(GameTestHelper h){
        var owner=stocked(h);h.assertTrue(owner.getTimer()==7777&&owner.getHeldItem().is(Items.STONE)&&owner.getHeldItem().getCount()==5,"Actual source timer and one hand mirror survive without a 5120 persistence cap");var before=owner.saveWithoutMetadata(h.getLevel().registryAccess());h.assertTrue(DeployerInventory.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),before.get("PlayerInventory")).getOrThrow().size()==40,"All original forty non-hand slots survive before a real fake player is needed");
        var player=owner.getPlayer();for(int slot=0;slot<36;slot++)h.assertTrue(player.getInventory().getItem(slot).is(Items.STONE)&&player.getInventory().getItem(slot).getCount()==(slot==0?5:slot+1),"Exact original regular slot "+slot);
        h.assertTrue(player.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS)&&player.getItemBySlot(EquipmentSlot.LEGS).is(Items.LEATHER_LEGGINGS)&&player.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE)&&player.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET)&&player.getOffhandItem().is(Items.SHIELD)&&owner.getOverflowItems().size()==1&&owner.getOverflowItems().getFirst().getCount()==7,"Original armor/offhand stay equipped while actual overflow stays separate");
        var saved=owner.saveWithFullMetadata(h.getLevel().registryAccess());owner.invalidate();var fresh=(DeployerBlockEntity)BlockEntity.loadStatic(owner.getBlockPos(),owner.getBlockState(),saved,h.getLevel().registryAccess());install(h,fresh);var restored=fresh.getPlayer();h.assertTrue(restored.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET)&&restored.getOffhandItem().is(Items.SHIELD)&&restored.getMainHandItem().getCount()==5&&fresh.getOverflowItems().getFirst().getCount()==7,"Actual hydrated inventory survives invalidation and a fresh real native world owner");h.succeed();
    }

    @GameTest public void originalMovingActorHandSupersedesCapturedMirrorWithoutDuplicatingItsOldStockOrFlatteningEquipment(GameTestHelper h)throws Exception{
        stocked(h);var structure=new BearingContraption(false,Direction.UP);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(P.below()))&&structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Actual source deployer is taken into an owned moving structure");var entity=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());entity.setContraption(structure);entity.setPos(Vec3.atLowerCornerOf(h.absolutePos(P)));h.assertTrue(h.getLevel().addFreshEntity(entity),"Actual moving owner is published");var context=structure.getActorAt(BlockPos.ZERO).right;
        context.data.put("HeldItem",ItemStack.OPTIONAL_CODEC.encodeStart(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),new ItemStack(Items.STONE,2)).getOrThrow());var behaviour=new DeployerMovementBehaviour();var player=behaviour.getPlayer(context);
        h.assertTrue(player.getMainHandItem().getCount()==2&&player.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET)&&player.getOffhandItem().is(Items.SHIELD)&&player.movingOverflow.size()==1&&player.movingOverflow.getFirst().getCount()==7,"Actual current actor hand2 replaces captured hand5 without adding old mirror stock; original equipment and separate overflow retain their addresses");
        behaviour.writeExtraData(context);var saved=structure.writeNBT(h.getLevel().registryAccess(),false);var restored=Contraption.fromNBT(h.getLevel(),saved,false).getActorAt(BlockPos.ZERO).right;var again=behaviour.getPlayer(restored);h.assertTrue(again.getMainHandItem().getCount()==2&&again.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET)&&again.getOffhandItem().is(Items.SHIELD)&&again.movingOverflow.getFirst().getCount()==7,"Actual moving serialization/reload retains hand/equipment/overflow once");h.succeed();
    }

    @GameTest public void actualOriginalActiveActorWithoutCapturedInventoryUsesOnlyItsSavedCurrentHand(GameTestHelper h)throws Exception{
        var old=fixture("deployer");old.remove("Inventory");old.put("HeldItem",item("minecraft:stone",5));var overflow=new ListTag();overflow.add(item("minecraft:gold_ingot",7));old.put("Overflow",overflow);
        var owner=(DeployerBlockEntity)loaded(h,"deployer",AllBlocks.DEPLOYER,old);h.assertTrue(owner.getHeldItem().isEmpty(),"Actual source actor had removed captured Inventory; stale BE hand5 is a mirror and never extra stock");install(h,owner);
        var structure=new BearingContraption(false,Direction.UP);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(P.below()))&&structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Real native owned actor carries the converted captured data");var entity=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());entity.setContraption(structure);entity.setPos(Vec3.atLowerCornerOf(h.absolutePos(P)));h.assertTrue(h.getLevel().addFreshEntity(entity),"Actual moving owner published");var context=structure.getActorAt(BlockPos.ZERO).right;
        var row=new CompoundTag();var pos=new CompoundTag();pos.putInt("X",0);pos.putInt("Y",0);pos.putInt("Z",0);row.put("Pos",pos);for(String key:List.of("Motion","RelativeMotion")){var zero=new ListTag();for(int axis=0;axis<3;axis++)zero.add(DoubleTag.valueOf(0));row.put(key,zero);}row.putBoolean("Stall",false);row.putBoolean("FirstMovement",false);var data=new CompoundTag();data.put("HeldItem",item("minecraft:stone",2));row.put("Data",data);
        var actual=NeoForgeMovementContextImport.convert(context.state,row,VERSION,h.getLevel().registryAccess(),NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(P)));context.data=actual.getCompoundOrEmpty("Data");var behavior=new DeployerMovementBehaviour();var player=behavior.getPlayer(context);long stones=0;for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot).is(Items.STONE))stones+=player.getInventory().getItem(slot).getCount();for(var stack:player.movingOverflow)if(stack.is(Items.STONE))stones+=stack.getCount();
        h.assertTrue(stones==2&&player.movingOverflow.size()==1&&player.movingOverflow.getFirst().is(Items.GOLD_INGOT)&&player.movingOverflow.getFirst().getCount()==7,"Genuine actor Data hand2 is the only stone stock; original independent captured overflow7 survives");h.succeed();
    }

    private static long recovered(GameTestHelper h,Item item){
        long count=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(P)).inflate(4),entity->entity.getItem().is(item)).stream().mapToLong(entity->entity.getItem().getCount()).sum();var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);var tag=(CompoundTag)DeployerOutputRecovery.CODEC.encodeStart(ops,h.getLevel().getDataStorage().computeIfAbsent(DeployerOutputRecovery.TYPE)).getOrThrow();
        for(var value:tag.getListOrEmpty("Pending")){var row=(CompoundTag)value;if(!row.read("Position",Vec3.CODEC).orElse(Vec3.ZERO).equals(Vec3.atCenterOf(h.absolutePos(P))))continue;var loose=row.read("Loose",ItemStack.OPTIONAL_CODEC,ops).orElse(ItemStack.EMPTY);var pending=row.getCompoundOrEmpty("Entity").read("Item",ItemStack.OPTIONAL_CODEC,ops).orElse(ItemStack.EMPTY);if(loose.is(item))count+=loose.getCount();if(pending.is(item))count+=pending.getCount();}return count;
    }
    @GameTest public void actualDeployerRemovalHandsEveryOriginalInventoryAndEquipmentSlotToItsRealDropJournalOnce(GameTestHelper h){
        var owner=stocked(h);owner.getPlayer();h.assertTrue(h.getLevel().destroyBlock(owner.getBlockPos(),false),"Actual native removal performs the genuine journal handoff");long stones=5;for(int slot=1;slot<36;slot++)stones+=slot+1;
        h.assertTrue(recovered(h,Items.STONE)==stones&&recovered(h,Items.GOLD_INGOT)==7&&recovered(h,Items.LEATHER_BOOTS)==1&&recovered(h,Items.LEATHER_LEGGINGS)==1&&recovered(h,Items.LEATHER_CHESTPLATE)==1&&recovered(h,Items.LEATHER_HELMET)==1&&recovered(h,Items.SHIELD)==1,"Every original real player slot and overflow portion is owned exactly once by admitted drops or the actual pending world journal; no HeldItem mirror duplicate");h.succeed();
    }
}
