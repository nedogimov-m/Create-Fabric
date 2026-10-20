package com.simibubi.create.content.equipment.goggles;

import com.simibubi.create.Create;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CClient;
import com.simibubi.create.infrastructure.config.ConfigScope;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Existing overlay/menu fields mirror the one real CLIENT configuration store. */
public final class GoggleOverlaySettings {
    public static final GoggleOverlaySettings INSTANCE=new GoggleOverlaySettings();
    public int overlayOffsetX=20,overlayOffsetY;
    public boolean overlayCustomColor,enableOverstressedTooltip=true;
    public int overlayBackgroundColor=0xF0100010,overlayBorderColorTop=0x505000FF,overlayBorderColorBot=0x5028007F;
    private Path path=FabricLoader.getInstance().getConfigDir().resolve("create-client.json");

    public Path path(){return path;}
    public void apply(CClient config){
        path=AllConfigs.store(ConfigScope.CLIENT).file().orElse(path);
        overlayOffsetX=config.overlayOffsetX.get();overlayOffsetY=config.overlayOffsetY.get();
        overlayCustomColor=config.overlayCustomColor.get();enableOverstressedTooltip=config.enableOverstressedTooltip.get();
        overlayBackgroundColor=config.overlayBackgroundColor.get();
        overlayBorderColorTop=config.overlayBorderColorTop.get();overlayBorderColorBot=config.overlayBorderColorBot.get();
    }
    public void load(){load(path);}
    public void load(Path requested){
        try{
            AllConfigs.reloadClient(requested);
            apply(AllConfigs.client());
        }catch(RuntimeException failure){Create.LOGGER.warn("Cannot read client overlay preferences at {}",requested,failure);}
    }
    public void save(){
        var config=AllConfigs.client();
        // Capture all pending menu edits before a first load can update these mirrored fields.
        var updates=Map.<String,Object>of(
            config.overlayOffsetX.key(),overlayOffsetX,config.overlayOffsetY.key(),overlayOffsetY,
            config.overlayCustomColor.key(),overlayCustomColor,config.enableOverstressedTooltip.key(),enableOverstressedTooltip,
            config.overlayBackgroundColor.key(),overlayBackgroundColor,
            config.overlayBorderColorTop.key(),overlayBorderColorTop,config.overlayBorderColorBot.key(),overlayBorderColorBot);
        try{
            var store=AllConfigs.store(ConfigScope.CLIENT);
            if(store.file().isEmpty())AllConfigs.reloadClient(path);
            store.importValues(updates,true);
            apply(config);
        }catch(RuntimeException failure){Create.LOGGER.warn("Cannot save client overlay preferences at {}",path,failure);}
    }
}
