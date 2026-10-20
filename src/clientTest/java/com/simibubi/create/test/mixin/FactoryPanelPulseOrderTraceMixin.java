package com.simibubi.create.test.mixin;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact fixture observation only: never writes a field, decodes input or changes packet flow. */
@Mixin(value=FactoryPanelBehaviour.class,remap=false)
abstract class FactoryPanelPulseOrderTraceMixin {
    @Shadow private float previousBulb;
    @Shadow private float bulb;
    @Unique private static final BlockPos createTest$ceiling=new BlockPos(1722,7,5);

    @Unique private static String createTest$identity(Object value) {
        return value==null?"null":value.getClass().getName()+"@"+Integer.toHexString(System.identityHashCode(value));
    }

    @Unique private void createTest$trace(String phase,FactoryPanelPosition from,Boolean success,Object input) {
        var self=(FactoryPanelBehaviour)(Object)this;
        if(self.slot!=PanelSlot.TOP_RIGHT||!self.getPos().equals(createTest$ceiling))return;
        var level=self.getWorld();
        var chunk=level==null?null:level.getChunkSource().getChunkNow(createTest$ceiling.getX()>>4,createTest$ceiling.getZ()>>4);
        var nativeOwner=chunk==null?null:chunk.getBlockEntities().get(createTest$ceiling);
        var nativePanel=nativeOwner instanceof FactoryPanelBlockEntity owner?owner.panels.get(PanelSlot.TOP_RIGHT):null;
        var connections=new StringBuilder();
        for(var entry:self.targetedBy.entrySet()) {
            var connection=entry.getValue();
            if(!connections.isEmpty())connections.append("; ");
            connections.append(entry.getKey()).append("=").append(createTest$identity(connection))
                .append("{from=").append(connection.from).append(",success=").append(connection.success)
                .append(",amount=").append(connection.amount).append('}');
        }
        boolean trackingObserved=level instanceof ServerLevel&&phase.startsWith("pulse-");
        var recipients=trackingObserved
            ?PlayerLookup.tracking((ServerLevel)level,new ChunkPos(createTest$ceiling.getX()>>4,createTest$ceiling.getZ()>>4))
                .stream().map(player->player.getUUID().toString()).sorted().toList():java.util.List.<String>of();
        Create.LOGGER.info("Factory panel pulse order: phase={}, wallMs={}, nanoTime={}, thread={}, side={}, gameTime={}, level={}, dimension={}, pos={}, slot={}, panel={}, owner={}, removed={}, targetedBy={}, connections=[{}], from={}, success={}, previousBulb={}, bulb={}, input={}, nativeChunk={}, nativeOwnerMap={}, nativeOwner={}, nativePanel={}, nativeTargetedBy={}, currentOwner={}, currentPanel={}, nativeOwnerMapSize={}, trackingChunkCount={}, trackingChunkRecipientUUIDs={}",
            phase,System.currentTimeMillis(),System.nanoTime(),Thread.currentThread().getName(),
            level==null?"NO_LEVEL":level.isClientSide()?"CLIENT":"SERVER",level==null?-1:level.getGameTime(),
            createTest$identity(level),level==null?null:level.dimension(),self.getPos(),self.slot,
            createTest$identity(self),createTest$identity(self.blockEntity),self.blockEntity.isRemoved(),
            createTest$identity(self.targetedBy),connections,from,success,previousBulb,bulb,createTest$identity(input),
            createTest$identity(chunk),createTest$identity(chunk==null?null:chunk.getBlockEntities()),
            createTest$identity(nativeOwner),createTest$identity(nativePanel),createTest$identity(nativePanel==null?null:nativePanel.targetedBy),
            nativeOwner==self.blockEntity,nativePanel==self,chunk==null?-1:chunk.getBlockEntities().size(),
            trackingObserved?recipients.size():-1,recipients);
    }

    @Inject(method="pulse",at=@At("HEAD"))
    private void createTest$beforePulse(FactoryPanelPosition from,boolean success,CallbackInfo callback) {
        createTest$trace("pulse-HEAD",from,success,null);
    }
    @Inject(method="pulse",at=@At("RETURN"))
    private void createTest$afterPulse(FactoryPanelPosition from,boolean success,CallbackInfo callback) {
        createTest$trace("pulse-RETURN",from,success,null);
    }
    @Inject(method="receivePulse",at=@At("HEAD"))
    private void createTest$beforeReceivePulse(FactoryPanelPosition from,boolean success,CallbackInfo callback) {
        createTest$trace("receivePulse-HEAD",from,success,null);
    }
    @Inject(method="receivePulse",at=@At("RETURN"))
    private void createTest$afterReceivePulse(FactoryPanelPosition from,boolean success,CallbackInfo callback) {
        createTest$trace("receivePulse-RETURN",from,success,null);
    }
    @Inject(method="read(Lnet/minecraft/world/level/storage/ValueInput;)V",at=@At("HEAD"))
    private void createTest$beforeRead(ValueInput input,CallbackInfo callback) {
        createTest$trace("read-HEAD",null,null,input);
    }
    @Inject(method="read(Lnet/minecraft/world/level/storage/ValueInput;)V",at=@At("RETURN"))
    private void createTest$afterRead(ValueInput input,CallbackInfo callback) {
        createTest$trace("read-RETURN",null,null,input);
    }
}
