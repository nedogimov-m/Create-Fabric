package com.simibubi.create.test.mixin;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelEffectPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact source receiver method selected from the actual canonical class bootstrap. */
@Mixin(value=FactoryPanelConnectionHandler.class,remap=false)
abstract class FactoryPanelEffectReceiverTraceMixin {
    @Unique private static final BlockPos createTest$ceiling=new BlockPos(1722,7,5);
    @Unique private static String createTest$identity(Object value) {
        return value==null?"null":value.getClass().getName()+"@"+Integer.toHexString(System.identityHashCode(value));
    }

    @Inject(method="lambda$register$0(Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelEffectPacket;Lnet/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking$Context;)V",at=@At("HEAD"))
    private static void createTest$beforeEffectReceiver(FactoryPanelEffectPacket packet,ClientPlayNetworking.Context context,CallbackInfo callback) {
        if(!packet.to().pos().equals(createTest$ceiling)||packet.to().slot()!=PanelSlot.TOP_RIGHT)return;
        var level=context.client().level;
        var chunk=level==null?null:level.getChunkSource().getChunkNow(createTest$ceiling.getX()>>4,createTest$ceiling.getZ()>>4);
        var nativeOwner=chunk==null?null:chunk.getBlockEntities().get(createTest$ceiling);
        var panel=nativeOwner instanceof FactoryPanelBlockEntity owner?owner.panels.get(PanelSlot.TOP_RIGHT):null;
        var connections=new StringBuilder();
        if(panel!=null)for(var entry:panel.targetedBy.entrySet()) {
            if(!connections.isEmpty())connections.append("; ");
            connections.append(entry.getKey()).append('=').append(createTest$identity(entry.getValue()))
                .append("{success=").append(entry.getValue().success).append('}');
        }
        Create.LOGGER.info("Factory panel pulse receiver ENTRY: wallMs={}, nanoTime={}, thread={}, gameTime={}, level={}, packet={}, from={}, to={}, success={}, nativeChunk={}, nativeOwnerMap={}, nativeOwner={}, nativePanel={}, panelActive={}, targetedBy={}, connections=[{}]",
            System.currentTimeMillis(),System.nanoTime(),Thread.currentThread().getName(),level==null?-1:level.getGameTime(),
            createTest$identity(level),createTest$identity(packet),packet.from(),packet.to(),packet.success(),
            createTest$identity(chunk),createTest$identity(chunk==null?null:chunk.getBlockEntities()),
            createTest$identity(nativeOwner),createTest$identity(panel),panel==null?null:panel.active,
            createTest$identity(panel==null?null:panel.targetedBy),connections);
    }
}
