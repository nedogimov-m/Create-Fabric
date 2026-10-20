package com.simibubi.create.content.schematics;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.*;
import org.jetbrains.annotations.Nullable;

/** Actual native structure export; a complete validated compressed file is published atomically. */
public final class SchematicExport {
    public record SchematicExportResult(Path file,Path dir,String fileName,boolean overwritten,BlockPos origin,BlockPos bounds){}
    public static CompoundTag capture(Level level,BlockPos first,BlockPos second)throws IOException{
        var box=BoundingBox.fromCorners(first,second);var origin=new BlockPos(box.minX(),box.minY(),box.minZ());var size=new BlockPos(box.getXSpan(),box.getYSpan(),box.getZSpan());
        var extent=new CompoundTag();extent.put("size",SchematicAndQuillItem.integers(size.getX(),size.getY(),size.getZ()));SchematicFiles.bounds(extent);
        if(!level.isInValidBounds(first)||!level.isInValidBounds(second))throw new IOException("Native schematic source lies outside valid world bounds");
        var glueQueries=new java.util.HashSet<Long>();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++){
            if(!LoadedEntityAccess.isReady(level,x,z))throw new IOException("Native schematic source terrain/entities are not fully ready");
            try{LoadedEntityAccess.requireGlueQueriesReady(level,new BlockPos(x<<4,origin.getY(),z<<4),glueQueries);}catch(com.simibubi.create.content.contraptions.AssemblyException unavailable){throw new IOException("Native schematic glue query has incomplete visible entity storage",unavailable);}
        }
        var template=new StructureTemplate();template.fillFromWorld(level,origin,size,true,List.of(Blocks.AIR));var tag=template.save(new CompoundTag());
        SchematicAndQuillItem.replaceStructureVoidWithAir(tag);SchematicAndQuillItem.clampGlueBoxes(level,new AABB(Vec3.atLowerCornerOf(origin),Vec3.atLowerCornerOf(origin.offset(size))),tag);return tag;
    }
    @Nullable public static SchematicExportResult saveSchematic(Path directory,String requested,boolean overwrite,Level level,BlockPos first,BlockPos second){
        try{
            String name=requested.isBlank()?"Schematic":requested;name=SchematicFiles.fileName(name.endsWith(".nbt")?name:name+".nbt");
            if(!overwrite){String stem=name.substring(0,name.length()-4);for(int index=1;Files.exists(SchematicFiles.contained(directory,name),LinkOption.NOFOLLOW_LINKS);index++)name=SchematicFiles.fileName(stem+"_"+index+".nbt");}
            var tag=capture(level,first,second);var target=SchematicFiles.contained(directory,name);boolean existed=Files.exists(target,LinkOption.NOFOLLOW_LINKS);var file=SchematicFiles.writeAtomic(directory,name,tag,overwrite,Long.MAX_VALUE);
            var box=BoundingBox.fromCorners(first,second);return new SchematicExportResult(file,directory,name,existed,new BlockPos(box.minX(),box.minY(),box.minZ()),new BlockPos(box.getXSpan(),box.getYSpan(),box.getZSpan()));
        }catch(IOException failure){Create.LOGGER.error("Unable to save actual native schematic {}",requested,failure);return null;}
    }
    private SchematicExport(){}
}
