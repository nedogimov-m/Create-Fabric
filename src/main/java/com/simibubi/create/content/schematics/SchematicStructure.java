package com.simibubi.create.content.schematics;

import com.simibubi.create.content.contraptions.StructureTransform;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/** A sparse native structure snapshot. Creating it never places blocks, admits entities or acquires chunks. */
public final class SchematicStructure implements BlockGetter {
    private final Level original;
    private final BlockPos anchor;
    private final BoundingBox bounds;
    private final Map<BlockPos,BlockState> states;
    private final Map<BlockPos,BlockEntity> blockEntities;
    private final List<Entity> entities;

    private SchematicStructure(Level original,BlockPos anchor,BoundingBox bounds,Map<BlockPos,BlockState> states,Map<BlockPos,BlockEntity> blockEntities,List<Entity> entities){
        this.original=original;this.anchor=anchor.immutable();this.bounds=bounds;this.states=Collections.unmodifiableMap(states);this.blockEntities=Collections.unmodifiableMap(blockEntities);this.entities=List.copyOf(entities);
    }

    public static SchematicStructure load(Level level,StructureTemplate template,BlockPos anchor,StructurePlaceSettings settings,boolean processNbt)throws IOException{
        var tag=template.save(new CompoundTag());var size=SchematicFiles.bounds(tag);
        var bounds=template.getBoundingBox(settings,BlockPos.ZERO);
        // A long cursor represents the whole transformed volume; memory depends on the actual stored entries.
        volume(bounds);
        checkedOffset(anchor,new BlockPos(bounds.minX(),bounds.minY(),bounds.minZ()));checkedOffset(anchor,new BlockPos(bounds.maxX(),bounds.maxY(),bounds.maxZ()));
        var palettes=tag.getListOrEmpty("palettes");ListTag palette;
        if(palettes.isEmpty())palette=tag.getListOrEmpty("palette");
        else {var chosen=palettes.get(settings.getRandom(anchor).nextInt(palettes.size()));if(!(chosen instanceof ListTag list))throw new IOException("Invalid alternate schematic palette");palette=list;}
        var decoded=new ArrayList<BlockState>(palette.size());
        for(var entry:palette){if(!(entry instanceof CompoundTag block))throw new IOException("Invalid schematic palette entry");decoded.add(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),block));}
        var states=new LinkedHashMap<BlockPos,BlockState>();var blockEntities=new LinkedHashMap<BlockPos,BlockEntity>();
        var transform=new StructureTransform(settings.getRotationPivot(),Direction.Axis.Y,settings.getRotation(),settings.getMirror());
        for(var entry:tag.getListOrEmpty("blocks")){
            if(!(entry instanceof CompoundTag block))throw new IOException("Invalid schematic block entry");var local=intPos(block.getListOrEmpty("pos"));
            if(local.getX()<0||local.getY()<0||local.getZ()<0||local.getX()>=size.getX()||local.getY()>=size.getY()||local.getZ()>=size.getZ())throw new IOException("Schematic block lies outside its declared source bounds");
            int index=block.getIntOr("state",-1);if(index<0||index>=decoded.size())throw new IOException("Schematic block references an absent palette state");
            var relative=StructureTemplate.calculateRelativePosition(settings,local);var pos=checkedOffset(anchor,relative);
            if(states.containsKey(pos))throw new IOException("Duplicate native schematic block position");
            var state=decoded.get(index).mirror(settings.getMirror()).rotate(settings.getRotation());states.put(pos,state);
            if(!(state.getBlock() instanceof EntityBlock factory))continue;
            var entity=factory.newBlockEntity(pos,state);if(entity==null)continue;
            var raw=block.getCompound("nbt").orElse(null);
            // Keep detached source metadata (belt length/casings, curve geometry) available to the
            // printer. Safe placement/requirement data is produced separately, as in the source.
            var processed=processNbt?SchematicNbt.filter(raw):raw;
            if(processed!=null)entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),processed.copy()));
            // Original custom BE transformers run after native state and position transforms.
            transform.apply(entity);blockEntities.put(pos,entity);
        }
        var entities=new ArrayList<Entity>();
        if(!settings.isIgnoreEntities())for(var entry:tag.getListOrEmpty("entities")){
            if(!(entry instanceof CompoundTag encoded))throw new IOException("Invalid schematic entity entry");
            var data=encoded.getCompound("nbt").orElseThrow(()->new IOException("Schematic entity lacks native NBT")).copy();
            var id=Identifier.tryParse(data.getStringOr("id",""));if(id==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(id))throw new IOException("Schematic requires an unavailable entity registry entry");
            if(processNbt&&BuiltInRegistries.ENTITY_TYPE.getValue(id).onlyOpCanSetNbt())continue;
            // Vanilla structure creation is deliberately nonrecursive; nested passengers are not free printable entities.
            data.remove("UUID");data.remove("Passengers");
            var pos=StructureTemplate.transform(doublePos(encoded.getListOrEmpty("pos")),settings.getMirror(),settings.getRotation(),settings.getRotationPivot()).add(Vec3.atLowerCornerOf(anchor));
            var hanging=checkedOffset(anchor,StructureTemplate.calculateRelativePosition(settings,intPos(encoded.getListOrEmpty("blockPos"))));
            var position=new ListTag();position.add(DoubleTag.valueOf(pos.x));position.add(DoubleTag.valueOf(pos.y));position.add(DoubleTag.valueOf(pos.z));data.put("Pos",position);
            if(data.contains("block_pos"))data.store("block_pos",BlockPos.CODEC,hanging);
            var entity=EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data),level,new EntitySpawnRequest(EntitySpawnReason.STRUCTURE,false)).orElseThrow(()->new IOException("Native schematic entity could not be decoded"));
            float yaw=entity.rotate(settings.getRotation())+entity.mirror(settings.getMirror())-entity.getYRot();entity.snapTo(pos,yaw,entity.getXRot());entity.setYBodyRot(yaw);entity.setYHeadRot(yaw);
            if(processNbt){
                if(entity instanceof ItemFrame frame)frame.setItem(SchematicNbt.sanitizeItem(frame.getItem()),false);
                if(entity instanceof ArmorStand stand)for(var slot:EquipmentSlot.values())stand.setItemSlot(slot,SchematicNbt.sanitizeItem(stand.getItemBySlot(slot)));
                var customName=entity.getCustomName();if(customName!=null&&SchematicNbt.hasClick(customName))entity.setCustomName(null);
            }
            entities.add(entity);
        }
        return new SchematicStructure(level,anchor,bounds,states,blockEntities,entities);
    }

    private static BlockPos intPos(ListTag list)throws IOException{if(list.size()!=3)throw new IOException("Native schematic position must have three integers");return new BlockPos(list.getInt(0).orElseThrow(()->new IOException("Invalid X coordinate")),list.getInt(1).orElseThrow(()->new IOException("Invalid Y coordinate")),list.getInt(2).orElseThrow(()->new IOException("Invalid Z coordinate")));}
    private static Vec3 doublePos(ListTag list)throws IOException{if(list.size()!=3)throw new IOException("Native schematic entity position must have three coordinates");double x=list.getDouble(0).orElse(Double.NaN),y=list.getDouble(1).orElse(Double.NaN),z=list.getDouble(2).orElse(Double.NaN);if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IOException("Nonfinite native schematic entity position");return new Vec3(x,y,z);}
    public static long volume(BoundingBox box)throws IOException{try{return Math.multiplyExact(Math.multiplyExact(1L+box.maxX()-box.minX(),1L+box.maxY()-box.minY()),1L+box.maxZ()-box.minZ());}catch(ArithmeticException overflow){throw new IOException("Native schematic bounds overflow the print cursor",overflow);}}
    private static BlockPos checkedOffset(BlockPos anchor,BlockPos relative)throws IOException{try{return new BlockPos(Math.addExact(anchor.getX(),relative.getX()),Math.addExact(anchor.getY(),relative.getY()),Math.addExact(anchor.getZ(),relative.getZ()));}catch(ArithmeticException overflow){throw new IOException("Native schematic anchor overflows block coordinates",overflow);}}
    public BlockPos anchor(){return anchor;}
    public BoundingBox bounds(){return new BoundingBox(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ());}
    public Collection<BlockPos> positions(){return states.keySet();}
    public List<Entity> entities(){return entities;}
    public Level original(){return original;}
    public BlockEntity safeBlockEntity(BlockPos pos){var state=getBlockState(pos);if(!(state.getBlock() instanceof EntityBlock factory))return null;var entity=factory.newBlockEntity(pos,state);if(entity==null)return null;var safe=SchematicNbt.prepare(original,state,getBlockEntity(pos));if(safe!=null)entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,original.registryAccess(),safe));return entity;}
    @Override public BlockEntity getBlockEntity(BlockPos pos){return blockEntities.get(pos);}
    @Override public BlockState getBlockState(BlockPos pos){return states.getOrDefault(pos,Blocks.AIR.defaultBlockState());}
    @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
    @Override public int getHeight(){return original.getHeight();}
    @Override public int getMinY(){return original.getMinY();}
}
