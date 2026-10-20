package com.simibubi.create.content.schematics;

import com.simibubi.create.*;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.schematics.cannon.MaterialChecklist;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.IMergeableBE;
import com.simibubi.create.foundation.utility.BlockHelper;
import java.io.*;
import java.security.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;

/** Source three-stage printer over a sparse native snapshot and a persisted long volume cursor. */
public final class SchematicPrinter {
    public enum PrintStage {BLOCKS,DEFERRED_BLOCKS,ENTITIES}
    private boolean schematicLoaded,isErrored;
    private SchematicStructure blockReader;
    private BlockPos schematicAnchor,currentPos;
    private int printingEntityIndex=-1;
    private PrintStage printStage=PrintStage.BLOCKS;
    private final Deque<BlockPos> deferredBlocks=new ArrayDeque<>();
    private long blockCursor=-1;
    private String fingerprint="";
    private CompoundTag resume;

    public void fromTag(CompoundTag tag,boolean clientPacket){
        // Parse into a temporary snapshot so malformed input cannot partially replace the current cursor.
        PrintStage stage;try{stage=PrintStage.valueOf(tag.getStringOr("PrintStage","BLOCKS"));}catch(IllegalArgumentException malformed){throw new IllegalArgumentException("Invalid native schematic printer stage",malformed);}
        int entity=tag.getIntOr("EntityProgress",-1);long cursor=tag.getLongOr("Cursor",-1);if(entity< -1||cursor< -1)throw new IllegalArgumentException("Invalid native schematic print progress");
        var deferred=new ArrayDeque<BlockPos>();for(var entry:tag.getListOrEmpty("DeferredBlocks")){if(!(entry instanceof CompoundTag saved))throw new IllegalArgumentException("Invalid deferred schematic block");deferred.add(saved.read("Pos",BlockPos.CODEC).orElseThrow(()->new IllegalArgumentException("Invalid deferred schematic position")));}
        var pos=tag.read("CurrentPos",BlockPos.CODEC).orElse(null);var anchor=tag.read("Anchor",BlockPos.CODEC).orElse(null);
        currentPos=pos;schematicAnchor=anchor;printStage=stage;printingEntityIndex=entity;blockCursor=cursor;fingerprint=tag.getStringOr("Fingerprint","");deferredBlocks.clear();deferredBlocks.addAll(deferred);resume=tag.copy();
        if(clientPacket){blockReader=null;schematicLoaded=anchor!=null;isErrored=false;}
    }
    public void write(CompoundTag tag){
        if(currentPos!=null)tag.store("CurrentPos",BlockPos.CODEC,currentPos);if(schematicAnchor!=null)tag.store("Anchor",BlockPos.CODEC,schematicAnchor);
        tag.putInt("EntityProgress",printingEntityIndex);tag.putString("PrintStage",printStage.name());tag.putLong("Cursor",blockCursor);if(!fingerprint.isEmpty())tag.putString("Fingerprint",fingerprint);
        var saved=new ListTag();for(var pos:deferredBlocks){var entry=new CompoundTag();entry.store("Pos",BlockPos.CODEC,pos);saved.add(entry);}tag.put("DeferredBlocks",saved);
    }
    public void loadSchematic(ItemStack blueprint,Level originalWorld,boolean processNbt){
        if(!blueprint.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED,false)||!blueprint.has(AllDataComponents.SCHEMATIC_ANCHOR))return;
        var saved=resume;
        try{
            var template=SchematicItem.loadSchematic(originalWorld,blueprint);var settings=SchematicItem.getSettings(blueprint,processNbt);var anchor=blueprint.get(AllDataComponents.SCHEMATIC_ANCHOR);
            var snapshot=SchematicStructure.load(originalWorld,template,anchor,settings,processNbt);var identity=fingerprint(template,blueprint,processNbt);
            blockReader=snapshot;schematicAnchor=anchor;schematicLoaded=true;isErrored=false;blockCursor=-1;currentPos=null;printingEntityIndex=-1;printStage=PrintStage.BLOCKS;deferredBlocks.clear();fingerprint=identity;resume=null;
            if(saved!=null){
                var oldAnchor=saved.read("Anchor",BlockPos.CODEC).orElse(anchor);var oldIdentity=saved.getStringOr("Fingerprint",identity);
                if(!oldAnchor.equals(anchor)||!oldIdentity.equals(identity))throw new IOException("Saved schematic cursor belongs to a different source structure or transform");
                fromTag(saved,false);fingerprint=identity;resume=null;validateProgress();
            }
        }catch(IOException|RuntimeException failure){blockReader=null;schematicLoaded=true;isErrored=true;Create.LOGGER.error("Failed to load native schematic for printing",failure);}
    }
    private static String fingerprint(StructureTemplate template,ItemStack blueprint,boolean processNbt)throws IOException{
        try{var digest=MessageDigest.getInstance("SHA-256");try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))){NbtIo.write(template.save(new CompoundTag()),out);out.writeUTF(blueprint.getOrDefault(AllDataComponents.SCHEMATIC_OWNER,""));out.writeUTF(blueprint.getOrDefault(AllDataComponents.SCHEMATIC_FILE,""));out.writeInt(blueprint.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION,Rotation.NONE).ordinal());out.writeInt(blueprint.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR,Mirror.NONE).ordinal());out.writeBoolean(processNbt);}return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private void validateProgress()throws IOException{
        var bounds=blockReader.bounds();long volume=SchematicStructure.volume(bounds);
        if(blockCursor>=volume||printingEntityIndex>=blockReader.entities().size())throw new IOException("Saved schematic cursor exceeds its actual source snapshot");
        if(currentPos!=null&&printStage!=PrintStage.ENTITIES&&!bounds.isInside(currentPos))throw new IOException("Saved schematic block lies outside its actual source bounds");
        var seen=new HashSet<BlockPos>();for(var pos:deferredBlocks)if(!bounds.isInside(pos)||!seen.add(pos)||!shouldDeferBlock(blockReader.getBlockState(schematicAnchor.offset(pos))))throw new IOException("Saved schematic deferred queue is invalid");
        // Accept original source saves which store only the current relative block position.
        if(blockCursor<0&&currentPos!=null&&printStage==PrintStage.BLOCKS)blockCursor=indexOf(currentPos);
        if(blockCursor>=0&&currentPos!=null&&printStage==PrintStage.BLOCKS&&indexOf(currentPos)!=blockCursor)throw new IOException("Saved schematic cursor disagrees with its actual source target");
        if(printStage==PrintStage.ENTITIES&&printingEntityIndex>=0)currentPos=blockReader.entities().get(printingEntityIndex).blockPosition().subtract(schematicAnchor);
    }
    public void resetSchematic(){schematicLoaded=false;isErrored=false;blockReader=null;schematicAnchor=null;currentPos=null;printingEntityIndex=-1;blockCursor=-1;printStage=PrintStage.BLOCKS;deferredBlocks.clear();resume=null;fingerprint="";}
    public boolean isLoaded(){return schematicLoaded;}
    public boolean isErrored(){return isErrored;}
    public BlockPos getCurrentTarget(){return !schematicLoaded||isErrored||currentPos==null||schematicAnchor==null?null:schematicAnchor.offset(currentPos);}
    public PrintStage getPrintStage(){return printStage;}
    public BlockPos getAnchor(){return schematicAnchor;}
    public boolean isWorldEmpty(){return blockReader==null||blockReader.positions().isEmpty()&&blockReader.entities().isEmpty();}
    public SchematicStructure getBlockReader(){return blockReader;}
    public long blockCursor(){return blockCursor;}
    public String fingerprint(){return fingerprint;}
    @FunctionalInterface public interface BlockTargetHandler {void handle(BlockPos target,BlockState state,BlockEntity entity);}
    @FunctionalInterface public interface EntityTargetHandler {void handle(BlockPos target,Entity entity);}
    public void handleCurrentTarget(BlockTargetHandler blockHandler,EntityTargetHandler entityHandler){
        var target=getCurrentTarget();if(target==null||blockReader==null)throw new IllegalStateException("Printer lacks a current native source target");
        if(printStage==PrintStage.ENTITIES)entityHandler.handle(target,blockReader.entities().get(printingEntityIndex));
        else blockHandler.handle(target,BlockHelper.setZeroAge(blockReader.getBlockState(target)),blockReader.getBlockEntity(target));
    }
    @FunctionalInterface public interface PlacementPredicate {boolean shouldPlace(BlockPos target,BlockState state,BlockEntity entity,BlockState toReplace,BlockState other,boolean isNormalCube);}
    public boolean shouldPlaceCurrent(Level level){return shouldPlaceCurrent(level,(a,b,c,d,e,f)->true);}
    public boolean shouldPlaceCurrent(Level level,PlacementPredicate predicate){if(level==null||blockReader==null||getCurrentTarget()==null)return false;return printStage==PrintStage.ENTITIES?level.isLoaded(getCurrentTarget())&&level.getWorldBorder().isWithinBounds(getCurrentTarget()):shouldPlaceBlock(level,predicate,getCurrentTarget());}
    public boolean shouldPlaceBlock(Level level,PlacementPredicate predicate,BlockPos pos){
        if(blockReader==null||!SchematicWorldAccess.readyForPlacement(level,blockReader.getBlockState(pos),pos)||!level.isLoaded(pos)||!level.getWorldBorder().isWithinBounds(pos)||level.isOutsideBuildHeight(pos))return false;
        var state=BlockHelper.setZeroAge(blockReader.getBlockState(pos));var be=blockReader.getBlockEntity(pos);var existing=level.getBlockState(pos);var existingBE=com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos);BlockState other=null;BlockPos counterpart=null;
        if(state.hasProperty(BlockStateProperties.BED_PART)&&state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)&&state.getValue(BlockStateProperties.BED_PART)==BedPart.FOOT)counterpart=pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)&&state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER)counterpart=pos.above();
        if(counterpart!=null){if(!level.isLoaded(counterpart)||!level.getWorldBorder().isWithinBounds(counterpart)||level.isOutsideBuildHeight(counterpart))return false;other=level.getBlockState(counterpart);}
        boolean merge=be!=null&&existingBE!=null&&existingBE.getType()==be.getType()&&(existingBE instanceof IMergeableBE||existingBE instanceof com.simibubi.create.content.trains.track.TrackBlockEntity&&be instanceof com.simibubi.create.content.trains.track.TrackBlockEntity);
        if(existing==state&&!merge||existing.getDestroySpeed(level,pos)==-1||other!=null&&other.getDestroySpeed(level,counterpart)==-1)return false;
        return predicate.shouldPlace(pos,state,be,existing,other,state.isRedstoneConductor(blockReader,pos));
    }
    public ItemRequirement getCurrentRequirement(){
        if(blockReader==null||getCurrentTarget()==null)return ItemRequirement.INVALID;
        if(printStage==PrintStage.ENTITIES)return ItemRequirement.of(blockReader.entities().get(printingEntityIndex));
        var target=getCurrentTarget();return ItemRequirement.of(BlockHelper.setZeroAge(blockReader.getBlockState(target)),blockReader.safeBlockEntity(target));
    }
    public int markAllBlockRequirements(MaterialChecklist checklist,Level level,PlacementPredicate predicate){int count=0;if(blockReader==null)return count;for(var pos:blockReader.positions()){
        if(!SchematicWorldAccess.readyForPlacement(level,blockReader.getBlockState(pos),pos)){checklist.warnBlockNotLoaded();continue;}if(!shouldPlaceBlock(level,predicate,pos))continue;
        var requirement=ItemRequirement.of(BlockHelper.setZeroAge(blockReader.getBlockState(pos)),blockReader.safeBlockEntity(pos));if(requirement.isEmpty()||requirement.isInvalid())continue;checklist.require(requirement);count=Math.incrementExact(count);
    }return count;}
    public void markAllEntityRequirements(MaterialChecklist checklist){if(blockReader==null)return;for(var entity:blockReader.entities()){var requirement=ItemRequirement.of(entity);if(!requirement.isEmpty()&&!requirement.isInvalid())checklist.require(requirement);}}
    public boolean advanceCurrentPos(){
        if(blockReader==null||isErrored)return false;
        while(true){
            if(printStage==PrintStage.BLOCKS){boolean defer=tryAdvanceCurrentPos();if(printStage==PrintStage.BLOCKS){if(defer){deferredBlocks.addLast(currentPos);continue;}return true;}}
            if(printStage==PrintStage.DEFERRED_BLOCKS){if(!deferredBlocks.isEmpty()){currentPos=deferredBlocks.removeFirst();return true;}printStage=PrintStage.ENTITIES;}
            if(printStage==PrintStage.ENTITIES){if(printingEntityIndex+1>=blockReader.entities().size())return false;currentPos=blockReader.entities().get(++printingEntityIndex).blockPosition().subtract(schematicAnchor);return true;}
        }
    }
    public boolean tryAdvanceCurrentPos(){
        if(blockReader==null||printStage!=PrintStage.BLOCKS)return false;
        try{long volume=SchematicStructure.volume(blockReader.bounds());if(blockCursor+1>=volume){printStage=PrintStage.DEFERRED_BLOCKS;return false;}currentPos=positionAt(++blockCursor);return shouldDeferBlock(blockReader.getBlockState(schematicAnchor.offset(currentPos)));}catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    private BlockPos positionAt(long cursor){var b=blockReader.bounds();long x=1L+b.maxX()-b.minX(),z=1L+b.maxZ()-b.minZ();return new BlockPos(Math.toIntExact(b.minX()+cursor%x),Math.toIntExact(b.minY()+cursor/(x*z)),Math.toIntExact(b.minZ()+cursor/x%z));}
    private long indexOf(BlockPos pos){var b=blockReader.bounds();long x=1L+b.maxX()-b.minX(),z=1L+b.maxZ()-b.minZ();return (pos.getY()-(long)b.minY())*x*z+(pos.getZ()-(long)b.minZ())*x+pos.getX()-(long)b.minX();}
    public static boolean shouldDeferBlock(BlockState state){return state.is(AllBlocks.GANTRY_CARRIAGE)||state.getBlock()==net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(Create.asResource("mechanical_arm")).orElse(null)||BlockMovementChecks.isBrittle(state);}
    public void sendBlockUpdates(Level level){if(blockReader!=null&&level instanceof net.minecraft.server.level.ServerLevel server)SchematicWaterUpdates.enqueue(server,schematicAnchor,blockReader.bounds());}

}
