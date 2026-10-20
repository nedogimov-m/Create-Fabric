package com.simibubi.create.content.fluids.tank;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;

/** Original five-tick samples and fifty-tick peak supply window, in Fabric fluid units. */
public final class BoilerData {
    private static final int SAMPLE_RATE=5,WATER_PER_LEVEL=10;
    private final float[] supplyOverTime=new float[10];
    private long gatheredSupply;
    private int ticksUntilNextSample,currentIndex;
    public boolean needsHeatLevelUpdate=true,passiveHeat;
    public int activeHeat,attachedEngines,attachedWhistles;
    public float waterSupply;
    private float previousGauge,gauge;
    private int maxHeatForSize,maxHeatForWater,minValue,maxValue;
    public void calcMinMaxForSize(int size){maxHeatForSize=getMaxHeatLevelForBoilerSize(size);maxHeatForWater=getMaxHeatLevelForWaterSupply();minValue=Math.min(passiveHeat?1:activeHeat,Math.min(maxHeatForSize,maxHeatForWater));maxValue=Math.max(passiveHeat?1:activeHeat,Math.max(maxHeatForSize,maxHeatForWater));}
    public MutableComponent getHeatLevelTextComponent(){int heat=Math.min(activeHeat,Math.min(maxHeatForSize,maxHeatForWater));return passiveHeat&&maxHeatForSize>0&&maxHeatForWater>0?GoggleTooltip.translate("boiler.passive"):heat==0?GoggleTooltip.translate("boiler.idle"):heat==18?GoggleTooltip.translate("boiler.max_lvl"):GoggleTooltip.translate("boiler.lvl",String.valueOf(heat));}
    private MutableComponent bars(int count,ChatFormatting color){return Component.literal("|".repeat(Math.clamp(count,0,18))).withStyle(color);}
    private MutableComponent component(String key,int level,boolean goggles,boolean blocks,ChatFormatting... styles){
        MutableComponent graph=blocks?Component.literal("\u2588".repeat(Math.max(0,minValue))+"\u2592".repeat(Math.max(0,level-minValue))+"\u2591".repeat(Math.max(0,maxValue-level)))
            :Component.empty().append(bars(minValue-1,ChatFormatting.DARK_GREEN)).append(bars(minValue>0?1:0,ChatFormatting.GREEN)).append(bars(level-minValue,ChatFormatting.DARK_GREEN)).append(bars(maxValue-level,ChatFormatting.DARK_RED)).append(bars(Math.min(18-maxValue,(maxValue/5+1)*5-maxValue),ChatFormatting.DARK_GRAY));
        if(!goggles)return graph;return GoggleTooltip.translate("boiler."+key).withStyle(styles.length>0?styles[0]:ChatFormatting.GRAY).append(GoggleTooltip.translate("boiler."+key+"_dots").withStyle(styles.length>1?styles[1]:ChatFormatting.DARK_GRAY)).append(graph);
    }
    public MutableComponent getSizeComponent(boolean goggles,boolean blocks,ChatFormatting... styles){return component("size",maxHeatForSize,goggles,blocks,styles);}
    public MutableComponent getWaterComponent(boolean goggles,boolean blocks,ChatFormatting... styles){return component("water",maxHeatForWater,goggles,blocks,styles);}
    public MutableComponent getHeatComponent(boolean goggles,boolean blocks,ChatFormatting... styles){return component("heat",passiveHeat?1:activeHeat,goggles,blocks,styles);}
    public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking,int size){
        if(!isActive())return false;calcMinMaxForSize(size);
        tooltip.add(GoggleTooltip.translate("boiler.status",getHeatLevelTextComponent().withStyle(ChatFormatting.GREEN)));
        GoggleTooltip.line(tooltip,getSizeComponent(true,false),1);GoggleTooltip.line(tooltip,getWaterComponent(true,false),1);GoggleTooltip.line(tooltip,getHeatComponent(true,false),1);
        if(attachedEngines==0)return true;tooltip.add(Component.empty());
        if(maxHeatForSize>0&&maxHeatForWater==0&&(passiveHeat?1:activeHeat)>0){
            tooltip.add(GoggleTooltip.translate("boiler.water_input_rate").withStyle(ChatFormatting.GRAY));
            GoggleTooltip.line(tooltip,GoggleTooltip.units(waterSupply,"millibuckets").withStyle(ChatFormatting.BLUE).append(Component.literal(" / ").withStyle(ChatFormatting.GRAY)).append(GoggleTooltip.translate("boiler.per_tick",GoggleTooltip.units(10,"millibuckets")).withStyle(ChatFormatting.DARK_GRAY)),1);return true;
        }
        double capacity=getEngineEfficiency(size)*16*Math.max(getActualHeat(size),attachedEngines)*com.simibubi.create.api.stress.BlockStressValues.getCapacity(AllBlocks.STEAM_ENGINE);
        tooltip.add(GoggleTooltip.translate("tooltip.capacityProvided").withStyle(ChatFormatting.GRAY));
        var engines=attachedEngines==1?GoggleTooltip.translate("boiler.via_one_engine"):GoggleTooltip.translate("boiler.via_engines",attachedEngines);
        GoggleTooltip.line(tooltip,GoggleTooltip.units(capacity,"stress").withStyle(ChatFormatting.AQUA).append(" ").append(engines.withStyle(ChatFormatting.DARK_GRAY)),1);return true;
    }
    private final BoilerFluidHandler storage=new BoilerFluidHandler();
    private final Map<Direction,SoundPool> soundPools=new EnumMap<>(Direction.class);
    public void queueSoundOnSide(BlockPos pos,Direction side){soundPools.computeIfAbsent(side,ignored->new SoundPool(4,2,(level,at)->{
        float volume=3f/Math.max(2,attachedEngines/6),pitch=1.18f-level.getRandom().nextFloat()*0.25f;
        level.playLocalSound(at.getX(),at.getY(),at.getZ(),net.minecraft.sounds.SoundEvents.CANDLE_EXTINGUISH,net.minecraft.sounds.SoundSource.BLOCKS,volume,pitch,false);
        level.playLocalSound(at.getX(),at.getY(),at.getZ(),com.simibubi.create.AllSoundEvents.STEAM,net.minecraft.sounds.SoundSource.BLOCKS,volume/16,0.8f,false);
    })).queueAt(pos);}
    public boolean isActive(){return attachedEngines>0||attachedWhistles>0;}
    public BoilerFluidHandler createHandler(){return storage;}
    public int getMaxHeatLevelForBoilerSize(int size){return Math.min(18,size/4);}
    public int getMaxHeatLevelForWaterSupply(){return Math.min(18,(int)Math.ceil(waterSupply)/WATER_PER_LEVEL);}
    public int getTheoreticalHeatLevel(){return activeHeat;}
    public int getActualHeat(int size){return Math.min(activeHeat,Math.min(getMaxHeatLevelForBoilerSize(size),getMaxHeatLevelForWaterSupply()));}
    public boolean isPassive(int size){return passiveHeat&&getMaxHeatLevelForBoilerSize(size)>0&&getMaxHeatLevelForWaterSupply()>0;}
    public float getEngineEfficiency(int size){
        if(attachedEngines==0)return 0;
        if(isPassive(size))return 0.125f/attachedEngines;
        if(activeHeat==0)return 0;
        int heat=getActualHeat(size);return attachedEngines<=heat?1:(float)heat/attachedEngines;
    }
    public float getGauge(float partial){return previousGauge+(gauge-previousGauge)*partial;}
    public void tick(FluidTankBlockEntity controller){
        if(!isActive())return;
        if(controller.getLevel().isClientSide()){
            soundPools.values().forEach(pool->pool.play(controller.getLevel()));
            previousGauge=gauge;int size=controller.getTotalTankSize(),max=getMaxHeatLevelForBoilerSize(size);
            float target=isPassive(size)?0.125f:max==0?0:(float)getActualHeat(size)/max;gauge+=(target-gauge)*0.125f;return;
        }
        if(needsHeatLevelUpdate&&updateTemperature(controller))controller.sync();
        if(--ticksUntilNextSample>0)return;ticksUntilNextSample=SAMPLE_RATE;
        supplyOverTime[currentIndex]=gatheredSupply/(81f*SAMPLE_RATE);waterSupply=Math.max(waterSupply,supplyOverTime[currentIndex]);
        currentIndex=(currentIndex+1)%supplyOverTime.length;gatheredSupply=0;
        if(currentIndex==0){waterSupply=0;for(float sample:supplyOverTime)waterSupply=Math.max(waterSupply,sample);}
        if(controller instanceof CreativeFluidTankBlockEntity)waterSupply=200;
        if(getActualHeat(controller.getTotalTankSize())==18)controller.sourceAdvancements.award("steam_engine_maxed");
        controller.setChanged();controller.sync();
    }
    public boolean evaluate(FluidTankBlockEntity controller){
        int previous=attachedEngines,previousWhistles=attachedWhistles;attachedEngines=attachedWhistles=0;var level=controller.getLevel();var start=controller.getBlockPos();
        for(int y=0;y<controller.getHeight();y++)for(int x=0;x<controller.getWidth();x++)for(int z=0;z<controller.getWidth();z++){
            var pos=start.offset(x,y,z);
            if(!level.isLoaded(pos)||!(LoadedLevelAccess.blockState(level,pos).getBlock() instanceof FluidTankBlock))continue;
            for(var side:Direction.values()){
                var neighbor=pos.relative(side);if(!level.isLoaded(neighbor))continue;var state=LoadedLevelAccess.blockState(level,neighbor);
                if(state.is(AllBlocks.STEAM_ENGINE)&&SteamEngineBlock.getFacing(state)==side)attachedEngines++;
                if(state.is(AllBlocks.STEAM_WHISTLE)&&com.simibubi.create.content.decoration.steamWhistle.WhistleBlock.getAttachedDirection(state).getOpposite()==side)attachedWhistles++;
            }
        }
        needsHeatLevelUpdate=true;return previous!=attachedEngines||previousWhistles!=attachedWhistles;
    }
    public void checkPipeOrganAdvancement(FluidTankBlockEntity controller){
        if(controller.sourceAdvancements.uuid()==null||controller.getLevel()==null||controller.getLevel().isClientSide()||!controller.structureLoaded())return;
        var level=controller.getLevel();var start=controller.getBlockPos();var pitches=new HashSet<Integer>();
        for(int y=0;y<controller.getHeight();y++)for(int x=0;x<controller.getWidth();x++)for(int z=0;z<controller.getWidth();z++){
            var pos=start.offset(x,y,z);if(!(LoadedLevelAccess.blockState(level,pos).getBlock() instanceof FluidTankBlock))continue;
            for(var side:Direction.values()){
                var attached=pos.relative(side);if(!level.isLoaded(attached))continue;var state=LoadedLevelAccess.blockState(level,attached);
                if(state.is(AllBlocks.STEAM_WHISTLE)&&com.simibubi.create.content.decoration.steamWhistle.WhistleBlock.getAttachedDirection(state).getOpposite()==side&&LoadedLevelAccess.blockEntity(level,attached) instanceof com.simibubi.create.content.decoration.steamWhistle.WhistleBlockEntity whistle)pitches.add(whistle.getPitchId());
            }
        }
        if(pitches.size()>=12)controller.sourceAdvancements.award("pipe_organ");
    }
    public boolean updateTemperature(FluidTankBlockEntity controller){
        needsHeatLevelUpdate=false;int previous=activeHeat;boolean wasPassive=passiveHeat;activeHeat=0;passiveHeat=false;
        var level=controller.getLevel();
        for(int x=0;x<controller.getWidth();x++)for(int z=0;z<controller.getWidth();z++){
            var pos=controller.getBlockPos().offset(x,-1,z);if(!level.isLoaded(pos))continue;
            float heat=BoilerHeater.findHeat(level,pos,LoadedLevelAccess.blockState(level,pos));
            if(heat==0)passiveHeat=true;else if(heat>0)activeHeat+=heat;
        }
        passiveHeat&=activeHeat==0;return previous!=activeHeat||wasPassive!=passiveHeat;
    }
    public void clear(){waterSupply=0;activeHeat=0;passiveHeat=false;attachedEngines=attachedWhistles=0;gatheredSupply=0;Arrays.fill(supplyOverTime,0);}
    public void save(ValueOutput out){out.putFloat("Supply",waterSupply);out.putInt("ActiveHeat",activeHeat);out.putBoolean("PassiveHeat",passiveHeat);out.putInt("Engines",attachedEngines);out.putInt("Whistles",attachedWhistles);out.putBoolean("Update",needsHeatLevelUpdate);}
    public void load(ValueInput in,int size){
        float supply=in.getFloatOr("Supply",0);waterSupply=Float.isFinite(supply)?Math.max(0,supply):0;
        activeHeat=Math.max(0,in.getIntOr("ActiveHeat",0));passiveHeat=in.getBooleanOr("PassiveHeat",false);
        attachedEngines=Math.max(0,in.getIntOr("Engines",0));attachedWhistles=Math.max(0,in.getIntOr("Whistles",0));needsHeatLevelUpdate=in.getBooleanOr("Update",true);
        Arrays.fill(supplyOverTime,waterSupply);int max=getMaxHeatLevelForBoilerSize(size);gauge=previousGauge=isPassive(size)?0.125f:max==0?0:(float)getActualHeat(size)/max;
    }
    /** Sink storage: only committed water input contributes to measured supply; water is never extractable. */
    public final class BoilerFluidHandler extends SnapshotParticipant<Long> implements Storage<FluidVariant> {
        @Override protected Long createSnapshot(){return gatheredSupply;}
        @Override protected void readSnapshot(Long amount){gatheredSupply=amount;}
        @Override public long insert(FluidVariant fluid,long max,TransactionContext tx){
            StoragePreconditions.notBlankNotNegative(fluid,max);if(max==0||!fluid.getFluid().is(net.minecraft.tags.FluidTags.WATER))return 0;
            long moved=Math.min(max,Long.MAX_VALUE-gatheredSupply);if(moved>0){updateSnapshots(tx);gatheredSupply+=moved;}return moved;
        }
        @Override public long extract(FluidVariant fluid,long max,TransactionContext tx){StoragePreconditions.notBlankNotNegative(fluid,max);return 0;}
        @Override public Iterator<StorageView<FluidVariant>> iterator(){return Collections.emptyIterator();}
    }
}
