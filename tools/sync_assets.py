#!/usr/bin/env python3
"""Copy the dependency closure of ported Create models from the pinned upstream tree."""

from __future__ import annotations

import argparse
import json
from sync_armor_assets import generate as sync_armor_assets
from sync_compat_crushed_assets import with_native_conditions
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
UPSTREAM = None
OUTPUT = PROJECT / "src/main/resources"
ROOTS = []
DOORS = ("andesite_door", "brass_door", "copper_door", "train_door", "framed_glass_door")
BLOCKS = ("peculiar_bell", "haunted_bell", "desk_bell", "redstone_contact", "mechanical_roller", "portable_storage_interface", "portable_fluid_interface", "mechanical_harvester", "mechanical_plough", "redstone_requester", "factory_gauge", "stock_link", "item_vault", "creative_crate", "packager", "repackager", "cardboard_block", "content_observer", "chute", "smart_chute", "industrial_iron_block", "andesite_tunnel", "brass_tunnel", "andesite_funnel", "brass_funnel", "shaft", "cogwheel", "large_cogwheel", "creative_motor", "hand_crank", "deployer", "rotation_speed_controller", "speedometer", "stressometer", "gearshift", "clutch", "gearbox",
          "millstone", "andesite_casing", "encased_chain_drive", "adjustable_chain_gearshift", "mechanical_press", "depot", "water_wheel", "large_water_wheel", "basin", "mechanical_mixer",
          "blaze_burner", "zinc_ore", "deepslate_zinc_ore", "zinc_block", "raw_zinc_block", "brass_block", "encased_fan", "brass_casing",
          "crushing_wheel", "asurine", "crimsite", "ochrum", "veridium", "mechanical_crafter", "mechanical_drill", "mechanical_saw", "fluid_tank", "creative_fluid_tank", "fluid_pipe", "mechanical_pump", "copper_casing", "smart_fluid_pipe", "fluid_valve", "spout", "item_drain", "hose_pulley", "limestone", "scoria", "steam_engine", "steam_whistle")
COMPAT_CRUSHED_ITEMS = tuple("crushed_raw_" + name for name in ("osmium", "platinum", "silver", "tin", "lead", "quicksilver", "aluminum", "uranium", "nickel"))
PACKAGE_ITEMS = tuple(f"cardboard_package_{size}" for size in ("12x12", "10x12", "10x8", "12x10")) + tuple(f"rare_{name}_package" for name in ("creeper", "darcy", "evan", "jinx", "kryppers", "simi", "starlotte", "thunder", "up", "vector"))
SEAT_COLORS = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")
BLOCKS += tuple(f"{color}_seat" for color in SEAT_COLORS)
TOOLBOXES = tuple(f"{color}_toolbox" for color in SEAT_COLORS)
BLOCKS += TOOLBOXES
BLOCKS += (*DOORS, "contraption_controls", "stockpile_switch")
SAIL_BLOCKS = tuple(f"{color}_sail" for color in SEAT_COLORS)
BLOCKS += ("windmill_bearing", "clockwork_bearing", "sail_frame", *SAIL_BLOCKS,
           "linear_chassis", "secondary_linear_chassis", "radial_chassis", "sticker",
           "redstone_link", "lectern_controller")
BLOCKS += ("mechanical_piston", "sticky_mechanical_piston", "piston_extension_pole",
           "mechanical_piston_head", "sequenced_gearshift")
CLOTH_TYPES = (*SEAT_COLORS, "andesite", "brass", "copper")
BLOCKS += ("mechanical_bearing", "package_frogport", "chain_conveyor", "stock_ticker",) + tuple(f"{color}_table_cloth" for color in CLOTH_TYPES)
ITEMS = ("super_glue", "shopping_list", *PACKAGE_ITEMS, "transmitter", "package_filter", "attribute_filter", "filter", "andesite_alloy", "wheat_flour", "vertical_gearbox", "wrench", "goggles", "brass_hand", "precision_mechanism", "incomplete_precision_mechanism", "sturdy_sheet", "unprocessed_obsidian_sheet", "golden_sheet", "iron_sheet", "copper_sheet",
         "brass_sheet", "brass_ingot", "electron_tube", "rose_quartz", "polished_rose_quartz", "pulp", "cardboard", "sand_paper", "red_sand_paper", "whisk", "dough",
         "empty_blaze_burner", "blaze_cake", "creative_blaze_cake", "zinc_ingot", "zinc_nugget", "raw_zinc", "brass_nugget", "propeller", "belt_connector",
         "crushed_raw_iron", "crushed_raw_gold", "crushed_raw_copper", "crushed_raw_zinc", "copper_nugget", "experience_nugget", "powdered_obsidian", "cinder_flour", "crafter_slot_cover",
         "honey_bucket", "chocolate_bucket", "bar_of_chocolate", "sweet_roll", "chocolate_glazed_berries", "honeyed_apple", "builders_tea", "blaze_cake_base")
ITEMS += COMPAT_CRUSHED_ITEMS
RECIPES = (
    *[f"crafting/kinetics/{name}" for name in ("andesite_door", "brass_door", "copper_door", "contraption_controls")],
    "crafting/curiosities/peculiar_bell", "crafting/logistics/desk_bell", "haunting/haunted_bell",
    "crafting/logistics/redstone_contact", "crafting/logistics/stockpile_switch",
    "crafting/kinetics/mechanical_roller",
    "crafting/kinetics/portable_storage_interface", "crafting/kinetics/portable_fluid_interface",
    "crafting/kinetics/mechanical_harvester", "crafting/kinetics/mechanical_plough",
    "crafting/kinetics/super_glue",
    "crafting/materials/andesite_alloy",
    "crafting/kinetics/shaft", "crafting/kinetics/cogwheel", "crafting/kinetics/large_cogwheel",
    "crafting/kinetics/large_cogwheel_from_little", "crafting/kinetics/hand_crank",
    "crafting/kinetics/gearbox", "crafting/kinetics/clutch", "crafting/kinetics/gearshift", "crafting/kinetics/millstone",
    "crafting/kinetics/vertical_gearbox", "crafting/kinetics/vertical_gearbox_from_conversion", "crafting/kinetics/gearbox_from_conversion",
    "crafting/kinetics/encased_chain_drive", "crafting/kinetics/adjustable_chain_gearshift", "crafting/kinetics/mechanical_press",
    "crafting/kinetics/depot", "crafting/kinetics/wrench", "crafting/materials/electron_tube", "crafting/materials/rose_quartz",
    "crafting/kinetics/spout",
    "crafting/kinetics/item_drain",
    "crafting/kinetics/hose_pulley",
    "crafting/kinetics/steam_engine",
    "crafting/kinetics/steam_whistle",
    "crafting/kinetics/goggles",
    "crafting/kinetics/item_vault", "crafting/logistics/redstone_requester", "crafting/logistics/factory_gauge", "crafting/logistics/stock_link", "crafting/logistics/stock_link_clear", "crafting/materials/transmitter", "crafting/logistics/packager", "crafting/logistics/packager_from_conversion", "crafting/logistics/repackager_from_conversion", "crafting/materials/cardboard_block", "crafting/materials/cardboard_from_block", "crafting/logistics/content_observer",
    "crafting/kinetics/chute", "crafting/kinetics/smart_chute", "industrial_iron_block_from_ingots_iron_stonecutting",
    "crafting/logistics/andesite_tunnel", "crafting/logistics/brass_tunnel",
    "crafting/logistics/andesite_funnel", "crafting/logistics/brass_funnel",
    "crafting/kinetics/package_filter", "crafting/kinetics/deployer", "crafting/kinetics/filter", "crafting/kinetics/attribute_filter", "crafting/kinetics/brass_hand",
    "crafting/kinetics/rotation_speed_controller",
    "crafting/kinetics/speedometer", "crafting/kinetics/speedometer_from_conversion", "crafting/kinetics/stressometer_from_conversion",
    "crafting/materials/sand_paper", "crafting/materials/red_sand_paper", "sandpaper_polishing/rose_quartz",
    "crafting/kinetics/water_wheel",
    "crafting/kinetics/large_water_wheel",
    "crafting/kinetics/basin", "crafting/kinetics/mechanical_mixer", "crafting/kinetics/whisk",
    "smelting/bread", "smoking/bread", "campfire_cooking/bread",
    "crafting/kinetics/empty_blaze_burner", "crafting/materials/zinc_ingot_from_decompacting",
    "crafting/materials/zinc_nugget_from_decompacting", "crafting/materials/andesite_alloy_from_zinc",
    "crafting/materials/raw_zinc", "crafting/materials/raw_zinc_block", "crafting/materials/brass_ingot_from_decompacting",
    "crafting/materials/brass_block_from_compacting", "crafting/materials/brass_ingot_from_compacting",
    "crafting/materials/brass_nugget_from_decompacting", "crafting/materials/zinc_ingot_from_compacting",
    "crafting/materials/zinc_block_from_compacting", "smelting/zinc_ingot_from_raw_ore", "smelting/zinc_ingot_from_ore",
    "blasting/zinc_ingot_from_raw_ore", "blasting/zinc_ingot_from_ore",
    "crafting/kinetics/encased_fan", "crafting/kinetics/propeller",
    "crafting/kinetics/belt_connector",
    "crafting/materials/copper_ingot", "crafting/materials/copper_nugget",
    "smelting/iron_ingot_from_crushed", "smelting/gold_ingot_from_crushed", "smelting/copper_ingot_from_crushed", "smelting/zinc_ingot_from_crushed",
    "blasting/iron_ingot_from_crushed", "blasting/gold_ingot_from_crushed", "blasting/copper_ingot_from_crushed", "blasting/zinc_ingot_from_crushed",
    "crafting/kinetics/mechanical_crafter", "crafting/kinetics/crafter_slot_cover", "mechanical_crafting/crushing_wheel",
    "crafting/kinetics/mechanical_drill", "crafting/kinetics/mechanical_saw", "cutting/andesite_alloy", "cutting/bamboo_planks", "crafting/kinetics/fluid_tank", "crafting/kinetics/fluid_pipe", "crafting/kinetics/mechanical_pump", "crafting/kinetics/smart_fluid_pipe", "crafting/kinetics/fluid_valve",
)
ITEMS += ("extendo_grip", "potato_cannon", "wand_of_symmetry")
RECIPES += ("mechanical_crafting/extendo_grip", "mechanical_crafting/potato_cannon", "mechanical_crafting/wand_of_symmetry")
BLOCKS += ("mechanical_arm", "weighted_ejector")
RECIPES += ("crafting/kinetics/mechanical_arm", "crafting/kinetics/weighted_ejector")
copied: set[str] = set()
ITEMS += ("linked_controller", "incomplete_track")
BLOCKS += ("track", "display_link", "track_station", "track_signal")
# Native blocks with dedicated resources still participate in source tool/NBT tags.
BLOCKS += ("cuckoo_clock", "mysterious_cuckoo_clock")
RECIPES += ("crafting/kinetics/cuckoo_clock", "crafting/kinetics/mysterious_cuckoo_clock")

RESOURCE_ONLY_BLOCKS = ("elevator_pulley", "elevator_contact", "rope_pulley",
    "gantry_shaft", "gantry_carriage", "pulse_extender", "pulse_repeater",
    "pulse_timer", "powered_latch", "powered_toggle_latch", "analog_lever",
    "small_bogey", "large_bogey", "metal_girder", "metal_girder_encased_shaft",
    "nixie_tube", "placard", "display_board",
    *[f"{color}_nixie_tube" for color in SEAT_COLORS if color != "orange"])
# Dedicated native Train resources are checked in; keep their source tool/NBT/language closure.
NATIVE_TRAIN_RESOURCE_BLOCKS = ("controls", "track_observer", *[f"{color}_postbox" for color in SEAT_COLORS])
RESOURCE_ONLY_BLOCKS += NATIVE_TRAIN_RESOURCE_BLOCKS
ITEMS += ("schedule",)
RECIPES += ("crafting/logistics/display_link",)
RECIPES += ("sequenced_assembly/track",)
RECIPES += ("crafting/kinetics/track_station",)
RECIPES += ("crafting/kinetics/track_signal",)
RECIPES += tuple(f"crafting/kinetics/{name}" for name in (
    "windmill_bearing", "clockwork_bearing", "white_sail", "white_sail_from_conversion",
    "sail_frame_from_conversion", "linear_chassis", "linear_chassis_from_conversion",
    "secondary_linear_chassis_from_conversion", "radial_chassis", "sticker"))
RECIPES += ("crafting/logistics/redstone_link", "crafting/appliances/linked_controller")
RECIPES += tuple(f"crafting/kinetics/{name}" for name in (
    "mechanical_piston", "sticky_mechanical_piston", "piston_extension_pole", "sequenced_gearshift"))
RECIPES += tuple(f"crafting/kinetics/{color}_seat{suffix}" for color in SEAT_COLORS for suffix in ("", "_from_other_seat"))
RECIPES += ("crafting/logistics/package_frogport", "crafting/kinetics/chain_conveyor", "crafting/logistics/stock_ticker",)
RECIPES += ("crafting/kinetics/mechanical_bearing",)
RECIPES += tuple(f"crafting/logistics/{color}_table_cloth{suffix}" for color in SEAT_COLORS for suffix in ("", "_from_other_table_cloth"))
RECIPES += tuple(f"crafting/logistics/{color}_table_cloth_clear" for color in CLOTH_TYPES)
RECIPES += ("andesite_table_cloth_from_andesite_alloy_stonecutting", "brass_table_cloth_from_ingots_brass_stonecutting", "copper_table_cloth_from_ingots_copper_stonecutting")

