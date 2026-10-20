package com.simibubi.create.content.schematics;

import com.simibubi.create.*;
import java.io.*;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Actual source schematic components/settings; files are decoded through native structure APIs. */
public final class SchematicItem extends Item {
    public SchematicItem(Properties properties){super(properties);}
    public static ItemStack create(Level level,String file,String owner){
        var stack=new ItemStack(AllItems.SCHEMATIC);stack.set(AllDataComponents.SCHEMATIC_DEPLOYED,false);stack.set(AllDataComponents.SCHEMATIC_OWNER,SchematicFiles.ownerName(owner));stack.set(AllDataComponents.SCHEMATIC_FILE,SchematicFiles.fileName(file));stack.set(AllDataComponents.SCHEMATIC_ANCHOR,BlockPos.ZERO);stack.set(AllDataComponents.SCHEMATIC_ROTATION,Rotation.NONE);stack.set(AllDataComponents.SCHEMATIC_MIRROR,Mirror.NONE);writeSize(level,stack);return stack;
    }
    public static void writeSize(Level level,ItemStack blueprint){blueprint.set(AllDataComponents.SCHEMATIC_BOUNDS,loadSchematic(level,blueprint).getSize());blueprint.remove(AllDataComponents.SCHEMATIC_HASH);}
    public static StructurePlaceSettings getSettings(ItemStack blueprint){return getSettings(blueprint,true);}
    public static StructurePlaceSettings getSettings(ItemStack blueprint,boolean processNbt){var settings=new StructurePlaceSettings().setRotation(blueprint.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION,Rotation.NONE)).setMirror(blueprint.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR,Mirror.NONE));if(processNbt)settings.addProcessor(SchematicProcessor.INSTANCE);return settings;}
    public static StructureTemplate loadSchematic(Level level,ItemStack blueprint){
        var file=blueprint.get(AllDataComponents.SCHEMATIC_FILE);var owner=blueprint.get(AllDataComponents.SCHEMATIC_OWNER);if(file==null||owner==null)throw new IllegalArgumentException("Schematic item lacks its actual source file/owner");
        try{var root=level.isClientSide()?SchematicFiles.localRoot():SchematicFiles.uploadedRoot().resolve(SchematicFiles.ownerName(owner));return SchematicFiles.read(level,SchematicFiles.contained(root,file));}catch(IOException failure){throw new UncheckedIOException("Unable to read the original native schematic "+file,failure);}
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> tooltip,TooltipFlag flag){var file=stack.get(AllDataComponents.SCHEMATIC_FILE);tooltip.accept(file==null?Component.translatable("create.schematic.invalid").withStyle(ChatFormatting.RED):Component.literal(file).withStyle(ChatFormatting.GOLD));super.appendHoverText(stack,context,display,tooltip,flag);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){return hand==InteractionHand.MAIN_HAND&&player.isShiftKeyDown()&&player.getMainHandItem().has(AllDataComponents.SCHEMATIC_FILE)?InteractionResult.SUCCESS:super.use(level,player,hand);}
    @Override public InteractionResult useOn(UseOnContext context){return context.getPlayer()!=null&&context.getHand()==InteractionHand.MAIN_HAND&&context.getPlayer().isShiftKeyDown()&&context.getItemInHand().has(AllDataComponents.SCHEMATIC_FILE)?InteractionResult.SUCCESS:super.useOn(context);}
}
