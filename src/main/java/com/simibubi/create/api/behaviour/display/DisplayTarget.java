package com.simibubi.create.api.behaviour.display;

import com.simibubi.create.AllDisplayTargets;
import com.simibubi.create.api.registry.*;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;

/** Actual target registry/ownership/text contract, including native persisted vanilla-BE reservations. */
public abstract class DisplayTarget {
    public static final SimpleRegistry<Block,DisplayTarget> BY_BLOCK=SimpleRegistry.create();
    public static final SimpleRegistry<BlockEntityType<?>,DisplayTarget> BY_BLOCK_ENTITY=SimpleRegistry.create();
    public abstract void acceptText(int line,List<MutableComponent> text,DisplayLinkContext context);
    public abstract DisplayTargetStats provideStats(DisplayLinkContext context);
    public AABB getMultiblockBounds(LevelAccessor level,BlockPos pos){var shape=LoadedLevelAccess.blockState(level,pos).getShape(level,pos);return shape.isEmpty()?new AABB(pos):shape.bounds().move(pos);}
    public Component getLineOptionText(int line){return Component.translatable("create.display_target.line",line+1);}
    public static void reserve(int line,BlockEntity target,DisplayLinkContext context){if(line>0&&line<256)((DisplayTargetReservations)target).create$displayReservations().claim(line,context.blockEntity().getBlockPos(),target);}
    public boolean isReserved(int line,BlockEntity target,DisplayLinkContext context){return ((DisplayTargetReservations)target).create$displayReservations().isReserved(line,target,context);}
    public void release(DisplayLinkContext context){var target=context.getTargetBlockEntity();if(target!=null)((DisplayTargetReservations)target).create$displayReservations().release(context.blockEntity().getBlockPos(),target);}
    public boolean requiresComponentSanitization(){return false;}
    public static DisplayTarget get(Identifier id){if(id==null)return null;var legacy="create".equals(id.getNamespace())?AllDisplayTargets.LEGACY_NAMES.get(id.getPath()):null;return legacy!=null?legacy:CreateBuiltInRegistries.DISPLAY_TARGET.getValue(id);}
    public static DisplayTarget get(LevelAccessor level,BlockPos pos){
        if(level instanceof net.minecraft.world.level.Level world&&!LoadedLevelAccess.isLoaded(world,pos))return null;var state=LoadedLevelAccess.blockState(level,pos);var direct=BY_BLOCK.get(state.getBlock());if(direct!=null)return direct;var be=LoadedLevelAccess.blockEntity(level,pos);if(be==null)return null;var byBe=BY_BLOCK_ENTITY.get(be.getType());return byBe!=null?byBe:be instanceof SignBlockEntity?AllDisplayTargets.SIGN:null;
    }
}
