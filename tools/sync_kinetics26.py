#!/usr/bin/env python3
"""Additive pinned source closure for the26 kinetics blocks and genuine native partials."""
from __future__ import annotations
import json

COLORS = ("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black")
ENCASED = tuple(f"{material}_encased_{kind}" for material in ("andesite","brass") for kind in ("shaft","cogwheel","large_cogwheel"))
VALVES = ("copper_valve_handle", *(f"{color}_valve_handle" for color in COLORS))
BLOCKS = (*ENCASED,*VALVES,"nozzle","turntable","flywheel")
RECIPES = ("copper_valve_handle","copper_valve_handle_from_others",*(f"{color}_valve_handle_from_other_valve_handle" for color in COLORS),"nozzle","turntable","flywheel")

def sync(api):
    find,write_json = api["find"],api["write_json"]
    model,walk = api["copy_model"],api["walk_models"]
    for block in BLOCKS:
        relative = f"assets/create/blockstates/{block}.json"
        data = json.loads(find(relative).read_text())
        write_json(relative,data); walk(data)
        model(f"create:item/{block}")
        write_json(f"assets/create/items/{block}.json",{"model":{"type":"minecraft:model","model":f"create:item/{block}"}})
        relative = f"data/create/loot_table/blocks/{block}.json"
        write_json(relative,api["modern_loot"](json.loads(find(relative).read_text())))
    for part in ("shaft_half","cogwheel_shaftless","large_cogwheel_shaftless"):
        model(f"create:block/{part}")
    for material in ("andesite","brass"):
        for path in (f"{material}_casing_connected",f"{material}_encased_cogwheel_side_connected"):
            api["copy_texture"](f"create:block/{path}")
    for name in RECIPES:
        relative = f"data/create/recipe/crafting/kinetics/{name}.json"
        write_json(relative,api["modern_ingredients"](json.loads(find(relative).read_text())))
        relative = f"data/create/advancement/recipes/misc/crafting/kinetics/{name}.json"
        write_json(relative,api["modern_advancement"](json.loads(find(relative).read_text())))
    for kind in ("block","item"):
        relative = f"data/create/tags/{kind}/valve_handles.json"
        write_json(relative,json.loads(find(relative).read_text()))
    # Only actual source members are added; previous native contributions survive the shared writer.
    for relative in ("data/create/tags/block/brittle.json","data/create/tags/block/safe_nbt.json",
                     "data/minecraft/tags/block/mineable/axe.json","data/minecraft/tags/block/mineable/pickaxe.json"):
        source = json.loads(find(relative).read_text())
        selected = [v for v in source["values"] if isinstance(v,str) and v.removeprefix("create:") in BLOCKS]
        write_json(relative,{"replace":False,"values":selected})
    for color in COLORS:
        relative = f"data/c/tags/item/dyes/{color}.json"
        write_json(relative,{"replace":False,"values":[f"minecraft:{color}_dye"]})
    for locale in ("en_us","ru_ru"):
        relative = f"assets/create/lang/{locale}.json"
        original = json.loads(find(relative).read_text())
        selected = {key:value for key,value in original.items() if key.startswith("create.kinetics.valve_handle.")
                    or any(key.startswith(f"block.create.{name}") for name in BLOCKS)}
        write_json(relative,selected)

if __name__ == "__main__":
    import sys
    sys.dont_write_bytecode = True
    import sync_assets
    sync(vars(sync_assets))
