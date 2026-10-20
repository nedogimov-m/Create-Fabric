package com.simibubi.create.content.equipment.clipboard;

import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.KineticSpeedSettings;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.*;
import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source clipboard keys applied to the existing genuine native settings owners. */
final class ClipboardSettingsAdapters {
    static void add(BlockEntity owner,List<ClipboardCloneable> result){
        if(owner instanceof KineticBlockEntity kinetic&&KineticSpeedSettings.supports(owner.getBlockState())&&!(owner instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity))result.add(value("Speed",()->KineticSpeedSettings.value(kinetic)<0?0:1,()->Math.abs(KineticSpeedSettings.value(kinetic)),(row,amount)->{int speed=Math.clamp(Math.max(1,amount),1,com.simibubi.create.content.kinetics.KineticNetworks.maxRotationSpeed())*(row==0?-1:1);if(kinetic instanceof SpeedControllerBlockEntity controller)controller.setTargetSpeed(speed);else kinetic.setGeneratedSpeed(speed);}));
        if(owner instanceof ChassisBlockEntity chassis)result.add(value("Chassis",()->0,()->chassis.getRange()-1,(row,amount)->chassis.setRange(amount==Integer.MAX_VALUE?Integer.MAX_VALUE:amount+1)));
        if(owner instanceof LinearActuatorBlockEntity actuator)result.add(option("MovementMode",LinearActuatorBlockEntity.MovementMode.values(),actuator::getMovementMode,actuator::setMovementMode));
        if(owner instanceof MechanicalBearingBlockEntity bearing)result.add(option("RotationMode",MechanicalBearingBlockEntity.RotationMode.values(),bearing::getMovementMode,bearing::setMovementMode));
        if(owner instanceof WindmillBearingBlockEntity windmill)result.add(option("RotationDirection",WindmillBearingBlockEntity.RotationDirection.values(),windmill::getMovementDirection,windmill::setMovementDirection));
        if(owner instanceof ClockworkBearingBlockEntity clockwork)result.add(option("ClockHands",ClockworkBearingBlockEntity.ClockHands.values(),clockwork::getOperationMode,clockwork::setOperationMode));
        if(owner instanceof BrassTunnelBlockEntity tunnel&&tunnel.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE)==null)result.add(option("SelectionMode",BrassTunnelBlockEntity.SelectionMode.values(),tunnel::getSelectionMode,mode->tunnel.setSelectionMode(mode)));
        if(owner instanceof com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity arm)result.add(option("SelectionMode",com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity.SelectionMode.values(),arm::getSelectionMode,arm::setSelectionMode));
        if(owner instanceof RollerBlockEntity roller)result.add(option("RollingMode",RollerBlockEntity.RollingMode.values(),roller::getMode,roller::setMode));
        if(owner instanceof SteamEngineBlockEntity engine)result.add(value("RotationDirection",()->0,()->engine.isCounterClockwise()?1:0,(row,amount)->{if((amount!=0)!=engine.isCounterClockwise())engine.reverse();}));
        var filtering=BlockEntityBehaviour.get(owner,FilteringBehaviour.TYPE);if(filtering!=null)result.add(new Filtering(filtering));
    }
    private static ClipboardCloneable value(String key,IntSupplier row,IntSupplier amount,BiConsumer<Integer,Integer> setter){return new ClipboardCloneable(){
        @Override public String getClipboardKey(){return key;}
        @Override public boolean writeToClipboard(HolderLookup.Provider registries,CompoundTag tag,Direction side){tag.putInt("Row",row.getAsInt());tag.putInt("Value",amount.getAsInt());return true;}
        @Override public boolean readFromClipboard(HolderLookup.Provider registries,CompoundTag tag,Player player,Direction side,boolean simulate){var r=tag.getInt("Row");var v=tag.getInt("Value");if(r.isEmpty()||v.isEmpty())return false;if(!simulate)setter.accept(r.get(),v.get());return true;}
    };}
    private static <E extends Enum<E>>ClipboardCloneable option(String key,E[] values,Supplier<E> current,Consumer<E> setter){return value(key,()->0,()->current.get().ordinal(),(row,amount)->setter.accept(values[Math.clamp(amount,0,values.length-1)]));}
    private static final class Filtering implements ClipboardCloneable {
        private final FilteringBehaviour owner;
        Filtering(FilteringBehaviour owner){this.owner=owner;}
        private FilteringBehaviour side(Direction side){return owner instanceof SidedFilteringBehaviour sided?sided.get(side):owner;}
        @Override public String getClipboardKey(){return "Filtering";}
        @Override public boolean writeToClipboard(HolderLookup.Provider registries,CompoundTag tag,Direction side){var filter=side(side);if(filter==null)return false;if(filter.isCountVisible()){tag.putInt("Row",filter.upTo?0:1);tag.putInt("Value",filter.count);}tag.store("Filter",ItemStack.OPTIONAL_CODEC,registries.createSerializationContext(NbtOps.INSTANCE),filter.getFilter());return true;}
        @Override public boolean readFromClipboard(HolderLookup.Provider registries,CompoundTag tag,Player player,Direction side,boolean simulate){
            var filter=side(side);if(filter==null||!filter.mayInteract(player))return false;
            var row=tag.getInt("Row");var amount=tag.getInt("Value");boolean settings=filter.isCountVisible()&&row.isPresent()&&amount.isPresent();
            ItemStack copied=null;if(tag.contains("Filter")){var parsed=tag.read("Filter",ItemStack.OPTIONAL_CODEC,registries.createSerializationContext(NbtOps.INSTANCE));if(parsed.isEmpty())return false;copied=parsed.get();if(!filter.canSetFilter(copied))return false;}
            if(!settings&&copied==null)return false;if(simulate)return true;
            var old=filter.getFilter().copy();boolean paidOld=FilterItemStack.of(old).isFilterItem()&&!player.isCreative();boolean payNew=copied!=null&&FilterItemStack.of(copied).isFilterItem()&&!player.isCreative();
            try(var transaction=Transaction.openOuter()){
                if(payNew&&(!paidOld||old.getItem()!=copied.getItem())){
                    var inventory=PlayerInventoryStorage.of(player);boolean taken=false;
                    for(boolean blankFirst:new boolean[]{true,false}){for(var slot:inventory.getSlots()){var actual=slot.getResource();if(actual.isBlank()||actual.toStack().getItem()!=copied.getItem()||actual.toStack().getComponentsPatch().isEmpty()!=blankFirst)continue;if(slot.extract(actual,1,transaction)==1){taken=true;break;}}if(taken)break;}
                    if(!taken){player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("create.logistics.filter.requires_item_in_inventory",copied.getHoverName()));return false;}
                }
                var participant=new FilterSnapshotOwner(filter);participant.updateSnapshots(transaction);
                if(settings)filter.setAmount(amount.get(),row.get()==0);
                if(copied!=null&&!filter.setFilter(side,copied))return false;
                transaction.commit();
            }
            if(copied!=null&&paidOld&&(!payNew||old.getItem()!=copied.getItem()))player.getInventory().placeItemBackInInventory(old,net.minecraft.util.Prediction.SERVER_ONLY);
            return true;
        }
    }
    private record FilterSnapshot(ItemStack filter,int amount,boolean upTo){}
    private static final class FilterSnapshotOwner extends net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant<FilterSnapshot>{
        private final FilteringBehaviour filter;FilterSnapshotOwner(FilteringBehaviour filter){this.filter=filter;}
        @Override protected FilterSnapshot createSnapshot(){return new FilterSnapshot(filter.getFilter().copy(),filter.count,filter.upTo);}
        @Override protected void readSnapshot(FilterSnapshot old){filter.setFilter(old.filter);filter.setAmount(old.amount,old.upTo);}
    }
    private ClipboardSettingsAdapters(){}
}
