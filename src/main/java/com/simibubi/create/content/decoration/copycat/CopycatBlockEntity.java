package com.simibubi.create.content.decoration.copycat;

import com.simibubi.create.*;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import com.simibubi.create.api.schematic.requirement.SpecialBlockEntityItemRequirement;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.redstone.RoseQuartzLampBlock;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.*;

/** The original one-item receipt retains every actual component through save, transfer and refund. */
public class CopycatBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements RenderDataBlockEntity,SpecialBlockEntityItemRequirement,TransformableBlockEntity,PartialSafeNBT,Clearable {
    private BlockState material=AllBlocks.COPYCAT_BASE.defaultBlockState();
    private ItemStack consumedItem=ItemStack.EMPTY;
    private boolean needsLight=true;
    public record RenderData(BlockState material){}
    public CopycatBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.COPYCAT,pos,state);}
    public BlockState getMaterial(){return material;}
    public boolean hasCustomMaterial(){return !material.is(AllBlocks.COPYCAT_BASE);}
    public boolean ownsConsumedItem(){return !consumedItem.isEmpty();}
    @Override public Object getRenderData(){return new RenderData(material);}
    /** Source public API returns a detached observation; transferring the receipt requires takeConsumedItem. */
    public ItemStack getConsumedItem(){return consumedItem.copy();}
    public void setConsumedItem(ItemStack stack){consumedItem=stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1);setChanged();}
    void installPaidMaterial(BlockState next,ItemStack paid){if(ownsConsumedItem()||hasCustomMaterial()||paid.isEmpty()||paid.getCount()!=1)throw new IllegalStateException("Copycat material requires one actual unowned paid receipt");consumedItem=paid;setChanged();setMaterial(next);}
    public ItemStack takeConsumedItem(){var original=consumedItem;consumedItem=ItemStack.EMPTY;setChanged();return original;}
    public void setMaterial(BlockState next){
        java.util.Objects.requireNonNull(next);var wrapper=getBlockState();
        if(level!=null&&!material.is(next.getBlock()))for(var side:Direction.values()){
            var pos=worldPosition.relative(side);if(!LoadedLevelAccess.isLoaded(level,pos))continue;var neighbor=LoadedLevelAccess.blockState(level,pos);
            if(neighbor.getBlock()!=wrapper.getBlock()||neighbor.setValue(CopycatBlock.LIGHT,0)!=wrapper.setValue(CopycatBlock.LIGHT,0))continue;
            if(LoadedLevelAccess.blockEntity(level,pos) instanceof CopycatBlockEntity copycat&&copycat.material.is(next.getBlock())){next=copycat.material;break;}
        }
        material=next;notifyUpdate();
    }
    private BlockState cycled(){
        if(material.hasProperty(TrapDoorBlock.HALF)&&material.getOptionalValue(TrapDoorBlock.OPEN).orElse(false))return material.cycle(TrapDoorBlock.HALF);
        if(material.hasProperty(BlockStateProperties.FACING))return material.cycle(BlockStateProperties.FACING);
        if(material.hasProperty(BlockStateProperties.HORIZONTAL_FACING))return material.setValue(BlockStateProperties.HORIZONTAL_FACING,material.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise());
        if(material.hasProperty(BlockStateProperties.AXIS))return material.cycle(BlockStateProperties.AXIS);
        if(material.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))return material.cycle(BlockStateProperties.HORIZONTAL_AXIS);
        if(material.hasProperty(BlockStateProperties.LIT))return material.cycle(BlockStateProperties.LIT);
        if(material.hasProperty(RoseQuartzLampBlock.POWERING))return material.cycle(RoseQuartzLampBlock.POWERING);
        return material;
    }
    public boolean canCycleMaterial(){return cycled()!=material;}
    public boolean cycleMaterial(){var next=cycled();if(next==material)return false;setMaterial(next);return true;}
    public void notifyUpdate(){
        setChanged();needsLight=true;if(level==null)return;
        if(!level.isClientSide())updateLight();
        if(LoadedLevelAccess.blockEntity(level,worldPosition)==this)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
    private void updateLight(){
        if(level==null||level.isClientSide()||LoadedLevelAccess.blockEntity(level,worldPosition)!=this)return;
        var state=LoadedLevelAccess.blockState(level,worldPosition);if(!(state.getBlock() instanceof CopycatBlock))return;
        int light=material.getLightEmission();if(state.getValue(CopycatBlock.LIGHT)!=light&&!level.setBlock(worldPosition,state.setValue(CopycatBlock.LIGHT,light),Block.UPDATE_ALL))return;
        needsLight=false;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,CopycatBlockEntity copycat){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, copycat)) {
            if (!createTick.shouldTick()) return;
if(copycat.needsLight)copycat.updateLight();
        }
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){var original=takeConsumedItem();if(!original.isEmpty())Block.popResource(level,pos,original);}super.preRemoveSideEffects(pos,state);}
    @Override public ItemRequirement getRequiredItems(BlockState state){return consumedItem.isEmpty()?ItemRequirement.NONE:new ItemRequirement(new ItemRequirement.StrictNbtStackRequirement(consumedItem.copy(),ItemRequirement.ItemUseType.CONSUME));}
    @Override public void transform(BlockEntity be,StructureTransform transform){material=transform.apply(material);notifyUpdate();}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.store("Item",ItemStack.OPTIONAL_CODEC,consumedItem);out.store("Material",BlockState.CODEC,material);}
    @Override protected void read(ValueInput in, boolean clientPacket){
        super.read(in, clientPacket);var previous=material;var incoming=in.read("Item",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);if(!incoming.isEmpty()&&incoming.getCount()!=1)throw new IllegalArgumentException("Copycat persisted receipt must have exactly one original item");
        var next=in.read("Material",BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState());
        if(level==null||!level.isClientSide()){
            var block=getBlockState().getBlock();var accepted=block instanceof CopycatBlock copycat?copycat.getAcceptedBlockState(level,worldPosition,incoming,null):null;
            // Invalid appearance never destroys a retained original receipt; it can still be refunded or dropped.
            if(!next.is(AllBlocks.COPYCAT_BASE)&&(accepted==null||!accepted.is(next.getBlock())))next=AllBlocks.COPYCAT_BASE.defaultBlockState();
        }
        consumedItem=incoming;material=next;needsLight=true;
        if(level!=null&&level.isClientSide()&&previous!=material)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_ALL);
    }
    @Override public void writeSafe(CompoundTag tag,HolderLookup.Provider registries){var ops=registries.createSerializationContext(NbtOps.INSTANCE);var plain=consumedItem.isEmpty()?ItemStack.EMPTY:new ItemStack(consumedItem.getItem(),1);tag.put("Item",ItemStack.OPTIONAL_CODEC.encodeStart(ops,plain).getOrThrow());tag.put("Material",BlockState.CODEC.encodeStart(ops,material).getOrThrow());}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void clearContent(){material=AllBlocks.COPYCAT_BASE.defaultBlockState();consumedItem=ItemStack.EMPTY;notifyUpdate();}
}
