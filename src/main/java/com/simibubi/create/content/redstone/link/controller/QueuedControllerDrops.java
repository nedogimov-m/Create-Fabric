package com.simibubi.create.content.redstone.link.controller;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.phys.Vec3;

/** A removed lectern cannot own a failed physical drop; transfer it to durable level SavedData. */
public final class QueuedControllerDrops extends SavedData {
    private record Entry(BlockPos pos,ItemStack stack){
        static final Codec<Entry> CODEC=RecordCodecBuilder.create(instance->instance.group(BlockPos.CODEC.fieldOf("Pos").forGetter(Entry::pos),ItemStack.CODEC.fieldOf("Stack").forGetter(Entry::stack)).apply(instance,Entry::new));
    }
    public static final Codec<QueuedControllerDrops> CODEC=Entry.CODEC.listOf().fieldOf("Pending").codec().xmap(QueuedControllerDrops::new,data->List.copyOf(data.entries));
    public static final SavedDataType<QueuedControllerDrops> TYPE=new SavedDataType<>(Create.asResource("controller_drops"),QueuedControllerDrops::new,CODEC,null);
    private final List<Entry> entries=new ArrayList<>();
    public QueuedControllerDrops(){}
    private QueuedControllerDrops(List<Entry> entries){this.entries.addAll(entries);}
    private static QueuedControllerDrops get(ServerLevel level){return level.getDataStorage().computeIfAbsent(TYPE);}
    public static void dropOrQueue(ServerLevel level,BlockPos pos,ItemStack stack){
        if(stack.isEmpty())return;
        if(spawn(level,pos,stack))return;
        var data=get(level);data.entries.add(new Entry(pos.immutable(),stack.copy()));data.setDirty();
    }
    private static boolean spawn(ServerLevel level,BlockPos pos,ItemStack stack){
        if(!LoadedEntityAccess.isReady(level,pos))return false;
        var center=Vec3.atCenterOf(pos);var item=new ItemEntity(level,center.x,center.y+.5,center.z,stack.copy());item.setDefaultPickUpDelay();
        return level.addFreshEntity(item);
    }
    public static void tick(ServerLevel level){var data=get(level);for(var iterator=data.entries.iterator();iterator.hasNext();){var entry=iterator.next();if(spawn(level,entry.pos,entry.stack)){iterator.remove();data.setDirty();}}}
}
