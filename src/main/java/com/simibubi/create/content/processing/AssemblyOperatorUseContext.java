package com.simibubi.create.content.processing;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Original placement context marks the real support offset and downward deployer orientation. */
public class AssemblyOperatorUseContext extends BlockPlaceContext {
    public AssemblyOperatorUseContext(Level level,@Nullable Player player,InteractionHand hand,ItemStack stack,BlockHitResult hit){super(level,player,hand,stack,hit);}
}
