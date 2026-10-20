package com.simibubi.create.content.logistics.redstoneRequester;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.stockTicker.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class RedstoneRequesterBlockEntity extends StockCheckingBlockEntity {
    public boolean allowPartialRequests,lastRequestSucceeded;
    public PackageOrderWithCrafts encodedRequest=PackageOrderWithCrafts.empty();
    public String encodedTargetAdress="";
    private boolean redstonePowered;
    public RedstoneRequesterBlockEntity(BlockPos pos,BlockState state) { super(AllBlockEntityTypes.REDSTONE_REQUESTER,pos,state); }
    public boolean isRedstonePowered() { return redstonePowered; }
    public void onRedstonePowerChanged() {
        if(level==null||level.isClientSide())return;
        boolean next=com.simibubi.create.foundation.utility.LoadedLevelAccess.getBestNeighborSignal(level,worldPosition)>0;
        if(next==redstonePowered)return;
        lastRequestSucceeded=false;
        if(next)triggerRequest();
        redstonePowered=next;
        notifyUpdate();
    }
    public void triggerRequest() {
        if(level==null||level.isClientSide()||encodedRequest.isEmpty()||Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)return;
        var requested=new InventorySummary();encodedRequest.stacks().forEach(requested::add);
        var available=getAccurateSummary();boolean anySucceeded=false;
        for(var entry:requested.getStacks()){
            if(available.getCountOf(entry.stack)>=entry.count){anySucceeded=true;continue;}
            if(!allowPartialRequests){sendEffect(false);return;}
        }
        // Source comparator semantics report an accepted trigger, including a partial empty order.
        broadcastPackageRequest(RequestType.REDSTONE,encodedRequest,null,encodedTargetAdress);
        sendEffect(anySucceeded);lastRequestSucceeded=true;notifyUpdate();
    }
    private void sendEffect(boolean success) { if(level instanceof ServerLevel server)for(var player:PlayerLookup.tracking(server,worldPosition))ServerPlayNetworking.send(player,new RedstoneRequesterEffectPacket(worldPosition,success)); }
    public void playEffect(boolean success) {
        if(level==null||!level.isClientSide())return;var point=Vec3.atCenterOf(worldPosition);
        level.playLocalSound(worldPosition,success?AllSoundEvents.CONFIRM:AllSoundEvents.DENY,SoundSource.PLAYERS,.5f,success?1.5f:1,false);
        if(success){level.playLocalSound(worldPosition,AllSoundEvents.STOCK_LINK,SoundSource.BLOCKS,1,1,false);level.addParticle(AllParticleTypes.WIFI,point.x,point.y,point.z,1,1,1);}else level.addParticle(ParticleTypes.ENCHANTED_HIT,point.x,point.y+1,point.z,0,0,0);
    }
    @Override public void notifyUpdate() { super.notifyUpdate();if(level!=null&&!level.isClientSide())level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock()); }
    @Override protected void write(ValueOutput out, boolean clientPacket) { super.write(out, clientPacket);out.putBoolean("Powered",redstonePowered);out.putBoolean("Success",lastRequestSucceeded);out.putBoolean("AllowPartial",allowPartialRequests);out.store("EncodedRequest",PackageOrderWithCrafts.CODEC,encodedRequest);out.putString("EncodedAddress",encodedTargetAdress); }
    @Override protected void read(ValueInput in, boolean clientPacket) { super.read(in, clientPacket);redstonePowered=in.getBooleanOr("Powered",false);lastRequestSucceeded=in.getBooleanOr("Success",false);allowPartialRequests=in.getBooleanOr("AllowPartial",false);encodedRequest=in.read("EncodedRequest",PackageOrderWithCrafts.CODEC).orElseGet(PackageOrderWithCrafts::empty);encodedTargetAdress=in.getStringOr("EncodedAddress",""); }
}
