"""Pinned14x18 stone palette plus source layered ore; no window/roof/content relabeling."""
import json
FAMILIES=('granite','diorite','andesite','calcite','dripstone','deepslate','tuff','asurine','crimsite','limestone','ochrum','scoria','scorchia','veridium')
RAW=FAMILIES[7:]
def palette_ids():
    result=[]
    for family in FAMILIES:
        for pattern in ('cut_'+family,'polished_cut_'+family,'cut_'+family+'_bricks','small_'+family+'_bricks','layered_'+family,family+'_pillar'):
            result.append(pattern)
            if not pattern.startswith('layered_') and not pattern.endswith('_pillar'):
                stem=pattern[:-1] if pattern.endswith('bricks') else pattern
                result.extend(stem+'_'+shape for shape in ('stairs','slab','wall'))
    assert len(result)==len(set(result))==252
    return tuple(result)
PALETTE_VARIANTS=palette_ids()
PALETTE_BLOCKS=PALETTE_VARIANTS+RAW

def modern_state(state):
    if not isinstance(state,dict) or 'Name' not in state:return state
    if not state.get('Properties'):return state['Name']
    return {'id':state['Name'],'properties':state['Properties']}

def sync_palette_worldgen_assets(g):
    find,write_json,copy_model,copy_texture,walk_models,modern_loot,modern_ingredients=(g[k] for k in ('find','write_json','copy_model','copy_texture','walk_models','modern_loot','modern_ingredients'))
    owned={'create:'+name for name in PALETTE_BLOCKS};roots=g['ROOTS'];output=g['OUTPUT']
    for name in PALETTE_BLOCKS:
        relative=f'assets/create/blockstates/{name}.json';state=json.loads(find(relative).read_text());write_json(relative,state);walk_models(state);copy_model('create:item/'+name);write_json(f'assets/create/items/{name}.json',{'model':{'type':'minecraft:model','model':'create:item/'+name}})
        relative=f'data/create/loot_table/blocks/{name}.json';write_json(relative,modern_loot(json.loads(find(relative).read_text())))
    for family in FAMILIES:
        for texture in ('pillar','cap','layered'):
            copy_texture(f'create:block/palettes/stone_types/{texture}/{family}_cut_{texture}_connected')
        relative=f'data/create/tags/item/stone_types/{family}.json';write_json(relative,json.loads(find(relative).read_text()))
    for ns in ('minecraft','create','c'):
        for kind in ('block','item'):
            for source in sorted((roots[1]/f'data/{ns}/tags/{kind}').rglob('*.json')):
                data=json.loads(source.read_text());members=[]
                for value in data.get('values',[]):
                    ident=value if isinstance(value,str) else value.get('id')
                    if ident in owned:members.append(value)
                if members:write_json(source.relative_to(roots[1]).as_posix(),{'replace':False,'values':members})
    recipes=[]
    for source in sorted((roots[1]/'data/create/recipe').rglob('*.json')):
        data=json.loads(source.read_text());result=data.get('result',{});identifier=result if isinstance(result,str) else result.get('id');ingredient=data.get('ingredient',{});material=ingredient.get('tag','') if isinstance(ingredient,dict) else ''
        if identifier not in owned and material not in {'create:stone_types/'+f for f in FAMILIES}:continue
        if data.get('neoforge:conditions'):continue
        relative=source.relative_to(roots[1]).as_posix();write_json(relative,modern_ingredients(data));recipes.append(relative.removeprefix('data/create/recipe/').removesuffix('.json'))
    for source in sorted((roots[1]/'data/create/advancement/recipes').rglob('*.json')):
        data=json.loads(source.read_text());rewards=data.get('rewards',{}).get('recipes',[])
        if any(recipe.removeprefix('create:') in recipes for recipe in rewards):write_json(source.relative_to(roots[1]).as_posix(),g['modern_advancement'](data))
    for locale in ('en_us','ru_ru','en_ud'):
        relative=f'assets/create/lang/{locale}.json'
        try:source=json.loads(find(relative).read_text())
        except FileNotFoundError:continue
        keys={'block.create.'+name for name in PALETTE_BLOCKS}|{'itemGroup.create.palettes'};write_json(relative,{k:v for k,v in source.items() if k in keys})
    for name in ('striated_ores_overworld','striated_ores_nether'):
        source=json.loads(find('data/create/worldgen/configured_feature/'+name+'.json').read_text());data={'type':source['type'],**source['config']}
        for pattern in data['layer_patterns']:
            for layer in pattern:
                for targets in layer['targets']:
                    for target in targets:target['state']=modern_state(target['state'])
        write_json('data/create/worldgen/feature/'+name+'.json',data)
        write_json('data/create/worldgen/placed_feature/'+name+'.json',json.loads(find('data/create/worldgen/placed_feature/'+name+'.json').read_text()))
    # The source disableWorldGen filter also gates zinc. Restore it after the older base generator's biome adapter.
    write_json('data/create/worldgen/placed_feature/zinc_ore.json',json.loads(find('data/create/worldgen/placed_feature/zinc_ore.json').read_text()))
    return {'families':len(FAMILIES),'variants':len(PALETTE_VARIANTS),'newRaw':'scorchia','sourcePaletteRecipes':len(recipes),'sourceRecipeIds':recipes,'worldgenFeatureIds':['striated_ores_overworld','striated_ores_nether'],'existingWorldRewrite':False}
