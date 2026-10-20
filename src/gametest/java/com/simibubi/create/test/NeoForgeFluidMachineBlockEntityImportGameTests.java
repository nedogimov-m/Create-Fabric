package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.NeoForgeBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeFluidStackImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

/** Genuine pinned FluidTank/FluidStack/search/connection schemas through the root dispatcher. UNRUN. */
public final class NeoForgeFluidMachineBlockEntityImportGameTests {
    private static final int VERSION=NeoForgeSavedDataImport.SOURCE_DATA_VERSION;
    private static Identifier id(String value){return Identifier.parse("create:"+value);}
    private static CompoundTag pos(BlockPos value){var tag=new CompoundTag();tag.putInt("X",value.getX());tag.putInt("Y",value.getY());tag.putInt("Z",value.getZ());return tag;}
    private static CompoundTag lerped(float value){var tag=new CompoundTag();tag.putFloat("Speed",.1f);tag.putFloat("Target",value);tag.putFloat("Value",value);return tag;}
    private static CompoundTag fluid(String id,int amount,boolean named){
        var fluid=new CompoundTag();fluid.putString("id",id);fluid.putInt("amount",amount);
        if(named){var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\"Original fluid\"}");fluid.put("components",components);}return fluid;
    }
    private static CompoundTag tank(CompoundTag fluid){var wrapper=new CompoundTag();if(!fluid.isEmpty())wrapper.put("Fluid",fluid);return wrapper;}
    private static CompoundTag migrated(GameTestHelper h,String type,BlockPos at,CompoundTag source){
        var original=source.copy();original.putString("id",id(type).toString());original.putInt("x",at.getX());original.putInt("y",at.getY());original.putInt("z",at.getZ());var before=original.copy();
        var converted=NeoForgeBlockEntityImport.convert(id(type),original,VERSION,h.getLevel().registryAccess(),NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),at));
        h.assertTrue(original.equals(before),"Typed root migration preserves the caller's actual source save");return converted;
    }
    private static BlockEntity twice(GameTestHelper h,String type,Block block,CompoundTag source){
        var at=h.absolutePos(new BlockPos(1,5,1));var first=BlockEntity.loadStatic(at,block.defaultBlockState(),migrated(h,type,at,source),h.getLevel().registryAccess());
        h.assertTrue(first!=null&&first.getLevel()==null,"Actual registered native owner decodes before a level exists");
        var second=BlockEntity.loadStatic(at,block.defaultBlockState(),first.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(second!=null&&second!=first,"Actual native re-save decodes into a fresh registered owner");return second;
    }
    private static CompoundTag spout(int ticks,CompoundTag fluid){
        var segment=new CompoundTag();segment.put("TankContent",tank(fluid));segment.put("Level",lerped(.75f));var tanks=new ListTag();tanks.add(segment);
        var source=new CompoundTag();source.put("Tanks",tanks);source.putInt("ProcessingTicks",ticks);return source;
    }
    private static CompoundTag pipe(GameTestHelper h,int bufferAmount){
        var flow=new CompoundTag();flow.put("Fluid",fluid("minecraft:water",1000,true));flow.putBoolean("In",true);flow.put("Progress",lerped(.375f));
        var location=new CompoundTag();location.put("Pos",pos(h.absolutePos(new BlockPos(1,5,1))));location.putString("Face","UP");
        var end=tank(fluid("minecraft:water",bufferAmount,true));end.putBoolean("Pulling",true);end.put("Location",location);
        var pressure=new ListTag();pressure.add(FloatTag.valueOf(2500000));pressure.add(FloatTag.valueOf(64));var row=new CompoundTag();row.put("Pressure",pressure);row.put("Flow",flow);row.put("OpenEnd",end);
        var source=new CompoundTag();source.put("up",row);return source;
    }

    @GameTest public void genuineFluidTankWrapperStaysDistinctFromItsNestedFluidStack(GameTestHelper h){
        var genuine=tank(fluid("minecraft:water",750,true));var converted=NeoForgeFluidStackImport.tank(genuine,VERSION,h.getLevel().registryAccess());
        h.assertTrue(converted.amount()==60750&&converted.variant().getComponents().get(DataComponents.CUSTOM_NAME).getString().equals("Original fluid"),"Actual original FluidTank wrapper unwraps exactly one typed FluidStack and its genuine component patch");
        h.assertTrue(NeoForgeFluidStackImport.tank(new CompoundTag(),VERSION,h.getLevel().registryAccess()).amount()==0,"Original physically empty FluidTank writer emits an empty compound");
        boolean refused=false;try{NeoForgeFluidStackImport.tank(fluid("minecraft:water",750,false),VERSION,h.getLevel().registryAccess());}catch(IllegalArgumentException expected){refused=true;}
        h.assertTrue(refused,"A direct FluidStack is not a genuine FluidTank wrapper");h.succeed();
    }

    @GameTest public void hoseRetainsActualNamedFluidExtensionAndBothOriginalSharedSearchContexts(GameTestHelper h){
        var source=new CompoundTag();source.put("Offset",lerped(4100.5f));source.put("Tank",tank(fluid("minecraft:water",750,true)));source.putBoolean("Infinite",true);
        var root=h.absolutePos(new BlockPos(1,1,1));source.put("LastPos",pos(root));source.put("AffectedAreaFrom",pos(root.west()));source.put("AffectedAreaTo",pos(root.east()));
        var owner=(HosePulleyBlockEntity)twice(h,"hose_pulley",AllBlocks.HOSE_PULLEY,source);var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(owner.getOffset()==4100.5f&&owner.tank.getAmount()==60750&&owner.tank.getResource().getFluid()==Fluids.WATER,"Actual source extension and one finite stock survive without a persistence4096 cap");
        var draining=saved.getCompound("Draining").orElseThrow();var filling=saved.getCompound("Filling").orElseThrow();
        h.assertTrue(draining.getBooleanOr("Infinite",false)&&filling.getBooleanOr("Infinite",false)&&draining.get("LastPos").equals(filling.get("LastPos"))&&!draining.contains("Tank")&&!filling.contains("Tank"),"Both original behaviours read the same source geometry/infinity root; their search contexts do not duplicate physical fluid");h.succeed();
    }

    @GameTest public void spoutKeepsOnlyItsActualTankAndTimerBeforeAnyRealInputOwnerIsLoaded(GameTestHelper h){
        var owner=(SpoutBlockEntity)twice(h,"spout",AllBlocks.SPOUT,spout(7,fluid("minecraft:water",750,true)));var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(owner.tank.getAmount()==60750&&owner.processingTicks==7&&saved.getBooleanOr("OriginalProcessing",false),"Actual original spout fluid and consumed timer survive a native re-save before handoff");
        h.assertTrue(!saved.contains("ProcessingTarget")&&!saved.contains("ProcessingInput")&&!saved.contains("Pending"),"No original target UUID, locked input or paid output is invented");h.succeed();
    }

    @GameTest public void allSixPipeOwnerTypesMigrateTheirActualConnectionSchema(GameTestHelper h){
        String[] types={"mechanical_pump","smart_fluid_pipe","fluid_pipe","encased_fluid_pipe","glass_fluid_pipe","fluid_valve"};
        Block[] blocks={AllBlocks.MECHANICAL_PUMP,AllBlocks.SMART_FLUID_PIPE,AllBlocks.FLUID_PIPE,AllBlocks.ENCASED_FLUID_PIPE,AllBlocks.GLASS_FLUID_PIPE,AllBlocks.FLUID_VALVE};
        for(int index=0;index<types.length;index++){
            var source=pipe(h,750);if(types[index].equals("fluid_valve"))source.put("Pointer",lerped(.375f));
            var owner=twice(h,types[index],blocks[index],source);var transport=BlockEntityBehaviour.get(owner,FluidTransportBehaviour.TYPE);var connection=transport.getConnection(Direction.UP);
            h.assertTrue(connection!=null&&connection.getInboundPressure()==2500000&&transport.getFlow(Direction.UP)!=null,"Original saved connection pressure survives for "+types[index]);
        }
        h.succeed();
    }

    @GameTest public void flowingReferenceNeverBecomesPhysicalStockBesideTheActualOpenEndBuffer(GameTestHelper h){
        var owner=twice(h,"fluid_pipe",AllBlocks.FLUID_PIPE,pipe(h,750));var transport=BlockEntityBehaviour.get(owner,FluidTransportBehaviour.TYPE);var flow=transport.getFlow(Direction.UP);
        h.assertTrue(flow!=null&&flow.fluid.getAmount()==81&&flow.progress==.375f,"Original pipe Flow.Fluid becomes only the actual native one-millibucket reference, retaining animation progress");
        var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess()).getCompound("Transport").orElseThrow().getCompound("up").orElseThrow();
        h.assertTrue(saved.getCompound("OpenEnd").orElseThrow().getLongOr("Amount",-1)==60750&&!saved.getCompound("Flow").orElseThrow().contains("Amount"),"Only the actual open-end buffer owns60750 droplets; flow samples do not create another tank");h.succeed();
    }

    @GameTest public void originalOpenEndReaderDomainKeepsPreviouslyStoredOverCapacityFluid(GameTestHelper h){
        var owner=twice(h,"fluid_pipe",AllBlocks.FLUID_PIPE,pipe(h,2000));var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.getCompound("Transport").orElseThrow().getCompound("up").orElseThrow().getCompound("OpenEnd").orElseThrow().getLongOr("Amount",-1)==162000,"Actual source FluidTank.readFromNBT retains its previously saved amount independently of new-fill capacity");h.succeed();
    }

    @GameTest public void physicalPipeBracketUsesActualVanillaBlockStateMigration(GameTestHelper h){
        var source=pipe(h,750);var bracket=new CompoundTag();bracket.putString("Name","create:metal_bracket");source.put("Bracket",bracket);
        var owner=twice(h,"fluid_pipe",AllBlocks.FLUID_PIPE,source);var behaviour=BlockEntityBehaviour.get(owner,BracketedBlockEntityBehaviour.TYPE);
        h.assertTrue(behaviour!=null&&behaviour.isBracketPresent()&&behaviour.getBracket().is(AllBlocks.METAL_BRACKET),"The actual source bracket remains one attached material through native block-state DFU and fresh load");h.succeed();
    }

    @GameTest public void originalSpoutFiveTickBoundaryAdoptsActualHeldBucketAndPaysFluidExactlyOnce(GameTestHelper h){
        var at=new BlockPos(3,5,3);var below=at.below(2);h.setBlock(at,AllBlocks.SPOUT);h.setBlock(below,AllBlocks.DEPOT);
        var depot=h.getBlockEntity(below,DepotBlockEntity.class);depot.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(new ItemStack(Items.BUCKET),.5f));
        var absolute=h.absolutePos(at);var owner=(SpoutBlockEntity)BlockEntity.loadStatic(absolute,h.getBlockState(at),migrated(h,"spout",absolute,spout(5,fluid("minecraft:water",1000,false))),h.getLevel().registryAccess());owner.setLevel(h.getLevel());h.getLevel().setBlockEntity(owner);
        SpoutBlockEntity.tick(h.getLevel(),absolute,h.getBlockState(at),owner);
        h.assertTrue(owner.tank.getAmount()==0&&depot.depotBehaviour.getHeldItemStack().is(Items.WATER_BUCKET)&&depot.depotBehaviour.getHeldItemStack().getCount()==1
            &&depot.inventory.outputs.stream().allMatch(slot->slot.isResourceBlank())
            &&h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(below)).inflate(1)).isEmpty(),
            "Actual original5 handoff pays exactly1000mB for one filled bucket in the canonical held slot, with no extra buffered or dropped stock");
        var saved=owner.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(SpoutBlockEntity)BlockEntity.loadStatic(absolute,h.getBlockState(at),saved,h.getLevel().registryAccess());restored.setLevel(h.getLevel());h.getLevel().setBlockEntity(restored);
        for(int index=0;index<8;index++)SpoutBlockEntity.tick(h.getLevel(),absolute,h.getBlockState(at),restored);
        h.assertTrue(restored.tank.getAmount()==0&&depot.depotBehaviour.getHeldItemStack().is(Items.WATER_BUCKET)&&depot.depotBehaviour.getHeldItemStack().getCount()==1
            &&depot.inventory.outputs.stream().allMatch(slot->slot.isResourceBlank())
            &&h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(below)).inflate(1)).isEmpty()
            &&!saved.getBooleanOr("OriginalProcessing",true),"Fresh native re-save/reload cannot replay the adopted original process or publish a second output");h.succeed();
    }
}
