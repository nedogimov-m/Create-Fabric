package com.simibubi.create.foundation.blockEntity.behaviour.filtering;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.NativeFilterValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsInputHandler;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Source item/count boxes read the original registered native filter, with no new setting owner. */
public class FilteringRenderer {
    private record Slot(FilteringBehaviour registered,Direction side){}
    public static void tick(){
        var client=Minecraft.getInstance();
        if(client.level==null||client.gui.screen()!=null||client.gui.hud.isHidden()||!ValueSettingsInputHandler.canInteract(client.player)
                ||!(client.hitResult instanceof BlockHitResult hit)||!client.player.isWithinBlockInteractionRange(hit.getBlockPos(),1)
                ||!client.level.mayInteract(client.player,hit.getBlockPos()))return;
        if(!(LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos()) instanceof SmartBlockEntity owner)||owner.isRemoved())return;
        for(var member:List.copyOf(owner.getAllBehaviours())){
            if(!(member instanceof FilteringBehaviour registered))continue;
            var actual=registered instanceof SidedFilteringBehaviour sided?sided.get(hit.getDirection()):registered;
            if(actual==null||!actual.isActive()||!actual.mayInteract(client.player))continue;
            var transform=new NativeFilterValueBoxTransform(registered,actual,hit.getDirection());
            if(!transform.shouldRender(client.level,owner.getBlockPos(),owner.getBlockState()))continue;
            var sample=actual.getFilter();boolean filterItem=FilterItemStack.of(sample).isFilterItem();
            var bounds=filterItem?new AABB(Vec3.ZERO,Vec3.ZERO).inflate(.45,.31,.2):new AABB(Vec3.ZERO,Vec3.ZERO).inflate(.25);
            boolean hitSlot=transform.testHit(client.level,owner.getBlockPos(),owner.getBlockState(),hit.getLocation().subtract(Vec3.atLowerCornerOf(owner.getBlockPos())));
            var count=countLabel(actual);var label=actual.getLabel();
            var box=new ValueBox.ItemValueBox(label,bounds,owner.getBlockPos(),sample,count);
            boolean bypass=client.player.getMainHandItem().is(com.simibubi.create.AllItems.WRENCH)
                ||client.player.getMainHandItem().is(com.simibubi.create.AllBlocks.CLIPBOARD.asItem())
                ||client.player.getMainHandItem().is(com.simibubi.create.AllBlocks.MECHANICAL_ARM.asItem());
            box.withOwner(registered).transform(transform).passive(!hitSlot||bypass);
            if(!box.isCurrent()||!transform.current())continue;
            Outliner.getInstance().showOutline(new Slot(registered,hit.getDirection()),box).lineWidth(1/64f).highlightFace(hit.getDirection());
            if(!hitSlot||bypass)continue;
            var tip=new ArrayList<MutableComponent>();tip.add(label.copy());
            tip.add(Component.translatable(sample.isEmpty()?"create.logistics.filter.click_to_set":"create.logistics.filter.click_to_replace"));
            if(actual.isCountVisible())tip.add(Component.translatable("create.logistics.filter.hold_to_set_amount"));
            ValueSettingsClient.INSTANCE.showHoverTip(tip);
        }
    }
    private static MutableComponent countLabel(FilteringBehaviour filter){
        if(filter instanceof FactoryPanelBehaviour)return null; // The native panel renderer owns its actual stock/promises text.
        if(filter.blockEntity instanceof TableClothBlockEntity cloth)return cloth.isShop()?Component.literal(Integer.toString(cloth.getPaymentAmount())):null;
        return Component.literal(filter.isCountVisible()?filter.upTo&&filter.count==filter.getMaxStackSize()?"*":Integer.toString(filter.count):"");
    }
    /** Suppress only the duplicate legacy HUD; its real native input/payment/amount screen is retained. */
    public static boolean coversNativeHud(Minecraft client,BlockHitResult hit){
        if(client.level==null||!ValueSettingsInputHandler.canInteract(client.player)||client.gui.screen()!=null||!client.player.isWithinBlockInteractionRange(hit.getBlockPos(),1))return false;
        if(!client.level.mayInteract(client.player,hit.getBlockPos()))return false;
        var filter=FilteringSettings.resolve(client.level,hit.getBlockPos());if(filter==null||!filter.isActive()||!filter.mayInteract(client.player))return false;
        var transform=new NativeFilterValueBoxTransform(filter,filter,hit.getDirection());
        return transform.testHit(client.level,hit.getBlockPos(),filter.blockEntity.getBlockState(),hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())));
    }
}
