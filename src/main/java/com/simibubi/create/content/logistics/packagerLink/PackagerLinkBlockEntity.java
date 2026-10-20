package com.simibubi.create.content.logistics.packagerLink;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.UUID;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import org.apache.commons.lang3.mutable.MutableBoolean;

public final class PackagerLinkBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider {
    public final LogisticallyLinkedBehaviour behaviour=new LogisticallyLinkedBehaviour(this,true);
    public UUID placedBy;
    private boolean initialized;
    private float previousGlow,glow;
    private int pulse;
    public PackagerLinkBlockEntity(BlockPos pos,BlockState state) { super(AllBlockEntityTypes.PACKAGER_LINK,pos,state); }
    public void initialize() { super.initialize(); if(level==null||isRemoved()||com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,worldPosition)!=this)return;initialized=true;if(!level.isClientSide())behaviour.redstonePowerChanged(PackagerLinkBlock.getPower(getBlockState(),level,worldPosition));var packager=getAttachedPackager();if(packager!=null)packager.recheckIfLinksPresent(); }
    public static void tick(Level level,BlockPos pos,BlockState state,PackagerLinkBlockEntity link) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, link)) {
            if (!createTick.shouldTick()) return;
 if(level.isClientSide()){link.previousGlow=link.glow;link.glow*=.5f;} 
        }
    }
    public PackagerBlockEntity getAttachedPackager() { if(level==null)return null;var source=worldPosition.relative(PackagerLinkBlock.getConnectedDirection(getBlockState()).getOpposite());return LoadedLevelAccess.blockEntity(level,source) instanceof PackagerBlockEntity packager&&!(packager instanceof RepackagerBlockEntity)?packager:null; }
    public PackagerBlockEntity getPackager() { return behaviour.redstonePower==15?null:getAttachedPackager(); }
    public InventorySummary fetchSummaryFromPackager(IdentifiedInventory ignored) { var packager=getPackager();return packager==null||packager.isTargetingSameInventory(ignored)?InventorySummary.EMPTY:packager.getAvailableItems(); }
    public Pair<PackagerBlockEntity,PackagingRequest> processRequest(ItemStack stack,int amount,String address,int index,MutableBoolean last,int order,PackageOrderWithCrafts context,IdentifiedInventory ignored) { var packager=getPackager();if(packager==null||amount<=0||packager.isTargetingSameInventory(ignored))return null;int count=Math.min(amount,packager.getAvailableItems().getCountOf(stack));return count<=0?null:Pair.of(packager,PackagingRequest.create(stack,count,address,index,last,0,order,context)); }
    public void notifyUpdate() { setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS); }
    public void flash() { pulse++;notifyUpdate(); }
    public float getGlow(float partial) { return previousGlow+(glow-previousGlow)*partial; }
    @Override public void invalidate() { initialized=false;super.invalidate(); }
    @Override protected void write(ValueOutput out, boolean clientPacket) { super.write(out, clientPacket);if(placedBy!=null)out.store("PlacedBy",UUIDUtil.CODEC,placedBy); }
    @Override protected void read(ValueInput in, boolean clientPacket) { super.read(in, clientPacket);placedBy=in.read("PlacedBy",UUIDUtil.CODEC).orElse(null);int next=in.getIntOr("Pulse",pulse);if(level!=null&&level.isClientSide()&&next!=pulse){previousGlow=glow=2;level.playLocalSound(worldPosition,AllSoundEvents.STOCK_LINK,net.minecraft.sounds.SoundSource.BLOCKS,.75f,1.25f,false);var state=getBlockState();var face=state.getValue(PackagerLinkBlock.FACE);var facing=state.getValue(PackagerLinkBlock.FACING);double sign=face==net.minecraft.world.level.block.state.properties.AttachFace.FLOOR?1:-1;var origin=net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).add(facing.getStepX()*sign*.125,face==net.minecraft.world.level.block.state.properties.AttachFace.WALL?.25:0,facing.getStepZ()*sign*.125);level.addParticle(AllParticleTypes.WIFI,origin.x,origin.y,origin.z,1,face==net.minecraft.world.level.block.state.properties.AttachFace.CEILING?-1:1,1);}pulse=next; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { var out=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,registries);write(out, true);out.putInt("Pulse",pulse);return out.buildResult(); }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (behaviour != null) behaviours.add(behaviour);
    }
}
