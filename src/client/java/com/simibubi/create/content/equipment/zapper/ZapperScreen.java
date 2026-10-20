package com.simibubi.create.content.equipment.zapper;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.foundation.gui.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Original source layout and close-to-configure interaction through genuine native GUI extraction. */
public abstract class ZapperScreen extends Screen {
    protected final Component patternSection=Component.translatable("create.gui.terrainzapper.patternSection");
    protected Component title=net.minecraft.network.chat.CommonComponents.EMPTY;
    protected AllGuiTextures background;protected ItemStack zapper;protected InteractionHand hand;protected float animationProgress;
    protected final List<ZapperIconButton> patternButtons=new ArrayList<>(6);protected int brightColor=0xFEFEFE,fontColor=AllGuiTextures.FONT_COLOR,guiLeft,guiTop;
    protected PlacementPatterns currentPattern;
    public ZapperScreen(AllGuiTextures background,ItemStack zapper,InteractionHand hand){super(net.minecraft.network.chat.CommonComponents.EMPTY);this.background=background;this.zapper=zapper;this.hand=hand;currentPattern=zapper.getOrDefault(AllDataComponents.PLACEMENT_PATTERN,PlacementPatterns.Solid);}
    @Override protected void init(){guiLeft=(width-background.getWidth())/2-10;guiTop=(height-background.getHeight())/2;animationProgress=0;int x=guiLeft,y=guiTop;addRenderableWidget(new ZapperIconButton(x+background.getWidth()-33,y+background.getHeight()-24,AllIcons.I_CONFIRM).withCallback(this::onClose));patternButtons.clear();for(int row=0;row<2;row++)for(int col=0;col<3;col++){var pattern=PlacementPatterns.values()[patternButtons.size()];var button=new ZapperIconButton(x+background.getWidth()-76+col*18,y+21+row*18,ClientZapperIcons.get(pattern.iconKey));button.withCallback(()->{patternButtons.forEach(value->value.green=false);button.green=true;currentPattern=pattern;});button.setToolTip(Component.translatable("create.gui.terrainzapper.pattern."+pattern.translationKey));patternButtons.add(button);addRenderableWidget(button);}patternButtons.get(currentPattern.ordinal()).green=true;}
    protected void addRenderableWidgets(Collection<? extends AbstractWidget> widgets){widgets.forEach(this::addRenderableWidget);}
    protected void removeWidgets(Collection<? extends AbstractWidget> widgets){widgets.forEach(this::removeWidget);}
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){background.render(gui,guiLeft,guiTop);drawOnBackground(gui,guiLeft,guiTop);renderBlock(gui,guiLeft,guiTop);renderZapper(gui,guiLeft,guiTop);super.extractRenderState(gui,mouseX,mouseY,partial);}
    protected void drawOnBackground(GuiGraphicsExtractor gui,int x,int y){gui.centeredText(font,title,x+background.getWidth()/2,y+4,0xFF54214F);}
    @Override public void tick(){super.tick();animationProgress+=5;}
    @Override public void removed(){var packet=getConfigurationPacket();packet.configureZapper(zapper);ClientPlayNetworking.send(packet);}
    protected void renderZapper(GuiGraphicsExtractor gui,int x,int y){gui.pose().pushMatrix();gui.pose().translate(x+background.getWidth()-32,y+background.getHeight()-80);gui.pose().scale(4,4);gui.item(zapper,0,0);gui.pose().popMatrix();}
    protected void renderBlock(GuiGraphicsExtractor gui,int x,int y){ZapperBlockPortrait.extract(gui,zapper.getOrDefault(AllDataComponents.SHAPER_BLOCK_USED,Blocks.AIR.defaultBlockState()),x+32,y+42);}
    protected abstract ConfigureZapperPacket getConfigurationPacket();
    @Override public boolean isPauseScreen(){return false;}
}
