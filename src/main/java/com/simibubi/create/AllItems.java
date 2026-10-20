package com.simibubi.create;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import com.simibubi.create.content.kinetics.gearbox.VerticalGearboxItem;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.equipment.sandPaper.SandPaperItem;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockItem;
import java.util.function.Function;
import com.simibubi.create.content.equipment.armor.*;
import net.minecraft.world.item.equipment.*;

public final class AllItems {
    public static final com.simibubi.create.content.legacy.ChromaticCompoundItem CHROMATIC_COMPOUND=(com.simibubi.create.content.legacy.ChromaticCompoundItem)item("chromatic_compound",p->new com.simibubi.create.content.legacy.ChromaticCompoundItem(p.rarity(net.minecraft.world.item.Rarity.UNCOMMON).stacksTo(16)));
    public static final com.simibubi.create.content.legacy.ShadowSteelItem SHADOW_STEEL=(com.simibubi.create.content.legacy.ShadowSteelItem)item("shadow_steel",p->new com.simibubi.create.content.legacy.ShadowSteelItem(p.rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final com.simibubi.create.content.legacy.RefinedRadianceItem REFINED_RADIANCE=(com.simibubi.create.content.legacy.RefinedRadianceItem)item("refined_radiance",p->new com.simibubi.create.content.legacy.RefinedRadianceItem(p.rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final Item MINECART_COUPLING=item("minecart_coupling",com.simibubi.create.content.contraptions.minecart.MinecartCouplingItem::new);
    public static final Item MINECART_CONTRAPTION=item("minecart_contraption",p->com.simibubi.create.content.contraptions.mounted.MinecartContraptionItem.rideable(p.overrideDescription("item.create.minecart_contraption")));
    public static final Item CHEST_MINECART_CONTRAPTION=item("chest_minecart_contraption",p->com.simibubi.create.content.contraptions.mounted.MinecartContraptionItem.chest(p.overrideDescription("item.create.minecart_contraption")));
    public static final Item FURNACE_MINECART_CONTRAPTION=item("furnace_minecart_contraption",p->com.simibubi.create.content.contraptions.mounted.MinecartContraptionItem.furnace(p.overrideDescription("item.create.minecart_contraption")));
    public static final com.simibubi.create.content.equipment.blueprint.BlueprintItem CRAFTING_BLUEPRINT=(com.simibubi.create.content.equipment.blueprint.BlueprintItem)item("crafting_blueprint",com.simibubi.create.content.equipment.blueprint.BlueprintItem::new);
    public static final com.simibubi.create.content.equipment.TreeFertilizerItem TREE_FERTILIZER=(com.simibubi.create.content.equipment.TreeFertilizerItem)item("tree_fertilizer",com.simibubi.create.content.equipment.TreeFertilizerItem::new);
    public static final com.simibubi.create.content.equipment.tool.CardboardSwordItem CARDBOARD_SWORD=(com.simibubi.create.content.equipment.tool.CardboardSwordItem)item("cardboard_sword",com.simibubi.create.content.equipment.tool.CardboardSwordItem::new);
    public static final BacktankItem.BacktankBlockItem COPPER_BACKTANK_PLACEABLE=(BacktankItem.BacktankBlockItem)item("copper_backtank_placeable",p->new BacktankItem.BacktankBlockItem(AllBlocks.COPPER_BACKTANK,()->AllItems.COPPER_BACKTANK,p.stacksTo(1)));
    public static final BacktankItem.BacktankBlockItem NETHERITE_BACKTANK_PLACEABLE=(BacktankItem.BacktankBlockItem)item("netherite_backtank_placeable",p->new BacktankItem.BacktankBlockItem(AllBlocks.NETHERITE_BACKTANK,()->AllItems.NETHERITE_BACKTANK,p.stacksTo(1)));
    public static final BacktankItem COPPER_BACKTANK=(BacktankItem)item("copper_backtank",p->new BacktankItem(AllArmorMaterials.COPPER,p,Create.asResource("copper_diving"),()->COPPER_BACKTANK_PLACEABLE));
    public static final BacktankItem NETHERITE_BACKTANK=(BacktankItem)item("netherite_backtank",p->new BacktankItem.Layered(ArmorMaterials.NETHERITE,p.fireResistant(),Create.asResource("netherite_diving"),()->NETHERITE_BACKTANK_PLACEABLE));
    public static final DivingHelmetItem COPPER_DIVING_HELMET=(DivingHelmetItem)item("copper_diving_helmet",p->new DivingHelmetItem(AllArmorMaterials.COPPER,p.durability(ArmorType.HELMET.getDurability(7)),Create.asResource("copper_diving")));
    public static final DivingHelmetItem NETHERITE_DIVING_HELMET=(DivingHelmetItem)item("netherite_diving_helmet",p->new DivingHelmetItem(ArmorMaterials.NETHERITE,p.fireResistant().durability(ArmorType.HELMET.getDurability(37)),Create.asResource("netherite_diving")));
    public static final DivingBootsItem COPPER_DIVING_BOOTS=(DivingBootsItem)item("copper_diving_boots",p->new DivingBootsItem(AllArmorMaterials.COPPER,p.durability(ArmorType.BOOTS.getDurability(7)),Create.asResource("copper_diving")));
    public static final DivingBootsItem NETHERITE_DIVING_BOOTS=(DivingBootsItem)item("netherite_diving_boots",p->new DivingBootsItem(ArmorMaterials.NETHERITE,p.fireResistant().durability(ArmorType.BOOTS.getDurability(37)),Create.asResource("netherite_diving")));
    public static final CardboardArmorItem CARDBOARD_HELMET=(CardboardArmorItem)item("cardboard_helmet",p->new CardboardArmorItem(ArmorType.HELMET,p));
    public static final CardboardArmorItem CARDBOARD_CHESTPLATE=(CardboardArmorItem)item("cardboard_chestplate",p->new CardboardArmorItem(ArmorType.CHESTPLATE,p));
    public static final CardboardArmorItem CARDBOARD_LEGGINGS=(CardboardArmorItem)item("cardboard_leggings",p->new CardboardArmorItem(ArmorType.LEGGINGS,p));
    public static final CardboardArmorItem CARDBOARD_BOOTS=(CardboardArmorItem)item("cardboard_boots",p->new CardboardArmorItem(ArmorType.BOOTS,p));
    public static final Item EMPTY_SCHEMATIC=item("empty_schematic");
    public static final Item SCHEMATIC_AND_QUILL=item("schematic_and_quill",p->new com.simibubi.create.content.schematics.SchematicAndQuillItem(p.stacksTo(1)));
    public static final Item SCHEMATIC=item("schematic",p->new com.simibubi.create.content.schematics.SchematicItem(p.stacksTo(1)));
    public static final Item WAND_OF_SYMMETRY=item("wand_of_symmetry",com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem::new);
    public static final Item EXTENDO_GRIP=item("extendo_grip",com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem::new);
    public static final com.simibubi.create.content.equipment.zapper.terrainzapper.WorldshaperItem WORLDSHAPER=(com.simibubi.create.content.equipment.zapper.terrainzapper.WorldshaperItem)item("handheld_worldshaper",p->new com.simibubi.create.content.equipment.zapper.terrainzapper.WorldshaperItem(p.rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final Item POTATO_CANNON=item("potato_cannon",com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem::new);
    public static final Item SCHEDULE=item("schedule",com.simibubi.create.content.trains.schedule.ScheduleItem::new);
    public static final Item INCOMPLETE_TRACK=item("incomplete_track",com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new);
    public static final Item LINKED_CONTROLLER=item("linked_controller",com.simibubi.create.content.redstone.link.controller.LinkedControllerItem::new);
    public static final Item SHOPPING_LIST=item("shopping_list",com.simibubi.create.content.logistics.tableCloth.ShoppingListItem::new);
    public static final Item TRANSMITTER=item("transmitter");
    public static final Item ANDESITE_ALLOY = item("andesite_alloy");
    public static final Item WHEAT_FLOUR = item("wheat_flour");
    public static final Item VERTICAL_GEARBOX = item("vertical_gearbox", VerticalGearboxItem::new);
    public static final Item WRENCH = item("wrench", WrenchItem::new);
    public static final Item SUPER_GLUE = item("super_glue", com.simibubi.create.content.contraptions.glue.SuperGlueItem::new);
    public static final Item GOGGLES=item("goggles",com.simibubi.create.content.equipment.goggles.GogglesItem::new);
    public static final Item FILTER=item("filter",com.simibubi.create.content.logistics.filter.ListFilterItem::new);
    public static final Item ATTRIBUTE_FILTER=item("attribute_filter",com.simibubi.create.content.logistics.filter.AttributeFilterItem::new);
    public static final Item PACKAGE_FILTER=item("package_filter",com.simibubi.create.content.logistics.filter.PackageFilterItem::new);
    static {for(var style:com.simibubi.create.content.logistics.box.PackageStyles.STYLES)item(style.getItemId().getPath(),properties->new com.simibubi.create.content.logistics.box.PackageItem(properties.stacksTo(1),style));}
    public static final Item GOLDEN_SHEET = item("golden_sheet");
    public static final Item IRON_SHEET = item("iron_sheet");
    public static final Item COPPER_SHEET = item("copper_sheet");
    public static final Item BRASS_SHEET = item("brass_sheet");
    public static final Item BRASS_INGOT = item("brass_ingot");
    public static final Item ELECTRON_TUBE = item("electron_tube");
    public static final Item PRECISION_MECHANISM=item("precision_mechanism");
    public static final Item INCOMPLETE_PRECISION_MECHANISM=item("incomplete_precision_mechanism",com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new);
    public static final Item BRASS_HAND=item("brass_hand");
    public static final Item STURDY_SHEET=item("sturdy_sheet");
    public static final Item UNPROCESSED_OBSIDIAN_SHEET=item("unprocessed_obsidian_sheet",com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new);
    public static final Item ROSE_QUARTZ = item("rose_quartz");
    public static final Item POLISHED_ROSE_QUARTZ = item("polished_rose_quartz");
    public static final Item PULP = item("pulp");
    public static final Item CARDBOARD = item("cardboard", p -> new Item(cookingFuel(p, "cardboard")));
    public static final Item SAND_PAPER = item("sand_paper", SandPaperItem::new);
    public static final Item RED_SAND_PAPER = item("red_sand_paper", SandPaperItem::new);
    public static final Item WHISK = item("whisk");
    public static final Item DOUGH = item("dough");
    public static final Item BLAZE_BURNER = item("blaze_burner", p -> new BlazeBurnerBlockItem(AllBlocks.BLAZE_BURNER, p.useBlockDescriptionPrefix(), true));
    public static final Item EMPTY_BLAZE_BURNER = item("empty_blaze_burner", p -> new BlazeBurnerBlockItem(AllBlocks.BLAZE_BURNER, p, false));
    public static final Item BLAZE_CAKE = item("blaze_cake", p -> new Item(cookingFuel(p, "blaze_cake")));
    public static final Item CREATIVE_BLAZE_CAKE = item("creative_blaze_cake", p -> new Item(cookingFuel(p, "creative_blaze_cake").rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final Item ZINC_INGOT = item("zinc_ingot");
    public static final Item ZINC_NUGGET = item("zinc_nugget");
    public static final Item RAW_ZINC = item("raw_zinc");
    public static final Item BRASS_NUGGET = item("brass_nugget");
    public static final Item PROPELLER = item("propeller");
    public static final Item BELT_CONNECTOR = item("belt_connector", com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem::new);
    public static final Item CRUSHED_IRON = item("crushed_raw_iron");
    public static final Item CRUSHED_GOLD = item("crushed_raw_gold");
    public static final Item CRUSHED_COPPER = item("crushed_raw_copper");
    public static final Item CRUSHED_ZINC = item("crushed_raw_zinc");
    public static final com.simibubi.create.foundation.item.TagDependentIngredientItem CRUSHED_OSMIUM = compatCrushedOre("osmium");
    public static final com.simibubi.create.foundation.item.TagDependentIngredientItem CRUSHED_PLATINUM = compatCrushedOre("platinum");
    public static final com.simibubi.create.foundation.item.TagDependentIngredientItem CRUSHED_SILVER = compatCrushedOre("silver");
    public static final com.simibubi.create.foundation.item.TagDependentIngredientItem CRUSHED_TIN = compatCrushedOre("tin");
    public static final com.simibubi.create.foundation.item.TagDependentIngredientItem CRUSHED_LEAD = compatCrushedOre("lead");
    public static final com.simibubi.create.foundation.item.TagDependentIngredientItem CRUSHED_QUICKSILVER = compatCrushedOre("quicksilver");
    public static final com.simibubi.create.foundation.item.TagDependentIngredientItem CRUSHED_BAUXITE = compatCrushedOre("aluminum");
    public static final com.simibubi.create.foundation.item.TagDependentIngredientItem CRUSHED_URANIUM = compatCrushedOre("uranium");
    public static final com.simibubi.create.foundation.item.TagDependentIngredientItem CRUSHED_NICKEL = compatCrushedOre("nickel");
    public static final java.util.List<com.simibubi.create.foundation.item.TagDependentIngredientItem> COMPAT_CRUSHED_ORES = java.util.List.of(CRUSHED_OSMIUM, CRUSHED_PLATINUM, CRUSHED_SILVER, CRUSHED_TIN, CRUSHED_LEAD, CRUSHED_QUICKSILVER, CRUSHED_BAUXITE, CRUSHED_URANIUM, CRUSHED_NICKEL);
    public static final Item COPPER_NUGGET = item("copper_nugget");
    public static final Item EXP_NUGGET = item("experience_nugget", p -> new com.simibubi.create.content.materials.ExperienceNuggetItem(p.rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final Item POWDERED_OBSIDIAN = item("powdered_obsidian");
    public static final Item CINDER_FLOUR = item("cinder_flour");
    public static final Item CRAFTER_SLOT_COVER = item("crafter_slot_cover");
    public static final Item HONEY_BUCKET=item("honey_bucket",p->new net.minecraft.world.item.BucketItem(AllFluids.HONEY,p.stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET)));
    public static final Item CHOCOLATE_BUCKET=item("chocolate_bucket",p->new net.minecraft.world.item.BucketItem(AllFluids.CHOCOLATE,p.stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET)));
    public static final Item BAR_OF_CHOCOLATE=item("bar_of_chocolate",p->new Item(p.food(new net.minecraft.world.food.FoodProperties(6,3.6f,false))));
    public static final Item SWEET_ROLL=item("sweet_roll",p->new Item(p.food(new net.minecraft.world.food.FoodProperties(6,9.6f,false))));
    public static final Item CHOCOLATE_BERRIES=item("chocolate_glazed_berries",p->new Item(p.food(new net.minecraft.world.food.FoodProperties(7,11.2f,false))));
    public static final Item HONEYED_APPLE=item("honeyed_apple",p->new Item(p.food(new net.minecraft.world.food.FoodProperties(8,12.8f,false))));
    public static final Item BLAZE_CAKE_BASE=item("blaze_cake_base");
    public static final Item BUILDERS_TEA=item("builders_tea",p->new Item(p.stacksTo(16).usingConvertsTo(net.minecraft.world.item.Items.GLASS_BOTTLE)
        .food(new net.minecraft.world.food.FoodProperties(1,1.2f,true),net.minecraft.world.item.component.Consumables.defaultDrink().consumeSeconds(42f/20).onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HASTE,3*60*20,0,false,false,false))).build())));

    /** Source fuel providers keep native normal/fast cooking rules and datapack speed settings. */
    private static Item.Properties cookingFuel(Item.Properties properties, String name) {
        return properties.component(net.minecraft.core.component.DataComponents.COOKING_FUEL,
            com.simibubi.create.foundation.item.CreateCookingFuels.component(name));
    }

    private static com.simibubi.create.foundation.item.TagDependentIngredientItem compatCrushedOre(String name) {
        var tag = net.minecraft.tags.TagKey.create(Registries.ITEM,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "ores/" + name));
        return (com.simibubi.create.foundation.item.TagDependentIngredientItem)item("crushed_raw_" + name,
            properties -> new com.simibubi.create.foundation.item.TagDependentIngredientItem(properties, tag));
    }

    private static Item item(String name) {
        return item(name, Item::new);
    }

    private static Item item(String name, Function<Item.Properties, Item> factory) {
        var key = ResourceKey.create(Registries.ITEM, Create.asResource(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
    }

    public static void register() {AllFluids.HONEY_FAMILY.bucket=HONEY_BUCKET;AllFluids.CHOCOLATE_FAMILY.bucket=CHOCOLATE_BUCKET;}

    private AllItems() {}
}
