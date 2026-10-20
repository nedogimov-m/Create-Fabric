package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.behaviour.display.*;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Actual source link lifecycle/configuration and actual existing factory-panel support. */
public final class DisplayLinkBlockEntity extends LinkWithBulbBlockEntity implements BlockEntityBehaviour.Provider,TransformableBlockEntity {
    protected BlockPos targetOffset=BlockPos.ZERO;
    public DisplaySource activeSource;
    private Identifier unresolvedSource;
    public DisplayTarget activeTarget;
    public int targetLine,refreshTicks;
    private CompoundTag sourceConfig=new CompoundTag();
    private boolean initialized,updating;
    private UUID advancementOwner;
    public UUID advancementOwner(){return advancementOwner;}
    public void placedBy(net.minecraft.server.level.ServerPlayer player){
        if(level==null||player.level()!=level||player instanceof net.fabricmc.fabric.api.entity.FakePlayer)return;
        advancementOwner=player.getUUID();DisplayLinkAdvancements.removeCompletedOwner(this);setChanged();
    }
    void clearAdvancementOwner(UUID owner){if(!com.simibubi.create.foundation.advancement.AdvancementBehaviour.hasPendingFor(this,owner)&&Objects.equals(advancementOwner,owner)){advancementOwner=null;setChanged();}}

