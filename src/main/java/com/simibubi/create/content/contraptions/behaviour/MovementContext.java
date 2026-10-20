package com.simibubi.create.content.contraptions.behaviour;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Persistent state of one mounted actor; positions and motion use world coordinates. */
public class MovementContext {
    public @Nullable Vec3 position;
    public Vec3 motion=Vec3.ZERO,relativeMotion=Vec3.ZERO;
    public UnaryOperator<Vec3> rotation=vector->vector;
    public Level world;
    public BlockState state;
    public BlockPos localPos;
    public CompoundTag blockEntityData;
    public boolean stall,disabled,firstMovement=true;
    public CompoundTag data=new CompoundTag();
    public Contraption contraption;
    public Object temporaryData;
    private FilterItemStack filter;
    public MovementContext(Level world,StructureBlockInfo info,Contraption contraption){this.world=world;this.state=info.state();this.localPos=info.pos();this.blockEntityData=info.nbt()==null?new CompoundTag():info.nbt();this.contraption=contraption;}
    public float getAnimationSpeed(){
        if(disabled)return 0;if(world.isClientSide()&&contraption.stalled)return 700;
        double speed=-motion.length();if(Math.abs(speed)<1/512f)return 0;
        return ((int)(speed*1000+100*Math.signum(speed))/100)*100;
    }
    public static MovementContext readNBT(Level world,StructureBlockInfo info,CompoundTag tag,Contraption contraption){
        var result=new MovementContext(world,info,contraption);result.motion=readVector(tag,"Motion",Vec3.ZERO);result.relativeMotion=readVector(tag,"RelativeMotion",Vec3.ZERO);result.position=readVector(tag,"Position",null);
        result.stall=tag.getBooleanOr("Stall",false);result.firstMovement=tag.getBooleanOr("FirstMovement",true);result.data=tag.getCompoundOrEmpty("Data").copy();return result;
    }
    private static Vec3 readVector(CompoundTag tag,String key,Vec3 fallback){if(!tag.contains(key))return fallback;var result=tag.read(key,Vec3.CODEC).orElseThrow(()->new IllegalArgumentException("Invalid actor "+key));if(!Double.isFinite(result.x)||!Double.isFinite(result.y)||!Double.isFinite(result.z))throw new IllegalArgumentException("Non-finite actor "+key);return result;}
    public CompoundTag writeToNBT(CompoundTag tag){tag.store("Motion",Vec3.CODEC,motion);tag.store("RelativeMotion",Vec3.CODEC,relativeMotion);if(position!=null)tag.store("Position",Vec3.CODEC,position);tag.putBoolean("Stall",stall);tag.putBoolean("FirstMovement",firstMovement);tag.put("Data",data.copy());return tag;}
    public FilterItemStack getFilterFromBE(){if(filter==null){var input=net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,world.registryAccess(),blockEntityData);filter=FilterItemStack.of(input.read("Filter",ItemStack.CODEC).orElse(ItemStack.EMPTY));}return filter;}
    public @Nullable MountedItemStorage getItemStorage(){return contraption.getStorage().getAllItemStorages().get(localPos);}
    public @Nullable MountedFluidStorage getFluidStorage(){return contraption.getStorage().getFluids().storages.get(localPos);}
}
