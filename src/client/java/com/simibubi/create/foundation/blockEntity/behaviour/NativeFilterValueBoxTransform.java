package com.simibubi.create.foundation.blockEntity.behaviour;

import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlock;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlock;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.logistics.chute.SmartChuteBlock;
import com.simibubi.create.content.logistics.chute.SmartChuteBlockEntity;
import com.simibubi.create.content.logistics.crate.CreativeCrateBlock;
import com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSlotPositioning;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelTransform;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelFilterSlotPositioning;
import com.simibubi.create.content.logistics.itemHatch.HatchFilterSlot;
import com.simibubi.create.content.logistics.itemHatch.ItemHatchBlockEntity;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlockEntity;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity;
import com.simibubi.create.content.logistics.tunnel.TunnelSettings;
import com.simibubi.create.content.redstone.DirectedDirectionalBlock;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.content.trains.observer.TrackObserverBlock;
import com.simibubi.create.content.trains.observer.TrackObserverBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Geometry only: reads the existing registered native filter and source per-face owners. */
public final class NativeFilterValueBoxTransform extends ValueBoxTransform {
    private final FilteringBehaviour registered,filter;
    private final Direction side;
    private float partial=1;
    public NativeFilterValueBoxTransform(FilteringBehaviour registered,FilteringBehaviour filter,Direction side){
        this.registered=registered;this.filter=filter;this.side=side;scale=getScale();
    }
    void partial(float partial){this.partial=partial;}
    public boolean current(){
        var owner=registered.blockEntity;var world=registered.getWorld();
        return world!=null&&!owner.isRemoved()&&filter.blockEntity==owner
            &&LoadedLevelAccess.blockEntity(world,registered.getPos())==owner&&owner.getBehaviour(registered.getType())==registered
            &&(registered instanceof SidedFilteringBehaviour sided?sided.get(side)==filter:registered==filter);
    }
    @Override public float getScale(){
        if(filter==null)return .5f;
        if(filter.blockEntity instanceof ItemHatchBlockEntity)return HatchFilterSlot.SCALE;
        if(filter.blockEntity instanceof ContraptionControlsBlockEntity)return .508f;
        return .5f;
    }
    @Override public Vec3 getLocalOffset(LevelAccessor level,BlockPos pos,BlockState state){
        var owner=filter.blockEntity;
        var processing=FilteringSettings.processingFilterPosition(state,side);
        if(processing!=null)return processing;
        if(filter instanceof FactoryPanelBehaviour panel)return FactoryPanelSlotPositioning.getCenterOfSlot(state,panel.slot);
        if(owner instanceof BrassTunnelBlockEntity)return TunnelSettings.filterPosition(side);
        if(owner instanceof TrackObserverBlockEntity)return new Vec3(.5,15.5/16d,.5);
        if(owner instanceof CreativeCrateBlockEntity)return CreativeCrateBlock.FILTER_POSITION;
        if(owner instanceof FunnelBlockEntity)return FunnelFilterSlotPositioning.position(state,side);
        if(owner instanceof SmartChuteBlockEntity)return SmartChuteBlock.filterPosition(side);
        if(owner instanceof SmartObserverBlockEntity)return SmartObserverBlock.FILTER_SLOT.position(side);
        if(owner instanceof ThresholdSwitchBlockEntity)return ThresholdSwitchBlock.FILTER_SLOT.position(side);
        if(owner instanceof ItemHatchBlockEntity)return HatchFilterSlot.position(state);
        if(owner instanceof TableClothBlockEntity cloth)return cloth.pricePosition();
        if(owner instanceof RollerBlockEntity)return RollerBlock.panelOffset(state,true);
        if(owner instanceof ContraptionControlsBlockEntity controls){
            var yaw=Math.toRadians(ContraptionControlsBlock.yaw(state.getValue(ContraptionControlsBlock.FACING).getOpposite()));
            var movement=new Vec3(-.325*Math.sin(yaw),1,-.325*Math.cos(yaw));
            return ContraptionControlsBlock.filterPosition(state).add(movement.scale(-.07-controls.getButton(partial)/24));
        }
        return null;
    }
    @Override public boolean shouldRender(LevelAccessor level,BlockPos pos,BlockState state){
        if(!current()||!registered.isActive()||!filter.isActive())return false;
        var owner=filter.blockEntity;boolean active;
        if(filter instanceof FactoryPanelBehaviour panel){var normal=FactoryPanelTransform.normal(state,new Vec3(0,1,0));active=panel.active&&normal.dot(side.getUnitVec3())>.5;}
        else if(owner instanceof BrassTunnelBlockEntity)active=side.getAxis().isHorizontal();
        else if(owner instanceof ContraptionControlsBlockEntity controls)active=!controls.elevatorMode;
        else active=FilteringSettings.activeSide(state,side);
        return active&&getLocalOffset(level,pos,state)!=null&&current();
    }
    @Override public boolean testHit(LevelAccessor level,BlockPos pos,BlockState state,Vec3 local){
        if(!shouldRender(level,pos,state))return false;
        var hit=new BlockHitResult(local.add(Vec3.atLowerCornerOf(pos)),side,pos,false);var owner=filter.blockEntity;
        if(filter instanceof FactoryPanelBehaviour panel)return FactoryPanelBlock.getTargetedSlot(pos,state,hit.getLocation())==panel.slot&&local.distanceTo(getLocalOffset(level,pos,state))<scale/2;
        if(owner instanceof BrassTunnelBlockEntity)return local.distanceTo(TunnelSettings.filterPosition(side))<.25;
        if(owner instanceof TableClothBlockEntity cloth)return cloth.targetsPriceTag(hit);
        if(owner instanceof ContraptionControlsBlockEntity)return ContraptionControlsBlock.onFilter(state,pos,hit.getLocation());
        return FilteringSettings.testHit(state,pos,hit);
    }
    private static void y(Matrix4f matrix,float degrees){matrix.rotateY(AngleHelper.rad(degrees));}
    private static void x(Matrix4f matrix,float degrees){matrix.rotateX(AngleHelper.rad(degrees));}
    private void sided(Matrix4f matrix){y(matrix,AngleHelper.horizontalAngle(side)+180);x(matrix,side==Direction.UP?90:side==Direction.DOWN?270:0);}
    @Override public void rotate(LevelAccessor level,BlockPos pos,BlockState state,Matrix4f matrix){
        var owner=filter.blockEntity;
        if(filter instanceof FactoryPanelBehaviour){matrix.rotateY(FactoryPanelBlock.getYRot(state)+(float)Math.PI).rotateX(-FactoryPanelBlock.getXRot(state));return;}
        if(owner instanceof TableClothBlockEntity cloth){y(matrix,180-cloth.facing.toYRot());x(matrix,cloth.sideOccluded?90:0);return;}
        if(owner instanceof ItemHatchBlockEntity){y(matrix,HatchFilterSlot.angle(state));x(matrix,-45);return;}
        if(owner instanceof RollerBlockEntity){y(matrix,AngleHelper.horizontalAngle(state.getValue(RollerBlock.FACING))+180);x(matrix,90);return;}
        if(owner instanceof ContraptionControlsBlockEntity){y(matrix,ContraptionControlsBlock.yaw(state.getValue(ContraptionControlsBlock.FACING))+180);x(matrix,67.5f);return;}
        if(owner instanceof TrackObserverBlockEntity||owner instanceof CreativeCrateBlockEntity){x(matrix,90);return;}
        if(owner instanceof FunnelBlockEntity){
            var facing=AbstractFunnelBlock.getFunnelFacing(state);
            boolean belt=state.getBlock() instanceof BeltFunnelBlock;
            if(facing.getAxis().isHorizontal()&&belt&&state.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.EXTENDED){y(matrix,AngleHelper.horizontalAngle(facing));x(matrix,90);return;}
            sided(matrix);
            if(facing.getAxis().isHorizontal()&&(state.getBlock() instanceof FunnelBlock||belt&&(state.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.PUSHING||state.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.PULLING)))x(matrix,-22.5f);
            return;
        }
        sided(matrix);
        if((owner instanceof SmartObserverBlockEntity||owner instanceof ThresholdSwitchBlockEntity)&&side==Direction.UP){
            var facing=state.getValue(DirectedDirectionalBlock.FACING);
            if(facing.getAxis().isHorizontal())matrix.rotateZ(AngleHelper.rad(-AngleHelper.horizontalAngle(facing)+180));
        }
    }
}
