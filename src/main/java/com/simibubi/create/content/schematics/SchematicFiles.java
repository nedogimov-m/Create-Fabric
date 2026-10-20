package com.simibubi.create.content.schematics;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Actual compressed native structure files, bounded decoding and atomic file completion. */
public final class SchematicFiles {
    public static final long MAX_DECODED_BYTES=0x20000000L;
    public static Path localRoot(){return FabricLoader.getInstance().getGameDir().resolve("schematics").toAbsolutePath().normalize();}
    public static Path uploadedRoot(){return localRoot().resolve("uploaded");}
    public static String fileName(String name){
        Objects.requireNonNull(name);
        if(name.isBlank()||name.length()>128||!name.endsWith(".nbt")||name.equals(".nbt")||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf('\0')>=0||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid schematic file name");
        if(!Path.of(name).getFileName().toString().equals(name))throw new IllegalArgumentException("Schematic must be a single file name");
        return name;
    }
    public static String ownerName(String owner){if(owner==null||!owner.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid schematic owner");return owner;}
    public static Path contained(Path directory,String name)throws IOException{
        var root=directory.toAbsolutePath().normalize();var path=root.resolve(fileName(name)).normalize();
        if(!path.getParent().equals(root))throw new IOException("Schematic path left its owner directory");
        for(var p=root;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Schematic owner directory is a symbolic link");
        if(Files.isSymbolicLink(path))throw new IOException("Schematic file is a symbolic link");
        return path;
    }
    public static BlockPos bounds(CompoundTag tag)throws IOException{
        var values=tag.getListOrEmpty("size");if(values.size()!=3)throw new IOException("Native schematic must have three size coordinates");
        int x=values.getInt(0).orElse(-1),y=values.getInt(1).orElse(-1),z=values.getInt(2).orElse(-1);
        if(x<1||y<1||z<1)throw new IOException("Native schematic bounds must be positive");
        try{Math.multiplyExact(Math.multiplyExact((long)x,y),z);}catch(ArithmeticException overflow){throw new IOException("Native schematic bounds overflow the streaming print cursor",overflow);}
        return new BlockPos(x,y,z);
    }
    public static CompoundTag readTag(Path path)throws IOException{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Schematic is not a regular completed file");
        try(var stream=Files.newInputStream(path)){var tag=NbtIo.readCompressed(stream,NbtAccounter.create(MAX_DECODED_BYTES));bounds(tag);return tag;}catch(RuntimeException malformed){throw new IOException("Malformed or over-budget native schematic data",malformed);}
    }
    public static StructureTemplate decode(Level level,CompoundTag tag)throws IOException{
        try{
            var fixed=net.minecraft.util.datafix.DataFixTypes.STRUCTURE.updateToCurrentVersion(net.minecraft.util.datafix.DataFixers.getDataFixer(),tag.copy(),NbtUtils.getDataVersion(tag,500));
            var size=bounds(fixed);var palette=fixed.getListOrEmpty("palette");var alternatives=fixed.getListOrEmpty("palettes");
            int states=palette.size();validatePalette(palette);
            for(var value:alternatives){if(!(value instanceof ListTag alternate))throw new IOException("Malformed native alternate structure palette");validatePalette(alternate);if(states==0)states=alternate.size();else if(states!=alternate.size())throw new IOException("Native alternate schematic palettes disagree on state indices");}
            var positions=new HashSet<BlockPos>();for(var value:fixed.getListOrEmpty("blocks")){
                if(!(value instanceof CompoundTag block))throw new IOException("Native schematic block entry is not a compound");var coordinates=block.getListOrEmpty("pos");if(coordinates.size()!=3)throw new IOException("Native schematic block requires three coordinates");
                int x=coordinates.getInt(0).orElse(-1),y=coordinates.getInt(1).orElse(-1),z=coordinates.getInt(2).orElse(-1),state=block.getIntOr("state",-1);
                if(x<0||y<0||z<0||x>=size.getX()||y>=size.getY()||z>=size.getZ()||state<0||state>=states||!positions.add(new BlockPos(x,y,z)))throw new IOException("Invalid native schematic block position or palette index");
            }
            var template=new StructureTemplate();template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),fixed);return template;
        }catch(RuntimeException malformed){throw new IOException("Malformed native structure palette/block data",malformed);}
    }
    private static void validatePalette(ListTag palette)throws IOException{for(var value:palette){if(!(value instanceof CompoundTag entry))throw new IOException("Native schematic palette entry is not a compound");var id=net.minecraft.resources.Identifier.tryParse(entry.getStringOr("id",""));if(id==null||!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id))throw new IOException("Native schematic requires an unavailable block registry entry");}}
    public static StructureTemplate read(Level level,Path path)throws IOException{return decode(level,readTag(path));}
    public static Path writeAtomic(Path directory,String name,CompoundTag tag,boolean replace,long maximumBytes)throws IOException{
        bounds(tag);var target=contained(directory,name);Files.createDirectories(target.getParent());
        var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(tag,bytes);if(bytes.size()>maximumBytes)throw new IOException("Compressed schematic exceeds the configured upload budget");
        var temporary=Files.createTempFile(target.getParent(),".create-schematic-",".pending");
        try{Files.write(temporary,bytes.toByteArray(),StandardOpenOption.TRUNCATE_EXISTING);moveCompleted(temporary,target,replace);}finally{Files.deleteIfExists(temporary);}return target;
    }
    public static void moveCompleted(Path temporary,Path target,boolean replace)throws IOException{
        if(Files.isSymbolicLink(target))throw new IOException("Completed schematic target is a symbolic link");
        var options=replace?new CopyOption[]{StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING}:new CopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        if(!replace&&Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(target.toString());
        try{Files.move(temporary,target,options);}catch(AtomicMoveNotSupportedException unsupported){if(replace)Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);else Files.move(temporary,target);}
    }
    public static List<String> list(Path directory)throws IOException{
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))return List.of();
        try(var paths=Files.list(directory)){return paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().endsWith(".nbt")).map(p->p.getFileName().toString()).sorted().toList();}
    }
    private SchematicFiles(){}
}
