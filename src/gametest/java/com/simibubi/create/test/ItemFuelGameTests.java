package com.simibubi.create.test;

import com.simibubi.create.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Observe actual vanilla normal/blast/smoker ticks, native context providers and original finite fuel tails. */
public final class ItemFuelGameTests {
    @GameTest(maxTicks=100)
    public void sourceFuelsBurnInRealNativeFurnacesWithFastCookingReductionAndOriginalTails(GameTestHelper h) {
        Item[] fuels={AllItems.CARDBOARD,AllBlocks.CARDBOARD_BLOCK.asItem(),AllItems.BLAZE_CAKE,AllItems.CREATIVE_BLAZE_CAKE,Items.COAL};
        int[] ticks={1000,4000,6400,Integer.MAX_VALUE,1600};
        var cells=new ArrayList<BlockPos>();
        for(int x=0;x<fuels.length;x++)for(int z=0;z<3;z++){
            var pos=new BlockPos(x,1,z);cells.add(pos);
            h.setBlock(pos,(z==0?Blocks.FURNACE:z==1?Blocks.BLAST_FURNACE:Blocks.SMOKER).defaultBlockState());
            var furnace=h.getBlockEntity(pos,AbstractFurnaceBlockEntity.class);
            furnace.setItem(0,new ItemStack(z==2?Items.PORKCHOP:Items.RAW_IRON));
            var stock=new ItemStack(fuels[x],2);stock.set(DataComponents.CUSTOM_NAME,Component.literal("Original native source fuel "+x));furnace.setItem(1,stock);
        }
        h.succeedWhen(()->{
            for(var pos:cells){
                var furnace=h.getBlockEntity(pos,AbstractFurnaceBlockEntity.class);int x=pos.getX(),z=pos.getZ();
                h.assertTrue(h.getBlockState(pos).getValue(BlockStateProperties.LIT),"Actual native cooking must start from the source fuel, including the vanilla coal control");
                var tag=furnace.saveWithFullMetadata(h.getLevel().registryAccess());
                h.assertValueEqual(tag.getIntOr("lit_total_time",-1),z==0?ticks[x]:ticks[x]/2,"Native normal/fast container context preserves the pinned source burn duration");
                h.assertTrue(tag.getIntOr("lit_time_remaining",0)>0,"Actual source fuel still has genuine saved burning work");
                var tail=furnace.getItem(1);
                h.assertTrue(tail.is(fuels[x])&&tail.getCount()==1&&tail.getHoverName().equals(Component.literal("Original native source fuel "+x)),"One real original round is consumed and its named tail remains in the true native owner");
            }
        });
    }
}
