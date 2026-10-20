package com.simibubi.create.content.equipment.symmetryWand;

import com.simibubi.create.content.equipment.symmetryWand.mirror.*;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Native widgets and input; one guarded confirmation on closing, with detached settings. */
public class SymmetryWandScreen extends Screen {
    private final ItemStack owner;private final InteractionHand hand;private final UUID session;private SymmetryMirror mirror;private int left,top;private boolean sent;
    public SymmetryWandScreen(ItemStack wand,InteractionHand hand){this(wand,hand,SymmetryWandItem.getMirror(wand),new UUID(0,0));}
    public SymmetryWandScreen(ItemStack wand,InteractionHand hand,SymmetryMirror settings,UUID session){super(wand.getHoverName());owner=wand;this.hand=hand;this.session=session;mirror=settings instanceof EmptyMirror?new PlaneMirror(Vec3.ZERO):settings.copy();}
    public SymmetryMirror settings(){return mirror.copy();}
    @Override public void init(){
        left=(width-188)/2-20;top=(height-101)/2;
        addRenderableWidget(Button.builder(typeLabel(),button->{int type=mirror instanceof TriplePlaneMirror?0:mirror instanceof CrossPlaneMirror?2:1;mirror=switch(type){case 1->new CrossPlaneMirror(mirror.getPosition());case 2->new TriplePlaneMirror(mirror.getPosition());default->new PlaneMirror(mirror.getPosition());};rebuildWidgets();}).bounds(left+45,top+21,109,18).build());
        var align=addRenderableWidget(Button.builder(alignLabel(),button->{mirror.rotate(true);button.setMessage(alignLabel());}).bounds(left+45,top+43,109,18).build());align.active=!(mirror instanceof TriplePlaneMirror);
        addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(left+155,top+77,18,18).build());
    }
    private Component typeLabel(){return SymmetryMirror.getMirrors().get(mirror instanceof TriplePlaneMirror?2:mirror instanceof CrossPlaneMirror?1:0);}
    private Component alignLabel(){return mirror.getAlignToolTips().get(mirror instanceof TriplePlaneMirror?0:mirror.getOrientationIndex());}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(x>=left+45&&x<=left+154&&y>=top+43&&y<=top+61&&!(mirror instanceof TriplePlaneMirror)){mirror.rotate(vertical>0);rebuildWidgets();return true;}if(x>=left+45&&x<=left+154&&y>=top+21&&y<=top+39){int type=mirror instanceof TriplePlaneMirror?2:mirror instanceof CrossPlaneMirror?1:0;type=Math.floorMod(type+(vertical>0?1:-1),3);mirror=switch(type){case 1->new CrossPlaneMirror(mirror.getPosition());case 2->new TriplePlaneMirror(mirror.getPosition());default->new PlaneMirror(mirror.getPosition());};rebuildWidgets();return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    private void send(){if(sent||minecraft.player==null||minecraft.player.getItemInHand(hand)!=owner)return;sent=true;ClientPlayNetworking.send(new ConfigureSymmetryWandPacket(hand,mirror,session));}
    @Override public void onClose(){send();super.onClose();}
    @Override public void removed(){send();super.removed();}
    @Override public void tick(){if(minecraft.player==null||minecraft.player.getItemInHand(hand)!=owner)minecraft.gui.setScreen(null);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){AllGuiTextures.WAND_OF_SYMMETRY.render(graphics,left,top);graphics.text(font,title,left+(188-font.width(title))/2,top+4,0xFF592424,false);SymmetryWandPortrait.extract(graphics,mirror,left+26,top+39);graphics.pose().pushMatrix();graphics.pose().translate(left+160,top+62);graphics.pose().scale(2,2);graphics.item(owner,0,0);graphics.pose().popMatrix();super.extractRenderState(graphics,mouseX,mouseY,partial);}
    @Override public boolean isPauseScreen(){return false;}
}
