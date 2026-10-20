"""The nine pinned tag-dependent ores and their original optional recipe closure."""
import json

METALS = ("osmium", "platinum", "silver", "tin", "lead", "quicksilver", "aluminum", "uranium", "nickel")
ITEMS = tuple("crushed_raw_" + metal for metal in METALS)


def native_condition(condition):
    kind = condition["type"]
    if kind == "neoforge:mod_loaded":
        return {"condition": "fabric:all_mods_loaded", "values": [condition["modid"]]}
    if kind == "neoforge:tag_empty":
        return {"condition": "create:item_tag_empty", "tag": condition["tag"]}
    if kind == "neoforge:not":
        return {"condition": "fabric:not", "value": native_condition(condition["value"])}
    if kind in ("neoforge:and", "neoforge:or"):
        return {"condition": "fabric:" + kind.split(":")[1],
                "values": [native_condition(value) for value in condition["values"]]}
    raise ValueError("Unsupported source condition: " + kind)


def with_native_conditions(data):
    data = dict(data)
    conditions = data.pop("neoforge:conditions", [])
    if conditions:
        data["fabric:load_conditions"] = [native_condition(value) for value in conditions]
    return data


def sync_compat_crushed_assets(g):
    find, write_json = g["find"], g["write_json"]
    owned = {"create:" + name for name in ITEMS}
    for name in ITEMS:
        g["copy_model"]("create:item/" + name)
        write_json("assets/create/items/" + name + ".json",
                   {"model": {"type": "minecraft:model", "model": "create:item/" + name}})
    relative = "data/create/tags/item/crushed_raw_materials.json"
    write_json(relative, json.loads(find(relative).read_text()))
    for locale in ("en_us", "ru_ru", "en_ud"):
        relative = "assets/create/lang/" + locale + ".json"
        data = json.loads(find(relative).read_text())
        write_json(relative, {key: value for key, value in data.items()
                              if key in {"item.create." + name for name in ITEMS}})
    recipes = {}
    for root in reversed(g["ROOTS"]):
        for source in sorted((root / "data/create/recipe").rglob("*.json")):
            raw = source.read_text()
            if any(json.dumps(identifier) in raw for identifier in owned):
                relative = source.relative_to(root).as_posix()
                recipes[relative] = json.loads(raw)
    for relative, data in recipes.items():
        write_json(relative, g["modern_ingredients"](with_native_conditions(data)))
    recipe_ids = {"create:" + path.removeprefix("data/create/recipe/").removesuffix(".json") for path in recipes}
    advancements = {}
    for root in reversed(g["ROOTS"]):
        for source in sorted((root / "data/create/advancement/recipes").rglob("*.json")):
            data = json.loads(source.read_text())
            if any(value in recipe_ids for value in data.get("rewards", {}).get("recipes", [])):
                advancements[source.relative_to(root).as_posix()] = data
    for relative, data in advancements.items():
        write_json(relative, g["modern_advancement"](with_native_conditions(data)))
    return {"items": len(ITEMS), "recipes": len(recipes), "advancements": len(advancements),
            "recipe_ids": sorted(recipe_ids), "ore_tag_visibility": True,
            "native_condition": "create:item_tag_empty", "runtime_verified": False}
