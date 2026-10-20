package com.simibubi.create.content.schematics;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.*;

/** Source quill/export helpers using actual current native entity serialization. */
public final class SchematicAndQuillItem extends Item {
    public SchematicAndQuillItem(Properties properties){super(properties);}
    public static void replaceStructureVoidWithAir(CompoundTag tag){
        replacePalette(tag.getListOrEmpty("palette"));for(var value:tag.getListOrEmpty("palettes"))if(value instanceof ListTag palette)replacePalette(palette);
    }
    private static void replacePalette(ListTag palette){for(var value:palette)if(value instanceof CompoundTag entry&&entry.getStringOr("id","").equals("minecraft:structure_void"))entry.putString("id","minecraft:air");}
    public static void clampGlueBoxes(Level level,AABB bounds,CompoundTag tag){
        var entities=tag.getListOrEmpty("entities").copy();entities.removeIf(value->value instanceof CompoundTag entity&&entity.getCompound("nbt").map(nbt->nbt.getStringOr("id","").equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(AllEntityTypes.SUPER_GLUE).toString())).orElse(false));
        for(var glue:SuperGlueEntity.collectCropped(level,bounds)){
            var relative=glue.position().subtract(bounds.minX,bounds.minY,bounds.minZ);var problems=new ProblemReporter.Collector(glue.problemPath());var output=TagValueOutput.createWithContext(problems,level.registryAccess());
            if(!glue.save(output)||!problems.isEmpty())throw new IllegalStateException("Native cropped glue serialization failed: "+problems.getReport());
            var entry=new CompoundTag();entry.put("pos",doubles(relative.x,relative.y,relative.z));var pos=BlockPos.containing(relative);entry.put("blockPos",integers(pos.getX(),pos.getY(),pos.getZ()));entry.put("nbt",output.buildResult());entities.add(entry);
        }
        tag.put("entities",entities);
    }
    public static ListTag integers(int...values){var list=new ListTag();for(var value:values)list.add(IntTag.valueOf(value));return list;}
    public static ListTag doubles(double...values){var list=new ListTag();for(var value:values)list.add(DoubleTag.valueOf(value));return list;}
}
