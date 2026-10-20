package com.simibubi.create.content.fluids.hosePulley;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.fluids.transfer.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class HosePulleyBlockEntity extends KineticBlockEntity {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"hose_pulley","hose_pulley_lava");
    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){boolean added=super.addToGoggleTooltip(tooltip,sneaking);if(isInfinite()){tooltip.add(net.minecraft.network.chat.Component.empty());tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate("hint.hose_pulley.title").withStyle(net.minecraft.ChatFormatting.GOLD));tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate("hint.hose_pulley").withStyle(net.minecraft.ChatFormatting.GRAY));}return added;}
    private float offset,previousOffset,lastSpeed=Float.NaN;
    private boolean moving;
    private HosePulleySettings settings=HosePulleySettings.DEFAULT;
    private boolean explicitSettings;
    public final SmartFluidTank tank=new SmartFluidTank(1500*81L,fluid->fluidChanged());
    private final FluidDrainingBehaviour drainer=new FluidDrainingBehaviour(this);
    private final FluidFillingBehaviour filler=new FluidFillingBehaviour(this);
    public final HosePulleyFluidHandler storage=new HosePulleyFluidHandler(this,tank,drainer,filler);
    public HosePulleyBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.HOSE_PULLEY,pos,state);}
    public HosePulleySettings settings(){refreshSettings();return settings;}
    private void refreshSettings(){if(explicitSettings||net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.isOpen())return;var actual=HosePulleySettings.configured();if(actual.equals(settings))return;settings=actual;drainer.reset();filler.reset();}
    public void configure(HosePulleySettings settings){this.explicitSettings=true;this.settings=java.util.Objects.requireNonNull(settings);drainer.reset();filler.reset();}
    public BlockPos rootPos(){return worldPosition.below((int)Math.ceil(offset));}
    public boolean canTransfer(){return level!=null&&!moving&&level.isLoaded(rootPos());}
    public boolean isMoving(){return moving;}
    public boolean isInfinite(){return drainer.isInfinite()||filler.isInfinite();}
    public float getOffset(){return offset;}
    public float getInterpolatedOffset(float partial){return Math.max(previousOffset+(offset-previousOffset)*partial,3f/16);}
    public float getMovementSpeed(){return getSpeed()/512f*(level!=null&&level.isClientSide()&&!isVirtual()?com.simibubi.create.foundation.utility.ServerSpeedProvider.get():1);}
    public void fluidChanged(){setChanged();if(level!=null&&!level.isClientSide()&&!net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.isOpen())sync();}
    public static void tick(Level level,BlockPos pos,BlockState state,HosePulleyBlockEntity pulley){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, pulley)) {
            if (!createTick.shouldTick()) return;

        pulley.refreshSettings();KineticBlockEntity.tick(level,pos,state,pulley);pulley.previousOffset=pulley.offset;
        float speed=pulley.getSpeed();boolean speedChanged=Float.compare(pulley.lastSpeed,speed)!=0;boolean wasMoving=pulley.moving;
        if(speedChanged){pulley.lastSpeed=speed;if(speed==0)pulley.offset=Math.round(pulley.offset);}
        float next=pulley.offset+pulley.getMovementSpeed();boolean loaded=level.isLoaded(pos.below((int)Math.ceil(next)));
        pulley.moving=speed!=0&&loaded&&next>=0;
        if(next<0)next=0;
        if(!loaded||level.isOutsideBuildHeight(pos.below((int)Math.ceil(next)))){next=pulley.offset;pulley.moving=false;}
        else if(!LoadedLevelAccess.blockState(level,pos.below((int)Math.ceil(next))).canBeReplaced()){
            next=(int)next;pulley.moving=false;
        }
        pulley.offset=next;
        if(!level.isClientSide()){
            if(pulley.moving&&(speedChanged||!wasMoving)){pulley.drainer.reset();pulley.filler.reset();}
            
            if(speedChanged||wasMoving!=pulley.moving||pulley.offset!=pulley.previousOffset){pulley.setChanged();if(speedChanged||wasMoving!=pulley.moving||level.getGameTime()%5==0)pulley.sync();}
        }
    
        }
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.child("Offset").putFloat("Value",offset);tank.save(out.child("Tank"));out.putBoolean("Moving",moving);out.putBoolean("Infinite",isInfinite());}
    @Override protected void read(ValueInput in, boolean clientPacket){float value=in.childOrEmpty("Offset").getFloatOr("Value",0);offset=previousOffset=Float.isFinite(value)?Math.max(0,value):0;moving=in.getBooleanOr("Moving",false);tank.load(in.childOrEmpty("Tank"));lastSpeed=Float.NaN;
        // Refresh before behaviours hydrate their saved search state.
        refreshSettings();
        super.read(in, clientPacket);
    }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (drainer != null) behaviours.add(drainer);
        if (filler != null) behaviours.add(filler);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("hose_pulley");
        ids.add("hose_pulley_lava");
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
