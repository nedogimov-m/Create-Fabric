#!/usr/bin/env python3
"""Native Blueprint entity partials and their exact source sprite/metadata closure."""
import json

SIZES=("small","medium","large")

def sync(g):
    for size in SIZES:
        g["copy_model"]("create:entity/crafting_blueprint_"+size)
        relative="assets/create/textures/entity/blueprint_"+size+".png.mcmeta"
        try:source=g["find"](relative)
        except FileNotFoundError:pass
        else:g["write"](relative,source.read_bytes())
    # Native26.3 entity partials use the block atlas; preserve every previous source.
    relative="assets/minecraft/atlases/blocks.json";destination=g["OUTPUT"]/relative
    data=json.loads(destination.read_text())if destination.is_file()else{"sources":[]}
    for size in SIZES:
        entry={"type":"minecraft:single","resource":"create:entity/blueprint_"+size}
        if entry not in data["sources"]:data["sources"].append(entry)
    g["write_json"](relative,data)
    return {"entity_models":3,"source_sprites":3,"runtime":"UNRUN"}

if __name__=="__main__":
    import sys
    sys.dont_write_bytecode=True
    import sync_assets
    sync(vars(sync_assets))
