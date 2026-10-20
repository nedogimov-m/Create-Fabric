package com.simibubi.create.content.schematics.client;

import com.simibubi.create.*;
import com.simibubi.create.content.schematics.*;
import com.simibubi.create.content.schematics.packet.InstantSchematicPacket;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Real source two-corner selection, ranged Ctrl targeting, face resize and native file export. */
public final class SchematicAndQuillHandler {
    public BlockPos firstPos,secondPos;
    private BlockPos selected;
    private Direction selectedFace;
    private int range=10;
    public boolean isActive(){var client=Minecraft.getInstance();return client.level!=null&&client.player!=null&&client.gui.screen()==null&&client.player.getMainHandItem().is(AllItems.SCHEMATIC_AND_QUILL);}
    public void clear(){firstPos=secondPos=selected=null;selectedFace=null;}
    public void tick(){
        if(!isActive())return;var client=Minecraft.getInstance();var player=client.player;var eye=player.getEyePosition();var end=eye.add(player.getLookAngle().scale(75));
        if(com.simibubi.create.AllKeys.ctrlDown())selected=BlockPos.containing(eye.add(player.getLookAngle().scale(range)));
        else{var hit=client.level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));selected=hit.getType()==HitResult.Type.BLOCK&&client.level.isLoaded(hit.getBlockPos())?hit.getBlockPos():null;if(selected!=null&&hit.getDirection().getAxis().isVertical()&&!client.level.getBlockState(selected).canBeReplaced())selected=selected.relative(hit.getDirection());}
        selectedFace=null;
        if(firstPos!=null&&secondPos!=null){var bounds=selection().inflate(.45);boolean inside=bounds.contains(eye);var hit=AABB.clip(List.of(bounds),inside?end:eye,inside?eye:end,BlockPos.ZERO);if(hit!=null)selectedFace=hit.getDirection();}
    }
    public boolean onUse(){
        if(!isActive())return false;var client=Minecraft.getInstance();
        if(client.player.isShiftKeyDown()){discard();return true;}
        if(secondPos!=null){client.gui.setScreen(new SchematicPromptScreen(this));return true;}
        if(selected==null){message("create.schematicAndQuill.noTarget");return true;}
        if(firstPos==null){firstPos=selected.immutable();message("create.schematicAndQuill.firstPos");}
        else{secondPos=selected.immutable();message("create.schematicAndQuill.secondPos");}
        return true;
    }
    public boolean mouseScrolled(double delta){
        var client=Minecraft.getInstance();if(!isActive()||!com.simibubi.create.AllKeys.ctrlDown()||delta==0)return false;
        if(secondPos==null){range=Math.clamp(range+(int)Math.signum(delta),1,100);return true;}
        if(selectedFace==null)return true;
        var bounds=new AABB(Vec3.atLowerCornerOf(firstPos),Vec3.atLowerCornerOf(secondPos));if(bounds.contains(client.player.getEyePosition()))delta*=-1;
        int step=(int)(delta>0?Math.ceil(delta):Math.floor(delta));var normal=selectedFace.getUnitVec3().scale(step);int sign=selectedFace.getAxisDirection().getStep();
        if(sign<0)bounds=bounds.move(normal.scale(-1));
        bounds=new AABB(bounds.minX,bounds.minY,bounds.minZ,Math.max(bounds.minX,bounds.maxX-normal.x*sign),Math.max(bounds.minY,bounds.maxY-normal.y*sign),Math.max(bounds.minZ,bounds.maxZ-normal.z*sign));
        firstPos=BlockPos.containing(bounds.minX,bounds.minY,bounds.minZ);secondPos=BlockPos.containing(bounds.maxX,bounds.maxY,bounds.maxZ);
        client.player.sendOverlayMessage(Component.translatable("create.schematicAndQuill.dimensions",(int)bounds.getXsize()+1,(int)bounds.getYsize()+1,(int)bounds.getZsize()+1));return true;
    }
    public AABB selection(){if(secondPos!=null)return new AABB(Vec3.atLowerCornerOf(firstPos),Vec3.atLowerCornerOf(secondPos)).expandTowards(1,1,1);if(firstPos!=null)return selected==null?new AABB(firstPos):new AABB(Vec3.atLowerCornerOf(firstPos),Vec3.atLowerCornerOf(selected)).expandTowards(1,1,1);return selected==null?null:new AABB(selected);}
    public SchematicOutline.Frame extract(){var bounds=selection();return isActive()&&bounds!=null?SchematicOutline.extract(bounds,selectedFace,0x6886C5):null;}
    public void discard(){clear();message("create.schematicAndQuill.abort");}
    public boolean saveSchematic(String name,boolean convert){
        var client=Minecraft.getInstance();if(client.level==null||client.player==null||firstPos==null||secondPos==null)return false;
        var result=SchematicExport.saveSchematic(SchematicFiles.localRoot(),name,false,client.level,firstPos,secondPos);
        if(result==null){message("create.schematicAndQuill.failed");return false;}
        client.player.sendOverlayMessage(Component.translatable("create.schematicAndQuill.saved",result.fileName()));clear();SchematicsClient.LOADER.refresh();
        if(convert)try{if(ClientSchematicLoader.validateSizeLimitation(Files.size(result.file()))&&ClientPlayNetworking.canSend(InstantSchematicPacket.TYPE))ClientPlayNetworking.send(new InstantSchematicPacket(result.fileName(),result.origin(),result.bounds()));}catch(IOException failure){Create.LOGGER.error("Unable to read completed native quill export size",failure);message("create.schematicAndQuill.failed");return false;}
        return true;
    }
    private static void message(String key){var player=Minecraft.getInstance().player;if(player!=null)player.sendOverlayMessage(Component.translatable(key));}
}
