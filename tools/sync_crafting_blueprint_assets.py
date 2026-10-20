"""Pinned crafting blueprint item/editor/recipe only; entity renderer closure has separate ownership."""
import json

def sync_crafting_blueprint_assets(g):
    find, write, write_json, copy_model = (g[k] for k in ('find', 'write', 'write_json', 'copy_model'))
    copy_model('create:item/crafting_blueprint')
    write_json('assets/create/items/crafting_blueprint.json', {'model': {'type': 'minecraft:model', 'model': 'create:item/crafting_blueprint'}})
    recipe = 'crafting/appliances/crafting_blueprint'
    relative = f'data/create/recipe/{recipe}.json'
    write_json(relative, g['modern_ingredients'](json.loads(find(relative).read_text())))
    relative = f'data/create/advancement/recipes/misc/{recipe}.json'
    write_json(relative, g['modern_advancement'](json.loads(find(relative).read_text())))
    for name in ('curiosities_2', 'player_inventory', 'widgets'):
        relative = f'assets/create/textures/gui/{name}.png'
        write(relative, find(relative).read_bytes())
        source = find(relative).with_suffix('.png.mcmeta')
        if source.is_file():
            write(relative + '.mcmeta', source.read_bytes())
    for locale in ('en_us', 'ru_ru', 'en_ud'):
        relative = f'assets/create/lang/{locale}.json'
        source = json.loads(find(relative).read_text())
        write_json(relative, {key: value for key, value in source.items() if key.startswith(('create.crafting_blueprint.', 'item.create.crafting_blueprint')) or key == 'entity.create.crafting_blueprint'})
    return {'owner': 'crafting_blueprint', 'sourceRecipeIds': [recipe], 'entityRendererClosureSeparatelyOwned': True, 'nativeRuntimeVerified': False}
