package com.simibubi.create.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.infrastructure.gui.CreateMenuArtwork;
import net.minecraft.resources.Identifier;

/** Observes only actual native CubeMap and PIP submission calls; the fixture never invokes these callbacks. */
public final class CreateMenuNativeProbe {
    private static boolean active;
    private static int panoramas,frames,submitted;
    private static float lastYaw;
    private static CreateMenuArtwork.State current,last;
    public record Receipt(int panoramas,int frames,CreateMenuArtwork.State state,float panoramaYaw){}
    public static void begin(){active=true;panoramas=frames=submitted=0;current=last=null;}
    public static void panorama(Identifier location,float yaw){if(!active)return;if(!location.equals(Create.asResource("textures/gui/title/background/panorama")))throw new AssertionError("Actual menu rendered the vanilla cube instead of the source Create panorama: "+location);lastYaw=yaw;panoramas++;}
    public static void frame(CreateMenuArtwork.State state){if(!active)return;current=state;submitted=0;if(state.gears().size()!=4)throw new AssertionError("Actual menu did not capture four real cogwheel models");}
    public static void model(PartialModelRenderState model,PoseStack pose){
        if(!active||current==null)return;
        if(submitted>=4||current.gears().get(submitted).model()!=model||model.mesh==null||model.mesh.size()==0||!pose.last().pose().isFinite())throw new AssertionError("Native menu submission lost actual detached 3D mesh/order/finite source transform");
        var gear=current.gears().get(submitted);float expected=gear.large()?(current.time()%11520)/32f*gear.side():(current.time()%5760)/-16f*gear.side()+22.5f;
        if(Math.abs(gear.angle()-expected)>.001f)throw new AssertionError("Native cogwheel lost original time, side or two-to-one rotation rate");submitted++;
    }
    public static void endFrame(){if(!active)return;if(submitted!=4)throw new AssertionError("Native menu PIP did not submit all four actual 3D cogwheel meshes");last=current;current=null;frames++;}
    public static Receipt end(){active=false;if(frames==0||panoramas==0||last==null)throw new AssertionError("Actual native menu draw did not render both Create panorama and four cogwheels");return new Receipt(panoramas,frames,last,lastYaw);}
    private CreateMenuNativeProbe(){}
}
