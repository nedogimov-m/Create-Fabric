package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.*;
import com.simibubi.create.api.schematic.requirement.SpecialEntityItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Source wall/floor/ceiling hanging blueprint with genuine native tracked geometry and ghost sections. */
public final class BlueprintEntity extends HangingEntity implements SpecialEntityItemRequirement,IInteractionChecker {
 private static final EntityDataAccessor<Integer> SIZE=SynchedEntityData.defineId(BlueprintEntity.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Direction> ORIENTATION=SynchedEntityData.defineId(BlueprintEntity.class,EntityDataSerializers.DIRECTION);
 private static final EntityDataAccessor<CompoundTag> RECIPES=SynchedEntityData.defineId(BlueprintEntity.class,BlueprintData.SERIALIZER);
 private final Map<Integer,BlueprintSection> sections=new HashMap<>();
 public BlueprintEntity(EntityType<? extends BlueprintEntity> type,Level level){super(type,level);}
 public BlueprintEntity(Level level,BlockPos pos,Direction facing,Direction orientation){this(AllEntityTypes.CRAFTING_BLUEPRINT,level);this.pos=pos.immutable();for(int size=3;size>0;size--){entityData.set(SIZE,size);updateFacingWithBoundingBox(facing,orientation);if(survives())break;}}
 @Override protected void defineSynchedData(SynchedEntityData.Builder data){super.defineSynchedData(data);data.define(SIZE,1);data.define(ORIENTATION,Direction.DOWN);data.define(RECIPES,new CompoundTag());}
 public int size(){return Math.clamp(entityData.get(SIZE),1,3);}
 public Direction verticalOrientation(){return entityData.get(ORIENTATION);}
 @Override protected void setDirection(Direction facing){updateFacingWithBoundingBox(facing,verticalOrientation());}
 public void updateFacingWithBoundingBox(Direction facing,Direction orientation){Objects.requireNonNull(facing);Objects.requireNonNull(orientation);if(facing.getAxis().isVertical()&&orientation.getAxis().isVertical())orientation=Direction.NORTH;setDirectionRaw(facing);entityData.set(ORIENTATION,orientation);if(facing.getAxis().isHorizontal()){setXRot(0);setYRot(facing.get2DDataValue()*90);}else{setXRot(-90*facing.getAxisDirection().getStep());setYRot(orientation.getAxis().isHorizontal()?180+orientation.toYRot():0);}xRotO=getXRot();yRotO=getYRot();recalculateBoundingBox();}
 @Override protected AABB calculateBoundingBox(BlockPos anchor,Direction facing){var at=Vec3.atCenterOf(anchor).subtract(facing.getUnitVec3().scale(.46875));setPosRaw(at.x,at.y,at.z);if(size()==2)at=at.add((facing.getAxis().isHorizontal()?facing.getCounterClockWise():verticalOrientation().getClockWise()).getUnitVec3().scale(.5)).add((facing.getAxis().isHorizontal()?Direction.UP:facing==Direction.UP?verticalOrientation():verticalOrientation().getOpposite()).getUnitVec3().scale(.5));double width=16*size(),height=width,x=facing.getAxis()==Direction.Axis.X?1:width,y=facing.getAxis()==Direction.Axis.Y?1:height,z=facing.getAxis()==Direction.Axis.Z?1:width;return new AABB(at.x-x/32,at.y-y/32,at.z-z/32,at.x+x/32,at.y+y/32,at.z+z/32);}
 @Override protected void recalculateBoundingBox(){if(entityData!=null&&pos!=null)setBoundingBox(calculateBoundingBox(pos,getDirection()));}
 @Override public EntityDimensions getDimensions(Pose pose){return super.getDimensions(pose).withEyeHeight(0);}
 @Override public boolean survives(){if(pos==null)return false;var facing=getDirection();var up=facing.getAxis().isHorizontal()?Direction.UP:facing==Direction.UP?verticalOrientation():verticalOrientation().getOpposite();var across=facing.getAxis().isVertical()?verticalOrientation().getClockWise():facing.getCounterClockWise();var behind=pos.relative(facing.getOpposite());for(int x=0;x<size();x++)for(int y=0;y<size();y++){var support=behind.relative(across,x+(size()-1)/-2).relative(up,y+(size()-1)/-2);if(!LoadedLevelAccess.isLoaded(level(),support)||!level().isInValidBounds(support)||!level().getWorldBorder().isWithinBounds(support))return false;var state=LoadedLevelAccess.blockState(level(),support);if(!Block.canSupportCenter(level(),support,facing)&&!state.isSolid()&&!DiodeBlock.isDiode(state))return false;}return level().noCollision(this)&&level().getEntities(this,getBoundingBox(),entity->entity instanceof BlockAttachedEntity).isEmpty();}
 @Override public void onSyncedDataUpdated(EntityDataAccessor<?> data){super.onSyncedDataUpdated(data);if(entityData==null)return;if(data.equals(SIZE)||data.equals(ORIENTATION))updateFacingWithBoundingBox(getDirection(),verticalOrientation());if(data.equals(RECIPES)&&sections!=null)sections.values().forEach(section->section.cachedDisplay=null);}
 @Override public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> values){super.onSyncedDataUpdated(values);updateFacingWithBoundingBox(getDirection(),verticalOrientation());if(sections!=null)sections.values().forEach(section->section.cachedDisplay=null);}
 @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);out.putByte("Facing",(byte)getDirection().get3DDataValue());out.putByte("Orientation",(byte)verticalOrientation().get3DDataValue());out.putInt("Size",size());out.store("Recipes",CompoundTag.CODEC,entityData.get(RECIPES));}
 @Override protected void readAdditionalSaveData(ValueInput in){super.readAdditionalSaveData(in);entityData.set(SIZE,Math.clamp(in.getIntOr("Size",1),1,3));var recipes=in.read("Recipes",CompoundTag.CODEC).orElseGet(()->in.read("NeoForgeData",CompoundTag.CODEC).orElseGet(CompoundTag::new).getCompoundOrEmpty("Recipes"));entityData.set(RECIPES,recipes.copy());updateFacingWithBoundingBox(Direction.from3DDataValue(in.getIntOr("Facing",Direction.SOUTH.get3DDataValue())),Direction.from3DDataValue(in.getIntOr("Orientation",Direction.DOWN.get3DDataValue())));}
 @Override public boolean skipAttackInteraction(Entity source){if(!(source instanceof Player player)||level().isClientSide()||!canPlayerUse(player))return super.skipAttackInteraction(source);double reach=player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)+(player.isCreative()?0:-.5);var start=player.getEyePosition();var hit=getBoundingBox().clip(start,start.add(player.getViewVector(1).scale(reach)));if(hit.isEmpty())return super.skipAttackInteraction(source);var section=getSectionAt(hit.get().subtract(position()));var items=section.getItems();if(items.getItem(9).isEmpty())return super.skipAttackInteraction(source);items.clearContent();section.save(items,false);return true;}
 @Override public void dropItem(ServerLevel level,Entity source){if(!level.getGameRules().get(GameRules.ENTITY_DROPS))return;playSound(SoundEvents.PAINTING_BREAK,1,1);if(source instanceof Player player&&player.hasInfiniteMaterials())return;spawnAtLocation(level,new ItemStack(AllItems.CRAFTING_BLUEPRINT));}
 @Override public ItemStack getPickResult(){return new ItemStack(AllItems.CRAFTING_BLUEPRINT);}
 @Override public ItemRequirement getRequiredItems(){return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME,AllItems.CRAFTING_BLUEPRINT);}
 @Override public void playPlacementSound(){playSound(SoundEvents.PAINTING_PLACE,1,1);}
 @Override public InteractionResult interact(Player player,InteractionHand hand,Vec3 local){if(player instanceof FakePlayer||!canPlayerUse(player))return InteractionResult.PASS;var section=getSectionAt(local);var items=section.getItems();boolean wrench=player.getItemInHand(hand).is(AllItems.WRENCH);if(!wrench&&!level().isClientSide()&&!items.getItem(9).isEmpty()){BlueprintCrafting.craft(player,section,player.isShiftKeyDown());return InteractionResult.SUCCESS;}if(!level().isClientSide()&&player instanceof ServerPlayer server)server.openMenu(section);return InteractionResult.SUCCESS;}
 public BlueprintSection getSectionAt(Vec3 local){int index=0;if(size()>1){local=local.yRot(getYRot()*Mth.DEG_TO_RAD);float angle=-getXRot()*Mth.DEG_TO_RAD;double sin=Mth.sin(angle),cos=Mth.cos(angle);local=new Vec3(local.x,local.y*cos-local.z*sin,local.z*cos+local.y*sin).add(.5,.5,0);if(size()==3)local=local.add(1,1,0);index=Math.clamp(Mth.floor(local.x),0,size()-1)+Math.clamp(Mth.floor(local.y),0,size()-1)*size();}return getSection(index);}
 public BlueprintSection getSection(int index){if(index<0||index>=size()*size())throw new IllegalArgumentException("Invalid actual blueprint section");return sections.computeIfAbsent(index,BlueprintSection::new);}
 @Override public boolean canPlayerUse(Player player){return isAlive()&&player.level()==level()&&!player.isSpectator()&&getBoundingBox().distanceToSqr(player.position())<=64;}
 public final class BlueprintSection implements ExtendedMenuProvider<BlueprintMenu.Opening>,IInteractionChecker {
  public final int index;private Couple<ItemStack> cachedDisplay;
  private BlueprintSection(int index){this.index=index;}
  public BlueprintEntity blueprint(){return BlueprintEntity.this;}
  public CompoundTag snapshot(){return entityData.get(RECIPES).getCompoundOrEmpty(Integer.toString(index)).copy();}
  public SimpleContainer getItems(){return BlueprintData.readItems(snapshot(),registryAccess());}
  public boolean inferredIcon(){var recipes=entityData.get(RECIPES);return snapshot().getBooleanOr("InferredIcon",recipes.getBooleanOr("InferredIcon",false));}
  public Couple<ItemStack> getDisplayItems(){if(cachedDisplay==null){var items=getItems();cachedDisplay=Couple.create(items.getItem(9),items.getItem(10));}return cachedDisplay;}
  public void save(SimpleContainer items,boolean inferred){if(level().isClientSide())return;var recipes=entityData.get(RECIPES).copy();var section=BlueprintData.writeItems(items,registryAccess());section.putBoolean("InferredIcon",inferred);recipes.put(Integer.toString(index),section);entityData.set(RECIPES,recipes);cachedDisplay=null;}
  @Override public Component getDisplayName(){return AllItems.CRAFTING_BLUEPRINT.getName(new ItemStack(AllItems.CRAFTING_BLUEPRINT));}
  @Override public BlueprintMenu.Opening getScreenOpeningData(ServerPlayer player){return new BlueprintMenu.Opening(getId(),getUUID(),index);}
  @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new BlueprintMenu(id,inventory,this);}
  @Override public boolean canPlayerUse(Player player){return BlueprintEntity.this.canPlayerUse(player)&&level().getEntity(getUUID())==BlueprintEntity.this&&index<size()*size()&&getSection(index)==this;}
 }
}
