#!/usr/bin/env python3
"""Pinned legacy item geometry, original sprites, native tint definitions and source names."""
import json

ITEMS=("chromatic_compound","shadow_steel","refined_radiance")
SPRITES=("chromatic_compound_0","chromatic_compound_1","chromatic_compound_2","shadow_steel","refined_radiance")

def sync(g):
    find,write,write_json=(g[k]for k in ("find","write","write_json"))
    for item in ITEMS:
        relative=f"assets/create/models/item/{item}.json"
        write_json(relative,json.loads(find(relative).read_text()))
        model={"type":"minecraft:model","model":f"create:item/{item}"}
        if item=="chromatic_compound":
            model["tints"]=[{"type":"create:chromatic_compound","layer":layer}for layer in range(3)]
        write_json(f"assets/create/items/{item}.json",{"model":model})
    for sprite in SPRITES:
        relative=f"assets/create/textures/item/{sprite}.png";write(relative,find(relative).read_bytes())
        try:metadata=find(relative+".mcmeta")
        except FileNotFoundError:pass
        else:write(relative+".mcmeta",metadata.read_bytes())
    # Generated English/upside-down names are distinct pinned inputs from main locale files.
    names={}
    for source_root in g["ROOTS"]:
        directory=source_root/"assets/create/lang"
        for source in sorted(directory.glob("*.json")):
            selected={k:v for k,v in json.loads(source.read_text()).items()
                if any(k=="item.create."+item or k.startswith("item.create."+item+".")for item in ITEMS)}
            if selected:names.setdefault(source.name,{}).update(selected)
    for filename,selected in names.items():
        relative="assets/create/lang/"+filename;destination=g["OUTPUT"]/relative
        merged=json.loads(destination.read_text())if destination.is_file()else{}
        merged.update(selected)
        write(relative,(json.dumps(merged,ensure_ascii=False,indent=2)+"\n").encode())
    return {"items":3,"source_sprites":5,"native_tint_layers":3,"translated_locales":len(names),
        "source_translation_keys":sum(map(len,names.values())),"runtime":"UNRUN"}

if __name__=="__main__":
    import sys
    sys.dont_write_bytecode=True
    import sync_assets
    sync(vars(sync_assets))
