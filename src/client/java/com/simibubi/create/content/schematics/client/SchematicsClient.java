package com.simibubi.create.content.schematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.Create;
import com.simibubi.create.content.schematics.packet.SchematicUploadStatusPacket;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.*;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Native lifecycle, real upload ACK, key/input and extraction/submission registration. */
public final class SchematicsClient {
    public static final ClientSchematicLoader LOADER=new ClientSchematicLoader();
    public static final SchematicHandler HANDLER=new SchematicHandler();
    public static final SchematicAndQuillHandler QUILL=new SchematicAndQuillHandler();
    public static final KeyMapping TOOL_MENU=new KeyMapping("create.keyinfo.toolmenu",InputConstants.KEY_LALT,KeyMapping.Category.register(Create.asResource("schematics")));
    private record Frame(SchematicHandler.Frame schematic,SchematicOutline.Frame quill){}
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    public static boolean onUse(Minecraft client){return client.player!=null&&client.gui.screen()==null&&(QUILL.onUse()||HANDLER.onUse());}
    public static boolean onScroll(double delta){return QUILL.mouseScrolled(delta)||HANDLER.mouseScrolled(delta);}
    private static void clear(){LOADER.cancel(false);HANDLER.clear();QUILL.clear();FRAMES.clear();level=null;com.simibubi.create.content.schematics.cannon.SchematicannonRenderer.clear();}
    public static void register(){
        KeyMappingHelper.registerKeyMapping(TOOL_MENU);
        com.simibubi.create.content.equipment.clipboard.ClipboardClient.register();
        com.simibubi.create.content.schematics.cannon.SchematicannonRenderer.registerModels();
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(com.simibubi.create.AllBlockEntityTypes.SCHEMATICANNON,com.simibubi.create.content.schematics.cannon.SchematicannonRenderer::new);
        net.minecraft.client.gui.screens.MenuScreens.register(com.simibubi.create.content.schematics.table.SchematicTableMenu.TYPE,com.simibubi.create.content.schematics.table.SchematicTableScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(com.simibubi.create.content.schematics.cannon.SchematicannonMenu.TYPE,com.simibubi.create.content.schematics.cannon.SchematicannonScreen::new);
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(client.level!=level){clear();level=client.level;}LOADER.tick();HANDLER.tick();QUILL.tick();});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
        ClientPlayNetworking.registerGlobalReceiver(SchematicUploadStatusPacket.TYPE,(packet,context)->LOADER.status(packet));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("schematics"),(gui,delta)->HANDLER.extractOverlay(gui));
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Create.asResource("schematic_models"),(ResourceManagerReloadListener)manager->HANDLER.updateRenderers());
        LevelExtractionEvents.END_EXTRACTION.register(context->{var camera=context.levelState().cameraRenderState.pos;FRAMES.put(context.levelState(),new Frame(HANDLER.extract(camera,SchematicTransformation.partialTicks()),QUILL.extract()));});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{
            var frame=FRAMES.get(context.levelState());if(frame==null)return;var collector=context.submitNodeCollector();var camera=context.levelState().cameraRenderState;
            if(frame.schematic!=null){var state=frame.schematic;var pose=new PoseStack();pose.mulPose(state.transform());if(state.model()!=null)state.model().submit(pose,collector,camera);state.toolState().submit(collector,camera);}

            if(frame.quill!=null){var pose=new PoseStack();pose.translate(-camera.pos.x,-camera.pos.y,-camera.pos.z);frame.quill.submit(pose,collector);}
        });
    }
    private SchematicsClient(){}
}
