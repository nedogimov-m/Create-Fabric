package com.simibubi.create.content.contraptions.actors.contraptionControls;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

/** Controls remain available while all other actor types are disabled. */
public class ContraptionControlsMovement implements MovementBehaviour {
    @Override public @Nullable ItemStack canBeDisabledVia(MovementContext context) { return null; }
    @Override public void startMoving(MovementContext context){if(context.contraption instanceof com.simibubi.create.content.contraptions.elevator.ElevatorContraption&&context.blockEntityData!=null)context.blockEntityData.remove("Filter");}
    @Override public void stopMoving(MovementContext context) {
        var filter = getFilter(context);
        if (filter != null) context.blockEntityData.putBoolean("Disabled", isDisabled(context.contraption, filter));
    }
    public static boolean isSameFilter(ItemStack first, ItemStack second) {
        return first.isEmpty() && second.isEmpty() || ItemStack.isSameItemSameComponents(first, second);
    }
    public static @Nullable ItemStack getFilter(MovementContext context) {
        if (context.blockEntityData == null) return null;
        return TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, context.world.registryAccess(), context.blockEntityData)
            .read("Filter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }
    public static boolean isDisabledInitially(MovementContext context) { return context.blockEntityData != null && context.blockEntityData.getBooleanOr("Disabled", false); }
    public static boolean isDisabled(Contraption contraption, ItemStack filter) { return contraption.isActorTypeDisabled(filter) || contraption.isActorTypeDisabled(ItemStack.EMPTY); }
    /**
     * Filter-changing convenience operation. The empty stack means all actor types, and enabling
     * one type under that sentinel must expand the remaining disabled types before removing it.
     * The caller publishes the completed state once; this helper does not send intermediate data.
     */
    public static void setActorTypeActive(Contraption contraption, ItemStack reference, boolean enable) {
        if(!contraption.supportsActorControls())return;var filters = contraption.getDisabledActors();
        if (reference.isEmpty()) {
            filters.clear();
            if (!enable) filters.add(ItemStack.EMPTY);
        } else if (enable) {
            boolean global = contraption.isActorTypeDisabled(ItemStack.EMPTY);
            filters.removeIf(filter -> filter.isEmpty() || isSameFilter(filter, reference));
            if (global) {
                for (var pair : List.copyOf(contraption.getActors())) {
                    var behaviour = MovementBehaviour.REGISTRY.get(pair.left.state());
                    if (behaviour == null || pair.right == null) continue;
                    var filter = behaviour.canBeDisabledVia(pair.right);
                    if (filter == null || isSameFilter(filter, reference) || contraption.isActorTypeDisabled(filter)) continue;
                    filters.add(filter.copyWithCount(1));
                }
            }
        } else if (!contraption.isActorTypeDisabled(ItemStack.EMPTY) && !contraption.isActorTypeDisabled(reference)) {
            filters.add(reference.copyWithCount(1));
        }
        applyDisabledActors(contraption);
    }
    /** Called once after every actor has received startMoving, so list ordering cannot skip actors. */
    public static void initializeControls(Contraption contraption) {
        if(!contraption.supportsActorControls()){applyDisabledActors(contraption);return;}
        for (var pair : contraption.getActors()) {
            if (pair.right == null || !(MovementBehaviour.REGISTRY.get(pair.left.state()) instanceof ContraptionControlsMovement) || !isDisabledInitially(pair.right)) continue;
            var filter = getFilter(pair.right);
            if (filter != null && !contraption.isActorTypeDisabled(filter)) contraption.getDisabledActors().add(filter.copyWithCount(1));
        }
        applyDisabledActors(contraption);
    }
    /** Reapply the persisted global/type filters after native actor snapshots are loaded. */
    public static void applyDisabledActors(Contraption contraption) {
        if(!contraption.supportsActorControls()){for(var actor:contraption.getActors())if(actor.right!=null)actor.right.disabled=false;return;}
        for (var pair : List.copyOf(contraption.getActors())) {
            var behaviour = MovementBehaviour.REGISTRY.get(pair.left.state());
            if (behaviour == null || pair.right == null) continue;
            var filter = behaviour.canBeDisabledVia(pair.right);
            boolean disabled = filter != null && isDisabled(contraption, filter);
            boolean changed = pair.right.disabled != disabled;
            pair.right.disabled = disabled;
            if (disabled && changed) behaviour.onDisabledByControls(pair.right);
        }
    }
    @Override public void tick(MovementContext context) {
        if (!context.world.isClientSide()) return;
        if(context.contraption instanceof com.simibubi.create.content.contraptions.elevator.ElevatorContraption cabin){
            if(!(context.temporaryData instanceof ElevatorFloorSelection))context.temporaryData=new ElevatorFloorSelection();var selection=(ElevatorFloorSelection)context.temporaryData;tickFloorSelection(selection,cabin);com.simibubi.create.content.contraptions.elevator.ElevatorNetworking.requestFloors(context.contraption.entity);
            if(context.contraption.getBlockEntityClientSide(context.localPos) instanceof ContraptionControlsBlockEntity controls){controls.tickAnimations();controls.disabled=false;controls.elevatorMode=true;controls.elevatorFloorLabel=selection.currentShortName;controls.elevatorFloorUnreachable=cabin.isTargetUnreachable(selection.currentTargetY);int currentY=(int)Math.round(cabin.entity.getY()+cabin.getContactYOffset());float indicator=controls.getIndicator();boolean atTarget=cabin.clientYTarget==currentY,below=atTarget?indicator>0:cabin.clientYTarget<=currentY;if(indicator==0&&!atTarget)controls.setElevatorIndicator(below?181:-181);else{int stage=Math.floorMod((int)Math.floor(indicator),360);if(!atTarget||stage/45!=0){float increment=stage/45==(below?4:3)?2.25f:33.75f;controls.setElevatorIndicator(indicator+(below?increment:-increment));}else controls.setElevatorIndicator(0);}}return;
        }
        if (!(context.contraption.getBlockEntityClientSide(context.localPos) instanceof ContraptionControlsBlockEntity controls)) return;
        var filter = getFilter(context);
        controls.disabled = filter != null && isDisabled(context.contraption, filter);
        controls.tickAnimations();
    }
    public static final class ElevatorFloorSelection{public int currentIndex,currentTargetY;public boolean targetYEqualsSelection=true;public String currentShortName="",currentLongName="";}
    public static void tickFloorSelection(ElevatorFloorSelection selection,com.simibubi.create.content.contraptions.elevator.ElevatorContraption cabin){
        if(cabin.namesList.isEmpty()){selection.currentIndex=0;selection.currentShortName="X";selection.currentLongName="No Floors";selection.targetYEqualsSelection=true;return;}
        selection.currentIndex=Math.clamp(selection.currentIndex,0,cabin.namesList.size()-1);var floor=cabin.namesList.get(selection.currentIndex);selection.currentTargetY=floor.y();selection.currentShortName=floor.shortName();selection.currentLongName=cabin.isTargetUnreachable(floor.y())?net.minecraft.network.chat.Component.translatable("create.contraption.controls.floor_unreachable").getString():floor.longName();selection.targetYEqualsSelection=floor.y()==cabin.clientYTarget;
    }
}