# Original Schematics and its real Clipboard dependency.
SCHEMATIC_BLOCKS = ("schematic_table", "schematicannon", "clipboard")
BLOCKS += SCHEMATIC_BLOCKS
ITEMS += ("empty_schematic", "schematic_and_quill", "schematic")
RECIPES += tuple(f"crafting/schematics/{name}" for name in ("empty_schematic", "schematic_and_quill", "schematic_table", "schematicannon"))
RECIPES += ("crafting/appliances/clipboard", "crafting/appliances/clipboard_clear")
COPYCAT_BLOCKS = ("copycat_base", "copycat_step", "copycat_panel", "copycat_bars", "rose_quartz_lamp")
COPYCAT_ITEMLESS = ("copycat_base", "copycat_bars")
BLOCKS += COPYCAT_BLOCKS
RECIPES += ("copycat_step_from_ingots_zinc_stonecutting", "copycat_panel_from_ingots_zinc_stonecutting", "crafting/kinetics/rose_quartz_lamp")


from sync_kinetics26 import BLOCKS as KINETICS26_BLOCKS, sync as sync_kinetics26
BLOCKS += KINETICS26_BLOCKS


def obj_mesh(identifier: str, flip_v: bool):
    """Preserve OBJ topology, per-vertex UVs and normals as Fabric mesh data."""
    source = find("assets/create/" + identifier.removeprefix("create:"))
    positions, uvs, normals, faces = [], [], [], []
    materials = {}
    current_material = None
    for line in source.read_text().splitlines():
        fields = line.split()
        if not fields or fields[0].startswith("#"):
            continue
        if fields[0] == "mtllib":
            material_name = None
            for mtl_line in source.with_name(fields[1]).read_text().splitlines():
                values = mtl_line.split()
                if not values:
                    continue
                if values[0] == "newmtl":
                    material_name = values[1]
                elif values[0] == "map_Kd":
                    materials[material_name] = values[1].removeprefix("#")
        elif fields[0] == "v":
            positions.append([float(v) for v in fields[1:4]])
        elif fields[0] == "vt":
            uvs.append([float(fields[1]), 1 - float(fields[2]) if flip_v else float(fields[2])])
        elif fields[0] == "vn":
            normals.append([float(v) for v in fields[1:4]])
        elif fields[0] == "usemtl":
            current_material = fields[1]
        elif fields[0] == "f":
            # NeoForge ObjModel.ModelMesh.addQuads skips faces without a material.
            # Blender exports invisible Bounding objects before the wheel's usemtl.
            if current_material is None:
                continue
            polygon = []
            for field in fields[1:]:
                position, uv, normal = (int(v) for v in field.split("/"))
                polygon.append(positions[position - 1 if position > 0 else position]
                               + uvs[uv - 1 if uv > 0 else uv] + normals[normal - 1 if normal > 0 else normal])
            # Triangles use a degenerate fourth vertex, as in the original OBJ renderer.
            if len(polygon) == 4:
                quads = [polygon]
            else:
                quads = [[polygon[0], polygon[i], polygon[i + 1], polygon[i + 1]] for i in range(1, len(polygon) - 1)]
            faces.extend({"texture": materials[current_material], "vertices": vertices} for vertices in quads)
    return faces


def find(relative: str) -> Path:
    for root in ROOTS:
        candidate = root / relative
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(f"Missing upstream resource: {relative}")


def write(relative: str, data: bytes) -> None:
    dest = OUTPUT / relative
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    copied.add(relative)


def clean(value):
    if isinstance(value, dict):
        return {k: clean(v) for k, v in value.items() if k not in {"neoforge_data", "forge_data", "render_type"}}
    if isinstance(value, list):
        return [clean(v) for v in value]
    return value


def write_json(relative: str, data) -> None:
    # Native slices contribute dictionaries and tag members which upstream generation does not own.
    # Keep those real additions when resyncing the pinned dependency closure.
    destination = OUTPUT / relative
    if destination.is_file() and isinstance(data, dict):
        existing = json.loads(destination.read_text())
        if relative.startswith("assets/create/lang/") or relative == "assets/create/sounds.json":
            data = {**data, **existing}
        elif "/tags/" in relative and isinstance(data.get("values"), list):
            values = list(data["values"])
            for value in existing.get("values", []):
                if value not in values:
                    values.append(value)
            data = {**existing, **data, "values": values}
    write(relative, (json.dumps(clean(data), ensure_ascii=False, indent=2) + "\n").encode())


def modern_loot(value):
    if isinstance(value, list):
        return [modern_loot(v) for v in value]
    if not isinstance(value, dict):
        return value
    value = dict(value)
    if "conditions" in value:
        conditions = value.pop("conditions")
        if conditions:
            value["condition"] = conditions[0] if len(conditions) == 1 else {"type": "minecraft:all_of", "terms": conditions}
    if "functions" in value:
        value["modifier"] = value.pop("functions")
    # Native loot entries/pools may already carry a named predicate reference in
    # `condition`. It is not the entry's dispatch type and must survive resync.
    if (isinstance(value.get("condition"), str) and value["condition"].startswith("minecraft:")
            and "type" not in value and "entries" not in value and "rolls" not in value):
        value["type"] = value.pop("condition")
    if "function" in value:
        value["type"] = value.pop("function")
    if value.get("type") == "minecraft:block_state_property":
        value["type"] = "minecraft:match_block"
        value["blocks"] = value.pop("block")
        value["state"] = value.pop("properties", {})
    if value.get("type") == "minecraft:match_tool" and value.get("predicate", {}).get("predicates", {}).get("minecraft:enchantments") == [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]:
        return "minecraft:tool/can_silk_touch"
    if value.get("type") == "minecraft:item" and value.get("name") == "minecraft:air":
        value["type"] = "minecraft:empty"
        value.pop("name")
    return {k: modern_loot(v) for k, v in value.items()}


def modern_advancement(value):
    if isinstance(value, list):
        return [modern_advancement(v) for v in value]
    if not isinstance(value, dict):
        return value
    value = {k: modern_advancement(v) for k, v in value.items()}
    if value.get("trigger") == "minecraft:recipe_unlocked":
        conditions = value.get("conditions", {})
        if "recipe" in conditions:
            recipe = conditions.pop("recipe")
            assert "recipes" not in conditions or conditions["recipes"] == recipe
            conditions["recipes"] = recipe
    return value


def copy_texture(identifier: str) -> None:
    if not identifier.startswith("create:"):
        return
    relative = "assets/create/textures/" + identifier.removeprefix("create:") + ".png"
    if relative in copied:
        return
    source = find(relative)
    write(relative, source.read_bytes())
    if source.with_suffix(".png.mcmeta").is_file():
        write(relative + ".mcmeta", source.with_suffix(".png.mcmeta").read_bytes())


def copy_model(identifier: str) -> None:
    if not identifier.startswith("create:"):
        return
    relative = "assets/create/models/" + identifier.removeprefix("create:") + ".json"
    if relative in copied:
        return
    data = json.loads(find(relative).read_text())
    if data.get("loader") == "neoforge:composite" and identifier.startswith("create:block/display_link/"):
        data.pop("loader")
        data["fabric:type"] = "create:display_link"
        for child in data["children"].values():
            for texture in child.get("textures", {}).values():
                copy_texture(texture)
    if data.get("loader") == "neoforge:composite":
        children = data.pop("children")
        data.pop("loader")
        textures = data.setdefault("textures", {})
        elements = data.setdefault("elements", [])
        for child_name, child in children.items():
            if "parent" in child or "loader" in child or "elements" not in child:
                raise ValueError(f"Composite child needs a native model adapter: {identifier}/{child_name}")
            aliases = {key: f"{child_name}_{key}" for key in child.get("textures", {})}
            for key, value in child.get("textures", {}).items():
                textures[aliases[key]] = "#" + aliases.get(value[1:], value[1:]) if value.startswith("#") else value
            for element in child["elements"]:
                for face in element.get("faces", {}).values():
                    texture = face["texture"]
                    if texture.startswith("#"):
                        face["texture"] = "#" + aliases.get(texture[1:], texture[1:])
                elements.append(element)
    if data.get("loader") == "neoforge:obj":
        data["mesh"] = obj_mesh(data.pop("model"), data.pop("flip_v", False))
        data.pop("loader")
        data["fabric:type"] = "create:mesh"
    if str(data.get("loader", "")).startswith("neoforge:"):
        raise ValueError(f"Unported model loader: {identifier}: {data['loader']}")
    textures = data.get("textures", {})
    if identifier.startswith("create:item/"):
        for key, texture in list(textures.items()):
            if texture.startswith("create:item/package/"):
                package_texture = texture.removeprefix("create:item/")
                replacement = "create:block/" + package_texture
                textures[key] = replacement
                write("assets/create/textures/block/" + package_texture + ".png", find("assets/create/textures/item/" + package_texture + ".png").read_bytes())
    if identifier.startswith(("create:item/extendo_grip", "create:item/potato_cannon")):
        for key, texture in list(textures.items()):
            if texture.startswith("create:item/"):
                source_texture = texture.removeprefix("create:")
                target_texture = "block/equipment/" + source_texture.removeprefix("item/")
                textures[key] = "create:" + target_texture
                write("assets/create/textures/" + target_texture + ".png", find("assets/create/textures/" + source_texture + ".png").read_bytes())
    if textures and "particle" not in textures:
        fallback = next((v for v in textures.values() if not v.startswith("#")), None)
        if fallback:
            textures["particle"] = fallback
    if identifier == "create:block/mechanical_press/head":
        data.setdefault("textures", {})["particle"] = "create:block/mechanical_press_head"
    # Three exact pinned Extendo faces extend beyond the original16x16 sprite.
    # Native transparency baking checks those bounds. Match the accepted motor
    # adaptation: translate only these UV rectangles inward without resizing.
    if identifier == "create:item/extendo_grip/item":
        expected = {(1, "east"): ([14, 6, 17, 12], [13, 6, 16, 12]),
                    (1, "west"): ([14, 6, 17, 12], [13, 6, 16, 12]),
                    (2, "north"): ([14, 0, 18, 3], [12, 0, 16, 3])}
        for (element, face), (original_uv, native_uv) in expected.items():
            actual = data["elements"][element]["faces"][face]
            if actual.get("uv") != original_uv:
                raise ValueError(f"Pinned Extendo UV changed: element{element}/{face}")
            actual["uv"] = native_uv
        invalid = [(index, face) for index, element in enumerate(data["elements"])
                   for face, values in element.get("faces", {}).items()
                   if any(value < 0 or value > 16 for value in values.get("uv", []))]
        if invalid or len(expected) != 3:
            raise ValueError(f"Unreviewed Extendo UV bounds: {invalid}")
    # The upstream motor has one UV rectangle extending past its 16x16 sprite.
    # 26.3 checks these bounds while computing material transparency.
    if identifier.startswith("create:block/creative_motor/"):
        for element in data.get("elements", []):
            for face in element.get("faces", {}).values():
                if face.get("uv") == [14, 4, 20, 10]:
                    face["uv"] = [10, 4, 16, 10]
    write_json(relative, data)
    if "parent" in data:
        copy_model(data["parent"])
    for texture in data.get("textures", {}).values():
        copy_texture(texture)


def walk_models(data) -> None:
    if isinstance(data, dict):
        for key, value in data.items():
            if key == "model" and isinstance(value, str):
                copy_model(value)
            else:
                walk_models(value)
    elif isinstance(data, list):
        for value in data:
            walk_models(value)


def modern_ingredients(value):
    if isinstance(value, dict):
        if set(value) == {"item"}:
            return value["item"]
        if set(value) == {"tag"}:
            return "#" + value["tag"]
        if value.get("type") == "neoforge:block_tag":
            assert set(value) == {"type", "tag"}
            tag = "minecraft:convertible_to_mud" if value["tag"] == "minecraft:convertable_to_mud" else value["tag"]
            return {"fabric:type": "create:block_tag", "tag": tag}
        return {k: modern_ingredients(v) for k, v in value.items()}
    if isinstance(value, list):
        return [modern_ingredients(v) for v in value]
    return value


def modern_processing(data):
    data = dict(data)
    inputs = []
    for ingredient in data["ingredients"]:
        kind = ingredient.get("type", "") if isinstance(ingredient, dict) else ""
        if kind == "neoforge:single":
            ingredient = {"fluid": ingredient["fluid"], "amount": ingredient["amount"] * 81}
        elif kind == "neoforge:tag":
            ingredient = {"fluid_tag": ingredient["tag"], "amount": ingredient["amount"] * 81}
        elif kind == "neoforge:components":
            assert isinstance(ingredient["fluids"], str) and not ingredient["fluids"].startswith("#")
            ingredient = {"fluid": ingredient["fluids"], "amount": ingredient["amount"] * 81,
                          "components": ingredient["components"]}
        inputs.append(ingredient)
    data["ingredients"] = inputs
    data["results"] = [{"fluid": "create:milk" if r["id"] == "neoforge:milk" else r["id"],
                        "amount": r["amount"] * 81, **({"components": r["components"]} if "components" in r else {})}
                       if "amount" in r else r for r in data["results"]]
    return modern_ingredients(data)


