package com.simibubi.create.content.equipment.tool;
import com.simibubi.create.Create;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.*;
import net.minecraft.world.item.*;
/** Source cardboard uses0/speed1/damage2/enchant1 on the actual modern native tool material. */
public final class AllToolMaterials {
 public static final TagKey<Item> CARDBOARD_REPAIR=TagKey.create(Registries.ITEM,Create.asResource("cardboard_sword_repair"));
 public static final ToolMaterial CARDBOARD=new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL,0,1,2,1,CARDBOARD_REPAIR);
 private AllToolMaterials(){}
}
