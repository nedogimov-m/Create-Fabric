"""Pinned Create cart/rail/item/model/recipe closure for native Fabric26.3."""
from __future__ import annotations
import json

BLOCKS=("cart_assembler","controller_rail","minecart_anchor")
ITEMS=("minecart_contraption","furnace_minecart_contraption","chest_minecart_contraption","minecart_coupling")
RECIPES=("crafting/curiosities/minecart_coupling","crafting/kinetics/cart_assembler","crafting/kinetics/controller_rail","crafting/kinetics/furnace_minecart_from_contraption_cart","crafting/kinetics/minecart_from_contraption_cart")

def sync_minecart_assets(g):
    find,write_json,model,walk=(g[key] for key in ("find","write_json","copy_model","walk_models"))
    for name in BLOCKS:
        relative=f"assets/create/blockstates/{name}.json";data=json.loads(find(relative).read_text());write_json(relative,data);walk(data)
        relative=f"data/create/loot_table/blocks/{name}.json";write_json(relative,g["modern_loot"](json.loads(find(relative).read_text())))
    for name in (*BLOCKS[:2],*ITEMS):
        model("create:item/"+name);write_json(f"assets/create/items/{name}.json",{"model":{"type":"minecraft:model","model":"create:item/"+name}})
    for part in ("attachment","ring","connector"):model("create:entity/minecart_coupling/"+part)
    # The coupling's genuine source entity sprite is used by native block-state meshes.
    relative="assets/minecraft/atlases/blocks.json";path=g["OUTPUT"]/relative
    atlas=json.loads(path.read_text()) if path.exists() else {"sources":[]}
    source={"type":"minecraft:single","resource":"create:entity/coupling"}
    if source not in atlas["sources"]:atlas["sources"].append(source)
    write_json(relative,atlas)
    for recipe in RECIPES:
        relative=f"data/create/recipe/{recipe}.json";write_json(relative,g["modern_ingredients"](json.loads(find(relative).read_text())))
        relative=f"data/create/advancement/recipes/misc/{recipe}.json";write_json(relative,g["modern_advancement"](json.loads(find(relative).read_text())))
    # Exact original advancement ancestors retain their original IDs and criteria.
    name="cart_pickup"
    while name:
        relative=f"data/create/advancement/{name}.json";data=json.loads(find(relative).read_text());native=g["modern_advancement"](data);existing=g["OUTPUT"]/relative
        if existing.is_file() and json.loads(existing.read_text())==native:g["write"](relative,existing.read_bytes())
        else:write_json(relative,native)
        name=data.get("parent","").removeprefix("create:")
    owned={"create:"+name for name in (*BLOCKS,*ITEMS)};root=g["ROOTS"][1]
    for namespace in ("minecraft","create","c"):
        for kind in ("block","item"):
            for path in sorted((root/f"data/{namespace}/tags/{kind}").rglob("*.json")):
                data=json.loads(path.read_text());values=[value for value in data.get("values",[]) if (value if isinstance(value,str) else value.get("id")) in owned]
                if values:write_json(path.relative_to(root).as_posix(),{"replace":False,"values":values})
    for locale in ("en_us","ru_ru","en_ud"):
        relative=f"assets/create/lang/{locale}.json";data=json.loads(find(relative).read_text())
        prefixes=("create.minecart_coupling.","create.block.cart_assembler.","create.contraptions.cart_movement_mode","create.contraption.minecart_contraption_","advancement.create.cart_pickup","advancement.create.pulley_maxed","advancement.create.ejector_maxed")
        keys={"block.create."+name for name in BLOCKS}|{"item.create."+name for name in ITEMS}
        write_json(relative,{key:value for key,value in data.items() if key in keys or key.startswith(prefixes) or any(key.startswith("item.create."+name+".") for name in ITEMS)})
    return {"sourceRecipes":RECIPES,"sourceBlocks":BLOCKS,"sourceItems":ITEMS,"nativeRuntime":"UNRUN","sodiumRequired":False}

if __name__=="__main__":
    import sys
    sys.dont_write_bytecode=True
    import sync_assets
    sync_minecart_assets(vars(sync_assets))
