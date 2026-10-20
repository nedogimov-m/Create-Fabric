package com.simibubi.create.api.behaviour.display;

import com.simibubi.create.AllDisplaySources;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.api.registry.*;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.*;
import com.simibubi.create.content.trains.display.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Real source lookup, transport and section-layout contract backed by native synced registries. */
public abstract class DisplaySource {
    public static final SimpleRegistry.Multi<Block,DisplaySource> BY_BLOCK=SimpleRegistry.Multi.create();
    public static final SimpleRegistry.Multi<BlockEntityType<?>,DisplaySource> BY_BLOCK_ENTITY=SimpleRegistry.Multi.create();
    public static final MutableComponent EMPTY_LINE=Component.empty(),WHITESPACE=Component.literal(" ");
    public static final List<MutableComponent> EMPTY=List.of(EMPTY_LINE);
    public abstract List<MutableComponent> provideText(DisplayLinkContext context,DisplayTargetStats stats);
    public void transferData(DisplayLinkContext context,DisplayTarget target,int line){
        if(!isAvailable(context))return;var stats=target.provideStats(context);if(stats.maxRows()<=0||stats.maxColumns()<=0)return;
        if(target instanceof DisplayBoardTarget board){board.acceptFlapText(line,provideFlapDisplayText(context,stats),context);return;}
        var text=provideText(context,stats);if(text.isEmpty())text=EMPTY;if(target.requiresComponentSanitization()&&text.stream().anyMatch(component->hasClickEvent(component,context)))return;target.acceptText(line,text,context);
    }
    private static boolean hasClickEvent(Component component,DisplayLinkContext context){var json=ComponentSerialization.CODEC.encodeStart(context.level().registryAccess().createSerializationContext(JsonOps.INSTANCE),component).result();return json.isEmpty()||hasClickEvent(json.get());}
    private static boolean hasClickEvent(JsonElement value){if(value.isJsonArray()){for(var child:value.getAsJsonArray())if(hasClickEvent(child))return true;}else if(value.isJsonObject()){for(var entry:value.getAsJsonObject().entrySet())if(entry.getKey().equals("click_event")||entry.getKey().equals("clickEvent")||hasClickEvent(entry.getValue()))return true;}return false;}
    public boolean isAvailable(DisplayLinkContext context){return context.endpointsLoaded();}
    public void onSignalReset(DisplayLinkContext context){}
    public void populateData(DisplayLinkContext context){}
    public int getPassiveRefreshTicks(){return 100;}
    public boolean shouldPassiveReset(){return true;}
    public Identifier getId(){return CreateBuiltInRegistries.DISPLAY_SOURCE.getKey(this);}
    protected String getTranslationKey(){return getId().getPath();}
    public Component getName(){return Component.translatable(getId().getNamespace()+".display_source."+getTranslationKey());}
    public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context){return List.of();}
    public void loadFlapDisplayLayout(DisplayLinkContext context,FlapDisplayBlockEntity board,FlapDisplayLayout layout,int line){loadFlapDisplayLayout(context,board,layout);}
    public void loadFlapDisplayLayout(DisplayLinkContext context,FlapDisplayBlockEntity board,FlapDisplayLayout layout){if(!layout.isLayout("Default"))layout.loadDefault(board.getMaxCharCount());}
    public List<List<MutableComponent>> provideFlapDisplayText(DisplayLinkContext context,DisplayTargetStats stats){return provideText(context,stats).stream().map(List::of).toList();}
    public static DisplaySource get(Identifier id){if(id==null)return null;var legacy="create".equals(id.getNamespace())?AllDisplaySources.LEGACY_NAMES.get(id.getPath()):null;return legacy!=null?legacy:CreateBuiltInRegistries.DISPLAY_SOURCE.getValue(id);}
    public static List<DisplaySource> getAll(LevelAccessor level,BlockPos pos){
        if(level instanceof net.minecraft.world.level.Level world&&!LoadedLevelAccess.isLoaded(world,pos))return List.of();var state=LoadedLevelAccess.blockState(level,pos);var result=new ArrayList<>(BY_BLOCK.get(state.getBlock()));var be=LoadedLevelAccess.blockEntity(level,pos);if(be!=null)result.addAll(BY_BLOCK_ENTITY.get(be.getType()));return result.stream().distinct().toList();
    }
}
