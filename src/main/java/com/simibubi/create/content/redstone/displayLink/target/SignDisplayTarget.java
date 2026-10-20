package com.simibubi.create.content.redstone.displayLink.target;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import java.util.List;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;

/** Native FRONT/BACK SignTextSlot and immutable SignText updates preserve real color/glow/wax state. */
public final class SignDisplayTarget extends DisplayTarget {
    @Override public void acceptText(int line,List<MutableComponent> text,DisplayLinkContext context){
        if(line<0||line>=4||!(context.getTargetBlockEntity()instanceof SignBlockEntity sign))return;var front=sign.getText(SignTextSlot.FRONT).asMutable();var back=sign.getText(SignTextSlot.BACK).asMutable();boolean changed=false;
        for(int offset=0;offset<text.size()&&line+offset<4;offset++){int row=line+offset;if(offset==0)reserve(row,sign,context);else if(isReserved(row,sign,context))break;var value=Component.literal(text.get(offset).getString(sign.getMaxTextLineWidth()));front.setLine(row,value);back.setLine(row,value.copy());changed=true;}
        if(changed){sign.setText(front.asImmutable(),SignTextSlot.FRONT);sign.setText(back.asImmutable(),SignTextSlot.BACK);sign.setChanged();context.level().sendBlockUpdated(context.getTargetPos(),sign.getBlockState(),sign.getBlockState(),Block.UPDATE_CLIENTS);}
    }
    @Override public DisplayTargetStats provideStats(DisplayLinkContext context){return new DisplayTargetStats(context.getTargetBlockEntity()instanceof SignBlockEntity?4:0,15,this);}
    @Override public boolean requiresComponentSanitization(){return true;}
}
