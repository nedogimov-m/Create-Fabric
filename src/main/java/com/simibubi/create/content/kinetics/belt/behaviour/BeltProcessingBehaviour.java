package com.simibubi.create.content.kinetics.belt.behaviour;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source callback API for actual processors two blocks above transported stock. */
public class BeltProcessingBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<BeltProcessingBehaviour> TYPE = new BehaviourType<BeltProcessingBehaviour>()
            ;
    public enum ProcessingResult { PASS, HOLD, REMOVE }
    @FunctionalInterface public interface ProcessingCallback {
        ProcessingResult apply(TransportedItemStack stack, TransportedItemStackHandlerBehaviour inventory);
    }
    private ProcessingCallback enter = (stack, inventory) -> ProcessingResult.PASS;
    private ProcessingCallback held = (stack, inventory) -> ProcessingResult.PASS;
    public BeltProcessingBehaviour(BlockEntity entity) { super(entity); }
    public BeltProcessingBehaviour whenItemEnters(ProcessingCallback callback) { enter = callback; return this; }
    public BeltProcessingBehaviour whileItemHeld(ProcessingCallback callback) { held = callback; return this; }
    public ProcessingResult handleReceivedItem(TransportedItemStack stack, TransportedItemStackHandlerBehaviour inventory) { return enter.apply(stack, inventory); }
    public ProcessingResult handleHeldItem(TransportedItemStack stack, TransportedItemStackHandlerBehaviour inventory) { return held.apply(stack, inventory); }
    public static boolean isBlocked(BlockGetter level, BlockPos position) {
        var space = position.above(); var state = LoadedLevelAccess.blockState(level, space);
        if (state.getBlock() instanceof AbstractFunnelBlock) return false;
        return !state.getCollisionShape(level, space).isEmpty();
    }
    public static BeltProcessingBehaviour createNative(BlockEntity entity) {
        if(entity instanceof com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity press)
            return press.getPressingBehaviour();
        if (entity instanceof com.simibubi.create.content.fluids.spout.SpoutBlockEntity spout)
            return new BeltProcessingBehaviour(entity).whenItemEnters((stack, inventory) ->
                    spout.onBeltItemReceived(stack) ? ProcessingResult.HOLD : ProcessingResult.PASS)
                    .whileItemHeld((stack, inventory) -> spout.processingTicks >= 0 || spout.onBeltItemReceived(stack)
                            ? ProcessingResult.HOLD : ProcessingResult.PASS);
        if (entity instanceof com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity deployer)
            return new BeltProcessingBehaviour(entity).whenItemEnters((stack, inventory) ->
                    deployer.onBeltItemReceived(stack) ? ProcessingResult.HOLD : ProcessingResult.PASS)
                    .whileItemHeld((stack, inventory) -> deployer.tryHoldTransportedItem(stack)
                            ? ProcessingResult.HOLD : ProcessingResult.PASS);
        return null;
    }
    @Override public BehaviourType<?> getType() { return TYPE; }
}
