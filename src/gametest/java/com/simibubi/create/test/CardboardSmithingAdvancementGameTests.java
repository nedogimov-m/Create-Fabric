package com.simibubi.create.test;

import com.simibubi.create.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Real smithing table, native menu clicks, finite original inputs and successful onTake. UNRUN. */
public final class CardboardSmithingAdvancementGameTests {
    private static final BlockPos P=new BlockPos(3,3,3);
    private static void insert(GameTestHelper h,ServerPlayer player,SmithingMenu menu,int inventorySlot,int inputSlot){
        int actual=-1;for(int i=0;i<menu.slots.size();i++){var slot=menu.getSlot(i);if(slot.container==player.getInventory()&&slot.getContainerSlot()==inventorySlot){actual=i;break;}}
        h.assertTrue(actual>=0&&menu.getCarried().isEmpty(),"Actual native player inventory slot maps to the current smithing menu");
        menu.clicked(actual,0,ContainerInput.PICKUP,player);menu.clicked(inputSlot,0,ContainerInput.PICKUP,player);
        h.assertTrue(menu.getCarried().isEmpty()&&!menu.getSlot(inputSlot).getItem().isEmpty(),"Native input slot accepts the one real inventory stack through normal menu clicks");
    }
    private static void smith(GameTestHelper h,Item material,boolean crafts,boolean trimmed){
        h.setBlock(P,Blocks.SMITHING_TABLE);
        try(var session=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2))){
            var player=session.player();var armor=new ItemStack(AllItems.CARDBOARD_HELMET);armor.set(DataComponents.CUSTOM_NAME,Component.literal("Original finite smithing cardboard"));
            player.getInventory().setItem(9,new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE));player.getInventory().setItem(10,armor);player.getInventory().setItem(11,new ItemStack(material));player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var actual=h.absolutePos(P);player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(actual).add(0,0,.5),Direction.SOUTH,actual,false));
            h.assertTrue(player.containerMenu instanceof SmithingMenu,"Physical native smithing table opens the real registered player's menu");var menu=(SmithingMenu)player.containerMenu;
            insert(h,player,menu,9,0);insert(h,player,menu,10,1);
            if(crafts)insert(h,player,menu,11,2);
            else{
                int source=-1;for(int i=0;i<menu.slots.size();i++){var slot=menu.getSlot(i);if(slot.container==player.getInventory()&&slot.getContainerSlot()==11){source=i;break;}}
                h.assertTrue(source>=0,"Original unsupported material has an actual native inventory slot");menu.clicked(source,0,ContainerInput.PICKUP,player);menu.clicked(2,0,ContainerInput.PICKUP,player);
            }
            menu.createResult();
            h.assertTrue(!GameplayAdvancementGameTests.done(player,"cardboard_armor_trim"),"Inventory/input/result preview is not a completed native smithing craft");
            if(!crafts){h.assertTrue(menu.getSlot(3).getItem().isEmpty()&&!menu.getSlot(0).getItem().isEmpty()&&!menu.getSlot(1).getItem().isEmpty()&&menu.getSlot(2).getItem().isEmpty()&&menu.getCarried().is(material)&&menu.getCarried().getCount()==1,"Native input rejection retains the exact unsupported cursor material, template and armor without output, payment or award");}
            else{
                var result=menu.getSlot(3).getItem();h.assertTrue(result.is(AllItems.CARDBOARD_HELMET)&&result.has(DataComponents.TRIM)==trimmed,"Native recipe manager publishes exact supported cardboard result/component state");
                menu.clicked(3,0,ContainerInput.PICKUP,player);
                h.assertTrue(menu.getCarried().is(AllItems.CARDBOARD_HELMET)&&menu.getCarried().getCount()==1&&menu.getCarried().has(DataComponents.TRIM)==trimmed&&menu.getCarried().getHoverName().getString().equals("Original finite smithing cardboard"),"One original component-bearing cardboard result passes through actual native result-slot onTake");
                h.assertTrue(menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty()&&menu.getSlot(2).getItem().isEmpty()&&player.getInventory().getItem(9).isEmpty()&&player.getInventory().getItem(10).isEmpty()&&player.getInventory().getItem(11).isEmpty(),"Actual successful smithing consumes each finite original template, base and material once");
                h.assertTrue(GameplayAdvancementGameTests.done(player,"cardboard_armor_trim"),"Pinned callback rewards genuine successful cardboard smithing onTake without an extra result-component requirement");
            }
            player.closeContainer();
        }h.succeed();
    }
    @GameTest public void actualPaidVanillaTrimEarnsCardboardSmithingCriterion(GameTestHelper h){smith(h,Items.COPPER_INGOT,true,true);}
    @GameTest public void actualPaidDatapackCardboardTransformWithoutTrimEarnsPinnedCriterion(GameTestHelper h){smith(h,Items.STONE,true,false);}
    @GameTest public void refusedNativeSmithingNeverPaysOrAwards(GameTestHelper h){smith(h,Items.COBBLESTONE,false,false);}
}
