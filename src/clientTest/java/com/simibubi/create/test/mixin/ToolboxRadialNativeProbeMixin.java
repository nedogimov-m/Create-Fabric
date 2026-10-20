package com.simibubi.create.test.mixin;

import com.simibubi.create.content.equipment.toolbox.*;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Read actual native radial events and close cause; does not change input, state or packet dispatch. */
@Mixin(value=RadialToolboxMenu.class,remap=false)
abstract class ToolboxRadialNativeProbeMixin {
    @Shadow private RadialToolboxMenu.State state;
    @Shadow private ToolboxBlockEntity selectedBox;
    @Shadow private int hovered;
    @Inject(method="mouseClicked",at=@At("HEAD"))
    private void createTest$mouse(MouseButtonEvent event,boolean twice,CallbackInfoReturnable<Boolean> ci){System.out.println("CREATE_TOOLBOX_RADIAL_MOUSE state="+state+" hovered="+hovered+" event="+event+" alt="+Minecraft.getInstance().hasAltDown()+" boxLive="+ToolboxHandler.live(selectedBox));}
    @Inject(method="mouseClicked",at=@At("RETURN"))
    private void createTest$afterMouse(MouseButtonEvent event,boolean twice,CallbackInfoReturnable<Boolean> ci){System.out.println("CREATE_TOOLBOX_RADIAL_AFTER state="+state+" hovered="+hovered+" accepted="+ci.getReturnValue()+" actualScreen="+Minecraft.getInstance().gui.screen()+" boxLive="+ToolboxHandler.live(selectedBox)+" box="+createTest$box());}
    @Unique private String createTest$box(){if(selectedBox==null)return "null";var level=selectedBox.getLevel();if(level==null)return "no-level:"+selectedBox.getBlockPos()+":"+selectedBox.getUniqueId();var actual=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,selectedBox.getBlockPos());return "pos="+selectedBox.getBlockPos()+",uuid="+selectedBox.getUniqueId()+",removed="+selectedBox.isRemoved()+",clientLevel="+(level==Minecraft.getInstance().level)+",loaded="+com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level,selectedBox.getBlockPos())+",currentSame="+(actual==selectedBox)+",current="+actual+",currentUUID="+(actual instanceof ToolboxBlockEntity box?box.getUniqueId():null)+",state="+com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,selectedBox.getBlockPos());}
    @Inject(method="keyReleased",at=@At("HEAD"))
    private void createTest$key(KeyEvent event,CallbackInfoReturnable<Boolean> ci){System.out.println("CREATE_TOOLBOX_RADIAL_KEY_RELEASE state="+state+" event="+event);}
    @Inject(method="removed",at=@At("HEAD"))
    private void createTest$removed(CallbackInfo ci){System.out.println("CREATE_TOOLBOX_RADIAL_REMOVED state="+state+" hovered="+hovered+" boxLive="+ToolboxHandler.live(selectedBox)+" stack="+Arrays.stream(Thread.currentThread().getStackTrace()).skip(1).limit(14).toList());}
}
