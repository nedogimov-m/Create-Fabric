package com.simibubi.create.infrastructure.gui;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import java.net.URI;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.Util;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;

/** Native menu with configuration, registered Ponder navigation and source project links. */
public final class CreateMainMenuScreen extends Screen {
    public static final String CURSEFORGE_LINK = "https://www.curseforge.com/minecraft/mc-mods/create";
    public static final String MODRINTH_LINK = "https://modrinth.com/mod/create";
    public static final String ISSUE_TRACKER_LINK = "https://github.com/Creators-of-Create/Create/issues";
    public static final String SUPPORT_LINK = "https://github.com/Creators-of-Create/Create/wiki/Supporting-the-Project";
    private final Screen parent;
    private final String version;
    private final Panorama panorama=new Panorama();
    private long firstRenderTime;

    public CreateMainMenuScreen(Screen parent) {
        super(Component.literal("Create"));
        this.parent = parent;
        panorama.startSpin();
        version = FabricLoader.getInstance().getModContainer("create")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    @Override protected void init() {
        int center=width/2,yStart=height/4+40;
        addRenderableWidget(Button.builder(Component.translatable("create.menu.configure"),
            button -> minecraft.gui.setScreen(new CreateConfigScreen(this)))
            .bounds(center-100,yStart+8,200,20).build());
        var ponder = Button.builder(Component.translatable("create.menu.ponder_index"),
            button -> minecraft.gui.setScreen(new net.createmod.ponder.foundation.ui.PonderIndexScreen(this)))
            .bounds(center+2,yStart+32,98,20).build();
        ponder.active = net.createmod.ponder.PonderClient.isGameActive();
        if(parent instanceof TitleScreen)ponder.setTooltip(Tooltip.create(Component.translatable("create.menu.only_ingame").withStyle(net.minecraft.ChatFormatting.GRAY)));
        addRenderableWidget(ponder);
        addRenderableWidget(new PlatformIconButton(center-100,yStart+32,"CurseForge",CURSEFORGE_LINK,AllGuiTextures.CURSEFORGE_LOGO,.085f,0xFC785C));
        addRenderableWidget(new PlatformIconButton(center-50,yStart+32,"Modrinth",MODRINTH_LINK,AllGuiTextures.MODRINTH_LOGO,.0575f,0x3FD32B));
        link(Component.translatable("create.menu.support"),SUPPORT_LINK,center-100,yStart+68,98);
        link(Component.translatable("create.menu.report_bugs"),ISSUE_TRACKER_LINK,center+2,yStart+68,98);
        addRenderableWidget(Button.builder(Component.translatable("create.menu.return"), button -> onClose())
            .bounds(center-100,yStart+92,200,20).build());
    }

    private void link(Component label, String url, int x, int y, int width) {
        addRenderableWidget(Button.builder(label, ConfirmLinkScreen.confirmLink(this, URI.create(url)))
            .bounds(x, y, width, 20).build());
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partialTick){
        if(parent instanceof TitleScreen){
            ((CreateMenuPanoramaState)((com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor)gui).create$getRenderState()).create$setMenuPanorama(true);
            panorama.extractRenderState(gui,width,height);
        }else super.extractBackground(gui,mouseX,mouseY,partialTick);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        long time=Util.getMillis();if(firstRenderTime==0)firstRenderTime=time;float intro=Mth.clamp((time-firstRenderTime)/1000f,0,1);
        CreateMenuArtwork.extract(gui,width,intro,time);
        AllGuiTextures.LOGO.stretched(gui,width/2-32,32,64,64,0xFFFFFFFF);
        gui.fill(width/2-64,88,width/2+64,99,0x88000000);
        gui.centeredText(font,Component.literal("Create").withStyle(net.minecraft.ChatFormatting.BOLD).append(Component.literal(" v"+version).withStyle(net.minecraft.ChatFormatting.BOLD,net.minecraft.ChatFormatting.WHITE)),width/2,89,0xFFE4BB67);
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
    }

    private final class PlatformIconButton extends Button {
        private final AllGuiTextures icon;
        private final float iconScale;
        PlatformIconButton(int x,int y,String name,String url,AllGuiTextures icon,float scale,int color){
            super(x,y,49,20,Component.literal(name),ConfirmLinkScreen.confirmLink(CreateMainMenuScreen.this,URI.create(url)),DEFAULT_NARRATION);
            this.icon=icon;iconScale=scale;setTooltip(Tooltip.create(Component.literal(name).withStyle(style->style.withColor(color).withBold(true))));
        }
        @Override protected void extractContents(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
            gui.pose().pushMatrix();try{gui.pose().translate(getX()+getWidth()/2f-icon.getWidth()*iconScale/2,getY()+getHeight()/2f-icon.getHeight()*iconScale/2);gui.pose().scale(iconScale,iconScale);icon.render(gui,0,0);}finally{gui.pose().popMatrix();}
        }
    }
}
