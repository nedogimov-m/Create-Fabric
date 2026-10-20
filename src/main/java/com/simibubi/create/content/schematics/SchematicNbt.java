package com.simibubi.create.content.schematics;

import com.google.gson.*;
import com.simibubi.create.Create;
import com.simibubi.create.api.schematic.nbt.*;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;

/** Source safe-NBT extension points and actual native data/components filtering before printing. */
public final class SchematicNbt {
    private static final TagKey<Block> SAFE=TagKey.create(Registries.BLOCK,Create.asResource("safe_nbt"));
    public static CompoundTag prepare(Level level,BlockState state,BlockEntity entity){
        if(entity==null)return null;CompoundTag data=null;var writer=SafeNbtWriterRegistry.REGISTRY.get(entity.getType());
        if(state.is(SAFE))data=entity.saveWithFullMetadata(level.registryAccess());
        else if(writer!=null){data=new CompoundTag();writer.writeSafe(entity,data,level.registryAccess());}
        else if(entity instanceof PartialSafeNBT safe){data=new CompoundTag();safe.writeSafe(data,level.registryAccess());}
        else if(entity instanceof BlockEntityBehaviour.Provider provider){
            var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
            var filtering=provider.getBehaviour(FilteringBehaviour.TYPE);if(filtering!=null&&filtering.isSafeNBT())filtering.write(output);
            var redstone=provider.getBehaviour(LinkBehaviour.TYPE);if(redstone!=null&&redstone.isSafeNBT())redstone.write(output);
            var logistics=provider.getBehaviour(LogisticallyLinkedBehaviour.TYPE);if(logistics!=null&&logistics.isSafeNBT())output.store("Freq",UUIDUtil.CODEC,logistics.freqId);
            data=output.buildResult();if(data.isEmpty())data=null;
        }
        return filter(nativeConfiguration(state,entity,data));
    }
    /** Native durable runtime owners did not exist in the source configuration contract. Never clone their authority. */
    private static CompoundTag nativeConfiguration(BlockState state,BlockEntity entity,CompoundTag data){
        if(data==null)return null;
        var keep=new ArrayList<String>();boolean configurationOnly=false;
        if(entity instanceof com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity){keep.add("ClockHands");configurationOnly=true;}
        else if(entity instanceof com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity){keep.add("MovementMode");if(entity instanceof com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity)keep.add("WindmillDirection");configurationOnly=true;}
        else if(entity instanceof com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity){keep.add("ScrollValue");configurationOnly=true;}
        else if(entity instanceof com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity){keep.add("Instructions");configurationOnly=true;}
        if(configurationOnly){var configuration=new CompoundTag();for(var key:keep)if(data.contains(key))configuration.put(key,data.get(key).copy());return configuration;}
        var safe=data.copy();
        if(entity instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity){for(var key:List.of("Source","Generator","KineticSourceFace","Network","NeedsSpeedUpdate","KineticConflictPending","Sequence","SequencePhase","Speed","TheoreticalSpeed","StressCapacity","StressImpact","Overstressed","PortSpeeds","Conflicted","InUse","Backwards","IndependentAngle"))safe.remove(key);if(!state.is(com.simibubi.create.AllBlocks.CREATIVE_MOTOR))safe.remove("GeneratedSpeed");}
        return safe;
    }
    /** Same native source safe configuration policy for another real placement caller. */
    public static CompoundTag configurationForPlacement(BlockState state,BlockEntity entity,CompoundTag data){return state.is(SAFE)?filter(nativeConfiguration(state,entity,data)):null;}
    public static CompoundTag filter(CompoundTag tag){
        if(tag==null||unsafeTag(tag,0))return null;
        var safe=tag.copy();safe.remove("x");safe.remove("y");safe.remove("z");return safe;
    }
    private static boolean unsafeTag(Tag tag,int depth){
        if(depth>64)return true;
        if(tag instanceof CompoundTag compound){for(var key:compound.keySet())if(key.equals("clickEvent")||key.equals("click_event")||unsafeTag(compound.get(key),depth+1))return true;}
        else if(tag instanceof ListTag list){for(var value:list)if(unsafeTag(value,depth+1))return true;}
        else if(tag instanceof StringTag string){var value=string.value();if((value.startsWith("{")||value.startsWith("["))&&(value.contains("clickEvent")||value.contains("click_event"))){try{if(unsafeJson(JsonParser.parseString(value),0))return true;}catch(JsonParseException malformed){return true;}}}
        return false;
    }
    private static boolean unsafeJson(JsonElement element,int depth){if(depth>64)return true;if(element.isJsonObject()){for(var e:element.getAsJsonObject().entrySet())if(e.getKey().equals("clickEvent")||e.getKey().equals("click_event")||unsafeJson(e.getValue(),depth+1))return true;}else if(element.isJsonArray())for(var e:element.getAsJsonArray())if(unsafeJson(e,depth+1))return true;return false;}
    public static boolean hasClick(Component text){
        var queue=new ArrayDeque<Component>();var seen=Collections.newSetFromMap(new IdentityHashMap<Component,Boolean>());queue.add(text);
        while(!queue.isEmpty()){
            var next=queue.removeFirst();if(!seen.add(next))continue;if(seen.size()>10000||next.getStyle().getClickEvent()!=null)return true;
            if(next.visit((style,value)->style.getClickEvent()!=null?Optional.of(Boolean.TRUE):Optional.empty(),net.minecraft.network.chat.Style.EMPTY).isPresent())return true;
            queue.addAll(next.getSiblings());
            // Inspect every argument, including arguments omitted by the current language's translation.
            if(next.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated)for(var arg:translated.getArgs())if(arg instanceof Component component)queue.add(component);
        }
        return false;
    }
    public static ItemStack sanitizeItem(ItemStack source){return sanitizeItem(source,0);}
    private static ItemStack sanitizeItem(ItemStack source,int depth){
        if(source.isEmpty())return ItemStack.EMPTY;var copy=source.copy();
        copy.remove(DataComponents.BLOCK_ENTITY_DATA);copy.remove(DataComponents.ENTITY_DATA);copy.remove(DataComponents.CONTAINER_LOOT);
        var name=copy.get(DataComponents.CUSTOM_NAME);if(name!=null&&hasClick(name))copy.remove(DataComponents.CUSTOM_NAME);
        var book=copy.get(DataComponents.WRITTEN_BOOK_CONTENT);if(book!=null&&book.pages().stream().anyMatch(page->hasClick(page.raw())))copy.remove(DataComponents.WRITTEN_BOOK_CONTENT);
        var container=copy.get(DataComponents.CONTAINER);if(container!=null){if(depth>=64)copy.remove(DataComponents.CONTAINER);else copy.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(container.itemCopies().map(item->sanitizeItem(item,depth+1)).toList()));}
        return copy;
    }
    private SchematicNbt(){}
}
