package com.simibubi.create.content.equipment.clipboard;

import com.mojang.serialization.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.simibubi.create.*;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.BlockHitResult;

/** Actual native item select property and live clipboard editor/highlight integration. */
public final class ClipboardClient {
    private static final class ModelType implements SelectItemModelProperty<ClipboardType> {
        private static final ModelType INSTANCE=new ModelType();
        private static final Type<ModelType,ClipboardType> TYPE=Type.create(MapCodec.unit(INSTANCE),ClipboardType.CODEC);
        @Override public ClipboardType get(ItemStack stack,ClientLevel level,LivingEntity owner,int seed,ItemDisplayContext display){return stack.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT,ClipboardContent.EMPTY).type();}
        @Override public Codec<ClipboardType> valueCodec(){return ClipboardType.CODEC;}
        @Override public Type<ModelType,ClipboardType> type(){return TYPE;}
    }
    private record Highlight(net.minecraft.core.BlockPos pos,VoxelShape shape){}
    private static final Map<LevelRenderState,Highlight> HIGHLIGHTS=new WeakHashMap<>();
    public static void register(){
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->HIGHLIGHTS.clear());
        LevelExtractionEvents.END_EXTRACTION.register(context->{
            var client=Minecraft.getInstance();Highlight highlight=null;
            if(client.player!=null&&client.level!=null&&!client.player.isSpectator()&&client.gui.screen()==null&&client.player.getMainHandItem().is(AllBlocks.CLIPBOARD.asItem())&&client.hitResult instanceof BlockHitResult hit&&client.level.getWorldBorder().isWithinBounds(hit.getBlockPos())){
                var be=LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos());boolean supported=be instanceof ClipboardBlockEntity;
                if(be!=null)for(var provider:ClipboardValueSettingsHandler.cloneables(be))supported|=provider.writeToClipboard(client.level.registryAccess(),new net.minecraft.nbt.CompoundTag(),hit.getDirection());
                if(supported){var shape=LoadedLevelAccess.blockState(client.level,hit.getBlockPos()).getShape(client.level,hit.getBlockPos());if(!shape.isEmpty())highlight=new Highlight(hit.getBlockPos(),shape);}
            }
            HIGHLIGHTS.put(context.levelState(),highlight);
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var highlight=HIGHLIGHTS.get(context.levelState());if(highlight==null)return;var camera=context.levelState().cameraRenderState.pos;var pose=new PoseStack();pose.translate(highlight.pos.getX()-camera.x,highlight.pos.getY()-camera.y,highlight.pos.getZ()-camera.z);context.submitNodeCollector().submitShapeOutline(pose,highlight.shape,net.minecraft.client.renderer.rendertype.RenderTypes.lines(),0xFFB6D7ED,2,false);});
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context,outline)->HIGHLIGHTS.get(context.levelState())==null);
        SelectItemModelProperties.ID_MAPPER.put(ClipboardType.ID,ModelType.TYPE);
        ClipboardBlockItem.CLIENT_OPEN=(player,pos)->{var client=Minecraft.getInstance();if(player!=client.player||client.level==null)return;var components=pos==null?player.getMainHandItem().getComponents():LoadedLevelAccess.blockEntity(client.level,pos) instanceof ClipboardBlockEntity owner?owner.components():null;if(components!=null)client.gui.setScreen(new ClipboardScreen(player.getInventory().getSelectedSlot(),components,pos));};
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("clipboard_copy_paste"),(gui,delta)->{
            var client=Minecraft.getInstance();if(client.player==null||client.level==null||client.gui.screen()!=null||client.gui.hud.isHidden()||client.player.isShiftKeyDown()||!client.player.getMainHandItem().is(AllBlocks.CLIPBOARD.asItem())||!(client.hitResult instanceof BlockHitResult hit))return;
            var be=LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos());if(be==null)return;var providers=ClipboardValueSettingsHandler.cloneables(be);var values=client.player.getMainHandItem().getOrDefault(AllDataComponents.CLIPBOARD_CONTENT,ClipboardContent.EMPTY).copiedValues();
            boolean copy=be instanceof ClipboardBlockEntity,paste=false;
            for(var provider:providers){var tag=new net.minecraft.nbt.CompoundTag();copy|=provider.writeToClipboard(client.level.registryAccess(),tag,hit.getDirection());if(values.isPresent())paste|=provider.readFromClipboard(client.level.registryAccess(),values.get().getCompoundOrEmpty(provider.getClipboardKey()),client.player,hit.getDirection(),true);}
            int y=gui.guiHeight()-88;if(!copy&&!paste)return;gui.centeredText(client.font,net.minecraft.network.chat.Component.translatable("create.clipboard.actions"),gui.guiWidth()/2,y,0xFFFBDC7D);
            if(copy)gui.centeredText(client.font,net.minecraft.network.chat.Component.translatable(be instanceof ClipboardBlockEntity?"create.clipboard.copy_other_clipboard":"create.clipboard.to_copy",net.minecraft.network.chat.Component.keybind("key.use")),gui.guiWidth()/2,y+12,0xFFFFFFFF);
            if(paste)gui.centeredText(client.font,net.minecraft.network.chat.Component.translatable("create.clipboard.to_paste",net.minecraft.network.chat.Component.keybind("key.attack")),gui.guiWidth()/2,y+24,0xFFFFFFFF);
        });
    }
    private ClipboardClient(){}
}
