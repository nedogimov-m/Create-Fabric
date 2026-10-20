"""Pinned armor/backtank resources, migrated to genuine 26.3 equipment and palette formats."""
from __future__ import annotations
import json

TANKS=("copper_backtank","netherite_backtank")
DIVING=("copper_diving_helmet","netherite_diving_helmet","copper_diving_boots","netherite_diving_boots")
CARDBOARD=tuple("cardboard_"+piece for piece in ("helmet","chestplate","leggings","boots"))
TRIMS=("quartz","iron","netherite","redstone","copper","gold","emerald","diamond","lapis","amethyst")


def generate(context):
    find,write,write_json=context["find"],context["write"],context["write_json"]
    copy_model,modern_ingredients,modern_loot=context["copy_model"],context["modern_ingredients"],context["modern_loot"]
    output=context["OUTPUT"]
    for name in TANKS:
        for part in ("block","block_cogs","block_shaft_input","item"):
            copy_model(f"create:block/{name}/{part}")
        write_json(f"assets/create/blockstates/{name}.json",json.loads(find(f"assets/create/blockstates/{name}.json").read_text()))
        write_json(f"data/create/loot_table/blocks/{name}.json",modern_loot(json.loads(find(f"data/create/loot_table/blocks/{name}.json").read_text())))
    for name in (*TANKS,*(name+"_placeable" for name in TANKS),*DIVING):
        copy_model("create:item/"+name)
        write_json(f"assets/create/items/{name}.json",{"model":{"type":"minecraft:model","model":"create:item/"+name}})
    for name in CARDBOARD:
        # Old overrides become native select models; generated trim layer1 sprites are paletted atlas entries.
        for material in (None,*TRIMS):
            model=name+("_"+material+"_trim" if material else "")
            data=json.loads(find(f"assets/create/models/item/{model}.json").read_text())
            data.pop("overrides",None)
            write_json(f"assets/create/models/item/{model}.json",data)
        write(f"assets/create/textures/item/{name}.png",find(f"assets/create/textures/item/{name}.png").read_bytes())
        cases=[{"when":"minecraft:"+material,"model":{"type":"minecraft:model","model":f"create:item/{name}_{material}_trim"}} for material in TRIMS]
        # The modern native resin palette also uses the same pinned cardboard mask.
        data=json.loads(find(f"assets/create/models/item/{name}_quartz_trim.json").read_text())
        data["textures"]["layer1"]=f"create:trims/items/card_{name.removeprefix('cardboard_')}_trim_resin"
        write_json(f"assets/create/models/item/{name}_resin_trim.json",data)
        cases.append({"when":"minecraft:resin","model":{"type":"minecraft:model","model":f"create:item/{name}_resin_trim"}})
        write_json(f"assets/create/items/{name}.json",{"model":{"type":"minecraft:select","property":"minecraft:trim_material","cases":cases,"fallback":{"type":"minecraft:model","model":"create:item/"+name}}})
    for name in ("copper_diving","netherite_diving","cardboard"):
        write_json(f"assets/create/equipment/{name}.json",{"layers":{"humanoid":[{"texture":"create:"+name}],**({"humanoid_leggings":[{"texture":"create:"+name}]} if name!="copper_diving" else {})}})
        for layer in (1,2) if name!="copper_diving" else (1,):
            source=f"assets/create/textures/models/armor/{name}_layer_{layer}.png"
            write(source,find(source).read_bytes())
            native="humanoid" if layer==1 else "humanoid_leggings"
            write(f"assets/create/textures/entity/equipment/{native}/{name}.png",find(source).read_bytes())
    for relative in ("assets/create/textures/models/armor/netherite_diving_arm.png","assets/create/textures/misc/package_blur.png","assets/create/textures/misc/package_blur.png.mcmeta"):
        write(relative,find(relative).read_bytes())
    # Native 26.3 dynamic entity trim recoloring reads this metadata and palettes from textures/palettes/.
    original_atlas=json.loads(find("assets/minecraft/atlases/armor_trims.json").read_text())
    for texture in original_atlas["sources"][0]["textures"]:
        old=texture.removeprefix("create:");path=old.removeprefix("trims/models/armor/")
        leggings=path.endswith("_leggings")
        target=f"assets/create/textures/trims/entity/{'humanoid_leggings' if leggings else 'humanoid'}/{path.removesuffix('_leggings')}.png"
        write(target,find("assets/create/textures/"+old+".png").read_bytes())
        write_json(target+".mcmeta",{"palette":{"base_palette":"minecraft:trim_base"}})
    for piece in ("helmet","chestplate","leggings","boots"):
        path=f"assets/create/textures/trims/items/card_{piece}_trim.png"
        write(path,find(path).read_bytes())
    atlas=output/"assets/minecraft/atlases/items.json"
    data=json.loads(atlas.read_text()) if atlas.exists() else {"sources":[]}
    source={"type":"minecraft:paletted_permutations","textures":[f"create:trims/items/card_{piece}_trim" for piece in ("helmet","chestplate","leggings","boots")],"palette_key":"minecraft:trim_base","permutations":{material:"minecraft:trim/"+material for material in (*TRIMS,"resin")}}
    if source not in data["sources"]:data["sources"].append(source)
    write_json("assets/minecraft/atlases/items.json",data)
    def tag(relative,values):write_json(relative,{"replace":False,"values":values})
    tag("data/create/tags/item/pressurized_air_sources.json",["create:"+name for name in TANKS])
    tag("data/create/tags/item/repairs/copper_armor.json",["minecraft:copper_ingot"])
    tag("data/create/tags/item/repairs/cardboard_armor.json",["create:cardboard"])
    for slot,pieces in {"head":[DIVING[0],DIVING[1],CARDBOARD[0]],"chest":[*TANKS,CARDBOARD[1]],"leg":[CARDBOARD[2]],"foot":[DIVING[2],DIVING[3],CARDBOARD[3]]}.items():
        tag(f"data/minecraft/tags/item/{slot}_armor.json",["create:"+name for name in pieces])
        tag(f"data/minecraft/tags/item/enchantable/{slot}_armor.json",["create:"+name for name in pieces])
    tag("data/minecraft/tags/item/enchantable/durability.json",["create:"+name for name in (*DIVING,*CARDBOARD)])
    tag("data/minecraft/tags/item/trimmable_armor.json",["create:"+name for name in (*TANKS,*DIVING,*CARDBOARD)])
    tag("data/minecraft/tags/block/mineable/pickaxe.json",["create:"+name for name in TANKS])
    for name in (*TANKS,*DIVING,*CARDBOARD,"netherite_backtank_from_netherite","netherite_diving_helmet_from_netherite","netherite_diving_boots_from_netherite"):
        relative=f"data/create/recipe/crafting/appliances/{name}.json"
        write_json(relative,modern_ingredients(json.loads(find(relative).read_text())))
    for name in ("backtank","diving_suit","diving_suit_lava","cardboard_armor","cardboard_armor_trim"):
        relative=f"data/create/advancement/{name}.json"
        write_json(relative,json.loads(find(relative).read_text()))
    sound=json.loads(find("assets/create/sounds.json").read_text())["copper_armor_equip"]
    write_json("assets/create/sounds.json",{"copper_armor_equip":sound})
    for root in context["ROOTS"]:
        lang=root/"assets/create/lang"
        if not lang.exists():continue
        for source in lang.glob("*.json"):
            original=json.loads(source.read_text())
            selected={k:v for k,v in original.items() if any(k.startswith(prefix) for prefix in ("item.create."+name for name in (*TANKS,*DIVING,*CARDBOARD,"cardboard_armor"))) or k.startswith("block.create.copper_backtank") or k.startswith("block.create.netherite_backtank") or k.startswith("create.subtitle.copper_armor_equip")}
            if selected:write_json("assets/create/lang/"+source.name,selected)
