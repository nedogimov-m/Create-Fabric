// Ponder1.0.82 client action behavior, MIT; native26.3 typed Fabric receiver.
package net.createmod.ponder.command;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderIndexScreen;
import net.createmod.ponder.foundation.ui.PonderTagIndexScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;

public final class SimplePonderActions {
    private static boolean registered;
    public static void register(){
        if(registered)return;registered=true;
        ClientPlayNetworking.registerGlobalReceiver(PonderActionPayload.TYPE,(packet,context)->context.client().execute(()->{
            switch(packet.action()){
                case INDEX->openPonder("ponder:index");
                case TAGS->openPonder("ponder:tags");
                case SCENE->openPonder(packet.scene().toString());
                case RELOAD->reloadPonder("");
            }
        }));
    }
    public static void openPonder(String value){
        if(value.equals("index")||value.equals("ponder:index")){ScreenOpener.transitionTo(new PonderIndexScreen());return;}
        if(value.equals("ponder:tags")){ScreenOpener.transitionTo(new PonderTagIndexScreen());return;}
        var id=Identifier.parse(value);
        if(!PonderIndex.getSceneAccess().doScenesExistForId(id)){Ponder.LOGGER.error("Could not find ponder scenes for item: "+id);return;}
        ScreenOpener.transitionTo(PonderUI.of(id));
    }
    public static void reloadPonder(String value){PonderIndex.reload();}
    private SimplePonderActions(){}
}
