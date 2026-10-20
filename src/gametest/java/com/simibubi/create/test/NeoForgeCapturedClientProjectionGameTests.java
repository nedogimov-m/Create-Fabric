package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.NeoForgeCapturedBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Separate genuine source client protocol omissions; actual native factories, no fake Level. UNRUN. */
public final class NeoForgeCapturedClientProjectionGameTests {
    private static final BlockPos P=new BlockPos(3,4,3);
    private static CompoundTag additional(CompoundTag source){var tag=source.copy();for(String field:new String[]{"id","x","y","z"})tag.remove(field);return tag;}
    private static CompoundTag cannon(){
        var tag=new CompoundTag();tag.putString("id","create:schematicannon");var inventory=new CompoundTag();inventory.putInt("Size",5);var items=new ListTag();var powder=new CompoundTag();powder.putString("id","minecraft:gunpowder");powder.putInt("count",3);powder.putInt("Slot",4);items.add(powder);inventory.put("Items",items);tag.put("Inventory",inventory);tag.putFloat("Progress",.25f);tag.putFloat("PaperProgress",0);tag.putInt("RemainingFuel",20);tag.putString("Status","ready");tag.putString("State","RUNNING");tag.putBoolean("Running",true);tag.putInt("AmountPlaced",2);tag.putInt("AmountToPlace",8);tag.putFloat("DefaultYaw",45);var options=new CompoundTag();options.putInt("replace_mode",2);options.putBoolean("skip_missing",false);options.putBoolean("replace_block_entities",true);tag.put("Options",options);var printer=new CompoundTag();printer.putInt("EntityProgress",-1);printer.putString("PrintStage","BLOCKS");printer.put("DeferredBlocks",new ListTag());tag.put("Printer",printer);tag.put("FlyingBlocks",new ListTag());return tag;
    }
    private static CompoundTag client(CompoundTag full){var tag=additional(full);tag.remove("Inventory");tag.remove("Running");return tag;}
    private static CompoundTag convert(GameTestHelper h,BlockState state,CompoundTag data,CompoundTag client){var row=new CompoundTag();row.put("Data",data);row.put("UpdateTag",client);var before=row.copy();var result=NeoForgeCapturedBlockEntityImport.convert(row,P,state,NeoForgeSavedDataImport.SOURCE_DATA_VERSION,h.getLevel().registryAccess(),NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(P)));h.assertTrue(row.equals(before),"Original full owner and distinct client view remain unchanged");return result;}

    @GameTest public void originalCannonClientStateDoesNotInheritADiskOnlyRunningMarkerOrPrivateInventory(GameTestHelper h){
        var data=cannon();var before=data.copy();var client=client(data);client.putString("State","STOPPED");client.putString("Status","idle");var state=AllBlocks.SCHEMATICANNON.defaultBlockState();var result=convert(h,state,data,client);var server=result.getCompoundOrEmpty("Data");var update=result.getCompoundOrEmpty("UpdateTag");var owner=(SchematicannonBlockEntity)BlockEntity.loadStatic(P,state,server,h.getLevel().registryAccess());var view=new SchematicannonBlockEntity(P,state);view.readClient(update,h.getLevel().registryAccess());
        h.assertTrue(owner.state==SchematicannonBlockEntity.State.RUNNING&&owner.inventory.getItem(4).getCount()==3&&server.getBooleanOr("Running",false)&&view.state==SchematicannonBlockEntity.State.STOPPED&&view.inventory.isEmpty()&&before.equals(data),"Actual full source retains running state and3 real gunpowder while the distinct client STOPPED view is decoded without a mismatched disk marker or a second material inventory");h.assertTrue(!update.contains("Inventory")&&!update.contains("Running")&&!update.contains("ActiveBlueprint")&&view.flyingBlocks.isEmpty(),"Genuine original client omissions stay omitted through the actual native writer; no private active blueprint or paid flying job is manufactured");h.succeed();
    }

    @GameTest public void cannonClientProjectionNeverWeakensFullOwnerValidationAndRefusesPrivateClientFields(GameTestHelper h){
        var data=cannon();data.putBoolean("Running",false);var client=client(data);client.putString("State","STOPPED");var beforeData=data.copy();var beforeClient=client.copy();boolean rejected=false;try{convert(h,AllBlocks.SCHEMATICANNON.defaultBlockState(),data,client);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&data.equals(beforeData)&&client.equals(beforeClient),"Malformed full owner disk marker must still fail before a client-only projection is considered");
        data=cannon();client=client(data);client.put("Inventory",data.get("Inventory").copy());beforeData=data.copy();beforeClient=client.copy();rejected=false;try{convert(h,AllBlocks.SCHEMATICANNON.defaultBlockState(),data,client);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&data.equals(beforeData)&&client.equals(beforeClient),"Original partial client writer cannot import server-only Inventory, and refusal does not mutate its real source owner");h.succeed();
    }

    @GameTest public void genuineBracketRemovalSnapshotErasesOnlyItsClientViewAndQueuesOneNativeRedrawBeforeLevelAssignment(GameTestHelper h){
        var data=new CompoundTag();data.putString("id","create:simple_kinetic");var bracket=new CompoundTag();bracket.putString("Name","create:metal_bracket");data.put("Bracket",bracket);var client=new CompoundTag();client.putBoolean("Redraw",true);var state=AllBlocks.SHAFT.defaultBlockState();var result=convert(h,state,data,client);var owner=BlockEntity.loadStatic(P,state,result.getCompoundOrEmpty("Data"),h.getLevel().registryAccess());var view=(SmartBlockEntity)((EntityBlock)state.getBlock()).newBlockEntity(P,state);view.readClient(result.getCompoundOrEmpty("UpdateTag"),h.getLevel().registryAccess());var fullBracket=BlockEntityBehaviour.get(owner,BracketedBlockEntityBehaviour.TYPE);var clientBracket=BlockEntityBehaviour.get(view,BracketedBlockEntityBehaviour.TYPE);
        h.assertTrue(fullBracket.isBracketPresent()&&fullBracket.getBracket().is(AllBlocks.METAL_BRACKET)&&!clientBracket.isBracketPresent()&&!result.getCompoundOrEmpty("UpdateTag").contains("Bracket"),"Source optional shared behaviour field is removed by its explicit client absence while the full original physical bracket remains intact");h.assertTrue(view.getUpdateTag(h.getLevel().registryAccess()).getBooleanOr("Redraw",false)&&!view.getUpdateTag(h.getLevel().registryAccess()).contains("Redraw")&&!view.saveWithoutMetadata(h.getLevel().registryAccess()).contains("Redraw"),"Actual native common client protocol queues/retires the source redraw before any real Level; full save gains no persistent event or fake world side effect");h.succeed();
    }
}
