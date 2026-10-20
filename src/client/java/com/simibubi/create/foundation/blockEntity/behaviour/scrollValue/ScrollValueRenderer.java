package com.simibubi.create.foundation.blockEntity.behaviour.scrollValue;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsInputHandler;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Source scroll boxes from actual installed owners; the motor keeps its dedicated native renderer. */
public class ScrollValueRenderer {
    public static void tick(){
        var client=Minecraft.getInstance();
        if(client.level==null||client.gui.screen()!=null||client.gui.hud.isHidden()||!ValueSettingsInputHandler.canInteract(client.player)
                ||!(client.hitResult instanceof BlockHitResult hit)||!client.player.isWithinBlockInteractionRange(hit.getBlockPos(),1)
                ||!client.level.mayInteract(client.player,hit.getBlockPos()))return;
        if(!(LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos()) instanceof SmartBlockEntity owner)||owner instanceof CreativeMotorBlockEntity||owner.isRemoved())return;
        boolean found=false;
        for(var member:List.copyOf(owner.getAllBehaviours())){
            if(!(member instanceof ScrollValueBehaviour scroll)||!valid(client,scroll,hit.getDirection()))continue;
            var slot=scroll.getSlotPositioning();if(slot instanceof ValueBoxTransform.Sided sided)sided.fromSide(hit.getDirection());
            if(!slot.shouldRender(client.level,scroll.getPos(),owner.getBlockState()))continue;
            boolean highlight=!found&&!scroll.bypassesInput(client.player.getMainHandItem())&&scroll.testHit(hit.getLocation());
            if(scroll instanceof BulkScrollValueBehaviour bulk&&com.simibubi.create.AllKeys.ctrlDown()){
                for(var otherOwner:List.copyOf(bulk.getBulk())){
                    var other=otherOwner.getBehaviour(ScrollValueBehaviour.TYPE);
                    if(other!=null&&otherOwner.getLevel()==client.level&&valid(client,other,hit.getDirection()))addBox(client.level,other.getPos(),hit.getDirection(),other,highlight);
                }
            }else addBox(client.level,scroll.getPos(),hit.getDirection(),scroll,highlight);
            if(highlight){found=true;ValueSettingsClient.INSTANCE.showHoverTip(List.of(scroll.label.copy(),Component.translatable("create.gui.value_settings.hold_to_edit")));}
        }
    }
    private static boolean valid(Minecraft client,ScrollValueBehaviour scroll,Direction side){
        var owner=scroll.blockEntity;
        if(owner.isRemoved()||owner.getLevel()!=client.level||LoadedLevelAccess.blockEntity(client.level,owner.getBlockPos())!=owner
                ||owner.getBehaviour(scroll.getType())!=scroll||!scroll.isActive()||!scroll.mayInteract(client.player)
                ||!client.level.mayInteract(client.player,owner.getBlockPos()))return false;
        return !scroll.onlyVisibleWithWrench()||client.player.getMainHandItem().is(AllItems.WRENCH);
    }
    protected static void addBox(ClientLevel world,BlockPos pos,Direction side,ScrollValueBehaviour scroll,boolean highlight){
        var slot=scroll.getSlotPositioning();if(slot instanceof ValueBoxTransform.Sided sided)sided.fromSide(side);
        if(!slot.shouldRender(world,pos,scroll.blockEntity.getBlockState()))return;
        AABB bounds=new AABB(Vec3.ZERO,Vec3.ZERO).inflate(.5).contract(0,0,-.5).move(0,0,-.125);
        ValueBox box=scroll instanceof ScrollOptionBehaviour<?> option
            ?new ValueBox.IconValueBox(scroll.label,option.getIconForSelected(),bounds,pos)
            :new ValueBox.TextValueBox(scroll.label,bounds,pos,Component.literal(scroll.formatValue()));
        box.withOwner(scroll).transform(slot).passive(!highlight).wideOutline();
        if(Minecraft.getInstance().hitResult instanceof BlockHitResult hit)box.fromInteractionOrigin(hit.getBlockPos());
        if(box.isCurrent())Outliner.getInstance().showOutline(scroll,box).highlightFace(side);
    }
}
