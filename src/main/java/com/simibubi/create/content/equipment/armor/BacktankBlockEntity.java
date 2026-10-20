package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.*;
import com.simibubi.create.content.equipment.EquipmentAdvancements;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.Objects;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

public class BacktankBlockEntity extends KineticBlockEntity implements Nameable {
    public int airLevel,airLevelTimer;
    private Component defaultName,customName;
    private java.util.UUID advancementOwner;
    private int capacityEnchantLevel;
    private DataComponentPatch componentPatch=DataComponentPatch.EMPTY;
    public BacktankBlockEntity(BlockPos pos,BlockState state){this(AllBlockEntityTypes.BACKTANK,pos,state);}
    public BacktankBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);defaultName=getDefaultName(state);}
    public static Component getDefaultName(BlockState state){return Component.translatable(state.is(AllBlocks.NETHERITE_BACKTANK)?"item.create.netherite_backtank":"item.create.copper_backtank");}
    @Override public void onSpeedChanged(float previousSpeed){super.onSpeedChanged(previousSpeed);if(getSpeed()!=0&&level!=null&&!level.isClientSide()&&advancementOwner!=null){var player=level.getPlayerByUUID(advancementOwner);if(player!=null)EquipmentAdvancements.award(player,"backtank");}}
    public static void tick(Level level,BlockPos pos,BlockState state,BacktankBlockEntity tank){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, tank)) {
            if (!createTick.shouldTick()) return;
KineticBlockEntity.tick(level,pos,state,tank);tank.tick();
        }
    }
    public void tick(){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, worldPosition, getBlockState(), this)) {
            if (!createTick.shouldTick()) return;

        if(level==null||getSpeed()==0||getBlockState().getValue(BlockStateProperties.WATERLOGGED))return;
        if(airLevelTimer>0){airLevelTimer--;if(!level.isClientSide())setChanged();return;}
        int max=BacktankUtil.maxAir(capacityEnchantLevel);
        if(level.isClientSide()){if(airLevel<max){var center=Vec3.atCenterOf(worldPosition);var at=center.add((level.getRandom().nextDouble()-.5)*1.3,(level.getRandom().nextDouble()-.5)*1.3,(level.getRandom().nextDouble()-.5)*1.3);var motion=center.subtract(at);level.addParticle(new com.simibubi.create.foundation.particle.AirParticleData(1,.05f),at.x,at.y,at.z,motion.x,motion.y,motion.z);}return;}
        if(airLevel>=max)return;int previous=getComparatorOutput();float speed=Math.abs(getSpeed());int increment=Math.clamp(((int)speed-100)/20,1,5);
        airLevel=(int)Math.min(max,(long)airLevel+increment);airLevelTimer=Math.clamp((int)(128f-speed/5f)-108,0,20);setChanged();
        if(previous!=getComparatorOutput())level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());
        if(airLevel==max)sync();
    
        }
    }
    public int getComparatorOutput(){return airLevel<=0?0:1+(int)Math.floor(14*Math.min(1,airLevel/(double)BacktankUtil.maxAir(capacityEnchantLevel)));}
    @Override protected void write(ValueOutput output, boolean clientPacket){super.write(output, clientPacket);output.putInt("Air",airLevel);output.putInt("Timer",airLevelTimer);output.putInt("CapacityEnchantment",capacityEnchantLevel);if(customName!=null)output.store("CustomName",ComponentSerialization.CODEC,customName);output.store("Components",DataComponentPatch.CODEC,componentPatch);}
    @Override protected void read(ValueInput input, boolean clientPacket){int previous=airLevel;super.read(input, clientPacket);capacityEnchantLevel=Math.max(0,input.getIntOr("CapacityEnchantment",0));airLevel=input.getIntOr("Air",0);if(airLevel<0)throw new IllegalArgumentException("Negative saved backtank air");airLevelTimer=Math.clamp(input.getIntOr("Timer",0),0,20);customName=input.read("CustomName",ComponentSerialization.CODEC).orElse(null);componentPatch=input.read("Components",DataComponentPatch.CODEC).orElse(DataComponentPatch.EMPTY);if(level!=null&&level.isClientSide()&&previous>0&&previous!=airLevel&&airLevel==BacktankUtil.maxAir(capacityEnchantLevel))playFilledEffect();}
    @Override protected void applyImplicitComponents(DataComponentGetter input){super.applyImplicitComponents(input);setAirLevel(input.getOrDefault(AllDataComponents.BACKTANK_AIR,0));}
    @Override protected void collectImplicitComponents(DataComponentMap.Builder components){super.collectImplicitComponents(components);components.set(AllDataComponents.BACKTANK_AIR,airLevel);}
    protected void playFilledEffect(){if(level==null)return;level.playLocalSound(worldPosition,AllSoundEvents.CONFIRM,SoundSource.BLOCKS,.4f,1,false);var center=Vec3.atCenterOf(worldPosition);for(int angle=0;angle<360;angle+=10){double x=Math.cos(Math.toRadians(angle))*.25,z=Math.sin(Math.toRadians(angle))*.25;level.addParticle(ParticleTypes.SPIT,center.x+x,center.y,center.z+z,x,.1,z);}}
    @Override public Component getName(){return customName==null?defaultName:customName;}
    public void setPlacedBy(net.minecraft.server.level.ServerPlayer player){if(player instanceof net.fabricmc.fabric.api.entity.FakePlayer)return;advancementOwner=player.getUUID();setChanged();}
    public void notifyUpdate(){setChanged();sync();}
    public int getAirLevel(){return airLevel;}
    public void setAirLevel(int value){if(value<0)throw new IllegalArgumentException("Negative backtank air");airLevel=value;setChanged();sync();}
    public void setCustomName(Component name){customName=name;setChanged();}
    public void setCapacityEnchantLevel(int level){capacityEnchantLevel=Math.max(0,level);setChanged();}
    public int getCapacityEnchantLevel(){return capacityEnchantLevel;}
    public void setComponentPatch(DataComponentPatch patch){componentPatch=Objects.requireNonNull(patch);setChanged();}
    public DataComponentPatch getComponentPatch(){return componentPatch;}
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("backtank");
    }
    private final com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage canonicalAdvancementOwner=new com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage() {
        @Override public java.util.UUID getPlayerId(){return advancementOwner;}
        @Override public void setPlayerId(java.util.UUID id){advancementOwner=id;}
    };
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return canonicalAdvancementOwner;}
}