def copycat_resources() -> None:
    """Source helper geometry/materials and native dynamic-model fallbacks; helpers deliberately have no item."""
    for block in COPYCAT_BLOCKS:
        if block == "copycat_panel":
            turns = {"up": (0, 0), "down": (180, 0), "north": (90, 0), "east": (90, 90), "south": (90, 180), "west": (90, 270)}
            state = {"variants": {"facing="+face: {"model": "create:block/copycat_base/panel", **({"x": x} if x else {}), **({"y": y} if y else {})} for face, (x, y) in turns.items()}}
        elif block == "copycat_step":
            state = {"variants": {f"facing={face},half={half}": {"model": "create:block/copycat_base/step", **({"y": y} if y else {}), **({"x": 180} if half == "top" else {})} for face, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)) for half in ("bottom", "top")}}
        else:
            state = json.loads(find(f"assets/create/blockstates/{block}.json").read_text())
        write_json(f"assets/create/blockstates/{block}.json", state)
        walk_models(state)
        if block not in COPYCAT_ITEMLESS:
            copy_model(f"create:item/{block}")
            write_json(f"assets/create/items/{block}.json", {"model": {"type": "minecraft:model", "model": f"create:item/{block}"}})
        write_json(f"data/create/loot_table/blocks/{block}.json", modern_loot(json.loads(find(f"data/create/loot_table/blocks/{block}.json").read_text())))
    for tag in ("copycat_allow", "copycat_deny"):
        relative = f"data/create/tags/block/{tag}.json"
        write_json(relative, json.loads(find(relative).read_text()))
    for recipe in ("copycat_step_from_ingots_zinc_stonecutting", "copycat_panel_from_ingots_zinc_stonecutting", "crafting/kinetics/rose_quartz_lamp"):
        relative = f"data/create/recipe/{recipe}.json"
        write_json(relative, modern_ingredients(json.loads(find(relative).read_text())))
    for tool, additions in (("axe", ("copycat_step", "copycat_panel")), ("pickaxe", ("rose_quartz_lamp", "copycat_base", "copycat_step", "copycat_panel"))):
        relative = f"data/minecraft/tags/block/mineable/{tool}.json"
        data = json.loads((OUTPUT/relative).read_text())
        data["values"] = [value for value in data["values"] if value not in {"create:"+name for name in COPYCAT_BLOCKS}]
        data["values"] += ["create:"+name for name in additions]
        # Existing full native lists were preserved above; use exact bytes to keep source helper omissions.
        write(relative, (json.dumps(data, ensure_ascii=False, indent=2)+"\n").encode())
    relative = "data/create/tags/block/fan_transparent.json"
    data = json.loads((OUTPUT/relative).read_text())
    data["values"] = [value for value in data["values"] if value != "create:copycat_base" and not (isinstance(value, dict) and value.get("id") == "create:copycat_base")]
    data["values"].append("create:copycat_base")
    write(relative, (json.dumps(data, ensure_ascii=False, indent=2)+"\n").encode())
    for locale in ("en_us", "ru_ru", "en_ud"):
        relative = f"assets/create/lang/{locale}.json"
        source = json.loads(find(relative).read_text())
        values = {key: value for key, value in source.items() if any(key == "block.create."+name or key.startswith("block.create."+name+".") for name in COPYCAT_BLOCKS)}
        write_json(relative, values)


def sync_burner_fuels() -> None:
    """Keep the real source data map authoritative; legacy tags remain addon fallbacks."""
    relative = "data/create/data_maps/item/superheated_blaze_burner_fuels.json"
    write(relative, find(relative).read_bytes())
    for kind in ("regular", "special"):
        write_json(f"data/create/tags/item/blaze_burner_fuel/{kind}.json", {"replace": False, "values": []})


def sync_ambient_sound_assets() -> None:
    """Original ambient events and recording, preserving every unrelated native sound."""
    relative = "assets/create/sounds.json"
    source = json.loads(find(relative).read_text())
    target = OUTPUT / relative
    sounds = json.loads(target.read_text()) if target.exists() else {}
    for name in ("cogs", "crushing_1", "crushing_2", "crushing_3"):
        sounds[name] = source[name]
    write_json(relative, sounds)
    recording = "assets/create/sounds/cogs.ogg"
    write(recording, find(recording).read_bytes())


