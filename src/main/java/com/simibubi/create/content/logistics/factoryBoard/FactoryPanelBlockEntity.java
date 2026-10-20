package com.simibubi.create.content.logistics.factoryBoard;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class FactoryPanelBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider,RenderDataBlockEntity {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"factory_gauge");
    public final EnumMap<FactoryPanelBlock.PanelSlot,FactoryPanelBehaviour> panels=new EnumMap<>(FactoryPanelBlock.PanelSlot.class);
    public boolean restocker;
    private int lazy=10;
    private VoxelShape shape;
    public record PanelRenderData(int activeMask,int requestedMask,boolean restocker) {}
    public FactoryPanelBlockEntity(BlockPos pos,BlockState state) { super(AllBlockEntityTypes.FACTORY_PANEL,pos,state);for(var slot:FactoryPanelBlock.PanelSlot.values())panels.put(slot,new FactoryPanelBehaviour(this,slot)); }
    @Override public Object getRenderData() { int active=0,requested=0;for(var panel:panels.values())if(panel.active){active|=1<<panel.slot.ordinal();if(panel.count!=0)requested|=1<<panel.slot.ordinal();}return new PanelRenderData(active,requested,restocker); }
    public boolean addPanel(FactoryPanelBlock.PanelSlot slot,UUID frequency) { var panel=panels.get(slot);if(panel.active)return false;panel.active=true;if(frequency!=null)panel.network=frequency;notifyUpdate();return true; }
    public boolean removePanel(FactoryPanelBlock.PanelSlot slot) { var panel=panels.get(slot);if(!panel.active)return false;panel.disable();notifyUpdate();return true; }
    public int activePanels() { return (int)panels.values().stream().filter(panel->panel.active).count(); }
    public void refreshRestocker() { if(level==null||level.isClientSide())return;var attached=LoadedLevelAccess.blockEntity(level,worldPosition.relative(FactoryPanelBlock.connectedDirection(getBlockState()).getOpposite()));boolean value=attached instanceof PackagerBlockEntity&&!(attached instanceof RepackagerBlockEntity);if(value!=restocker){restocker=value;notifyUpdate();} }
    public PackagerBlockEntity getRestockedPackager() { if(!restocker||level==null)return null;var pos=worldPosition.relative(FactoryPanelBlock.connectedDirection(getBlockState()).getOpposite());return LoadedLevelAccess.blockEntity(level,pos) instanceof PackagerBlockEntity packager&&!(packager instanceof RepackagerBlockEntity)?packager:null; }
    public static void tick(Level level,BlockPos pos,BlockState state,FactoryPanelBlockEntity gauge) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, gauge)) {
            if (!createTick.shouldTick()) return;

        
        if(!level.isClientSide()&&--gauge.lazy<=0){gauge.lazy=10;if(gauge.activePanels()==0){level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());return;}gauge.refreshRestocker();}
        
    
        }
    }
    public VoxelShape getShape() {
        if(shape!=null)return shape;shape=Shapes.empty();var direction=FactoryPanelBlock.connectedDirection(getBlockState());
        for(var panel:panels.values())if(panel.active){var center=FactoryPanelTransform.point(getBlockState(),new Vec3(.25+panel.slot.xOffset*.5,1/16d,.25+panel.slot.yOffset*.5));var box=new AABB(center,center).inflate(1/16d).inflate(direction.getAxis()==Direction.Axis.X?0:3/16d,direction.getAxis()==Direction.Axis.Y?0:3/16d,direction.getAxis()==Direction.Axis.Z?0:3/16d);shape=Shapes.or(shape,Shapes.create(box));}return shape;
    }
    public void notifyUpdate() { shape=null;setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS); }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state) { if(level!=null&&!level.isClientSide()){int count=activePanels();if(count>1)Block.popResource(level,pos,new net.minecraft.world.item.ItemStack(AllBlocks.FACTORY_GAUGE,count-1));}super.preRemoveSideEffects(pos,state); }
    @Override protected void write(ValueOutput out, boolean clientPacket) { super.write(out, clientPacket);out.putBoolean("Restocker",restocker); }
    @Override protected void read(ValueInput in, boolean clientPacket) { super.read(in, clientPacket);restocker=in.getBooleanOr("Restocker",false);shape=null;if(level!=null&&level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var out=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,registries);write(out, true);var tag=out.buildResult();
        for(var slot:FactoryPanelBlock.PanelSlot.values())tag.getCompound(slot.getSerializedName()).ifPresent(panel->panel.remove("Promises"));
        return tag;
    }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (panels != null) behaviours.addAll(panels.values());
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
