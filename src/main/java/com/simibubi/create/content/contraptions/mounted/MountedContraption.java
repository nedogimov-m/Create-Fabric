package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockEntity.CartMovementMode;
import com.simibubi.create.foundation.utility.*;
import com.simibubi.create.impl.contraption.storage.MountedStorageAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.tuple.Pair;

/** Pinned mounted capture/anchors with actual independently owned stabilized children and native base-cart inventory. */
public class MountedContraption extends Contraption {
    public CartMovementMode rotationMode;
    public AbstractMinecart connectedCart;
    public MountedContraption(){this(CartMovementMode.ROTATE);}
    public MountedContraption(CartMovementMode mode){rotationMode=Objects.requireNonNull(mode);}
    @Override public ContraptionType getType(){return AllContraptionTypes.MOUNTED.value();}
    @Override public boolean assemble(Level world,BlockPos pos)throws AssemblyException{
        LoadedEntityAccess.requireReady(world,pos);var state=LoadedLevelAccess.blockState(world,pos);
        if(!state.is(AllBlocks.CART_ASSEMBLER)||!state.hasProperty(CartAssemblerBlock.RAIL_SHAPE))return false;
        connectedCart=null;if(!searchMovedStructure(world,pos,null))return false;
        // Capture the real assembler identity into sourceStates/sourceEntities before swapping its
        // rendered helper. Adding a synthetic anchor alone would fail actual root ownership preflight.
        addBlock(world,pos,capture(world,pos));return blocks.size()>1;
    }
    @Override protected boolean addToInitialFrontier(Level world,BlockPos pos,Direction direction,Queue<BlockPos> frontier){frontier.clear();frontier.add(pos.above());return true;}
    @Override protected Pair<StructureBlockInfo,BlockEntity> capture(Level world,BlockPos pos){
        var actual=super.capture(world,pos);var info=actual.getLeft();if(!info.state().is(AllBlocks.CART_ASSEMBLER))return actual;
        if(!pos.equals(anchor)&&connectedCart==null)connectedCart=secondary(world,pos);
        return Pair.of(new StructureBlockInfo(pos,CartAssemblerBlock.createAnchor(info.state()),null),null);
    }
    @Override protected boolean shouldMoveBlockEntity(Level world,BlockPos local,BlockState state,BlockEntity source){return !state.is(AllBlocks.CART_ASSEMBLER)&&!state.is(AllBlocks.MINECART_ANCHOR)&&super.shouldMoveBlockEntity(world,local,state,source);}
    @Override protected boolean movementAllowed(BlockState state,Level world,BlockPos pos){
        if(!pos.equals(anchor)&&state.is(AllBlocks.CART_ASSEMBLER))return secondary(world,pos)!=null;
        return super.movementAllowed(state,world,pos);
    }
    private AbstractMinecart secondary(Level world,BlockPos pos){
        if(anchor.getY()!=pos.getY()||anchor.getX()!=pos.getX()&&anchor.getZ()!=pos.getZ()||!LoadedEntityAccess.isReady(world,pos))return null;
        for(var cart:world.getEntitiesOfClass(AbstractMinecart.class,new AABB(pos))){
            if(!CartAssemblerBlock.canAssembleTo(cart)||!cart.getPassengers().isEmpty())continue;
            var controller=CapabilityMinecartController.getIfPresent(world,cart.getUUID());
            if(controller==null||controller.ownsPackedReceipt()||controller.isCoupledThroughContraption()||controller.isFullyCoupled())continue;
            return cart;
        }
        return null;
    }
    @Override public CompoundTag writeNBT(HolderLookup.Provider registries,boolean spawnPacket){var tag=super.writeNBT(registries,spawnPacket);tag.putString("RotationMode",rotationMode.name());return tag;}
    @Override public void readNBT(Level world,CompoundTag tag,boolean spawnData){rotationMode=CartMovementMode.valueOf(tag.getStringOr("RotationMode","ROTATE").toUpperCase(Locale.ROOT));super.readNBT(world,tag,spawnData);}
    @Override protected boolean customBlockPlacement(LevelAccessor world,BlockPos pos,BlockState state){return state.is(AllBlocks.MINECART_ANCHOR);}
    @Override protected boolean customBlockRemoval(LevelAccessor world,BlockPos pos,BlockState state){return state.is(AllBlocks.MINECART_ANCHOR);}
    @Override public boolean canBeStabilized(Direction facing,BlockPos localPos){return true;}
    private boolean isMountedBase(AbstractMinecart actual){
        if(entity.getVehicle()==actual)return true;
        if(entity instanceof OrientedContraptionEntity oriented){var pair=oriented.getCoupledCartsIfPresent();return pair!=null&&(pair.getFirst().cart()==actual||pair.getSecond().cart()==actual);}
        return false;
    }
    /** Actual Container remains the sole native cargo owner. No inventory is copied into mounted storage. */
    public void addExtraInventories(Entity cart){
        if(!(cart instanceof AbstractMinecart actual)||!(cart instanceof Container nativeContainer))return;
        var inventory=ContainerStorage.of(nativeContainer,null);
        var guarded=new MountedStorageAccess<ItemVariant>(ItemVariant.blank()){
            @Override protected Storage<ItemVariant> delegate(){return inventory;}
            @Override public Iterator<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<ItemVariant>> iterator(){return isAvailable()?super.iterator():Collections.emptyIterator();}
        };
        guarded.bindOwner(()->entity!=null&&entity.hasOwnedStructure()&&isMountedBase(actual)
            &&actual.level() instanceof ServerLevel server&&server.getEntity(actual.getUUID())==actual
            &&LoadedEntityAccess.isReady(server,actual.blockPosition())&&!actual.isRemoved()
            &&Optional.ofNullable(CapabilityMinecartController.getIfPresent(server,actual.getUUID())).filter(controller->!controller.ownsPackedReceipt()).isPresent());
        getStorage().attachExternal(guarded);
    }
}