def main() -> None:
    global UPSTREAM, ROOTS
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, required=True, help='Checkout of the pinned upstream Create source')
    args = parser.parse_args()
    UPSTREAM = args.source_root.resolve()
    ROOTS = [UPSTREAM / 'src/main/resources', UPSTREAM / 'src/generated/resources']
    if not all(path.is_dir() for path in ROOTS):
        parser.error('--source-root must contain the upstream main and generated resources')
    for part in ("hour_hand", "minute_hand", "left_door", "right_door", "pig", "creeper"):
        copy_model(f"create:block/cuckoo_clock/{part}")
    write_json("data/c/tags/item/gunpowders.json", {"replace": False, "values": ["minecraft:gunpowder"]})
    for variant in ("cuckoo_clock", "mysterious_cuckoo_clock"):
        relative = f"data/create/advancement/recipes/misc/crafting/kinetics/{variant}.json"
        write_json(relative, modern_advancement(json.loads(find(relative).read_text())))
    for relative in ("assets/create/textures/gui/overlay.png", "assets/create/textures/gui/icons.png", "assets/create/textures/gui/schematics.png", "assets/create/textures/gui/schematics_2.png", "assets/create/textures/gui/clipboard.png", "assets/create/textures/special/checkerboard.png", "assets/create/textures/map/decorations/station.png"):
        write(relative, find(relative).read_bytes())
    for part in ("connector", "pipe"):
        copy_model("create:block/schematicannon/" + part)
    for index in range(3):
        copy_model(f"create:item/clipboard_{index}")
    copy_model("create:block/weighted_ejector/top")
    for item, parts in {"extendo_grip": ("item", "cog", "thin_short", "wide_short", "thin_long", "wide_long"), "potato_cannon": ("item", "cog"), "wand_of_symmetry": ("item", "core", "core_glow", "bits")}.items():
        for part in parts:
            copy_model(f"create:item/{item}/{part}")
    for part in ("plane", "crossplane", "tripleplane"):
        copy_model(f"create:block/symmetry_effect/{part}")
    relative="assets/create/textures/gui/curiosities.png"
    write(relative,find(relative).read_bytes())
    for name,values in {"glass_blocks": ["minecraft:glass"], "ender_pearls": ["minecraft:ender_pearl"], "obsidians": ["minecraft:obsidian"]}.items():
        write_json(f"data/c/tags/item/{name}.json", {"replace": False, "values": values})
    for source in sorted((ROOTS[1] / "data/create/create/potato_projectile/type").glob("*.json")):
        write_json("data/create/create/potato_projectile/type/" + source.name, json.loads(source.read_text()))
    for name in ("capacity", "potato_recovery"):
        relative = f"data/create/enchantment/{name}.json"
        write_json(relative, modern_loot(json.loads(find(relative).read_text())))
    write_json("data/create/tags/item/pressurized_air_sources.json", {"replace": False, "values": []})
    for tag in ("in_enchanting_table", "non_treasure"):
        write_json(f"data/minecraft/tags/enchantment/{tag}.json", {"replace": False, "values": ["create:capacity", "create:potato_recovery"]})
    for name in ("crush", "cuckoo_surprise", "fan_fire", "fan_lava", "mechanical_drill", "mechanical_roller", "mechanical_saw", "potato_cannon", "run_over"):
        relative = f"data/create/damage_type/{name}.json"
        write_json(relative, json.loads(find(relative).read_text()))
    for tag, values in {"durability": ["create:extendo_grip", "create:potato_cannon"], "bow": ["create:potato_cannon"]}.items():
        write_json(f"data/minecraft/tags/item/enchantable/{tag}.json", {"replace": False, "values": values})
    write_json("data/c/tags/item/enchantables.json", {"replace": False, "values": ["create:potato_cannon"]})
    for part in ("cog", "base", "lower_body", "upper_body", "claw_base", "claw_base_goggles", "upper_claw_grip", "lower_claw_grip"):
        copy_model(f"create:block/mechanical_arm/{part}")
    for advancement in ("factory_gauge","table_cloth_shop","frogport","stock_ticker","packager","cardboard","foods","steam_engine_maxed","hose_pulley_lava","windmill_maxed", "portable_storage_interface", "wrench_goggles", "stressometer", "stressometer_maxed", "cuckoo_clock", "mechanical_arm", "arm_blaze_burner", "arm_many_targets", "musical_arm", "crusher_maxed_0000", "linked_controller", "extendo_grip", "extendo_grip_dual", "potato_cannon", "potato_cannon_collide", "ejector_maxed"):
        relative = f"data/create/advancement/{advancement}.json"
        data = modern_advancement(json.loads(find(relative).read_text()))
        write_json(relative, data)
    relative = "data/create/advancement/recipes/misc/crafting/kinetics/mechanical_arm.json"
    write_json(relative, modern_advancement(json.loads(find(relative).read_text())))
    relative = "data/create/advancement/recipes/misc/crafting/kinetics/weighted_ejector.json"
    write_json(relative, modern_advancement(json.loads(find(relative).read_text())))
    write_json("data/create/tags/item/invalid_for_track_paving.json", {"values": [{"id": "create:wooden_bracket", "required": False}, {"id": "create:metal_bracket", "required": False}]})
    write("assets/create/textures/gui/display_link.png", find("assets/create/textures/gui/display_link.png").read_bytes())
    for part in ("track_signal/indicator_on", "track_signal/indicator_off", "track_overlay/signal", "track_overlay/signal_dual"):
        copy_model("create:block/" + part)
    for part in ("track_station/flag_off", "track_station/flag_assemble", "track_overlay/station", "track_overlay/assembling"):
        copy_model("create:block/" + part)
    relative = "data/create/advancement/recipes/misc/crafting/kinetics/track_station.json"
    write_json(relative, modern_advancement(json.loads(find(relative).read_text())))
    copy_model("create:block/fake_track")
    write_json("assets/create/blockstates/fake_track.json", json.loads(find("assets/create/blockstates/fake_track.json").read_text()))
    write_json("data/create/loot_table/blocks/fake_track.json", {"type":"minecraft:block","pools":[],"random_sequence":"create:blocks/fake_track"})
    for part in ("bearing/top_wooden", "sticker/head"):
        copy_model(f"create:block/{part}")
    for part in ("item", "powered", "button"):
        copy_model(f"create:item/linked_controller/{part}")
        relative = f"assets/create/models/item/linked_controller/{part}.json"
        controller = json.loads((OUTPUT / relative).read_text())
        # Native partials and cuboid item models each require a single atlas.
        for key, texture in controller.get("textures", {}).items():
            if texture.startswith("create:item/linked_controller"):
                target = texture.replace("create:item/", "create:block/", 1)
                controller["textures"][key] = target
                write("assets/create/textures/" + target.removeprefix("create:") + ".png",
                      find("assets/create/textures/" + texture.removeprefix("create:") + ".png").read_bytes())
        write_json(relative, controller)
    for name in ("linear_chassis_side", "secondary_linear_chassis_side", "linear_chassis_end", "linear_chassis_end_sticky"):
        copy_texture(f"create:block/{name}_connected")
    for relative in ("assets/create/textures/special/highlighted_checkerboard.png",
                     "assets/create/textures/gui/curiosities_2.png",
                     "assets/create/textures/gui/sequencer.png"):
        write(relative, find(relative).read_bytes())
    for name in ("logistics_hat", "train_hat"):
        copy_model(f"create:entity/{name}")
        hat = clean(json.loads(find(f"assets/create/models/entity/{name}.json").read_text()))
        hat["textures"] = {"0": f"create:block/{name}", "particle": f"create:block/{name}"}
        write_json(f"assets/create/models/entity/{name}.json", hat)
        write(f"assets/create/textures/block/{name}.png", find(f"assets/create/textures/entity/{name}.png").read_bytes())
    for source in (ROOTS[1] / "assets/minecraft/train_hat_info").glob("*.json"):
        write("assets/minecraft/train_hat_info/" + source.name, source.read_bytes())
    for fluid in ("potion", "tea", "milk", "honey", "chocolate"):
        for kind in ("still", "flow"):
            source = find(f"assets/create/textures/fluid/{fluid}_{kind}.png")
            relative = f"assets/create/textures/block/{fluid}_{kind}.png"
            write(relative, source.read_bytes())
            if source.with_suffix(".png.mcmeta").is_file():
                write(relative + ".mcmeta", source.with_suffix(".png.mcmeta").read_bytes())
    for fluid in ("honey", "chocolate"):
        write_json(f"assets/create/blockstates/{fluid}.json", {"variants": {"": {"model": "minecraft:block/air"}}})
    for tag, values in {"honey": ["honey", "flowing_honey"], "milk": ["milk", "flowing_milk"]}.items():
        write_json(f"data/c/tags/fluid/{tag}.json", {"values": [f"create:{value}" for value in values]})
    for tag, values in {"chocolate": ["chocolate", "flowing_chocolate"], "tea": ["tea", "flowing_tea"]}.items():
        write_json(f"data/create/tags/fluid/{tag}.json", {"values": [f"create:{value}" for value in values]})
    write_json("data/create/tags/block/single_block_inventories.json", json.loads(find("data/create/tags/block/single_block_inventories.json").read_text()))
    write_json("data/c/tags/block/barrels.json", {"values":["minecraft:barrel"]})
    for tag in ("corals", "brittle", "non_movable", "movable_empty_collider", "windmill_sails", "chest_mounted_storage", "simple_mounted_storage"):
        relative=f"data/create/tags/block/{tag}.json"
        data=json.loads(find(relative).read_text())
        # Preserve rules for content still to be registered as the full port advances.
        data["values"]=[{"id":value,"required":False} if isinstance(value,str) and value.startswith("create:") else value for value in data["values"]]
        write_json(relative,data)
    for tag in ("internal", "fuel_blacklist"):
        relative=f"data/create/tags/create/mounted_item_storage_type/{tag}.json"
        write_json(relative,json.loads(find(relative).read_text()))
    write_json("data/minecraft/tags/item/enchantable/durability.json",{"replace":False,"values":["create:super_glue"]})
    write_json("data/create/tags/item/not_potion.json", {"values": []})
    relative = "data/create/tags/item/deployable_drink.json"
    write_json(relative, json.loads(find(relative).read_text()))
    write_json("data/create/tags/fluid/placeable.json", {"values": ["create:honey", "create:flowing_honey", "create:chocolate", "create:flowing_chocolate"]})
    write_json("data/minecraft/tags/fluid/entity_floatable.json", {"values": ["#create:placeable"]})
    write_json("data/create/tags/item/seats.json", {"values": [f"create:{color}_seat" for color in SEAT_COLORS]})
    write_json("data/create/tags/entity_type/ignore_seat.json", {"values": [{"id": "#c:capturing_not_supported", "required": False}]})
    for kind in ("item", "block"):
        write_json(f"data/create/tags/{kind}/table_cloths.json", {"values": [f"create:{color}_table_cloth" for color in CLOTH_TYPES]})
    write_json("data/minecraft/tags/block/combination_step_sound_blocks.json", {"replace": False,"values": [f"create:{color}_table_cloth" for color in SEAT_COLORS]})
    write_json("data/minecraft/tags/block/inside_step_sound_blocks.json", {"replace": False,"values": [f"create:{color}_table_cloth" for color in ("andesite", "brass", "copper")]})
    write_json("data/create/tags/item/dyed_table_cloths.json", {"values": [f"create:{color}_table_cloth" for color in SEAT_COLORS]})
    for name in ("tie","segment_left","segment_right"):
        copy_model("create:block/track/"+name)
    for kind in ("block","item"):
        write_json(f"data/create/tags/{kind}/tracks.json",{"replace":False,"values":["create:track"]})
    write_json("data/create/tags/block/girdable_tracks.json",{"replace":False,"values":["create:track"]})
    write_json("data/create/tags/item/sleepers.json",json.loads(find("data/create/tags/item/sleepers.json").read_text()))
    relocation=OUTPUT/"data/c/tags/block/relocation_not_supported.json"
    relocation_tag=json.loads(relocation.read_text()) if relocation.exists() else {"replace":False,"values":[]}
    if "create:track" not in relocation_tag["values"]:relocation_tag["values"].append("create:track")
    write_json("data/c/tags/block/relocation_not_supported.json",relocation_tag)
    for block in BLOCKS:
        relative = f"assets/create/blockstates/{block}.json"
        data = json.loads(find(relative).read_text())
        if block == "track":
            variants = {"shape=none": {"model": "minecraft:block/air"}}
            for properties, model in data["variants"].items():
                shape = next(p for p in properties.split(",") if p.startswith("shape="))
                if shape in variants and variants[shape] != model:
                    raise ValueError(f"Track model depends on unsupported extra state: {properties}")
                variants[shape] = model
            data = {"variants": variants}
        write_json(relative, data)
        walk_models(data)
        if block not in (*SAIL_BLOCKS[1:], "lectern_controller", "mechanical_piston_head", *COPYCAT_ITEMLESS):
            copy_model(f"create:item/{block}")
            write_json(f"assets/create/items/{block}.json", {
                "model": {"type": "minecraft:model", "model": f"create:item/{block}"}
            })
        write_json(f"data/create/loot_table/blocks/{block}.json", modern_loot({
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"create:{block}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]
        }))
    for block in (*DOORS, *SAIL_BLOCKS, "lectern_controller", "mechanical_piston_head", "display_link", "track", "track_station", "track_signal", "stockpile_switch"):
        relative=f"data/create/loot_table/blocks/{block}.json"
        write_json(relative,modern_loot(json.loads(find(relative).read_text())))
    relative = "data/create/tags/block/safe_nbt.json"
    data = json.loads(find(relative).read_text())
    data["values"] = [{"id": v, "required": False} if isinstance(v, str) and v.startswith("create:") and v[7:] not in (*BLOCKS, *RESOURCE_ONLY_BLOCKS) else v for v in data["values"]]
    write_json(relative, data)
    for door in ("andesite_door", "copper_door"):
        for half in ("fold_left", "fold_right"):
            copy_model(f"create:block/{door}/{half}")
    for control_part in ("button", *[f"indicator_{i}" for i in range(8)]):
        copy_model(f"create:block/contraption_controls/{control_part}")
    write_json("data/minecraft/tags/block/doors.json",{"replace":False,"values":[f"create:{door}" for door in DOORS]})
    write_json("data/minecraft/tags/block/wooden_doors.json",{"replace":False,"values":[f"create:{door}" for door in DOORS]})
    write_json("data/create/tags/block/non_double_door.json",{"values":[f"create:{door}" for door in DOORS]})
    write_json("data/minecraft/tags/item/doors.json",{"replace":False,"values":[f"create:{door}" for door in DOORS]})
    write_json("data/create/tags/item/dispense_behavior_wrap_blacklist.json",{"values":[]})
    # Original corner pieces are emitted only when both bordering sides are visible.
    base = json.loads(find("assets/create/models/block/table_cloth/block.json").read_text())
    for corner in ("north_west", "north_east", "south_west", "south_east"):
        piece = json.loads(find(f"assets/create/models/block/table_cloth/{corner}.json").read_text())
        base["elements"].extend(piece["elements"])
    base["ambientocclusion"] = False
    write_json("assets/create/models/block/table_cloth/block.json", base)
    for part in ("price_tag_side", "price_tag_top"):
        copy_model(f"create:block/table_cloth/{part}")
    for material in ("andesite", "brass"):
        name=f"{material}_belt_funnel"
        data=json.loads(find(f"assets/create/blockstates/{name}.json").read_text())
        write_json(f"assets/create/blockstates/{name}.json",data);walk_models(data)
        write_json(f"data/create/loot_table/blocks/{name}.json",modern_loot(json.loads(find(f"data/create/loot_table/blocks/{name}.json").read_text())))
    write("assets/create/textures/block/tunnel/brass_tunnel_top_connected.png",find("assets/create/textures/block/tunnel/brass_tunnel_top_connected.png").read_bytes())
    for part in ("factory_gauge/panel", "factory_gauge/panel_with_bulb", "factory_gauge/panel_restocker", "factory_gauge/panel_restocker_with_bulb", "factory_gauge/bulb_light", "factory_gauge/bulb_red", "display_link/tube", "display_link/glow", "packager/tray", "repackager/tray", "packager/hatch_open", "packager/hatch_closed"):
        copy_model(f"create:block/{part}")
    for kind in ("arrow", "line", "dotted"):
        for side in ("north", "south", "west", "east"):
            copy_model(f"create:block/factory_gauge/connections/{kind}_{side}")
            if kind != "dotted":
                relative = f"assets/create/models/block/factory_gauge/connections/{kind}_{side}.json"
                data = json.loads(find(relative).read_text())
                data["textures"] = {key: value.replace("block/factory_panel_connections", "block/factory_panel_connections_animated") for key, value in data["textures"].items()}
                write_json(relative.removesuffix(".json") + "_animated.json", data)
    copy_texture("create:block/factory_panel_connections_animated")
    relative="assets/create/textures/gui/requester.png"
    write(relative,find(relative).read_bytes())
    for name in ("stock_keeper", "stock_keeper_categories"):
        relative=f"assets/create/textures/gui/{name}.png"
        write(relative,find(relative).read_bytes())
    relative="assets/create/textures/gui/factory_gauge.png"
    write(relative,find(relative).read_bytes())
    copy_model("create:block/belt_tunnel/flap")
    copy_model("create:block/funnel/flap")
    copy_model("create:block/belt_funnel/flap")
    copy_model("create:block/fluid_valve/pointer")
    copy_model("create:block/rotation_speed_controller/bracket")
    controlled = {"minecraft:dispenser", "minecraft:dropper", "minecraft:bell", "minecraft:campfire", "minecraft:soul_campfire", "contraption_controls", *DOORS, "andesite_funnel", "brass_funnel", "peculiar_bell", "haunted_bell", "redstone_contact", "mechanical_roller", "portable_storage_interface", "portable_fluid_interface", "mechanical_drill", "mechanical_saw", "deployer", "mechanical_harvester", "mechanical_plough"}
    original_controlled = json.loads(find("data/create/tags/item/contraption_controlled.json").read_text())
    write_json("data/create/tags/item/contraption_controlled.json", {"values": [value for value in original_controlled["values"] if value.removeprefix("create:") in controlled]})
    for endpoint in ("portable_storage_interface", "portable_fluid_interface"):
        for part in ("block_middle", "block_middle_powered", "block_top"):
            copy_model(f"create:block/{endpoint}/{part}")
    for part in ("wheel", "frame"):
        copy_model(f"create:block/mechanical_roller/{part}")
    # The block atlas scans textures/block; keep the original GUI pixels for world value boxes.
    write("assets/create/textures/block/roller_mode_icons.png", find("assets/create/textures/gui/icons.png").read_bytes())
    for i, mode in enumerate(("tunnel_pave", "straight_fill", "wide_fill")):
        write_json(f"assets/create/models/block/mechanical_roller/mode_{mode}.json", {"textures": {"icon": "create:block/roller_mode_icons", "particle": "create:block/roller_mode_icons"}, "elements": [{"from": [0, 0, 0], "to": [16, 0, 16], "faces": {"up": {"texture": "#icon", "uv": [9+i, 4, 10+i, 5]}}}]})
    copy_model("create:block/mechanical_harvester/blade")
    for name in ("plough_blacklist", "plough_whitelist", "non_harvestable", "sugar_cane_variants"):
        relative=f"data/create/tags/block/{name}.json"
        write(relative,find(relative).read_bytes())
    for part in ("pole","hand_pointing","hand_holding","hand_punching"):
        copy_model(f"create:block/deployer/{part}")
    write("assets/create/textures/gui/value_settings.png",find("assets/create/textures/gui/value_settings.png").read_bytes())
    for name in ("filters", "filters_2", "player_inventory", "icons", "threshold_switch"):
        relative=f"assets/create/textures/gui/{name}.png"
        write(relative, find(relative).read_bytes())
    data=json.loads(find("assets/create/blockstates/lit_blaze_burner.json").read_text())
    write_json("assets/create/blockstates/lit_blaze_burner.json",data);walk_models(data)
    write_json("data/create/loot_table/blocks/lit_blaze_burner.json",modern_loot(json.loads(find("data/create/loot_table/blocks/lit_blaze_burner.json").read_text())))
    write_json("data/create/tags/item/extinguish_flame.json",{"values":[]})
    for part in ("speedometer/head", "stressometer/head", "dial"):
        copy_model(f"create:block/gauge/{part}")
    copy_model("create:block/goggles")
    goggles_model=json.loads(find("assets/create/models/block/goggles.json").read_text())
    # 26.3 item geometry uses one atlas; the worn model combines block materials with this item texture.
    goggles_model["textures"]["2"]="create:block/goggles_model"
    write_json("assets/create/models/block/goggles.json",goggles_model)
    write("assets/create/textures/block/goggles_model.png",find("assets/create/textures/item/goggles_model.png").read_bytes())
    for part in ("piston", "linkage", "shaft_connector", "gauge", "gauge_dial"):
        copy_model(f"create:block/steam_engine/{part}")
    for size in ("small", "medium", "large"):
        copy_model(f"create:block/steam_whistle/{size}_mouth")
    data=json.loads(find("assets/create/blockstates/steam_whistle_extension.json").read_text())
    write_json("assets/create/blockstates/steam_whistle_extension.json",data);walk_models(data)
    write_json("data/create/loot_table/blocks/steam_whistle_extension.json",{"type":"minecraft:block","pools":[]})
    for bell_part in ("peculiar_bell", "haunted_bell", "desk_bell/plunger", "desk_bell/bell"):
        copy_model(f"create:block/{bell_part}")
    for kind in ("soul", "soul_base", "soul_perimeter", "soul_expanding_perimeter"):
        description = json.loads(find(f"assets/create/particles/{kind}.json").read_text())
        write_json(f"assets/create/particles/{kind}.json", description)
        for texture in description["textures"]:
            relative = "assets/create/textures/particle/" + texture.removeprefix("create:") + ".png"
            write(relative, find(relative).read_bytes())
    air=json.loads(find("assets/create/particles/air.json").read_text());write_json("assets/create/particles/air.json",air)
    for texture in air["textures"]:copy_texture(texture)
    wifi=json.loads(find("assets/create/particles/wifi.json").read_text());write_json("assets/create/particles/wifi.json",wifi)
    for texture in wifi["textures"]:
        relative="assets/create/textures/particle/"+texture.removeprefix("create:")+".png"
        write(relative,find(relative).read_bytes())
    particle=json.loads(find("assets/create/particles/steam_jet.json").read_text())
    write_json("assets/create/particles/steam_jet.json",particle)
    for texture in particle["textures"]:
        relative="assets/create/textures/particle/"+texture.removeprefix("create:")+".png"
        write(relative,find(relative).read_bytes())
    data=json.loads(find("assets/create/blockstates/powered_shaft.json").read_text())
    write_json("assets/create/blockstates/powered_shaft.json",data);walk_models(data)
    write_json("data/create/loot_table/blocks/powered_shaft.json",modern_loot({"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"create:shaft"}]}]}))
    write_json("data/create/tags/block/passive_boiler_heaters.json",{"values":["minecraft:magma_block","minecraft:lava","#minecraft:campfires","#minecraft:fire",{"id":"create:lit_blaze_burner","required":False}]})
    for part in ("rope", "rope_half", "pulley_magnet", "rope_half_magnet", "hose_coil"):
        copy_model(f"create:block/hose_pulley/{part}")
    copy_texture("create:block/hose_pulley_coil_scroll")
    for name in ("allow", "deny"):
        relative = f"data/create/tags/fluid/bottomless/{name}.json"
        try:
            write_json(relative, json.loads(find(relative).read_text()))
        except FileNotFoundError:
            write_json(relative, {"values": []})
    for part in ("top", "middle", "bottom"):
        copy_model(f"create:block/spout/{part}")
    copy_model("create:block/mechanical_pump/cog")
    copy_model("create:block/fluid_pipe/casing")
    for kind in ("connection", "rim_connector", "rim", "drain"):
        for side in ("up", "down", "north", "south", "east", "west"):
            copy_model(f"create:block/fluid_pipe/{kind}/{side}")
    for block in ("glass_fluid_pipe", "encased_fluid_pipe"):
        relative=f"assets/create/blockstates/{block}.json"
        data=json.loads(find(relative).read_text());write_json(relative,data);walk_models(data)
        write_json(f"data/create/loot_table/blocks/{block}.json", modern_loot({"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"create:fluid_pipe"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}))
    copy_model("create:block/mechanical_drill/head")
    for orientation in ("horizontal", "vertical"):
        for mode in ("active", "inactive", "reversed"):
            copy_model(f"create:block/mechanical_saw/blade_{orientation}_{mode}")
    # Match native cooking/time_coal: fast-cooking reduction and datapack factors
    # belong to the actual container context, not a constant item burn duration.
    for name, ticks in {"cardboard": 1000, "cardboard_block": 4000,
                        "blaze_cake": 6400, "creative_blaze_cake": 2147483647}.items():
        write_json(f"data/create/context_int_provider/cooking/time_{name}.json", {
            "type": "minecraft:div", "left": ticks,
            "right": {"type": "minecraft:conditional", "condition": "minecraft:block/fast_cooking",
                      "on_false": "minecraft:cooking/normal_burn_time_reduction_factor",
                      "on_true": "minecraft:cooking/fast_burn_time_reduction_factor"}})
    # Dedicated Train Controls resources also require the native26.3 loot schema.
    relative = "data/create/loot_table/blocks/controls.json"
    write_json(relative, modern_loot(json.loads(find(relative).read_text())))
    for name in ("mechanical_drill", "mechanical_saw", "mechanical_roller"):
        relative = f"data/create/damage_type/{name}.json"
        write(relative, find(relative).read_bytes())
    for name in ("roots", "tree_attachments"):
        relative = f"data/create/tags/block/{name}.json"
        write(relative, find(relative).read_bytes())
    write_json("data/create/tags/block/non_breakable.json", {"values": []})
    write_json("data/create/tags/block/slimy_logs.json", {"values": [{"id": "#tconstruct:slimy_logs", "required": False}]})
    for part in ("body", "head", "head_goggles", "tongue"):
        copy_model(f"create:block/package_frogport/{part}")
    write("assets/create/textures/gui/frogport_and_mailbox.png",find("assets/create/textures/gui/frogport_and_mailbox.png").read_bytes())
    write_json("data/c/tags/item/slimeballs.json", {"values": ["minecraft:slime_ball"]})
    for part in ("wheel", "shaft", "guard"):
        copy_model(f"create:block/chain_conveyor/{part}")
    for size in ("12x10", "12x12", "10x8", "10x12"):
        copy_model(f"create:item/package/rigging_{size}")
    copy_model("create:block/shaft_half")
    copy_model("create:block/bearing/top")
    copy_model("create:block/hand_crank/handle")
    copy_model("create:block/millstone/inner")
    copy_model("create:block/mechanical_press/head")
    copy_model("create:block/cogwheel_shaftless")
    copy_model("create:block/mechanical_mixer/pole")
    copy_model("create:block/mechanical_mixer/head")
    copy_model("create:block/encased_fan/propeller")
    copy_model("create:block/crushing_wheel/block")
    for part in ("lid", "arrow", "belt", "belt_animated"):
        copy_model(f"create:block/mechanical_crafter/{part}")
    for face in ("front", "side", "top", "bottom"):
        for size in ("medium", "large"):
            copy_texture(f"create:block/vault/vault_{face}_{size}")
    for name in ("fluid_tank", "fluid_tank_top", "fluid_tank_inner", "creative_fluid_tank", "creative_casing"):
        copy_texture(f"create:block/{name}_connected")
    copy_texture("create:block/brass_casing_connected")
    copy_texture("create:block/crafter_side_connected")
    relative = "data/create/tags/recipe_serializer/automation_ignore.json"
    write(relative, find(relative).read_bytes())
    write_json("assets/create/blockstates/crushing_wheel_controller.json", {"variants": {"": {"model": "minecraft:block/air"}}})
    relative = "data/create/damage_type/crush.json"
    write(relative, find(relative).read_bytes())
    write_json("data/minecraft/tags/damage_type/bypasses_armor.json", {"values": ["create:crush"]})
    relative = "assets/create/blockstates/belt.json"
    data = json.loads(find(relative).read_text())
    write_json(relative, data)
    walk_models(data)
    for part in ("start", "middle", "end", "start_bottom", "middle_bottom", "end_bottom", "diagonal_start", "diagonal_middle", "diagonal_end"):
        copy_model(f"create:block/belt/{part}")
    copy_model("create:block/belt_pulley")
    copy_texture("create:block/belt_scroll")
    copy_texture("create:block/belt_diagonal_scroll")
    copy_texture("create:block/belt/andesite_belt_casing")
    for material in ("andesite", "brass"):
        for axis in ("x", "z"):
            copy_model(f"create:block/belt_cover/{material}_belt_cover_{axis}")
    for color in ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"):
        copy_texture(f"create:block/belt/{color}_scroll")
        copy_texture(f"create:block/belt/{color}_diagonal_scroll")
    relative = "data/create/loot_table/blocks/belt.json"
    write_json(relative, modern_loot(json.loads(find(relative).read_text())))
    for model in ("blaze/inert", "blaze/idle", "blaze/active", "blaze/super", "blaze/super_active",
                  "rods_small", "rods_large", "superheated_rods_small", "superheated_rods_large", "flame", "goggles", "goggles_small"):
        copy_model(f"create:block/blaze_burner/{model}")
    for texture in ("blaze_burner_flame_scroll", "blaze_burner_flame_superheated_scroll"):
        copy_texture(f"create:block/{texture}")
    for block in ("blaze_burner", "zinc_ore", "deepslate_zinc_ore"):
        relative = f"data/create/loot_table/blocks/{block}.json"
        write_json(relative, modern_loot(json.loads(find(relative).read_text())))
    for kind in ("configured_feature", "placed_feature"):
        relative = f"data/create/worldgen/{kind}/zinc_ore.json"
        data = json.loads(find(relative).read_text())
        if kind == "configured_feature":
            data = {"type": data["type"], **data["config"]}
            for target in data["targets"]:
                target["state"] = target["state"]["Name"]
            # ConfiguredFeature was replaced by the polymorphic Feature registry in 26.3.
            obsolete = OUTPUT / relative
            if obsolete.exists():
                obsolete.unlink()
            relative = "data/create/worldgen/feature/zinc_ore.json"
        else:
            data["placement"] = [p for p in data["placement"] if p["type"] != "create:config_filter"]
            data["placement"].append({"type": "minecraft:biome"})
        write_json(relative, data)
    write_json("data/minecraft/tags/block/needs_stone_tool.json", {"replace": False, "values": ["create:zinc_ore", "create:deepslate_zinc_ore"]})
    relative = "data/create/tags/entity_type/blaze_burner_capturable.json"
    write(relative, find(relative).read_bytes())
    for kind in ("block", "fluid"):
        for mode in ("splashing", "haunting", "smoking", "blasting"):
            relative = f"data/create/tags/{kind}/fan_processing_catalysts/{mode}.json"
            try:
                data = json.loads(find(relative).read_text())
            except FileNotFoundError:
                data = {"values": []}
            data["values"] = [v for v in data["values"] if not v.startswith("create:") or v[7:] in (*BLOCKS,"lit_blaze_burner")]
            write_json(relative, data)
    relative = "data/create/tags/block/fan_transparent.json"
    data = json.loads(find(relative).read_text())
    data["values"] = [v for v in data["values"] if not v.startswith("create:") or v[7:] in (*BLOCKS,"lit_blaze_burner")]
    write_json(relative, data)
    copy_model("create:block/water_wheel/wheel")
    write_json("assets/create/blockstates/water_wheel_structure.json", {"variants": {"": {"model": "minecraft:block/air"}}})
    for wood in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "poplar", "bamboo", "crimson", "warped"):
        log = f"{wood}_stem" if wood in {"crimson", "warped"} else f"{wood}_block" if wood == "bamboo" else f"{wood}_log"
        write_json(f"assets/create/models/block/water_wheel/material/{wood}.json", {
            "parent": "create:block/water_wheel/wheel",
            "textures": {"planks": f"minecraft:block/{wood}_planks", "log": f"minecraft:block/{log}"}
        })
        for extension, parent in (("", "block"), ("_extension", "block_extension")):
            copy_model(f"create:block/large_water_wheel/{parent}")
            write_json(f"assets/create/models/block/large_water_wheel/material/{wood}{extension}.json", {
                "parent": f"create:block/large_water_wheel/{parent}",
                "textures": {"planks": f"minecraft:block/{wood}_planks", "log": f"minecraft:block/{log}", "log_top": f"minecraft:block/{log}_top"}
            })
    for item in ITEMS:
        copy_model(f"create:item/{item}")
        write_json(f"assets/create/items/{item}.json", {
            "model": {"type": "minecraft:model", "model": f"create:item/{item}"}
        })
    write_json("assets/create/items/clipboard.json", {"model": {"type": "minecraft:select", "property": "create:clipboard_type", "cases": [{"when": name, "model": {"type": "minecraft:model", "model": f"create:item/clipboard_{index}"}} for index, name in enumerate(("empty", "written", "editing"))], "fallback": {"type": "minecraft:model", "model": "create:item/clipboard_0"}}})
    for recipe in (*[f"schematics/{name}" for name in ("empty_schematic", "schematic_and_quill", "schematic_table", "schematicannon")], "appliances/clipboard", "appliances/clipboard_clear"):
        relative=f"data/create/advancement/recipes/misc/crafting/{recipe}.json"
        write_json(relative,modern_advancement(json.loads(find(relative).read_text())))
    for name in SCHEMATIC_BLOCKS:
        relative=f"data/create/loot_table/blocks/{name}.json"
        write_json(relative,modern_loot(json.loads(find(relative).read_text())))
    write_json("assets/create/items/goggles.json",{"model":{"type":"minecraft:select","property":"minecraft:display_context","cases":[{"when":"head","model":{"type":"minecraft:model","model":"create:block/goggles"}}],"fallback":{"type":"minecraft:model","model":"create:item/goggles"}}})
    # Exact source Toolbox closure, native item wrappers/loot components and retained toolbelt atlas.
    copy_model("create:block/toolbox/drawer")
    for color in SEAT_COLORS:
        copy_model(f"create:block/toolbox/lid/{color}")
    for texture in ("toolbox", "widgets", "icons", "player_inventory"):
        relative=f"assets/create/textures/gui/{texture}.png"
        write(relative,find(relative).read_bytes())
    for kind in ("block", "item"):
        relative=f"data/create/tags/{kind}/toolboxes.json"
        write_json(relative,json.loads(find(relative).read_text()))
    for item in TOOLBOXES:
        relative=f"data/create/loot_table/blocks/{item}.json"
        write_json(relative,modern_loot(json.loads(find(relative).read_text())))
    for name in ("brown_toolbox", "toolbox_dyeing"):
        relative=f"data/create/recipe/crafting/curiosities/{name}.json"
        write_json(relative,modern_ingredients(json.loads(find(relative).read_text())))
    relative="data/create/advancement/recipes/misc/crafting/curiosities/brown_toolbox.json"
    write_json(relative,modern_advancement(json.loads(find(relative).read_text())))
    write_json("data/c/tags/item/leathers.json", {"replace": False, "values": ["minecraft:leather"]})
    write_json("data/c/tags/item/chests/wooden.json", {"replace": False, "values": ["minecraft:chest", "minecraft:trapped_chest"]})
    for recipe in RECIPES:
        relative = f"data/create/recipe/{recipe}.json"
        data = json.loads(find(relative).read_text())
        data = modern_ingredients(data)
        if recipe == "sequenced_assembly/track":
            # Native Ingredient accepts one tag or direct items, not a list of tags.
            # Preserve the source's iron-or-zinc tag union through a real nested tag.
            values = data["sequence"][0]["ingredients"][1]
            assert values == ["#c:nuggets/iron", "#c:nuggets/zinc"]
            write_json("data/create/tags/item/track_nuggets.json", {"replace": False, "values": values})
            for step in data["sequence"]:
                if step["type"] == "create:deploying":
                    assert step["ingredients"][1] == values
                    step["ingredients"][1] = "#create:track_nuggets"
        write_json(relative, data)
    relative="data/create/recipe/sequenced_assembly/precision_mechanism.json"
    write_json(relative,modern_ingredients(json.loads(find(relative).read_text())))
    relative="data/create/recipe/sequenced_assembly/sturdy_sheet.json"
    data=json.loads(find(relative).read_text());data["sequence"]=[modern_processing(step) for step in data["sequence"]];write_json(relative,modern_ingredients(data))
    for kind in ("deploying","item_application"):
        for source in sorted((ROOTS[1]/f"data/create/recipe/{kind}").glob("*.json")):
            data=json.loads(source.read_text());ids=[i["item"] for i in data["ingredients"] if "item" in i]+[r["id"] for r in data["results"]]
            if any(identifier.startswith("create:") and identifier[7:] not in (*ITEMS,*BLOCKS) for identifier in ids):continue
            data.pop("neoforge:conditions",None);write_json(source.relative_to(ROOTS[1]).as_posix(),modern_ingredients(data))
    # Base-game milling recipes are copied in full. Optional mod integration
    # recipes remain in upstream until their Fabric 26.3 dependencies are ported.
    milling = ROOTS[1] / "data/create/recipe/milling"
    milling_count = 0
    for source in sorted(milling.glob("*.json")):
        relative = source.relative_to(ROOTS[1]).as_posix()
        write_json(relative, modern_ingredients(json.loads(source.read_text())))
        milling_count += 1
    crushing_count = 0
    for source in sorted((ROOTS[1] / "data/create/recipe/crushing").glob("*.json")):
        data = json.loads(source.read_text())
        conditions = data.pop("neoforge:conditions", [])
        if conditions:
            if conditions != [{"type": "neoforge:not", "value": {"type": "neoforge:mod_loaded", "modid": "exnihilosequentia"}}]:
                continue
            data["fabric:load_conditions"] = [{"condition": "fabric:not", "value": {"condition": "fabric:all_mods_loaded", "values": ["exnihilosequentia"]}}]
        write_json(source.relative_to(ROOTS[1]).as_posix(), modern_ingredients(data))
        crushing_count += 1
    for stone in ("asurine", "crimsite", "ochrum", "veridium", "tuff", "diorite"):
        relative = f"data/create/tags/item/stone_types/{stone}.json"
        data = json.loads(find(relative).read_text())
        data["values"] = [v for v in data["values"] if not isinstance(v, str) or not v.startswith("create:") or v[7:] in BLOCKS]
        write_json(relative, data)
    for name in ("iron_ingot", "gold_ingot", "copper_ingot", "brass_ingot", "cardboard", "sugar_cane", "path", "path_from_grass"):
        relative = f"data/create/recipe/pressing/{name}.json"
        data = json.loads(find(relative).read_text())
        write_json(relative, modern_ingredients(with_native_conditions(data)))
    for kind, names in (("mixing", ("andesite_alloy", "andesite_alloy_from_zinc", "brass_ingot", "dough_by_mixing", "cardboard_pulp", "mud_by_mixing", "lava_from_cobble", "tea", "chocolate", "chocolate_melting", "honey")),
                        ("compacting", ("ice", "andesite_from_flint", "diorite_from_flint", "granite_from_flint", "honey", "chocolate", "blaze_cake"))):
        for name in names:
            relative = f"data/create/recipe/{kind}/{name}.json"
            data = json.loads(find(relative).read_text())
            ingredients = []
            for ingredient in data["ingredients"]:
                if ingredient.get("type") == "neoforge:single":
                    ingredient = {"fluid": ingredient["fluid"], "amount": ingredient["amount"] * 81}
                elif ingredient.get("type") == "neoforge:tag":
                    ingredient = {"fluid_tag": ingredient["tag"], "amount": ingredient["amount"] * 81}
                ingredients.append(ingredient)
            data["ingredients"] = ingredients
            data["results"] = [{"fluid": r["id"], "amount": r["amount"] * 81} if "amount" in r else r for r in data["results"]]
            write_json(relative, modern_ingredients(data))
    for kind in ("filling", "emptying"):
        for source in sorted((ROOTS[1] / f"data/create/recipe/{kind}").glob("*.json")):
            write_json(source.relative_to(ROOTS[1]).as_posix(), modern_processing(json.loads(source.read_text())))
    for kind in ("splashing", "haunting"):
        for source in sorted((ROOTS[1] / f"data/create/recipe/{kind}").glob("*.json")):
            data = json.loads(source.read_text())
            if any(i.get("type", "").startswith("neoforge:") for i in data["ingredients"]):
                continue
            item_ids = [i["item"] for i in data["ingredients"] if "item" in i] + [r["id"] for r in data["results"]]
            if any(identifier.startswith("create:") and identifier[7:] not in (*ITEMS, *BLOCKS) for identifier in item_ids):
                continue
            data.pop("neoforge:conditions", None)
            write_json(source.relative_to(ROOTS[1]).as_posix(), modern_ingredients(data))
    for kind in ("item", "block"):
        write_json(f"data/c/tags/{kind}/storage_blocks/cardboard.json", {"values": ["create:cardboard_block"]})
    write_json("data/c/tags/item/barrels/wooden.json", {"values": ["minecraft:barrel"]})
    write_json("data/c/tags/item/flours/wheat.json", {"replace": False, "values": ["create:wheat_flour"]})
    write_json("data/c/tags/item/dusts/obsidian.json",{"values":["create:powdered_obsidian"]})
    write_json("data/create/tags/item/convertible_to_mud.json", {"replace": False, "values": ["minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt"]})
    for name in ("pulpifiable",):
        relative = f"data/create/tags/item/{name}.json"
        write(relative, find(relative).read_bytes())
    for metal, item in (("gold", "golden_sheet"), ("iron", "iron_sheet"), ("copper", "copper_sheet"), ("brass", "brass_sheet")):
        write_json(f"data/c/tags/item/plates/{metal}.json", {"replace": False, "values": [f"create:{item}"]})
    write_json("data/c/tags/item/ingots/brass.json", {"replace": False, "values": ["create:brass_ingot"]})
    for path, values in {
        "ingots/zinc": ["create:zinc_ingot"], "nuggets/zinc": ["create:zinc_nugget"],
        "nuggets/brass": ["create:brass_nugget"], "raw_materials/zinc": ["create:raw_zinc"],
        "ores/zinc": ["create:zinc_ore", "create:deepslate_zinc_ore"],
        "storage_blocks/zinc": ["create:zinc_block"], "storage_blocks/raw_zinc": ["create:raw_zinc_block"],
        "storage_blocks/brass": ["create:brass_block"], "nuggets/copper": ["create:copper_nugget"],
    }.items():
        write_json(f"data/c/tags/item/{path}.json", {"replace": False, "values": values})
    sync_burner_fuels()
    upright = json.loads(find("data/create/tags/item/upright_on_belt.json").read_text())
    upright["values"] = [v for v in upright["values"] if not isinstance(v, str) or not v.startswith("create:") or v[7:] in ITEMS]
    write_json("data/create/tags/item/upright_on_belt.json", upright)
    write_json("data/create/tags/item/not_upright_on_belt.json", {"values": []})
    write_json("data/c/tags/item/tools/wrench.json", {"values": ["create:wrench"]})
    write_json("data/create/tags/item/chain_rideable.json", {"values": ["#c:tools/wrench"]})
    write_json("data/create/tags/item/packages.json", {"values": [f"create:{name}" for name in PACKAGE_ITEMS]})
    write_json("data/create/tags/item/crushed_raw_materials.json", {"values": [f"create:crushed_raw_{metal}" for metal in ("iron", "gold", "copper", "zinc")]})
    for locale in ("en_us", "ru_ru"):
        original = json.loads(find(f"assets/create/lang/{locale}.json").read_text())
        lang = {f"block.create.{name}": original.get(f"block.create.{name}", name)
                for name in (*BLOCKS, *RESOURCE_ONLY_BLOCKS, "rope", "pulley_magnet",
                             "railway_casing", "framed_glass", "track_observer")}
        lang.update({key: value for key, value in original.items()
                     if key.startswith(("advancement.create.", "create.cuckoo_clock.", "create.display_source.time", "create.display_source.stop_watch", "create.generic.daytime.", "block.create.cuckoo_clock", "block.create.mysterious_cuckoo_clock", "create.mechanical_arm.", "create.weighted_ejector.", "block.create.weighted_ejector", "block.create.depot", "create.subtitle.depot_", "create.hint.mechanical_arm_no_targets", "create.potato_cannon.", "create.backtank.", "item.create.extendo_grip", "item.create.potato_cannon", "enchantment.create.capacity", "enchantment.create.potato_recovery", "death.attack.create.potato_cannon", "create.subtitle.fwoomp", "create.subtitle.potato_hit", "create.train", "create.schedule.", "create.gui.train", "create.gui.schedule.",
                                        "create.logistics.train_observer", "create.postbox.",
                                        "create.contraption.door_control", "create.elevator_contact.",
                                        "create.flap_display.cycles.", "create.logistics.redstone_interval"))
                     or key == "entity.create.gantry_contraption"})
        lang.update({key: value for key,value in original.items() if key.startswith(("create.symmetry.", "create.gui.symmetryWand.", "create.orientation.", "item.create.wand_of_symmetry", "create.schematic", "create.gui.schematic", "create.clipboard.", "create.gui.clipboard.", "create.keyinfo.toolmenu", "create.gui.toolmenu.", "create.materialChecklist", "create.action."))})
        lang.update({"key.category.create.schematics": "Чертежи Create" if locale=="ru_ru" else "Create Schematics",
                     "create.gui.schematicannon.play": "Пуск" if locale=="ru_ru" else "Play",
                     "create.gui.schematicannon.pause": "Пауза" if locale=="ru_ru" else "Pause",
                     "create.gui.schematicannon.stop": "Стоп" if locale=="ru_ru" else "Stop"})
        lang["block.create.fake_track"] = original["block.create.fake_track"]
        lang["block.create.belt"] = original.get("block.create.belt", "Mechanical Belt")
        lang["block.create.lit_blaze_burner"] = original.get("block.create.lit_blaze_burner", "Lit Blaze Burner")
        lang.update({f"item.create.{name}": original.get(f"item.create.{name}", name) for name in ITEMS})
        for name in ("potion", "tea", "milk", "honey", "chocolate"):
            lang[f"fluid.create.{name}"] = original.get(f"fluid.create.{name}", "Молоко" if name == "milk" and locale == "ru_ru" else name.title())
        for key in ("item.create.package", "item.create.rare_package", "entity.create.package", "entity.create.seat", "entity.create.super_glue", "tag.item.create.seats", "create.gui.package_filter"):
            if key in original: lang[key] = original[key]
        lang["itemGroup.create.base"] = "Create"
        lang["create.motor.speed"] = "Скорость двигателя: %s об/мин" if locale == "ru_ru" else "Motor speed: %s RPM"
        lang["create.table_cloth.stock"] = "В наличии: %s" if locale == "ru_ru" else "In stock: %s"
        lang["create.table_cloth.selected"] = "В списке: %s" if locale == "ru_ru" else "Selected: %s"
        lang["create.stock_ticker.category_name"] = "Название категории" if locale == "ru_ru" else "Category name"
        lang["create.stock_ticker.recipes"] = "Рецепты крафта" if locale == "ru_ru" else "Crafting recipes"
        lang["create.stock_ticker.crafting_order"] = "Для механического крафта" if locale == "ru_ru" else "For mechanical crafting"
        lang["create.stock_ticker.mixed_order"] = "Доставка ингредиентов" if locale == "ru_ru" else "Ingredient delivery"
        lang["create.stock_ticker.network_lock"] = "Блокировка сети" if locale == "ru_ru" else "Network lock"
        lang.update({key: value for key, value in original.items() if key.startswith(("create.contraptions.chassis.", "create.contraptions.clockwork.", "create.contraptions.movement_mode.", "create.gui.sequenced_gearshift.", "create.contraptions.piston.", "create.logistics.firstFrequency", "create.logistics.secondFrequency", "create.logistics.filter.", "create.linked_controller.", "create.contraption.controls.", "create.contraptions.contoller.", "create.gui.assembly.exception.", "create.super_glue.", "create.gui.package_port.", "create.package_port.", "create.hint.blocked_frogport", "create.chain_conveyor.", "create.table_cloth.", "create.stock_ticker.", "create.stock_keeper.", "create.gui.stock_keeper.", "create.gui.stock_ticker."))})
        lang["create.gui.assembly.exception.structureChanged"] = "Конструкция изменилась в %s %s %s. Повторите сборку." if locale == "ru_ru" else "Structure changed at %s %s %s. Assemble it again."
        if "create.contraptions.moving_container" in original:
            lang["create.contraptions.moving_container"] = original["create.contraptions.moving_container"]
        for key, value in original.items():
            if key.startswith(("create.factory_panel.", "create.gui.factory_panel.", "create.schedule.condition.threshold.", "create.logistically_linked.", "create.tunnel.", "create.logistics.creative_crate.", "create.logistics.when_multiple_outputs_available", "create.logistics.filter", "create.subtitle.", "create.block.deployer.", "create.recipe.assembly.", "create.recipe.sequenced_assembly", "create.gui.config.overlay", "create.gui.filter.", "create.gui.attribute_filter.", "create.item_attributes.", "create.gui.goggles.", "create.gui.contraptions.", "create.gui.gauge.", "create.gui.speedometer.", "create.gui.stressometer.", "create.gui.value_settings.", "create.kinetics.speed_controller.", "create.kinetics.creative_motor.", "create.boiler.", "create.generic.", "create.tooltip.", "create.hint.full_deployer", "create.hint.hose_pulley", "tag.item.c.", "tag.item.create.", "death.attack.create.crush", "death.attack.create.mechanical_drill", "death.attack.create.mechanical_saw", "death.attack.create.mechanical_roller", "create.contraptions.roller_mode", "create.contraptions.mechanical_roller")):
                lang[key] = value
        for metal in ("gold", "iron", "copper", "brass"):
            lang.setdefault(f"tag.item.c.plates.{metal}", f"{metal.title()} plates")
        lang.setdefault("tag.item.c.ingots.brass", "Brass ingots")
        for key, value in {"ingots.zinc": "Zinc ingots", "nuggets.zinc": "Zinc nuggets", "nuggets.brass": "Brass nuggets",
                           "raw_materials.zinc": "Raw zinc", "ores.zinc": "Zinc ores", "storage_blocks.zinc": "Zinc blocks",
                           "storage_blocks.raw_zinc": "Raw zinc blocks", "storage_blocks.brass": "Brass blocks", "flours.wheat": "Wheat flour"}.items():
            lang.setdefault(f"tag.item.c.{key}", value)
        lang.update({key:value for key,value in original.items() if key.startswith(("create.toolbox.","create.keyinfo.toolbelt","create.track.","create.portal_track.","create.display_link.","create.display_source.","create.display_target.","create.gui.display_link."))})
        lang.update({'block.create.display_link': 'Передатчик информации',
         'create.display_link.attached_side': 'Блок, к которому подключён',
         'create.display_link.clear': 'Выбор целевой позиции отменён',
         'create.display_link.display_on': 'Местоположение визуализации',
         'create.display_link.display_on_multiline': 'Местоположение визуализации',
         'create.display_link.information_type': 'Тип информации',
         'create.display_link.invalid': 'Передатчик не имеет выбранной цели, привяжите его и попробуйте ещё раз',
         'create.display_link.no_source': 'Нет информации',
         'create.display_link.no_target': 'Нельзя визуализировать',
         'create.display_link.reading_from': 'Считывать из:',
         'create.display_link.set': 'Выбрана целевая позиция',
         'create.display_link.success': 'Успешно подключено к целевой позиции',
         'create.display_link.targeted_location': 'Блок, куда отправляет информацию',
         'create.display_link.title': 'Передатчик информации',
         'create.display_link.too_far': 'Целевая позиция слишком далеко...',
         'create.display_link.view_compatible': 'Кликните для просмотра всех совместимых блоков',
         'create.display_link.writing_to': 'Отправлять в:',
         'create.display_source.accumulate_items': 'Счётчик предметов',
         'create.display_source.boiler.for_boiler_status': 'для текста',
         'create.display_source.boiler.not_enough_space': 'нет места',
         'create.display_source.boiler_status': 'Статус котла',
         'create.display_source.combine_item_names': 'Название предмета',
         'create.display_source.computer': 'От компьютера',
         'create.display_source.count_items': 'Количество предметов',
         'create.display_source.current_floor': 'Местоположение лифта',
         'create.display_source.entity_name': 'Имя сущности',
         'create.display_source.fill_level': 'Уровень заполнения',
         'create.display_source.fill_level.display': 'Формат отображения',
         'create.display_source.fill_level.percent': 'Процент',
         'create.display_source.fill_level.progress_bar': 'Полоса прогресса',
         'create.display_source.fluid_amount': 'Объём жидкостей',
         'create.display_source.gauge_status': 'Статус контроллера',
         'create.display_source.item_throughput': 'Производительность',
         'create.display_source.item_throughput.interval': 'Интервал',
         'create.display_source.item_throughput.interval.hour': 'в час',
         'create.display_source.item_throughput.interval.minute': 'в минуту',
         'create.display_source.item_throughput.interval.second': 'в секунду',
         'create.display_source.kinetic_speed': 'Скорость вращения',
         'create.display_source.kinetic_speed.absolute': 'Без направления',
         'create.display_source.kinetic_speed.directional': 'С направлением',
         'create.display_source.kinetic_stress': 'Нагрузка на сеть',
         'create.display_source.kinetic_stress.current': 'Нагрузка в ЕН',
         'create.display_source.kinetic_stress.display': 'Отображаемая информация',
         'create.display_source.kinetic_stress.max': 'Общая мощность',
         'create.display_source.kinetic_stress.percent': 'Процент нагрузки',
         'create.display_source.kinetic_stress.progress_bar': 'Полоса прогресса',
         'create.display_source.kinetic_stress.remaining': 'Остаток ЕН',
         'create.display_source.label': 'Добавочная надпись',
         'create.display_source.list_fluids': 'Список жидкостей',
         'create.display_source.list_items': 'Список предметов',
         'create.display_source.max_enchant_level': 'Уровень чар',
         'create.display_source.nixie_tube': 'Скопировать значение',
         'create.display_source.observed_train_name': 'Название поезда',
         'create.display_source.player_deaths': 'Смерти игрока',
         'create.display_source.read_package_address': 'Считывание адреса',
         'create.display_source.redstone_power': 'Редстоун-сигнал',
         'create.display_source.redstone_power.display': 'Формат отображения',
         'create.display_source.redstone_power.number': 'Число',
         'create.display_source.redstone_power.progress_bar': 'Полоса прогресса',
         'create.display_source.scoreboard': 'Скорборд',
         'create.display_source.scoreboard.objective': 'Название скорборда',
         'create.display_source.scoreboard.objective.deaths': 'Смерти игрока',
         'create.display_source.scoreboard.objective_not_found': '«%1$s» не найден',
         'create.display_source.station_summary': 'Сводка станции',
         'create.display_source.station_summary.filter': 'Фильтр названия станции',
         'create.display_source.station_summary.minutes': ' мин',
         'create.display_source.station_summary.now': 'отп',
         'create.display_source.station_summary.platform_column': 'Ширина столбца «Платформа»',
         'create.display_source.station_summary.seconds': '%1$sс',
         'create.display_source.station_summary.train_name_column': 'Ширина столбца «Поезд»',
         'create.display_source.stop_watch': 'Секундомер',
         'create.display_source.time.12_hour': '12 часов',
         'create.display_source.time.24_hour': '24 часа',
         'create.display_source.time.format': 'Формат времени',
         'create.display_source.time_of_day': 'Время суток',
         'create.display_source.train_status': 'Статус расписания',
         'create.display_source.value_list.display': 'Отображение значения',
         'create.display_source.value_list.full_number': 'Полное',
         'create.display_source.value_list.million': 'm',
         'create.display_source.value_list.shortened': 'Сокращённое',
         'create.display_source.value_list.thousand': 'к',
         'create.display_target.line': '%1$s строка',
         'create.display_target.page': '%1$s страница',
         'create.display_target.single_line': 'Единственная строка'} if locale == "ru_ru" else {'block.create.display_link': 'Display Link',
         'create.display_link.attached_side': 'Block on attached side',
         'create.display_link.clear': 'Cleared position selection',
         'create.display_link.display_on': 'Write data to:',
         'create.display_link.display_on_multiline': 'Start writing at:',
         'create.display_link.information_type': 'Type of Information',
         'create.display_link.invalid': 'Link has no valid target, try placing it again',
         'create.display_link.no_source': 'Not a Display Source',
         'create.display_link.no_target': 'Not a Display Target',
         'create.display_link.reading_from': 'Read from:',
         'create.display_link.set': 'Targeted position selected',
         'create.display_link.success': 'Successfully bound to targeted position',
         'create.display_link.targeted_location': 'Block in targeted location',
         'create.display_link.title': 'Display Link',
         'create.display_link.too_far': 'Targeted position is too far from here',
         'create.display_link.view_compatible': 'Click to view all Compatible',
         'create.display_link.writing_to': 'Send to:',
         'create.display_source.accumulate_items': 'Accumulate Item Count',
         'create.display_source.boiler.for_boiler_status': 'for Boiler Status',
         'create.display_source.boiler.not_enough_space': 'Not enough space ',
         'create.display_source.boiler_status': 'Boiler Status',
         'create.display_source.combine_item_names': 'Combine Item Names',
         'create.display_source.computer': 'From Computer',
         'create.display_source.count_items': 'Amount of matching Items',
         'create.display_source.current_floor': 'Elevator Location',
         'create.display_source.entity_name': 'Entity Name',
         'create.display_source.fill_level': 'Container Fill Level',
         'create.display_source.fill_level.display': 'Display Format',
         'create.display_source.fill_level.percent': 'Percent',
         'create.display_source.fill_level.progress_bar': 'Progress Bar',
         'create.display_source.fluid_amount': 'Amount of matching Fluids',
         'create.display_source.gauge_status': 'Factory gauge status',
         'create.display_source.item_throughput': 'Item Throughput',
         'create.display_source.item_throughput.interval': 'Interval',
         'create.display_source.item_throughput.interval.hour': 'per Hour',
         'create.display_source.item_throughput.interval.minute': 'per Minute',
         'create.display_source.item_throughput.interval.second': 'per Second',
         'create.display_source.kinetic_speed': 'Rotation Speed (RPM)',
         'create.display_source.kinetic_speed.absolute': 'Ignore Direction',
         'create.display_source.kinetic_speed.directional': 'Include Direction',
         'create.display_source.kinetic_stress': 'Network Stress',
         'create.display_source.kinetic_stress.current': 'Stress in SU',
         'create.display_source.kinetic_stress.display': 'Displayed Info',
         'create.display_source.kinetic_stress.max': 'Total Capacity',
         'create.display_source.kinetic_stress.percent': 'Percentage',
         'create.display_source.kinetic_stress.progress_bar': 'Progress Bar',
         'create.display_source.kinetic_stress.remaining': 'Remaining SU',
         'create.display_source.label': 'Attached Label',
         'create.display_source.list_fluids': 'List matching Fluids',
         'create.display_source.list_items': 'List matching Items',
         'create.display_source.max_enchant_level': 'Max Enchanting Cost',
         'create.display_source.nixie_tube': 'Copy Nixie Tubes',
         'create.display_source.observed_train_name': 'Detected Train Name',
         'create.display_source.player_deaths': 'Player Deaths',
         'create.display_source.read_package_address': 'Read Package Address',
         'create.display_source.redstone_power': 'Redstone Power',
         'create.display_source.redstone_power.display': 'Display Format',
         'create.display_source.redstone_power.number': 'Number',
         'create.display_source.redstone_power.progress_bar': 'Progress Bar',
         'create.display_source.scoreboard': 'Scoreboard',
         'create.display_source.scoreboard.objective': 'Objective ID',
         'create.display_source.scoreboard.objective.deaths': 'Player Deaths',
         'create.display_source.scoreboard.objective_not_found': "'%1$s' not found",
         'create.display_source.station_summary': 'Train Station Summary',
         'create.display_source.station_summary.filter': 'Station name filter',
         'create.display_source.station_summary.minutes': ' min',
         'create.display_source.station_summary.now': 'now',
         'create.display_source.station_summary.platform_column': 'Platform column size',
         'create.display_source.station_summary.seconds': '%1$ss',
         'create.display_source.station_summary.train_name_column': 'Train column size',
         'create.display_source.stop_watch': 'Stopwatch',
         'create.display_source.time.12_hour': '12-hour',
         'create.display_source.time.24_hour': '24-hour',
         'create.display_source.time.format': 'Time Format',
         'create.display_source.time_of_day': 'Time of Day',
         'create.display_source.train_status': 'Train Schedule Status',
         'create.display_source.value_list.display': 'Value Display',
         'create.display_source.value_list.full_number': 'Full Number',
         'create.display_source.value_list.million': 'm',
         'create.display_source.value_list.shortened': 'Shortened',
         'create.display_source.value_list.thousand': 'k',
         'create.display_target.line': 'Line %1$s',
         'create.display_target.page': 'Page %1$s',
         'create.display_target.single_line': 'Single Line'})
        lang.update({"create.track."+key:(ru if locale=="ru_ru" else en) for key,(ru,en) in {'different_dimension': ('Выбранный путь находится в другом измерении.', 'The selected track is in another dimension.'), 'unloaded': ('Дождитесь загрузки обоих концов пути.', 'Wait for both track endpoints to load.'), 'forbidden': ('Здесь нельзя проложить путь.', 'Track cannot be placed here.'), 'blocked': ('Путь перекрыт.', 'Track placement is blocked.'), 'portal_pending': ('Привязка пути к порталу пока недоступна.', 'Portal track binding is not available yet.'), 'girder_pending': ('Прокладка пути с балками пока недоступна.', 'Track placement with girders is not available yet.')}.items()})
        lang.update({'en_us': {'block.create.track_station': 'Train Station', 'create.bogey.style.invalid': 'Unnamed style', 'create.bogey.style.no_other_sizes': 'No other sizes', 'create.bogey.style.standard': 'Standard', 'create.bogey.style.updated_style': 'Updated style', 'create.bogey.style.updated_style_and_size': 'Updated style and size', 'create.track_target.clear': 'Cleared track selection', 'create.track_target.invalid': 'Cannot target this track here', 'create.track_target.missing': 'Right-click the targeted train track first', 'create.track_target.no_junctions': 'Targeted track cannot be an intersection', 'create.track_target.occupied': 'Targeted track is occupied', 'create.track_target.set': 'Targeted track selected', 'create.track_target.success': 'Successfully bound to targeted track', 'create.track_target.too_far': 'Targeted track is too far from here', 'create.train_assembly.bogey_created': 'Bogey created. Click again to cycle type', 'create.train_assembly.bogeys_too_close': 'Bogeys %1$s and %2$s are too close to each other', 'create.train_assembly.frontmost_bogey_at_station': 'Frontmost Bogey must be at Station Marker', 'create.train_assembly.no_bogeys': 'No Bogeys Found', 'create.train_assembly.no_controls': 'Attach at least one forward-facing Train Controls block (Are you missing Super Glue?)', 'create.train_assembly.not_connected_in_order': 'Bogeys are not connected in order', 'create.train_assembly.nothing_attached': 'No structure attached to Bogey %1$s', 'create.train_assembly.requires_casing': 'Use Train Casing to create bogeys on tracks', 'create.train_assembly.sideways_controls': 'Train Controls cannot face sideways', 'create.train_assembly.single_bogey_carriage': 'This Bogey type cannot support a carriage on its own', 'create.train_assembly.too_many_bogeys': 'Too many Bogeys attached: %1$s', 'create.station.name': 'Station name', 'create.station.save': 'Save', 'create.station.doors': 'Doors: %s', 'create.station.door.all': 'All', 'create.station.door.north': 'North', 'create.station.door.east': 'East', 'create.station.door.south': 'South', 'create.station.door.west': 'West', 'create.station.door.none': 'None', 'create.station.enter_assembly': 'Enter assembly mode', 'create.station.leave_assembly': 'Leave assembly mode', 'create.station.check_layout': 'Check bogey layout', 'create.station.train_assembly_pending': 'Train assembly is not available yet', 'create.station.changed_retry': 'Station changed. Review and retry.', 'create.station.preflight.not_checked': 'Layout not checked', 'create.station.preflight.ready_for_carriage_capture': 'Bogey layout is valid', 'create.station.preflight.invalid_owner': 'Station ownership changed', 'create.station.preflight.invalid_track': 'Invalid assembly track', 'create.station.preflight.no_bogeys': 'Place the first bogey beside the marker', 'create.station.preflight.frontmost_bogey': 'First bogey must be beside the marker', 'create.station.preflight.bogeys_too_close': 'Bogeys must be at least 3 blocks apart', 'create.station.preflight.too_many_bogeys': 'Too many bogeys', 'create.station.preflight.incompatible_bogey': 'Incompatible bogey', 'create.station.preflight.graph_not_ready': 'Track graph is not ready', 'create.station.preflight.unloaded_frontier': 'Wait for the assembly track to load', 'create.station.preflight.entities_not_ready': 'Wait for nearby entities to load', 'create.station.preflight.permission_denied': 'Station access denied'}, 'ru_ru': {'block.create.track_station': 'Железнодорожная станция', 'create.bogey.style.invalid': 'Безымянный стиль', 'create.bogey.style.no_other_sizes': 'Нет других размеров', 'create.bogey.style.standard': 'Стандартный', 'create.bogey.style.updated_style': 'Выбран стиль', 'create.bogey.style.updated_style_and_size': 'Выбран стиль и размер', 'create.track_target.clear': 'Выбор путей отменён', 'create.track_target.invalid': 'Невозможно выбрать эти пути', 'create.track_target.missing': 'Сначала выберите участок железнодорожного пути, кликнув по нему ПКМ', 'create.track_target.no_junctions': 'Невозможно разместить маркер станции на перекрёстке', 'create.track_target.occupied': 'Выбранные пути заняты', 'create.track_target.set': 'Пути выбраны', 'create.track_target.success': 'Компонент привязан к выбранным путям', 'create.track_target.too_far': 'Разместите компонент ближе к путям...', 'create.train_assembly.bogey_created': 'Тележка создана. Кликните ещё раз, чтобы изменить её вид', 'create.train_assembly.bogeys_too_close': 'Вагонные тележки %1$s и %2$s слишком близко друг к другу.', 'create.train_assembly.frontmost_bogey_at_station': 'Первая вагонная тележка должна быть на указателе станции.', 'create.train_assembly.no_bogeys': 'Вагонные тележки не найдены', 'create.train_assembly.no_controls': 'Прикрепите хотя бы один контроллер поезда, обращённый вперёд (может, вы забыли приклеить?)', 'create.train_assembly.not_connected_in_order': 'Вагонные тележки соединены не по порядку.', 'create.train_assembly.nothing_attached': 'Конструкция не склеена с вагонной тележкой № %1$s.', 'create.train_assembly.requires_casing': 'Используйте ж/д корпус для создания вагонных тележек на путях', 'create.train_assembly.sideways_controls': 'Контроллер поезда направлен не вперёд', 'create.train_assembly.single_bogey_carriage': 'Этот тип вагонной тележки не может самостоятельно поддерживать вагон.', 'create.train_assembly.too_many_bogeys': 'Слишком много вагонных тележек прицеплено: %1$s шт.', 'create.station.name': 'Название станции', 'create.station.save': 'Сохранить', 'create.station.doors': 'Двери: %s', 'create.station.door.all': 'Все', 'create.station.door.north': 'Север', 'create.station.door.east': 'Восток', 'create.station.door.south': 'Юг', 'create.station.door.west': 'Запад', 'create.station.door.none': 'Закрыты', 'create.station.enter_assembly': 'Режим сборки', 'create.station.leave_assembly': 'Завершить режим сборки', 'create.station.check_layout': 'Проверить тележки', 'create.station.train_assembly_pending': 'Сборка поездов пока недоступна', 'create.station.changed_retry': 'Станция изменилась. Повторите действие.', 'create.station.preflight.not_checked': 'Тележки ещё не проверены', 'create.station.preflight.ready_for_carriage_capture': 'Тележки расставлены правильно', 'create.station.preflight.invalid_owner': 'Владелец станции изменился', 'create.station.preflight.invalid_track': 'Путь не подходит для сборки', 'create.station.preflight.no_bogeys': 'Поставьте первую тележку у отметки', 'create.station.preflight.frontmost_bogey': 'Первая тележка должна быть у отметки', 'create.station.preflight.bogeys_too_close': 'Между тележками нужно хотя бы 3 блока', 'create.station.preflight.too_many_bogeys': 'Слишком много тележек', 'create.station.preflight.incompatible_bogey': 'Тележка не подходит', 'create.station.preflight.graph_not_ready': 'Граф пути ещё не готов', 'create.station.preflight.unloaded_frontier': 'Дождитесь загрузки пути', 'create.station.preflight.entities_not_ready': 'Дождитесь загрузки сущностей', 'create.station.preflight.permission_denied': 'Нет доступа к станции'}}[locale])
        lang.update({'en_us': {'block.create.track_signal': 'Train Signal', 'create.track_signal.cannot_change_mode': 'Unable to switch mode of this Signal', 'create.track_signal.mode_change.cross_signal': '-> Allow passage if section fully traversable', 'create.track_signal.mode_change.entry_signal': '-> Allow passage if section unoccupied', 'create.track_signal.mode_controlled_by_computer': 'Signal mode is controlled by computer', 'create.track_signal.editor.mode.entry_signal': 'Entry signal', 'create.track_signal.editor.mode.cross_signal': 'Chain signal', 'create.track_signal.editor.state.red': 'Red', 'create.track_signal.editor.state.yellow': 'Yellow', 'create.track_signal.editor.state.green': 'Green', 'create.track_signal.editor.state.invalid': 'Invalid signal', 'create.track_signal.editor.unbound': 'Select a valid track'}, 'ru_ru': {'block.create.track_signal': 'Железнодорожный светофор', 'create.track_signal.cannot_change_mode': 'Невозможно переключить режим этого светофора', 'create.track_signal.mode_change.cross_signal': '-> Разрешить проезд, если сегмент можно пройти без остановок', 'create.track_signal.mode_change.entry_signal': '-> Разрешить проезд, если сегмент свободен', 'create.track_signal.mode_controlled_by_computer': 'Режим светофора регулируется компьютером', 'create.track_signal.editor.mode.entry_signal': 'Проходной светофор', 'create.track_signal.editor.mode.cross_signal': 'Цепной светофор', 'create.track_signal.editor.state.red': 'Красный', 'create.track_signal.editor.state.yellow': 'Жёлтый', 'create.track_signal.editor.state.green': 'Зелёный', 'create.track_signal.editor.state.invalid': 'Нет данных светофора', 'create.track_signal.editor.unbound': 'Выберите доступный путь'}}[locale])
        lang.update({key:value for key,value in original.items() if key.startswith(("create.gui.threshold_switch.", "create.display_source.fill_level"))})
        lang.update({"create.threshold_switch."+key:(ru if locale=="ru_ru" else en) for key,(ru,en) in {"normal":("Обычный сигнал","Normal signal"),"inverted":("Инвертированный сигнал","Inverted signal"),"unavailable":("Показания недоступны","Measurement unavailable"),"on_at":("Вкл. при ≥","On at ≥"),"off_at":("Выкл. при ≤","Off at ≤")}.items()})
        write_json(f"assets/create/lang/{locale}.json", lang)
    # Dedicated upside-down locale is checked in with the Train resources. Preserve
    # its other translations while closing the original advancement title/description keys.
    upside_down_path = OUTPUT / "assets/create/lang/en_ud.json"
    if upside_down_path.is_file():
        upside_down = json.loads(upside_down_path.read_text())
        source_upside_down = json.loads(find("assets/create/lang/en_ud.json").read_text())
        upside_down.update({key: value for key, value in source_upside_down.items()
                            if key.startswith(("advancement.create.", "create.cuckoo_clock.", "create.display_source.time", "create.display_source.stop_watch", "create.generic.daytime.", "block.create.cuckoo_clock", "block.create.mysterious_cuckoo_clock", "create.mechanical_arm.", "create.weighted_ejector.", "block.create.weighted_ejector", "block.create.depot", "create.subtitle.depot_", "create.hint.mechanical_arm_no_targets", "create.potato_cannon.", "create.backtank.", "item.create.extendo_grip", "item.create.potato_cannon", "enchantment.create.capacity", "enchantment.create.potato_recovery", "death.attack.create.potato_cannon", "create.subtitle.fwoomp", "create.subtitle.potato_hit")) or key in ("block.create.mechanical_arm", "create.logistics.when_multiple_outputs_available")})
        write_json("assets/create/lang/en_ud.json", upside_down)
    all_sounds = json.loads(find("assets/create/sounds.json").read_text())
    sounds = {key: value for key, value in all_sounds.items()
              if key.startswith(("contraption_", "frogport_", "cranking", "mechanical_press_activation", "sanding_", "wrench_", "mixing", "crushing_", "crafter_", "saw_", "spout", "steam", "funnel_flap")) or key in {"schematicannon_launch_block", "schematicannon_finish", "clipboard_check", "clipboard_erase", "potato_hit", "fwoomp", "whistle", "whistle_high", "whistle_low", "chiff", "slime_added", "package_pop", "packager", "stock_link", "stock_ticker_request", "stock_ticker_trade", "peculiar_bell_use", "haunted_bell_use", "haunted_bell_convert", "desk_bell", "controller_click", "controller_put", "controller_take", "confirm", "confirm_2", "deny", "train2", "scroll_value", "depot_slide", "depot_plop"}}
    write_json("assets/create/sounds.json", sounds)
    sync_ambient_sound_assets()
    sounds = json.loads((OUTPUT / "assets/create/sounds.json").read_text())
    for sound in sounds.values():
        for variant in sound["sounds"]:
            name = variant if isinstance(variant, str) else variant["name"]
            kind = "file" if isinstance(variant, str) else variant.get("type", "file")
            if kind == "file" and name.startswith("create:"):
                relative = "assets/create/sounds/" + name.removeprefix("create:") + ".ogg"
                try:
                    source = find(relative)
                except FileNotFoundError:
                    source = OUTPUT / relative
                    if not source.is_file():
                        raise
                write(relative, source.read_bytes())
    write_json("data/minecraft/tags/block/mineable/axe.json",{"replace":False,"values":["create:mechanical_piston", "create:sticky_mechanical_piston", "create:piston_extension_pole", "create:windmill_bearing", "create:clockwork_bearing", "create:linear_chassis", "create:secondary_linear_chassis", "create:radial_chassis", "create:redstone_link", "create:display_link", "create:lectern_controller", "create:elevator_pulley", "create:elevator_contact", "create:rope_pulley", "create:gantry_shaft", "create:gantry_carriage", "create:analog_lever", "create:sail_frame", *[f"create:{color}_sail" for color in SEAT_COLORS], *[f"create:{box}" for box in TOOLBOXES], "create:mechanical_arm", "create:cuckoo_clock", "create:mysterious_cuckoo_clock", "create:depot", "create:weighted_ejector", "create:redstone_contact", "create:portable_storage_interface", "create:portable_fluid_interface", "create:content_observer", "create:stockpile_switch", "create:cardboard_block", "create:schematic_table", "create:clipboard", "create:controls", *[f"create:{color}_postbox" for color in SEAT_COLORS], *[f"create:{color}_seat" for color in SEAT_COLORS]]})
    write_json("data/minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": [f"create:{b}" for b in (*BLOCKS, *RESOURCE_ONLY_BLOCKS, "lit_blaze_burner", "water_wheel_structure", "belt", "glass_fluid_pipe", "encased_fluid_pipe", "andesite_belt_funnel", "brass_belt_funnel", "powered_shaft", "steam_whistle_extension") if not b.endswith("_seat") and b not in (*TOOLBOXES,*SAIL_BLOCKS,"sail_frame","lectern_controller","controls",*[f"{color}_postbox" for color in SEAT_COLORS],*[f"{color}_table_cloth" for color in SEAT_COLORS])]})
    # Dedicated checked-in native Train models still need their original texture closure.
    copy_texture("create:block/train_controller_base")
    copy_texture("create:entity/blueprint_small")
    # 26.3 block and item atlases are separate; an entity texture used by a world
    # model must be explicitly stitched into the blocks atlas, without changing its pixels.
    block_atlas = OUTPUT / "assets/minecraft/atlases/blocks.json"
    atlas_data = json.loads(block_atlas.read_text()) if block_atlas.exists() else {"sources": []}
    blueprint_source = {"type": "minecraft:single", "resource": "create:entity/blueprint_small"}
    if blueprint_source not in atlas_data["sources"]:
        atlas_data["sources"].append(blueprint_source)
    write_json("assets/minecraft/atlases/blocks.json", atlas_data)
    from sync_zapper_assets import sync_zapper_assets
    sync_zapper_assets(globals())
    sync_armor_assets(globals())
    from sync_palette_worldgen_assets import sync_palette_worldgen_assets
    sync_palette_worldgen_assets(globals())
    copycat_resources()
    sync_kinetics26(globals())
    from sync_windows_copper_assets import sync_windows_copper_assets
    sync_windows_copper_assets(globals())
    from sync_compat_crushed_assets import sync_compat_crushed_assets
    sync_compat_crushed_assets(globals())
    from sync_core_recipe_discovery_assets import sync as sync_core_recipe_discovery_assets
    sync_core_recipe_discovery_assets(globals())
    from sync_decoration21 import sync as sync_decoration21
    sync_decoration21(globals())
    from sync_hatch_sword_assets import sync_hatch_sword_assets
    sync_hatch_sword_assets(globals())
    from sync_tree_fertilizer_assets import sync_tree_fertilizer_assets
    sync_tree_fertilizer_assets(globals())
    from sync_crafting_blueprint_assets import sync_crafting_blueprint_assets
    sync_crafting_blueprint_assets(globals())
    from sync_blueprint_render_assets import sync as sync_blueprint_render_assets
    sync_blueprint_render_assets(globals())
    from sync_legacy_material_assets import sync as sync_legacy_material_assets
    sync_legacy_material_assets(globals())
    from sync_gameplay_advancement_assets import sync as sync_gameplay_advancement_assets
    sync_gameplay_advancement_assets(globals())
    from sync_pulley_advancement_assets import sync as sync_pulley_advancement_assets
    sync_pulley_advancement_assets(globals())
    from sync_optional_compat_assets import sync_optional_compat_assets
    sync_optional_compat_assets(globals())
    (PROJECT / "LICENSE").write_bytes((UPSTREAM / "LICENSE.md").read_bytes())
    from sync_minecart_assets import sync_minecart_assets
    sync_minecart_assets(globals())
    from sync_train_passenger_assets import sync as sync_train_passenger_assets
    sync_train_passenger_assets(globals())
    from sync_config_menu_assets import generate as sync_config_menu_assets
    sync_config_menu_assets(OUTPUT, UPSTREAM)
    from sync_item_description_assets import generate as sync_item_description_assets
    sync_item_description_assets(OUTPUT, UPSTREAM)
    from sync_train_relocation_assets import generate as sync_train_relocation_assets
    sync_train_relocation_assets(OUTPUT, UPSTREAM)

    print(f"Copied/generated {len(copied)} resources from Create 6.0.10, including {milling_count} milling and {crushing_count} crushing recipes.")


if __name__ == "__main__":
    main()
