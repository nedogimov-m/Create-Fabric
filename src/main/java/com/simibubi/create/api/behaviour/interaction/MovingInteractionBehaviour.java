package com.simibubi.create.api.behaviour.interaction;

import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

/** Public source callbacks for ordinary use and collisions with mounted blocks. */
public abstract class MovingInteractionBehaviour {
    public static final SimpleRegistry<Block,MovingInteractionBehaviour> REGISTRY=SimpleRegistry.create();
    public static <B extends Block> Consumer<B> interactionBehaviour(MovingInteractionBehaviour behaviour){return block->REGISTRY.register(block,behaviour);}
    public boolean handlePlayerInteraction(Player player,InteractionHand hand,BlockPos localPos,AbstractContraptionEntity owner){return true;}
    public void handleEntityCollision(Entity entity,BlockPos localPos,AbstractContraptionEntity owner){}
}
