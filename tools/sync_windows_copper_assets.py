"""Source windows/panes and48 native copper roof owners. Additive dedicated slice."""
import json
WOODS=('oak','spruce','birch','jungle','acacia','dark_oak','mangrove','crimson','warped','cherry','bamboo')
WINDOW_BASES=('tiled_glass','framed_glass','horizontal_framed_glass','vertical_framed_glass')+tuple(x+'_window' for x in WOODS+('ornate_iron','industrial_iron','weathered_iron'))
WINDOWS=WINDOW_BASES+tuple(x+'_pane' for x in WINDOW_BASES)
ROOFS=tuple(('waxed_' if waxed else '')+age+(family if not shape else family[:-1]+shape) for family in ('copper_shingles','copper_tiles') for waxed in (False,True) for age in ('','exposed_','weathered_','oxidized_') for shape in ('','_slab','_stairs'))
OWNERS=WINDOWS+ROOFS+('weathered_iron_block',)
assert len(WINDOWS)==36 and len(ROOFS)==48 and len(set(OWNERS))==85

def sync_windows_copper_assets(g):
 find,write_json,copy_model,copy_texture,walk_models,modern_loot,modern_ingredients=(g[k] for k in ('find','write_json','copy_model','copy_texture','walk_models','modern_loot','modern_ingredients'))
 root=g['ROOTS'][1];owned={'create:'+x for x in OWNERS}
 for name in OWNERS:
  rel=f'assets/create/blockstates/{name}.json';data=json.loads(find(rel).read_text());write_json(rel,data);walk_models(data);copy_model('create:item/'+name);write_json(f'assets/create/items/{name}.json',{'model':{'type':'minecraft:model','model':'create:item/'+name}})
  rel=f'data/create/loot_table/blocks/{name}.json';write_json(rel,modern_loot(json.loads(find(rel).read_text())))
 for texture in tuple('palettes/'+x+'_window' for x in WOODS)+tuple('palettes/'+x for x in ('framed_glass','horizontal_framed_glass','vertical_framed_glass','ornate_iron_window','industrial_iron_window'))+tuple('palettes/weathered_iron_window_'+str(i) for i in range(1,5))+tuple('copper/'+age+family+'_top' for age in ('','exposed_','weathered_','oxidized_') for family in ('copper_shingles','copper_tiles')):
  copy_texture('create:block/'+texture+'_connected')
 for ns in ('minecraft','create','c'):
  for kind in ('block','item'):
   for path in sorted((root/f'data/{ns}/tags/{kind}').rglob('*.json')):
    data=json.loads(path.read_text());values=[v for v in data.get('values',[]) if (v if isinstance(v,str) else v.get('id')) in owned]
    if values:write_json(path.relative_to(root).as_posix(),{'replace':False,'values':values})
 def identifiers(value):
  if isinstance(value,str):yield value
  elif isinstance(value,list):
   for x in value:yield from identifiers(x)
  elif isinstance(value,dict):
   for x in value.values():yield from identifiers(x)
 recipes=[];processing=[]
 for path in sorted((root/'data/create/recipe').rglob('*.json')):
  data=json.loads(path.read_text());outputs=set(identifiers(data.get('result',data.get('results',{}))));inputs=set(identifiers(data.get('ingredient',data.get('ingredients',{}))))
  if not outputs&owned and not (outputs&{'minecraft:glass','minecraft:glass_pane'} and inputs&owned):continue
  if data.get('neoforge:conditions'):continue
  rel=path.relative_to(root).as_posix();is_processing=data['type'].startswith('create:');write_json(rel,g['modern_processing'](data) if is_processing else modern_ingredients(data));recipes.append(rel.removeprefix('data/create/recipe/').removesuffix('.json'))
  if is_processing:processing.append(recipes[-1])
 advancements=[]
 for path in sorted((root/'data/create/advancement/recipes').rglob('*.json')):
  data=json.loads(path.read_text())
  if any(x.removeprefix('create:') in recipes for x in data.get('rewards',{}).get('recipes',[])):
   rel=path.relative_to(root).as_posix();write_json(rel,g['modern_advancement'](data));advancements.append(rel)
 for locale in ('en_us','ru_ru','en_ud'):
  rel=f'assets/create/lang/{locale}.json';data=json.loads(find(rel).read_text());keys={'block.create.'+x for x in OWNERS};write_json(rel,{k:v for k,v in data.items() if k in keys})
 return {'sourceWindowOwners':36,'newWindowOwners':35,'reusedSingleton':'create:framed_glass','copperRoofOwners':48,'sourceDependency':'create:weathered_iron_block','sourceRecipeIds':recipes,'processingRecipeIds':processing,'sourceAdvancementPaths':advancements,'wholeGeneratorExecuted':False,'runtimeVerified':False}
