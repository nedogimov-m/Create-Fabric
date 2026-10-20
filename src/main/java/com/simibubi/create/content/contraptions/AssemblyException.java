package com.simibubi.create.content.contraptions;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.block.state.BlockState;

/** Original assembly error payload and optional highlighted world position. */
public class AssemblyException extends Exception {
    private static final long serialVersionUID=1L;
    public final Component component;
    private BlockPos position;
    private boolean retryable;
    public AssemblyException(Component component){super(component.getString());this.component=component;}
    public AssemblyException(String key,Object...args){this(Component.translatable("create.gui.assembly.exception."+key,args));}
    private static AssemblyException at(String key,BlockPos pos,Object...args){var result=new AssemblyException(key,args);result.position=pos.immutable();return result;}
    public static AssemblyException unmovableBlock(BlockPos pos,BlockState state){return at("unmovableBlock",pos,pos.getX(),pos.getY(),pos.getZ(),state.getBlock().getName());}
    public static AssemblyException unloadedChunk(BlockPos pos){return at("chunkNotLoaded",pos,pos.getX(),pos.getY(),pos.getZ());}
    public static AssemblyException entitiesNotReady(BlockPos pos){var result=unloadedChunk(pos);result.retryable=true;return result;}
    public static AssemblyException structureChanged(BlockPos pos){var result=at("structureChanged",pos,pos.getX(),pos.getY(),pos.getZ());result.retryable=true;return result;}
    public boolean canRetry(){return retryable;}
    public static AssemblyException structureTooLarge(){return new AssemblyException("structureTooLarge",ContraptionSettings.maxBlocksMoved);}
    public static AssemblyException tooManyPistonPoles(){return new AssemblyException("tooManyPistonPoles",ContraptionSettings.maxPistonPoles);}
    public static AssemblyException noPistonPoles(){return new AssemblyException("noPistonPoles");}
    public static AssemblyException notEnoughSails(int sails){return new AssemblyException("not_enough_sails",sails,ContraptionSettings.minimumWindmillSails);}
    public boolean hasPosition(){return position!=null;}
    public BlockPos getPosition(){return position;}
    public static void write(CompoundTag compound,HolderLookup.Provider registries,AssemblyException exception){
        if(exception==null)return;var data=new CompoundTag();data.putString("Component",ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE),exception.component).getOrThrow().toString());if(exception.position!=null)data.putLong("Position",exception.position.asLong());if(exception.retryable)data.putBoolean("Retryable",true);compound.put("LastException",data);
    }
    public static AssemblyException read(CompoundTag compound,HolderLookup.Provider registries){
        if(!compound.contains("LastException"))return null;var data=compound.getCompoundOrEmpty("LastException");var component=ComponentSerialization.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(data.getStringOr("Component","\"\""))).getOrThrow();var result=new AssemblyException(component);if(data.contains("Position"))result.position=BlockPos.of(data.getLongOr("Position",0));result.retryable=data.getBooleanOr("Retryable",false);return result;
    }
}
