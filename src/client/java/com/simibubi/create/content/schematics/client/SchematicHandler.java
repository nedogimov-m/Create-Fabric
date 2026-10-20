package com.simibubi.create.content.schematics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.schematics.*;
import com.simibubi.create.content.schematics.packet.*;
import com.simibubi.create.content.schematics.client.tools.*;
import com.simibubi.create.foundation.gui.AllIcons;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Actual source deploy/move/rotate/flip/creative tools, backed by held schematic components. */
public class SchematicHandler {
    /** Compatibility metadata for native port callers. Runtime state and dispatch use ToolType. */
    @Deprecated
    public enum Tool {
        DEPLOY(AllIcons.I_TOOL_DEPLOY), MOVE(AllIcons.I_TOOL_MOVE_XZ), MOVE_Y(AllIcons.I_TOOL_MOVE_Y),
        ROTATE(AllIcons.I_TOOL_ROTATE), FLIP(AllIcons.I_TOOL_MIRROR), PRINT(AllIcons.I_CONFIRM);
        public final AllIcons icon;
        Tool(AllIcons icon) { this.icon=icon; }
        public ToolType type(){return ToolType.valueOf(name());}
        public Component title() { return type().getDisplayName(); }
        public List<Component> description() { return type().getDescription(); }
    }
    public record Frame(Matrix4fc transform,SchematicRenderer.Frame model,SchematicOutline.Frame outline,
                        Matrix4fc deploymentTransform,SchematicOutline.Frame deployment,
                        SchematicOutline.Edge rotationAxis,SchematicOutline.Frame mirrorPlane,Direction face,Tool tool,
                        SchematicToolRenderState toolState) {}
    private final SchematicHotbarSlotOverlay hotbarOverlay=new SchematicHotbarSlotOverlay();
    private ToolSelectionScreen selector;
    private final SchematicRenderer[] renderers=new SchematicRenderer[3];
    private SchematicTransformation transformation=new SchematicTransformation();
    private AABB bounds=new AABB(BlockPos.ZERO);
    private ItemStack item=ItemStack.EMPTY;
    private String file="",owner="";
    private int slot=-1,syncCooldown;
    private boolean active,deployed,menuWasDown;
    private ToolType currentTool=ToolType.DEPLOY;
    public boolean isActive(){return active;}
    public boolean isDeployed(){return deployed;}
    public SchematicTransformation getTransformation(){return transformation;}
    public AABB getBounds(){return bounds;}
    public ItemStack getActiveSchematicItem(){return item;}
    public String getCurrentSchematicName(){return file;}
    public ToolType currentToolType(){return currentTool;}
    public Tool currentTool(){return Tool.valueOf(currentTool.name());}
    public List<ToolType> toolTypes(){var client=Minecraft.getInstance();return !deployed?List.of(ToolType.DEPLOY):ToolType.getTools(client.player!=null&&client.player.isCreative());}
    public List<Tool> tools(){return toolTypes().stream().map(type->Tool.valueOf(type.name())).toList();}
    public SchematicOutline.Frame getOutline(){return SchematicOutline.extract(bounds,null,0x6886C5);}
    public void clear(){active=deployed=menuWasDown=false;item=ItemStack.EMPTY;file=owner="";slot=-1;syncCooldown=0;selector=null;Arrays.fill(renderers,null);}
    public void tick(){
        var client=Minecraft.getInstance();var player=client.player;
        if(player==null||client.level==null||player.isSpectator()){clear();return;}
        transformation.tick();
        var held=player.getMainHandItem();var heldFile=held.get(AllDataComponents.SCHEMATIC_FILE);var heldOwner=held.get(AllDataComponents.SCHEMATIC_OWNER);
        if(!held.is(AllItems.SCHEMATIC)||heldFile==null||heldOwner==null){if(active&&syncCooldown>0)sync();active=false;menuWasDown=false;return;}
        int heldSlot=player.getInventory().getSelectedSlot();
        if(!active||heldSlot!=slot||!file.equals(heldFile)||!owner.equals(heldOwner)){
            if(active&&syncCooldown>0)sync();
            item=held;slot=heldSlot;file=heldFile;owner=heldOwner;active=true;
            var size=item.get(AllDataComponents.SCHEMATIC_BOUNDS);if(size==null||size.getX()<=0||size.getY()<=0||size.getZ()<=0){active=false;return;}
            loadSettings(item);var previous=currentTool;
            selector=new ToolSelectionScreen(toolTypes(),this::equip);
            if(deployed&&toolTypes().contains(previous)){selector.setSelectedElement(previous);equip(previous);}
            syncCooldown=0;if(deployed)setupRenderers();
        }else item=held;
        if(syncCooldown>0&&--syncCooldown==0)sync();
        boolean menu=SchematicsClient.TOOL_MENU.isDown()&&client.gui.screen()==null;
        updateToolMenu(menu);selector.update();
        if(client.gui.screen()!=null)return;
        currentTool.getTool().updateSelection();
    }
    private void setupRenderers(){
        var client=Minecraft.getInstance();if(client.level==null)return;
        try{
            var template=SchematicItem.loadSchematic(client.level,item);var size=template.getSize();
            if(size.getX()!=(int)bounds.getXsize()||size.getY()!=(int)bounds.getYsize()||size.getZ()!=(int)bounds.getZsize())throw new java.io.IOException("Original schematic file size differs from held bounds");
            for(int index=0;index<3;index++){
                var mirror=index==1?Mirror.FRONT_BACK:index==2?Mirror.LEFT_RIGHT:Mirror.NONE;
                var anchor=index==1?new BlockPos(size.getX()-1,0,0):index==2?new BlockPos(0,0,size.getZ()-1):BlockPos.ZERO;
                var snapshot=SchematicStructure.load(client.level,template,anchor,new StructurePlaceSettings().setMirror(mirror),false);
                var world=new SchematicRenderWorld(client.level,snapshot,local->BlockPos.containing(worldPosition(Vec3.atCenterOf(local))));
                renderers[index]=new SchematicRenderer(world);
            }
        }catch(java.io.IOException|RuntimeException failure){Arrays.fill(renderers,null);playerMessage("create.schematic.error");Create.LOGGER.error("Unable to extract actual native schematic preview {}",file,failure);}
    }
    private Vec3 worldPosition(Vec3 local){var pose=new PoseStack();transformation.applyTransformations(pose,Vec3.ZERO,1);var point=pose.last().pose().transformPosition(new Vector3f((float)local.x,(float)local.y,(float)local.z));return new Vec3(point.x,point.y,point.z);}
    private boolean ownsHeld(){var player=Minecraft.getInstance().player;if(!active||player==null||player.getInventory().getSelectedSlot()!=slot)return false;var held=player.getMainHandItem();return held.is(AllItems.SCHEMATIC)&&file.equals(held.get(AllDataComponents.SCHEMATIC_FILE))&&owner.equals(held.get(AllDataComponents.SCHEMATIC_OWNER));}
    public boolean onUse(){
        var client=Minecraft.getInstance();if(!ownsHeld()||client.gui.screen()!=null)return false;
        if(client.player.isShiftKeyDown()){client.gui.setScreen(new SchematicEditScreen(this));return true;}
        return onMouseInput(InputConstants.MOUSE_BUTTON_RIGHT,true);
    }
    /** Native SDL right button is 3; source integer entry point retains its actual action semantics. */
    public boolean onMouseInput(int button,boolean pressed){
        var client=Minecraft.getInstance();if(!pressed||button!=InputConstants.MOUSE_BUTTON_RIGHT||!ownsHeld()||client.gui.screen()!=null||client.player.isShiftKeyDown())return false;
        if(client.hitResult instanceof BlockHitResult hit){var state=client.level.getBlockState(hit.getBlockPos());if(state.is(AllBlocks.SCHEMATICANNON)||state.is(AllBlocks.DEPLOYER))return false;}
        return currentTool.getTool().handleRightClick();
    }
    public void onKeyInput(int key,boolean pressed){onKeyInput(new KeyEvent(key,0,0),pressed);}
    public void onKeyInput(KeyEvent key,boolean pressed){
        if(!active||selector==null||!SchematicsClient.TOOL_MENU.matches(key))return;
        updateToolMenu(pressed&&Minecraft.getInstance().gui.screen()==null);
    }
    private void updateToolMenu(boolean focused){
        if(selector==null)return;
        if(focused&&!menuWasDown)selector.setSelectedElement(currentTool);
        if(!focused&&menuWasDown)selector.onClose();
        menuWasDown=focused;selector.focused=focused;
    }
    public boolean mouseScrolled(double delta){
        var client=Minecraft.getInstance();if(!ownsHeld()||client.gui.screen()!=null||delta==0)return false;
        if(SchematicsClient.TOOL_MENU.isDown()){updateToolMenu(true);selector.cycle((int)Math.signum(delta));return true;}
        return com.simibubi.create.AllKeys.ctrlDown()&&currentTool.getTool().handleMouseWheel(delta);
    }
    public void equip(ToolType next){currentTool=Objects.requireNonNull(next);currentTool.getTool().init();}
    public void equip(Tool next){if(tools().contains(next))equip(next.type());}
    public void loadSettings(ItemStack blueprint){
        var size=blueprint.get(AllDataComponents.SCHEMATIC_BOUNDS);if(size==null||size.getX()<=0||size.getY()<=0||size.getZ()<=0)return;
        bounds=new AABB(0,0,0,size.getX(),size.getY(),size.getZ());deployed=blueprint.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED,false);
        transformation=new SchematicTransformation();transformation.init(blueprint.getOrDefault(AllDataComponents.SCHEMATIC_ANCHOR,BlockPos.ZERO),SchematicItem.getSettings(blueprint),bounds);
    }
    public void deploy(){
        boolean wasDeployed=deployed;deployed=true;
        if(!wasDeployed)selector=new ToolSelectionScreen(toolTypes(),this::equip);
        setupRenderers();writeSettings();
    }
    public void markDirty(){writeSettings();syncCooldown=10;}
    private void writeSettings(){if(item.isEmpty())return;var settings=transformation.toSettings();item.set(AllDataComponents.SCHEMATIC_DEPLOYED,deployed);item.set(AllDataComponents.SCHEMATIC_ANCHOR,transformation.getAnchor());item.set(AllDataComponents.SCHEMATIC_ROTATION,settings.getRotation());item.set(AllDataComponents.SCHEMATIC_MIRROR,settings.getMirror());item.remove(AllDataComponents.SCHEMATIC_HASH);}
    public void sync(){var player=Minecraft.getInstance().player;if(player==null||item.isEmpty()||slot<0||!ClientPlayNetworking.canSend(SchematicSyncPacket.TYPE))return;var actual=player.getInventory().getItem(slot);if(!actual.is(AllItems.SCHEMATIC)||!Objects.equals(actual.get(AllDataComponents.SCHEMATIC_FILE),file)||!Objects.equals(actual.get(AllDataComponents.SCHEMATIC_OWNER),owner))return;writeSettings();ClientPlayNetworking.send(new SchematicSyncPacket(slot,transformation.toSettings(),transformation.getAnchor(),deployed));syncCooldown=0;}
    public void printInstantly(){var player=Minecraft.getInstance().player;if(!ownsHeld()||player==null||!player.isCreative()||!deployed||!ClientPlayNetworking.canSend(SchematicPlacePacket.TYPE))return;sync();ClientPlayNetworking.send(new SchematicPlacePacket(item));deployed=false;markDirty();sync();active=false;}
    public void updateRenderers(){for(var renderer:renderers)if(renderer!=null)renderer.update();}
    public Frame extract(Vec3 camera,float partial){
        if(!active)return null;var pose=new PoseStack();var output=new SchematicToolRenderState.Builder();
        currentTool.getTool().extractTool(pose,output,camera,partial);
        transformation.applyTransformations(pose,camera,partial);
        SchematicRenderer.Frame model=null;if(deployed){boolean lr=transformation.getScaleLR().getValue(partial)<0,fb=transformation.getScaleFB().getValue(partial)<0;int index=lr&&!fb?2:fb&&!lr?1:0;if(renderers[index]!=null)model=renderers[index].extract(partial,transformation.toLocalSpace(camera));}
        currentTool.getTool().extractOnSchematic(pose,output,camera,partial);var state=output.build();
        var outline=state.outline(SchematicToolRenderState.Role.BOUNDS);var deployment=state.outline(SchematicToolRenderState.Role.DEPLOYMENT);
        var axis=state.line(SchematicToolRenderState.Role.ROTATION_AXIS);var mirror=state.outline(SchematicToolRenderState.Role.MIRROR_PLANE);
        var face=currentTool.getTool() instanceof SchematicToolBase placement?placement.getSelectedFace():null;
        return new Frame(new Matrix4f(pose.last().pose()),model,outline==null?null:outline.geometry(),deployment==null?null:deployment.transform(),deployment==null?null:deployment.geometry(),axis==null?null:axis.geometry(),mirror==null?null:mirror.geometry(),face,currentTool(),state);
    }
    public void extractOverlay(GuiGraphicsExtractor gui){
        var client=Minecraft.getInstance();if(!active||client.gui.hud.isHidden()||client.gui.screen()!=null)return;
        hotbarOverlay.renderOn(gui,slot);currentTool.getTool().extractOverlay(gui,SchematicTransformation.partialTicks(),gui.guiWidth(),gui.guiHeight());if(selector!=null)selector.renderPassive(gui,SchematicTransformation.partialTicks());
    }
    private static void playerMessage(String key){var player=Minecraft.getInstance().player;if(player!=null)player.sendOverlayMessage(Component.translatable(key));}
}
