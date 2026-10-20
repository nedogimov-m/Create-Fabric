package com.simibubi.create.content.contraptions.glue;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock;
import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Persistent source glue volume. Native tracked vectors replace the NeoForge complex spawn payload. */
public class SuperGlueEntity extends Entity {
    private static final EntityDataAccessor<Vector3fc> FROM=SynchedEntityData.defineId(SuperGlueEntity.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3fc> TO=SynchedEntityData.defineId(SuperGlueEntity.class,EntityDataSerializers.VECTOR3);
    public SuperGlueEntity(EntityType<? extends SuperGlueEntity> type,Level level){super(type,level);noPhysics=true;}
    public SuperGlueEntity(Level level,AABB box){this(AllEntityTypes.SUPER_GLUE,level);setGlueBox(box);}
    public static AABB span(BlockPos from,BlockPos to){return new AABB(Vec3.atLowerCornerOf(from),Vec3.atLowerCornerOf(to)).expandTowards(1,1,1);}
    public void setGlueBox(AABB box){
        if(!finite(box)||box.getXsize()<=0||box.getYsize()<=0||box.getZsize()<=0)throw new IllegalArgumentException("Invalid glue bounds");
        setPosRaw(box.getCenter().x,box.minY,box.getCenter().z);
        setRelativeBox(box.move(position().scale(-1)));
    }
    private void setRelativeBox(AABB box){
        entityData.set(FROM,new Vector3f((float)box.minX,(float)box.minY,(float)box.minZ));
        entityData.set(TO,new Vector3f((float)box.maxX,(float)box.maxY,(float)box.maxZ));
        setBoundingBox(box.move(position()));
    }
    public static boolean finite(AABB box){return Double.isFinite(box.minX)&&Double.isFinite(box.minY)&&Double.isFinite(box.minZ)&&Double.isFinite(box.maxX)&&Double.isFinite(box.maxY)&&Double.isFinite(box.maxZ);}
    private void applyTrackedBox(){var from=entityData.get(FROM);var to=entityData.get(TO);setBoundingBox(new AABB(from.x(),from.y(),from.z(),to.x(),to.y(),to.z()).move(position()));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){data.define(FROM,new Vector3f());data.define(TO,new Vector3f());}
    @Override public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> values){super.onSyncedDataUpdated(values);applyTrackedBox();}
    @Override protected void reapplyPosition(){if(entityData==null)super.reapplyPosition();else applyTrackedBox();}
    @Override public void setPos(double x,double y,double z){var previous=position();var box=getBoundingBox();setPosRaw(x,y,z);setBoundingBox(box.move(position().subtract(previous)));}
    public void resetPositionToBB(){var box=getBoundingBox();setPosRaw(box.getCenter().x,box.minY,box.getCenter().z);setRelativeBox(box.move(position().scale(-1)));}
    @Override public void tick(){xo=getX();yo=getY();zo=getZ();if(getBoundingBox().getXsize()==0)discard();}
    @Override public void move(MoverType type,Vec3 amount){if(!level().isClientSide()&&isAlive()&&amount.lengthSqr()>0)discard();}
    @Override public void push(double x,double y,double z){if(!level().isClientSide()&&isAlive()&&x*x+y*y+z*z>0)discard();}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){return false;}
    @Override public void thunderHit(ServerLevel level,LightningBolt bolt){}
    @Override public void refreshDimensions(){}
    @Override public boolean isIgnoringBlockTriggers(){return true;}
    @Override public PushReaction getPistonPushReaction(){return PushReaction.IGNORE_ENTITY;}
    @Override protected boolean repositionEntityAfterLoad(){return false;}
    @Override public EntityDimensions getDimensions(Pose pose){return super.getDimensions(pose).withEyeHeight(0);}
    @Override public float rotate(Rotation rotation){var box=getBoundingBox().move(position().scale(-1));if(rotation==Rotation.CLOCKWISE_90||rotation==Rotation.COUNTERCLOCKWISE_90)setRelativeBox(new AABB(box.minZ,box.minY,box.minX,box.maxZ,box.maxY,box.maxX));return super.rotate(rotation);}
    @Override protected void addAdditionalSaveData(ValueOutput out){var box=getBoundingBox().move(position().scale(-1));out.store("From",Vec3.CODEC,new Vec3(box.minX,box.minY,box.minZ));out.store("To",Vec3.CODEC,new Vec3(box.maxX,box.maxY,box.maxZ));}
    @Override protected void readAdditionalSaveData(ValueInput in){var from=in.read("From",Vec3.CODEC).orElse(Vec3.ZERO);var to=in.read("To",Vec3.CODEC).orElse(Vec3.ZERO);var box=new AABB(from,to);if(finite(box)&&box.getXsize()<=64&&box.getYsize()<=64&&box.getZsize()<=64)setRelativeBox(box);else discard();}
    public static void writeBoundingBox(CompoundTag tag,AABB box){tag.store("From",Vec3.CODEC,new Vec3(box.minX,box.minY,box.minZ));tag.store("To",Vec3.CODEC,new Vec3(box.maxX,box.maxY,box.maxZ));}
    public static AABB readBoundingBox(CompoundTag tag){return new AABB(tag.read("From",Vec3.CODEC).orElse(Vec3.ZERO),tag.read("To",Vec3.CODEC).orElse(Vec3.ZERO));}
    public boolean contains(BlockPos pos){return getBoundingBox().contains(Vec3.atCenterOf(pos));}
    public static boolean isGlued(LevelAccessor level,BlockPos pos,Direction direction,Set<SuperGlueEntity> cached){
        var other=pos.relative(direction);
        if(cached!=null)for(var glue:cached)if(!glue.isRemoved()&&glue.contains(pos)&&glue.contains(other))return true;
        for(var glue:level.getEntitiesOfClass(SuperGlueEntity.class,span(pos,other).inflate(16))){if(glue.isRemoved()||!glue.contains(pos)||!glue.contains(other))continue;if(cached!=null)cached.add(glue);return true;}
        return false;
    }
    public static List<SuperGlueEntity> collectCropped(Level level,AABB bounds){var result=new ArrayList<SuperGlueEntity>();for(var glue:level.getEntitiesOfClass(SuperGlueEntity.class,bounds)){var intersection=bounds.intersect(glue.getBoundingBox());if(intersection.getXsize()*intersection.getYsize()*intersection.getZsize()<=0||Math.abs(intersection.getSize()-1)<1e-5)continue;result.add(new SuperGlueEntity(level,intersection));}return result;}
    public static boolean isValidFace(Level level,BlockPos pos,Direction direction){var state=LoadedLevelAccess.blockState(level,pos);if(BlockMovementChecks.isBlockAttachedTowards(state,level,pos,direction))return true;return BlockMovementChecks.isMovementNecessary(state,level,pos)&&!BlockMovementChecks.isNotSupportive(state,direction);}
    public static boolean isSideSticky(Level level,BlockPos pos,Direction direction){
        var state=LoadedLevelAccess.blockState(level,pos);
        if(state.getBlock() instanceof StickerBlock)return state.getValue(StickerBlock.FACING)==direction;
        if(state.getBlock() instanceof AbstractChassisBlock chassis){var flag=chassis.getGlueableSide(state,direction);return flag!=null&&state.getValue(flag);}
        return state.is(Blocks.SLIME_BLOCK)||state.is(Blocks.HONEY_BLOCK);
    }
    public void playPlaceSound(){level().playSound(null,blockPosition(),com.simibubi.create.AllSoundEvents.SLIME_ADDED,SoundSource.BLOCKS,.5f,.75f);}
    public void spawnParticles(){
        if(!(level() instanceof ServerLevel server))return;
        var box=getBoundingBox();double[] min={box.minX,box.minY,box.minZ},max={box.maxX,box.maxY,box.maxZ};
        for(int axis=0;axis<3;axis++){int a=(axis+1)%3,b=(axis+2)%3;for(double t=min[axis];t<=max[axis]+1e-4;t+=.5)for(int i=0;i<2;i++)for(int j=0;j<2;j++){double[] p=min.clone();p[axis]=t;p[a]=i==0?min[a]:max[a];p[b]=j==0?min[b]:max[b];server.sendParticles(ParticleTypes.ITEM_SLIME,p[0],p[1],p[2],1,0,0,0,0);}}
    }
}