    public final FactoryPanelSupportBehaviour factoryPanelSupport=new FactoryPanelSupportBehaviour(this,()->false,()->false,this::updateGatheredData);
    public DisplayLinkBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.DISPLAY_LINK,pos,state);}
    public static void tick(Level level,BlockPos pos,BlockState state,DisplayLinkBlockEntity link){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, link)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide()){link.tickGlow();return;}if(!link.initialized){link.initialized=true;DisplayLinkAdvancements.removeCompletedOwner(link);if(link.activeSource!=null)link.tickSource();}if(link.activeSource==null)return;link.refreshTicks++;if(link.refreshTicks>=Math.max(1,link.activeSource.getPassiveRefreshTicks())&&link.activeSource.shouldPassiveReset())link.tickSource();
    
        }
    }
    public void tickSource(){refreshTicks=0;if(level==null||level.isClientSide()||getBlockState().getOptionalValue(DisplayLinkBlock.POWERED).orElse(true))return;updateGatheredData();}
    public void onNoLongerPowered(){if(activeSource==null||level==null||level.isClientSide())return;refreshTicks=0;activeSource.onSignalReset(new DisplayLinkContext(level,this));updateGatheredData();}
    public void updateGatheredData(){
        if(level==null||level.isClientSide()||updating||targetOffset.equals(BlockPos.ZERO))return;var context=new DisplayLinkContext(level,this);if(!context.endpointsLoaded())return;updating=true;
        try{var target=DisplayTarget.get(level,getTargetPosition());var sources=DisplaySource.getAll(level,getSourcePosition());boolean notify=false;if(activeTarget!=target){if(activeTarget!=null)activeTarget.release(context);activeTarget=target;notify=true;}if(activeSource!=null&&!sources.contains(activeSource)){if(activeTarget!=null)activeTarget.release(context);activeSource=null;unresolvedSource=null;sourceConfig=new CompoundTag();notify=true;}if(notify)notifyUpdate();if(activeSource==null||activeTarget==null||!activeSource.isAvailable(context))return;var stats=activeTarget.provideStats(context);if(stats.maxRows()==0||stats.maxColumns()==0)return;activeSource.transferData(context,activeTarget,targetLine);DisplayLinkAdvancements.award(this,DisplayLinkAdvancements.Award.DISPLAY_LINK);sendPulseNextSync();notifyUpdate();}finally{updating=false;}
    }
    public boolean configure(CompoundTag config,int line){
        if(level==null||level.isClientSide()||!new DisplayLinkContext(level,this).endpointsLoaded()||!targetOffset.closerThan(BlockPos.ZERO,com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.displayLinkRange.get())||targetOffset.equals(BlockPos.ZERO))return false;
        var source=DisplaySource.get(Identifier.tryParse(config.getStringOr("Id","")));var target=DisplayTarget.get(level,getTargetPosition());if(source==null||target==null||!DisplaySource.getAll(level,getSourcePosition()).contains(source))return false;var context=new DisplayLinkContext(level,this);int rows=target.provideStats(context).maxRows();if(line<0||line>=rows)return false;
        if(activeTarget!=null&&(line!=targetLine||activeSource!=source))activeTarget.release(context);targetLine=line;var next=activeSource==source?sourceConfig.copy():new CompoundTag();var draftContext=new DisplayLinkContext(level,this,next);source.populateData(draftContext);for(var option:source.configuration(draftContext))if(config.contains(option.key()))option.copyValidated(config,next);next.putString("Id",CreateBuiltInRegistries.DISPLAY_SOURCE.getKey(source).toString());activeSource=source;unresolvedSource=null;sourceConfig=next;activeTarget=target;refreshTicks=0;updateGatheredData();notifyUpdate();return true;
    }
    public void target(BlockPos position){if(level!=null&&!level.isClientSide()&&activeTarget!=null)activeTarget.release(new DisplayLinkContext(level,this));targetOffset=position.subtract(worldPosition);activeTarget=null;notifyUpdate();}
    public BlockPos getTargetOffset(){return targetOffset;}
    public BlockPos getTargetPosition(){return worldPosition.offset(targetOffset);}
    public BlockPos getSourcePosition(){for(var panel:factoryPanelSupport.getLinkedPanels())return panel.pos();return worldPosition.relative(getDirection());}
    public Direction getDirection(){return getBlockState().getOptionalValue(DisplayLinkBlock.FACING).orElse(Direction.UP).getOpposite();}
    public CompoundTag getSourceConfig(){return sourceConfig;}
    public void setSourceConfig(CompoundTag config){sourceConfig=config.copy();setChanged();}
    public void notifyUpdate(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){if(activeTarget!=null)activeTarget.release(new DisplayLinkContext(level,this));}super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput out, boolean clientPacket){
        super.write(out, clientPacket);out.store("TargetOffset",BlockPos.CODEC,targetOffset);out.putInt("TargetLine",targetLine);if(activeSource!=null||unresolvedSource!=null){var source=sourceConfig.copy();var id=activeSource==null?unresolvedSource:CreateBuiltInRegistries.DISPLAY_SOURCE.getKey(activeSource);if(id!=null)source.putString("Id",id.toString());out.store("Source",CompoundTag.CODEC,source);}if(activeTarget!=null){var id=CreateBuiltInRegistries.DISPLAY_TARGET.getKey(activeTarget);if(id!=null)out.putString("TargetType",id.toString());}
    }
    @Override protected void read(ValueInput in, boolean clientPacket){
        super.read(in, clientPacket);targetOffset=in.read("TargetOffset",BlockPos.CODEC).orElseGet(()->{var child=in.childOrEmpty("TargetOffset");return new BlockPos(child.getIntOr("X",0),child.getIntOr("Y",0),child.getIntOr("Z",0));});targetLine=Math.clamp(in.getIntOr("TargetLine",0),0,255);activeTarget=DisplayTarget.get(Identifier.tryParse(in.getStringOr("TargetType","")));var source=in.read("Source",CompoundTag.CODEC).orElseGet(CompoundTag::new);var sourceId=Identifier.tryParse(source.getStringOr("Id",""));activeSource=DisplaySource.get(sourceId);unresolvedSource=activeSource==null?sourceId:null;sourceConfig=sourceId==null?new CompoundTag():source.copy();refreshTicks=0;initialized=false;
    }
    @Override public Vec3 getBulbOffset(BlockState state){return state.getValue(DisplayLinkBlock.FACING).getAxis().isVertical()?new Vec3(5/16d,7/16d,11/16d):new Vec3(11/16d,7/16d,5/16d);}
    @Override public void transform(BlockEntity block,StructureTransform transform){targetOffset=transform.applyWithoutOffset(targetOffset);notifyUpdate();}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (factoryPanelSupport != null) behaviours.add(factoryPanelSupport);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("display_link");
        ids.add("display_board_0");
    }
    private final com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage canonicalAdvancementOwner=new com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage() {
        @Override public java.util.UUID getPlayerId(){return advancementOwner;}
        @Override public void setPlayerId(java.util.UUID id){advancementOwner=id;}
    };
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return canonicalAdvancementOwner;}
}
