package com.simibubi.create.content.schematics.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.client.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Genuine source per-tool target, face, distance and chasing state, using real native rays. */
public abstract class SchematicToolBase implements ISchematicTool {
    protected SchematicHandler schematicHandler;
    protected BlockPos selectedPos;
    protected Vec3 chasingSelectedPos,lastChasingSelectedPos;
    protected boolean selectIgnoreBlocks;
    protected int selectionRange;
    protected boolean schematicSelected,renderSelectedFace;
    protected Direction selectedFace;
    @Override public void init(){
        schematicHandler=CreateClient.SCHEMATIC_HANDLER;selectedPos=null;selectedFace=null;schematicSelected=false;
        chasingSelectedPos=lastChasingSelectedPos=Vec3.ZERO;
    }
    @Override public void updateSelection(){
        updateTargetPos();if(selectedPos==null)return;lastChasingSelectedPos=chasingSelectedPos;var target=Vec3.atLowerCornerOf(selectedPos);
        chasingSelectedPos=target.distanceTo(chasingSelectedPos)<1/512f?target:chasingSelectedPos.add(target.subtract(chasingSelectedPos).scale(.5));
    }
    public void updateTargetPos(){
        var client=Minecraft.getInstance();var player=client.player;selectedFace=null;schematicSelected=false;
        if(player==null||client.level==null||schematicHandler==null||!schematicHandler.isActive()){selectedPos=null;return;}
        var eye=player.getEyePosition();
        if(schematicHandler.isDeployed()){
            var transform=schematicHandler.getTransformation();var start=transform.toLocalSpace(eye);var end=transform.toLocalSpace(eye.add(player.getLookAngle().scale(70)));
            var hit=AABB.clip(List.of(schematicHandler.getBounds()),start,end,BlockPos.ZERO);schematicSelected=hit!=null;if(hit!=null)selectedFace=hit.getDirection();
        }
        boolean snap=selectedPos==null;
        if(selectIgnoreBlocks)selectedPos=BlockPos.containing(player.getEyePosition(SchematicTransformation.partialTicks()).add(player.getLookAngle().scale(selectionRange)));
        else{
            selectedPos=null;var hit=client.level.clip(new ClipContext(eye,eye.add(player.getLookAngle().scale(75)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(hit.getType()==HitResult.Type.BLOCK&&client.level.getChunkSource().getChunkNow(hit.getBlockPos().getX()>>4,hit.getBlockPos().getZ()>>4)!=null){
                var target=BlockPos.containing(hit.getLocation());if(client.level.getChunkSource().getChunkNow(target.getX()>>4,target.getZ()>>4)!=null){selectedPos=target;if(hit.getDirection().getAxis().isVertical()&&!client.level.getBlockState(selectedPos).canBeReplaced())selectedPos=selectedPos.relative(hit.getDirection());}
            }
        }
        if(snap&&selectedPos!=null)lastChasingSelectedPos=chasingSelectedPos=Vec3.atLowerCornerOf(selectedPos);
    }
    public Direction getSelectedFace(){return selectedFace;}
    public BlockPos getSelectedPos(){return selectedPos;}
    public boolean isSchematicSelected(){return schematicSelected;}
    // These two optional hooks are empty in the pinned source base. Concrete tools supply their actual geometry.
    @Override public void extractTool(PoseStack pose,SchematicToolRenderState.Builder output,Vec3 camera,float partial){}
    @Override public void extractOverlay(GuiGraphicsExtractor gui,float partial,int width,int height){}
    @Override public void extractOnSchematic(PoseStack pose,SchematicToolRenderState.Builder output,Vec3 camera,float partial){
        if(schematicHandler!=null&&schematicHandler.isDeployed())output.addOutline(SchematicToolRenderState.Role.BOUNDS,pose,SchematicOutline.extract(schematicHandler.getBounds(),renderSelectedFace?selectedFace:null,0x6886C5,com.simibubi.create.AllKeys.ctrlDown()));
    }
}
