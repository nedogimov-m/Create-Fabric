package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.foundation.utility.*;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Actual original complete saved shapes/native factories; all authored runtime cases UNRUN. */
public final class NeoForgeStandaloneEntityGameTests {
    private static final UUID SOURCE=UUID.fromString("355b916c-8da5-4bb3-afac-6c4f0ee90c1e"),CART=UUID.fromString("47f6c7c1-d1dd-4320-94a3-8af48645e411");
    private static ListTag rows(Tag... values){var tag=new ListTag();for(var value:values)tag.add(value);return tag;}
    private static ListTag vector(double x,double y,double z){return rows(DoubleTag.valueOf(x),DoubleTag.valueOf(y),DoubleTag.valueOf(z));}
    private static CompoundTag xyz(BlockPos p){var tag=new CompoundTag();tag.putInt("X",p.getX());tag.putInt("Y",p.getY());tag.putInt("Z",p.getZ());return tag;}
    private static CompoundTag saved(String type,UUID id){
        var tag=new CompoundTag();tag.putString("id",type);tag.store("UUID",UUIDUtil.CODEC,id);tag.put("Pos",vector(-.25,128,3.75));tag.put("Motion",vector(0,0,0));tag.put("Rotation",rows(FloatTag.valueOf(0),FloatTag.valueOf(0)));
        tag.putFloat("FallDistance",0);tag.putShort("Fire",(short)-1);tag.putShort("Air",(short)300);tag.putBoolean("OnGround",false);tag.putBoolean("Invulnerable",false);tag.putInt("PortalCooldown",0);return tag;
    }
    private static CompoundTag sourceState(BlockState state){var tag=new CompoundTag();tag.putString("Name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());var properties=new CompoundTag();state.getValues().forEach(value->properties.putString(value.property().getName(),value.valueName()));if(!properties.isEmpty())tag.put("Properties",properties);return tag;}
    private static CompoundTag body(String type){
        var tag=new CompoundTag();tag.putString("Type",type);tag.put("Anchor",xyz(new BlockPos(-4,131,7)));tag.putBoolean("Stalled",false);tag.putBoolean("BottomlessSupply",false);
        for(String field:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions"))tag.put(field,new ListTag());
        var palette=rows(sourceState(type.equals("create:mounted")?AllBlocks.MINECART_ANCHOR.defaultBlockState():Blocks.STONE.defaultBlockState()),sourceState(Blocks.STONE.defaultBlockState()));var a=new CompoundTag();a.putLong("Pos",BlockPos.ZERO.asLong());a.putInt("State",0);var b=new CompoundTag();b.putLong("Pos",BlockPos.ZERO.above().asLong());b.putInt("State",1);var blocks=new CompoundTag();blocks.put("Palette",palette);blocks.put("BlockList",rows(a,b));tag.put("Blocks",blocks);
        if(type.equals("create:bearing")){tag.putInt("Sails",0);tag.putInt("Facing",Direction.UP.get3DDataValue());}if(type.equals("create:mounted"))tag.putString("RotationMode","ROTATE");return tag;
    }
    private static CompoundTag controlled(){var tag=saved("create:stationary_contraption",SOURCE);tag.put("Contraption",body("create:bearing"));tag.putBoolean("Stalled",false);tag.putBoolean("Initialized",true);tag.put("ControllerRelative",xyz(new BlockPos(-3,2,4)));tag.putString("Axis","Y");tag.putFloat("Angle",45);return tag;}
    @GameTest public void genuineControlledRelativePositionAndOriginalUuidReachActualNativeFactory(GameTestHelper h){
        var source=controlled();var old=source.copy();var actual=NeoForgeStandaloneEntityImport.convertTree(source,3955,h.getLevel().registryAccess());
        var entity=EntityType.loadEntityRecursive(actual,h.getLevel(),new EntitySpawnRequest(EntitySpawnReason.LOAD,false),EntityProcessor.NOP);
        h.assertTrue(source.equals(old)&&entity instanceof ControlledContraptionEntity&&entity.getUUID().equals(SOURCE),"Actual source UUID reaches the real controlled native factory after typed source-ID/body conversion");
        var controlled=(ControlledContraptionEntity)entity;h.assertTrue(controlled.getControllerPos().equals(new BlockPos(-4,130,7))&&controlled.getRotationAxis()==Direction.Axis.Y&&controlled.getAngle(1)==45
            &&actual.get("ControllerRelative") instanceof IntArrayTag&&!actual.contains("FallDistance")&&actual.getDouble("fall_distance").orElseThrow()==0,
            "Source floor(-.25)+genuine relative(-3,2,4) determines real controller; native relative BlockPos/axis/angle retain original semantics");h.succeed();
    }
    @GameTest public void genuineVanillaCartAndCreateMountedBodyRemainOneActualOriginalPassengerTree(GameTestHelper h){
        var cart=saved("minecraft:minecart",CART);var attachments=new CompoundTag();var controller=new CompoundTag();controller.putString("Type","NORMAL");attachments.put("create:minecart_controller",controller);cart.put("neoforge:attachments",attachments);
        var body=saved("create:contraption",SOURCE);body.put("Contraption",body("create:mounted"));body.putBoolean("Stalled",false);body.putBoolean("Initialized",true);body.putBoolean("Placed",false);body.putString("InitialOrientation","EAST");body.putFloat("Yaw",90);body.putFloat("Pitch",0);cart.put("Passengers",rows(body));var old=cart.copy();
        var actual=NeoForgeStandaloneEntityImport.convertTree(cart,3955,h.getLevel().registryAccess());var child=actual.getListOrEmpty("Passengers").getCompoundOrEmpty(0);
        h.assertTrue(old.equals(cart)&&actual.get("UUID").equals(cart.get("UUID"))&&child.get("UUID").equals(body.get("UUID"))&&child.getString("id").orElseThrow().equals("create:oriented_contraption")
            &&child.read("MountedCart",UUIDUtil.CODEC).orElseThrow().equals(CART)&&actual.getCompoundOrEmpty("fabric:attachments").contains("create:minecart_controller"),"The actual containing original saved cart supplies mounted UUID and its independently validated genuine controller; no replacement cart/receipt is created");
        var loaded=EntityType.loadEntityRecursive(actual,h.getLevel(),new EntitySpawnRequest(EntitySpawnReason.LOAD,false),EntityProcessor.NOP);
        h.assertTrue(loaded instanceof net.minecraft.world.entity.vehicle.minecart.Minecart&&loaded.getUUID().equals(CART)&&loaded.getPassengers().size()==1&&loaded.getFirstPassenger() instanceof OrientedContraptionEntity
            &&loaded.getFirstPassenger().getUUID().equals(SOURCE),"Real recursive native LOAD factory retains genuine original vehicle/passenger class/order/UUID");h.succeed();
    }
    @GameTest public void actualSourceControllerSchemaAndDuplicateOriginalOwnerFailBeforeFactoryPublication(GameTestHelper h){
        var unknown=controlled();unknown.putString("FutureOwnedInventory","never silently default");refuses(h,unknown);
        var orphan=controlled();var child=saved("create:seat",CART);orphan.put("Passengers",rows(child));refuses(h,orphan);
        var roots=rows(saved("create:seat",SOURCE),saved("create:seat",SOURCE));var before=roots.copy();boolean refused=false;try{NeoForgeStandaloneEntityImport.convertRoots(roots,3955,h.getLevel().registryAccess());}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused&&roots.equals(before),"Two real source rows cannot become two native owners of the same genuine original UUID");
        var cache=saved("create:contraption",SOURCE);cache.put("Contraption",body("create:mounted"));cache.putBoolean("Stalled",false);cache.putBoolean("Initialized",true);cache.putBoolean("Placed",false);cache.putFloat("Yaw",0);cache.putFloat("Pitch",0);cache.put("CachedMotion",vector(1,0,0));refuses(h,cache);h.succeed();
    }
    @GameTest public void genuineEntityChunkEnvelopeKeepsActualRegionCoordinatesAndOriginalImmutableRoots(GameTestHelper h){
        var original=new CompoundTag();original.putInt("DataVersion",3955);original.putIntArray("Position",new int[]{-1,0});original.put("Entities",rows(controlled()));var before=original.copy();var actual=NeoForgeEntityChunkImport.convert(original,new net.minecraft.world.level.ChunkPos(-1,0),h.getLevel().registryAccess());
        h.assertTrue(original.equals(before)&&actual.get("Position").equals(original.get("Position"))&&actual.getInt("DataVersion").orElseThrow()==net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version()
            &&actual.getListOrEmpty("Entities").getCompoundOrEmpty(0).get("UUID").equals(original.getListOrEmpty("Entities").getCompoundOrEmpty(0).get("UUID")),"Pure whole original envelope has its native version only after every actual original owner validates");
        boolean misplaced=false;try{NeoForgeEntityChunkImport.convert(original,new net.minecraft.world.level.ChunkPos(0,0),h.getLevel().registryAccess());}catch(IllegalArgumentException expected){misplaced=true;}
        h.assertTrue(misplaced&&original.equals(before),"Actual requested native chunk must agree with the genuine original region header before permissive native logs/factories");
        var wrongRoot=original.copy();wrongRoot.getListOrEmpty("Entities").getCompoundOrEmpty(0).put("Pos",vector(16,128,3.75));misplaced=false;
        try{NeoForgeEntityChunkImport.convert(wrongRoot,h.getLevel().registryAccess());}catch(IllegalArgumentException expected){misplaced=true;}
        h.assertTrue(misplaced,"Only genuine original root positions are required in their real header chunk; child positions may cross chunk boundaries");
        original.putInt("DataVersion",9999);boolean refused=false;try{NeoForgeEntityChunkImport.convert(original,h.getLevel().registryAccess());}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Unknown source version cannot enter the3955 branch");h.succeed();
    }
    private static void refuses(GameTestHelper h,CompoundTag source){var old=source.copy();boolean refused=false;try{NeoForgeStandaloneEntityImport.convertTree(source,3955,h.getLevel().registryAccess());}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused&&source.equals(old),"Unknown/unowned complete original tree refuses without rewriting or publishing source material");}
}
