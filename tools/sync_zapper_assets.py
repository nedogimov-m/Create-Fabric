"""Pinned Creative Worldshaper resources for the genuine native model/GUI renderer."""
import json


def sync_zapper_assets(g):
    find, write, write_json, copy_model = (g[name] for name in ('find','write','write_json','copy_model'))
    output=g['OUTPUT']
    for part in ('item','core','core_glow','accelerator'):
        copy_model('create:item/handheld_worldshaper/'+part)
    write_json('assets/create/models/item/handheld_worldshaper.json',{'parent':'create:item/handheld_worldshaper/item'})
    write_json('assets/create/items/handheld_worldshaper.json',{'model':{'type':'minecraft:model','model':'create:item/handheld_worldshaper'}})
    for relative in ('assets/create/textures/gui/curiosities.png','assets/create/textures/gui/icons.png','assets/create/textures/gui/widgets.png','assets/create/textures/special/checkerboard.png'):
        write(relative,find(relative).read_bytes())
    for locale in ('en_us','ru_ru','en_ud'):
        relative='assets/create/lang/'+locale+'.json'
        try: source=json.loads(find(relative).read_text())
        except FileNotFoundError: continue
        target=output/relative
        translated=json.loads(target.read_text()) if target.exists() else {}
        translated.update({key:value for key,value in source.items() if key.startswith(('create.terrainzapper.','create.gui.terrainzapper.','item.create.handheld_worldshaper','create.subtitle.worldshaper_place')) or key in ('create.generic.width','create.generic.height','create.generic.length','create.generic.radius','create.generic.height','create.generic.range')})
        write_json(relative,translated)
    relative='assets/create/sounds.json'
    target=json.loads((output/relative).read_text())
    sound=json.loads(find(relative).read_text())['worldshaper_place']
    target['worldshaper_place']=sound
    write_json(relative,target)
    for variant in sound['sounds']:
        name=variant if isinstance(variant,str) else variant['name']
        kind='file' if isinstance(variant,str) else variant.get('type','file')
        if kind=='file' and name.startswith('create:'):
            relative='assets/create/sounds/'+name.removeprefix('create:')+'.ogg'
            write(relative,find(relative).read_bytes())
