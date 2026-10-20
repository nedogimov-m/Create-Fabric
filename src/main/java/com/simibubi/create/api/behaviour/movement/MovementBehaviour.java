package com.simibubi.create.api.behaviour.movement;

import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.contraptions.ContraptionSettings;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Source actor lifecycle. Native client renderers are registered separately from common logic. */
public interface MovementBehaviour {
    SimpleRegistry<Block,MovementBehaviour> REGISTRY=SimpleRegistry.create();
    static <B extends Block> Consumer<B> movementBehaviour(MovementBehaviour behaviour){return block->REGISTRY.register(block,behaviour);}
    default boolean isActive(MovementContext context){return !context.disabled;}
    default void tick(MovementContext context){}
    default void startMoving(MovementContext context){}
    default void visitNewPosition(MovementContext context,BlockPos pos){}
    default Vec3 getActiveAreaOffset(MovementContext context){return Vec3.ZERO;}
    default @Nullable ItemStack canBeDisabledVia(MovementContext context){return new ItemStack(context.state.getBlock());}
    default void onDisabledByControls(MovementContext context){cancelStall(context);}
    default boolean mustTickWhileDisabled(){return false;}
    @Deprecated(since="6.0.9",forRemoval=true)
    default void dropItem(MovementContext context,ItemStack stack){collectOrDropItem(context,stack);}
    default void collectOrDropItem(MovementContext context,ItemStack stack){
        if(context.world.isClientSide()||stack.isEmpty())return;
        var remainder=insertItem(context,stack,ContraptionSettings.moveItemsToStorage);
        if(remainder.isEmpty()||context.position==null)return;
        var p=context.position;var item=new ItemEntity(context.world,p.x,p.y,p.z,remainder);
        item.setDeltaMovement(context.motion.add(0,.5,0).scale(context.world.getRandom().nextFloat()*.3f));context.world.addFreshEntity(item);
    }
    static ItemStack insertItem(MovementContext context,ItemStack stack,boolean enabled){
        if(!enabled||stack.isEmpty())return stack.copy();
        long inserted;try(var transaction=Transaction.openOuter()){inserted=context.contraption.getStorage().getAllItems().insert(ItemVariant.of(stack),stack.getCount(),transaction);transaction.commit();}
        return stack.copyWithCount(stack.getCount()-(int)inserted);
    }
    default void onSpeedChanged(MovementContext context,Vec3 previous,Vec3 motion){}
    default void stopMoving(MovementContext context){}
    default void cancelStall(MovementContext context){context.stall=false;}
    default void writeExtraData(MovementContext context){}
    /** Apply visual consequences after a tracked actor-data delta, including while disabled. */
    default void onDataUpdated(MovementContext context){}
    default boolean disableBlockEntityRendering(){return false;}
}
